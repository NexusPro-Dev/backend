package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.importe;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.commissions.domain.service.PayCommissionBatchService;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.ConcurrencyHarness;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Corregir el vendedor de una línea ya comisionada (`RF-CM-024`, `CA-CM-290` a `CA-CM-299`; y
 * `RF-MV-016`, `CA-MV-351` a `CA-MV-356`). <b>Todo por la ruta de `RF-MV-016`</b>, que es la única
 * que llega al puerto: así entran en la prueba el {@code MANDATORY}, el aviso y el devengo de la
 * cadena nueva.
 *
 * <p>La cadena: dos agentes, <b>A</b> —el vendedor viejo— y <b>B</b> —el nuevo—, los dos con el
 * mismo director <b>D</b>, que por eso está en las dos cadenas. El agente cobra el 10 % y el
 * director el 5 % de un producto de 100.
 */
@AutoConfigureMockMvc
class ReleaseCommissionedLineIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);
  private static final String ASIGNAR = "movements:assign-sellers";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private PayCommissionBatchService pago;

  private UUID admin;
  private UUID cliente;
  private UUID viejo;
  private UUID nuevo;
  private UUID director;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    admin = SettlementFixtures.persona(jdbc, "rl-admin", null);
    cliente = SettlementFixtures.persona(jdbc, "rl-cliente", null);
    viejo = SettlementFixtures.persona(jdbc, "rl-viejo", AGENTE);
    nuevo = SettlementFixtures.persona(jdbc, "rl-nuevo", AGENTE);
    director = SettlementFixtures.persona(jdbc, "rl-director", DIRECTOR);
    SettlementFixtures.superior(jdbc, viejo, director);
    SettlementFixtures.superior(jdbc, nuevo, director);
    vincular(viejo, "REGISTRO");
    vincular(nuevo, "HOTLINK");
    producto = SettlementFixtures.producto(jdbc, "RL", "100.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-CM-340, CA-MV-700 — corregir el vendedor BORRA toda la cadena vieja, en lotes pendientes,"
          + " y cada lote rebaja su total")
  void borraLaCadenaVieja() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    cierre.closeManually(admin);
    List<UUID> viejas = comisionesDe(linea);
    assertThat(viejas).hasSize(2);

    mvc.perform(corregir(venta, nuevo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].seller.id").value(nuevo.toString()));

    assertThat(comisionesDe(linea)).hasSize(2).doesNotContainAnyElementsOf(viejas);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commissions WHERE id IN (?, ?)",
                Integer.class,
                viejas.get(0),
                viejas.get(1)))
        .isZero();
    assertThat(total(pendienteDe(viejo))).isEqualByComparingTo("0");
    // Rebaja su 5 y, desde `RN-CM-051`, recibe en el mismo pendiente el 5 de la
    // cadena nueva: lo viejo ya no está (arriba) y el total es solo lo nuevo.
    assertThat(total(pendienteDe(director))).isEqualByComparingTo("5.00");
  }

  @Test
  @DisplayName(
      "CA-CM-356 y CA-CM-358 — tras el cierre, la cadena NUEVA va al PENDIENTE sin pagar de quien"
          + " lo tiene y a un ABIERTO nuevo de quien no tiene ninguno, con la tasa y la cadena del"
          + " día de la venta; la marca se borra")
  void devengaLaNueva() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    cierre.closeManually(admin);
    UUID pendienteDelDirector = pendienteDe(director);

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    var vivas =
        jdbc.queryForList(
            "SELECT k.user_id, k.commission_amount, k.resolved_on, b.status FROM commissions k"
                + " JOIN commission_batches b ON b.id = k.batch_id"
                + " WHERE k.movement_detail_id = ?",
            linea);
    assertThat(vivas).hasSize(2);
    // CA-CM-356: el director tenía su pendiente, y la comisión va a él.
    assertThat(loteDeLaComision(linea, director)).isEqualTo(pendienteDelDirector);
    assertThat(total(pendienteDelDirector)).isEqualByComparingTo("5.00");
    assertThat(lotesDe(director)).isEqualTo(1);
    // CA-CM-358: el vendedor nuevo no tenía ningún lote: se le abre uno.
    assertThat(lotesDe(nuevo)).isEqualTo(1);
    assertThat(loteDeLaComision(linea, nuevo)).isEqualTo(abiertoDe(nuevo));
    assertThat(marcas(linea)).isZero();
    assertThat(vivas)
        .allSatisfy(
            f ->
                assertThat(((java.sql.Date) f.get("resolved_on")).toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 9, 10)));
    assertThat(vivas.stream().map(f -> f.get("user_id")).toList())
        .containsExactlyInAnyOrder(nuevo, director);
    assertThat(
            jdbc.queryForObject(
                "SELECT outcome FROM commission_accruals WHERE movement_detail_id = ?",
                String.class,
                linea))
        .isEqualTo("DEVENGADA");
  }

  @Test
  @DisplayName(
      "CA-CM-341 — quien está en las DOS cadenas acaba con UNA sola comisión de la línea: la nueva")
  void enLasDosCadenas() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    UUID vieja = comisionDe(linea, director);

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    var delDirector =
        jdbc.queryForList(
            "SELECT id FROM commissions WHERE movement_detail_id = ? AND user_id = ?",
            UUID.class,
            linea,
            director);
    assertThat(delDirector).hasSize(1).doesNotContain(vieja);
  }

  @Test
  @DisplayName(
      "CA-CM-293, CA-MV-352 — si ALGUNA comisión de la línea está PAGADA —aunque sea la del"
          + " superior—, 409 y nada cambia")
  void pagadaNo() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    cierre.closeManually(admin);
    pago.pay(pendienteDe(director));
    List<UUID> antes = comisionesDe(linea);

    mvc.perform(corregir(venta, nuevo))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(
            jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("pagó")));

    assertThat(vendedorDe(linea)).isEqualTo(viejo);
    assertThat(comisionesDe(linea)).isEqualTo(antes);
    assertThat(total(pendienteDe(viejo))).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("CA-CM-294, CA-MV-353 — un FTD ya CONTADO no se corrige; uno aún no contado, sí")
  void ftd() throws Exception {
    UUID ftd = AfftrackFixtures.productoFtd(jdbc, "RLFTD", false);
    // Confirmadas por SQL: confirmar un upgrade por la API concede la membresía,
    // y lo que aquí importa es solo que la venta esté CONFIRMADA.
    UUID contada = SettlementFixtures.venta(jdbc, cliente, VENDIDA_EL, linea(ftd, viejo, 1, "0"));
    UUID sinContar = SettlementFixtures.venta(jdbc, cliente, VENDIDA_EL, linea(ftd, viejo, 1, "0"));
    jdbc.update(
        "UPDATE movements SET status = 'CONFIRMADA', confirmed_at = now() WHERE id IN (?, ?)",
        contada,
        sinContar);
    cierre.closeManually(admin);
    UUID liquidacion = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO afftrack_settlements (id, closing_id, user_id, product_id, carried_in,"
            + " new_ftds, paid_ftds, carried_out, created_at)"
            + " SELECT ?, id, ?, ?, 0, 1, 0, 1, now() FROM commission_closings LIMIT 1",
        liquidacion,
        viejo,
        ftd);
    jdbc.update(
        "INSERT INTO afftrack_ftds (movement_detail_id, user_id, chain_level, settlement_id,"
            + " created_at) VALUES (?, ?, 0, ?, now())",
        lineaDe(contada),
        viejo,
        liquidacion);

    mvc.perform(corregir(contada, ftd, nuevo))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(
            jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("FTD")));
    assertThat(vendedorDe(lineaDe(contada))).isEqualTo(viejo);

    mvc.perform(corregir(sinContar, ftd, nuevo)).andExpect(status().isOk());
    assertThat(vendedorDe(lineaDe(sinContar))).isEqualTo(nuevo);
  }

  @Test
  @DisplayName(
      "CA-CM-295 — una línea SIN COMISIÓN se corrige, y la cadena nueva se intenta como una línea"
          + " nueva")
  void sinComision() throws Exception {
    UUID gratis = SettlementFixtures.producto(jdbc, "RLSIN", "100.00");
    UUID venta =
        SettlementFixtures.venta(jdbc, cliente, VENDIDA_EL, linea(gratis, viejo, 1, "100.00"));
    confirmar(venta);
    UUID linea = lineaDe(venta);
    assertThat(desenlace(linea)).isEqualTo("SIN_COMISION");
    // Una tasa que llega tarde: SIN_COMISION es definitivo, pero la línea
    // reatribuida se intenta como nueva.
    CommissionFixtures.sembrarTasaDeRol(jdbc, gratis, AGENTE, "10.00");

    mvc.perform(corregir(venta, gratis, nuevo)).andExpect(status().isOk());

    assertThat(desenlace(linea)).isEqualTo("DEVENGADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT user_id FROM commissions WHERE movement_detail_id = ? AND chain_level = 0",
                UUID.class,
                linea))
        .isEqualTo(nuevo);
  }

  @Test
  @DisplayName(
      "CA-CM-342 — una comisión RETIRADA al abierto se borra allí, y su pendiente de origen deja de"
          + " listarla")
  void retirada() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    cierre.closeManually(admin);
    UUID pendiente = pendienteDe(viejo);
    UUID comision = comisionDe(linea, viejo);
    mvc.perform(
            post(
                    "/api/v1/commission-batches/{id}/commissions/{commissionId}/withdrawal",
                    pendiente,
                    comision)
                .with(como("commission-batches:withdraw-commission")))
        .andExpect(status().isOk());
    UUID abierto = abiertoDe(viejo);

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commissions WHERE id = ?", Integer.class, comision))
        .isZero();
    assertThat(total(abierto)).isEqualByComparingTo("0");
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/commission-batches/{id}", pendiente)
                .with(como("commission-batches:read-detail")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.withdrawn.length()").value(0));
    mvc.perform(
            post(
                    "/api/v1/commission-batches/{id}/commissions/{commissionId}/return",
                    pendiente,
                    comision)
                .with(como("commission-batches:return-commission")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("retiradas")));
  }

  @Test
  @DisplayName(
      "CA-CM-297, CA-MV-354, CA-MV-356 — dos líneas, una liberable y otra pagada: 409 y NADA"
          + " cambia, tampoco la reversión de la primera; y asignar la que no tenía vendedor no"
          + " pregunta a CM")
  void dosLineasUnaPagada() throws Exception {
    UUID otro = SettlementFixtures.producto(jdbc, "RL2", "100.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, otro, AGENTE, "10.00");
    UUID venta =
        SettlementFixtures.venta(
            jdbc,
            cliente,
            VENDIDA_EL,
            linea(producto, viejo, 1, "100.00"),
            linea(otro, null, 1, "100.00"));
    confirmar(venta);
    cierre.closeManually(admin);
    pago.pay(pendienteDe(viejo));
    // CA-MV-356: la línea sin vendedor se asigna en la venta confirmada, sin
    // preguntar a CM, y devenga en el abierto.
    mvc.perform(asignar(venta, par(otro, viejo)).with(conPermiso())).andExpect(status().isOk());
    UUID lineaOtra = lineaDe(venta, otro);
    assertThat(desenlace(lineaOtra)).isEqualTo("DEVENGADA");
    List<UUID> antes = comisionesDe(lineaOtra);

    // La liberable primero: si la reversión quedara escrita, se vería.
    mvc.perform(asignar(venta, par(otro, nuevo), par(producto, nuevo)).with(conPermiso()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(vendedorDe(lineaOtra)).isEqualTo(viejo);
    assertThat(vendedorDe(lineaDe(venta, producto))).isEqualTo(viejo);
    assertThat(comisionesDe(lineaOtra)).isEqualTo(antes);
    assertThat(desenlace(lineaOtra)).isEqualTo("DEVENGADA");
  }

  @Test
  @DisplayName(
      "CA-CM-298 — corregir y pagar a la vez: o se niega la corrección, o el pago abona el total"
          + " SIN la comisión borrada")
  void corregirYPagarALaVez() throws Exception {
    UUID venta = confirmada(viejo);
    confirmada(viejo); // otra venta, para que el pendiente del director no se quede vacío
    cierre.closeManually(admin);
    UUID delDirector = pendienteDe(director);

    ConcurrencyHarness.runTogether(
        List.of(
            () -> mvc.perform(corregir(venta, nuevo)).andReturn().getResponse().getStatus(),
            () -> {
              pago.pay(delDirector);
              return 200;
            }));

    int quedan =
        jdbc.queryForObject(
            "SELECT count(*) FROM commissions WHERE batch_id = ?", Integer.class, delDirector);
    // 10 de dos ventas al 5 %, menos lo borrado antes de pagar.
    assertThat(billeteraDe(director)).isEqualByComparingTo(BigDecimal.valueOf(5L * quedan));
  }

  @Test
  @DisplayName(
      "CA-CM-343 — queda AUDITADO en CM: la línea, cada comisión borrada con su lote e importe, y"
          + " quién corrigió")
  void auditado() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'commission_accruals'"
                + " AND entity_id = ? AND module = 'CM' AND changes::text LIKE '%deleted_by%'"
                + " ORDER BY occurred_at DESC LIMIT 1",
            String.class, linea);
    assertThat(cambios)
        .contains(admin.toString())
        .contains(viejo.toString())
        .contains(director.toString())
        .contains("commission_amount");
  }

  @Test
  @DisplayName(
      "CA-MV-355 — reescribir el MISMO vendedor en una línea confirmada y pagada se admite y no"
          + " borra nada")
  void mismoVendedor() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    cierre.closeManually(admin);
    pago.pay(pendienteDe(viejo));
    List<UUID> antes = comisionesDe(linea);

    mvc.perform(corregir(venta, viejo)).andExpect(status().isOk());

    assertThat(comisionesDe(linea)).isEqualTo(antes);
  }

  @Test
  @DisplayName(
      "CA-CM-344 — el detalle ya NO muestra la comisión borrada ni lleva revertedAt; total y número"
          + " de comisiones son los de las que quedan")
  void elDetalleYaNoMuestraLoBorrado() throws Exception {
    UUID venta = confirmada(viejo);
    confirmada(viejo);
    cierre.closeManually(admin);
    UUID pendiente = pendienteDe(viejo);

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/commission-batches/{id}", pendiente)
                .with(como("commission-batches:read-detail")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmount").value(10.0))
        .andExpect(jsonPath("$.commissionsCount").value(1))
        .andExpect(jsonPath("$.commissions.length()").value(1))
        .andExpect(jsonPath("$.commissions[0].revertedAt").doesNotExist())
        .andExpect(jsonPath("$.commissions[0].revertedBy").doesNotExist());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/commission-batches")
                .param("userId", viejo.toString())
                .param("status", "PENDIENTE")
                .with(como("commission-batches:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].commissionsCount").value(1));
  }

  @Test
  @DisplayName(
      "CA-CM-345 — en sus propios lotes, el vendedor ya NO ve la comisión borrada, y su total es el"
          + " de las que quedan")
  void misLotesYaNoMuestranLoBorrado() throws Exception {
    UUID venta = confirmada(viejo);
    confirmada(viejo);
    cierre.closeManually(admin);
    UUID pendiente = pendienteDe(viejo);

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/commission-batches/mine/{id}", pendiente)
                .with(user(viejo.toString()).authorities(() -> "commission-batches:read-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmount").value(10.0))
        .andExpect(jsonPath("$.commissions.length()").value(1));
  }

  @Test
  @DisplayName(
      "CA-CM-346 y CA-CM-356 — la línea corregida se devenga OTRA VEZ, y el superior de las dos"
          + " cadenas queda con una sola comisión de ella, en su pendiente sin pagar")
  void seDevengaOtraVez() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    cierre.closeManually(admin);

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    assertThat(desenlace(linea)).isEqualTo("DEVENGADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT b.id FROM commissions k JOIN commission_batches b ON b.id = k.batch_id"
                    + " WHERE k.movement_detail_id = ? AND k.user_id = ?",
                UUID.class,
                linea,
                director))
        .isEqualTo(pendienteDe(director));
  }

  @Test
  @DisplayName(
      "CA-CM-357 — quien tiene un ABIERTO y un PENDIENTE recibe la comisión nueva en el ABIERTO,"
          + " que es su lote sin pagar más reciente")
  void conAbiertoVaAlAbierto() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    cierre.closeManually(admin);
    confirmada(viejo);
    UUID abiertoDelDirector = abiertoDe(director);

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    assertThat(loteDeLaComision(linea, director)).isEqualTo(abiertoDelDirector);
    assertThat(total(pendienteDe(director))).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName(
      "CA-CM-358 — quien solo tiene lotes PAGADOS recibe la comisión nueva en un ABIERTO que se"
          + " abre")
  void soloPagadosAbreUno() throws Exception {
    UUID suya = confirmada(nuevo);
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    cierre.closeManually(admin);
    pago.pay(pendienteDe(nuevo));
    assertThat(lotesDe(nuevo)).isEqualTo(1);

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    assertThat(lotesDe(nuevo)).isEqualTo(2);
    assertThat(loteDeLaComision(linea, nuevo)).isEqualTo(abiertoDe(nuevo));
    assertThat(loteDeLaComision(lineaDe(suya), nuevo)).isNotEqualTo(abiertoDe(nuevo));
  }

  @Test
  @DisplayName(
      "CA-CM-359 — una venta NUEVA tras el cierre va al ABIERTO, que se abre, aunque haya un"
          + " pendiente sin pagar: solo la línea reatribuida va al pendiente")
  void laVentaNuevaVaAlAbierto() throws Exception {
    confirmada(viejo);
    cierre.closeManually(admin);
    UUID nueva = confirmada(viejo);

    assertThat(lotesDe(director)).isEqualTo(2);
    assertThat(loteDeLaComision(lineaDe(nueva), director)).isEqualTo(abiertoDe(director));
    assertThat(marcas(lineaDe(nueva))).isZero();
  }

  @Test
  @DisplayName(
      "CA-CM-360 — una reatribuida RECHAZADA conserva la marca, y su reintento del cierre va al"
          + " lote más reciente sin pagar")
  void laRechazadaConservaLaMarca() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    cierre.closeManually(admin);
    UUID pendienteDelDirector = pendienteDe(director);
    // 96 % del agente y 5 % del director: la cadena nueva pasa del 100 %.
    tasaDelAgente("96.00");

    mvc.perform(corregir(venta, nuevo)).andExpect(status().isOk());

    assertThat(desenlace(linea)).isEqualTo("RECHAZADA");
    assertThat(marcas(linea)).isEqualTo(1);

    tasaDelAgente("10.00");
    cierre.closeManually(admin);

    assertThat(desenlace(linea)).isEqualTo("DEVENGADA");
    assertThat(loteDeLaComision(linea, director)).isEqualTo(pendienteDelDirector);
    assertThat(marcas(linea)).isZero();
  }

  @Test
  @DisplayName(
      "CA-CM-305 — si el aviso de la corrección se PIERDE, el barrido del siguiente cierre devenga"
          + " la línea")
  void elBarridoLaRecoge() throws Exception {
    UUID venta = confirmada(viejo);
    UUID linea = lineaDe(venta);
    // Lo que deja una corrección cuyo aviso se perdió: el vendedor nuevo, la
    // cadena vieja borrada y la línea sin desenlace.
    jdbc.update("UPDATE movement_details SET seller_id = ? WHERE id = ?", nuevo, linea);
    jdbc.update("DELETE FROM commissions WHERE movement_detail_id = ?", linea);
    jdbc.update(
        "UPDATE commission_batches SET total_amount = 0 WHERE user_id IN (?, ?)", viejo, director);
    jdbc.update("DELETE FROM commission_accruals WHERE movement_detail_id = ?", linea);

    cierre.closeManually(admin);

    assertThat(desenlace(linea)).isEqualTo("DEVENGADA");
    assertThat(
            jdbc.queryForList(
                "SELECT user_id FROM commissions WHERE movement_detail_id = ?", UUID.class, linea))
        .containsExactlyInAnyOrder(nuevo, director);
  }

  // ---------------------------------------------------------------------------

  private UUID confirmada(UUID vendedor) throws Exception {
    UUID venta =
        SettlementFixtures.venta(jdbc, cliente, VENDIDA_EL, linea(producto, vendedor, 1, "100.00"));
    confirmar(venta);
    return venta;
  }

  private void confirmar(UUID venta) throws Exception {
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, venta))
                .with(como("movements:confirm-payment")))
        .andExpect(status().isOk());
  }

  private MockHttpServletRequestBuilder corregir(UUID venta, UUID vendedor) {
    return corregir(venta, producto, vendedor);
  }

  private MockHttpServletRequestBuilder corregir(UUID venta, UUID deProducto, UUID vendedor) {
    return asignar(venta, par(deProducto, vendedor)).with(conPermiso());
  }

  private static String par(UUID deProducto, UUID vendedor) {
    return "{\"productId\":\"%s\",\"sellerId\":\"%s\"}".formatted(deProducto, vendedor);
  }

  private static MockHttpServletRequestBuilder asignar(UUID venta, String... pares) {
    return post("/api/v1/movements/{id}/seller-assignments", venta)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"lines\":[" + String.join(",", pares) + "]}");
  }

  private RequestPostProcessor conPermiso() {
    return user(admin.toString()).authorities(() -> ASIGNAR);
  }

  private void vincular(UUID vendedor, String origen) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, ?)",
        cliente,
        vendedor,
        origen);
  }

  private UUID lineaDe(UUID venta) {
    return SettlementFixtures.lineaDe(jdbc, venta);
  }

  private UUID lineaDe(UUID venta, UUID deProducto) {
    return jdbc.queryForObject(
        "SELECT id FROM movement_details WHERE movement_id = ? AND product_id = ?",
        UUID.class,
        venta,
        deProducto);
  }

  private UUID vendedorDe(UUID linea) {
    return jdbc.queryForObject(
        "SELECT seller_id FROM movement_details WHERE id = ?", UUID.class, linea);
  }

  /** Las comisiones de la línea, por identificador: lo que una corrección negada no toca. */
  private List<UUID> comisionesDe(UUID linea) {
    return jdbc.queryForList(
        "SELECT id FROM commissions WHERE movement_detail_id = ? ORDER BY id", UUID.class, linea);
  }

  private UUID comisionDe(UUID linea, UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commissions WHERE movement_detail_id = ? AND user_id = ?",
        UUID.class,
        linea,
        persona);
  }

  private String desenlace(UUID linea) {
    return jdbc
        .queryForList(
            "SELECT outcome FROM commission_accruals WHERE movement_detail_id = ?",
            String.class,
            linea)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private UUID pendienteDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'PENDIENTE'",
        UUID.class,
        persona);
  }

  private UUID abiertoDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'ABIERTO'",
        UUID.class,
        persona);
  }

  private UUID loteDeLaComision(UUID linea, UUID persona) {
    return jdbc.queryForObject(
        "SELECT batch_id FROM commissions WHERE movement_detail_id = ? AND user_id = ?",
        UUID.class,
        linea,
        persona);
  }

  private int lotesDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM commission_batches WHERE user_id = ?", Integer.class, persona);
  }

  private int marcas(UUID linea) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM commission_reattributions WHERE movement_detail_id = ?",
        Integer.class,
        linea);
  }

  private void tasaDelAgente(String porcentaje) {
    jdbc.update(
        "UPDATE commission_rates SET percentage = CAST(? AS numeric) WHERE product_id = ? AND"
            + " role_id = CAST(? AS uuid)",
        porcentaje,
        producto,
        AGENTE);
  }

  private BigDecimal total(UUID lote) {
    return importe(
        jdbc.queryForObject(
            "SELECT total_amount FROM commission_batches WHERE id = ?", Long.class, lote));
  }

  private BigDecimal billeteraDe(UUID persona) {
    return jdbc
        .queryForList(
            "SELECT balance FROM accounts WHERE user_id = ? AND kind = 'BILLETERA'",
            Long.class,
            persona)
        .stream()
        .findFirst()
        .map(CommissionFixtures::importe)
        .orElse(BigDecimal.ZERO);
  }

  private void limpiar() {
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'st-rl-%')");
    SettlementFixtures.limpiar(jdbc);
    AfftrackFixtures.limpiar(jdbc);
  }

  private static RequestPostProcessor como(String permiso) {
    return user(UUID.randomUUID().toString()).authorities(() -> permiso);
  }
}
