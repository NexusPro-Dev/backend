package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.centesimas;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.importe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.domain.service.CommissionAccrualService;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.CommissionCleanup;
import com.factech.nexus.testing.ConcurrencyHarness;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El devengo automático (`RF-CM-013`, `CA-CM-154` a `CA-CM-169`).
 *
 * <p><b>Las ventas se confirman y se atribuyen por la API de `MV`</b>, no invocando el servicio: lo
 * que se prueba es que el aviso, el {@code AFTER_COMMIT} y la transacción nueva funcionen juntos.
 * Por eso esta suite <b>no es transaccional</b> —una prueba con {@code @Transactional} nunca
 * dispararía el aviso— y limpia al empezar y al terminar.
 *
 * <p>La cadena es siempre la misma: el agente vende, su superior es el director y el del director,
 * el manager. Las ventas se siembran por SQL, pendientes y con su pago pendiente, como en {@code
 * ConfirmSaleIT}.
 */
@AutoConfigureMockMvc
class CommissionAccrualIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  /** 15:00 en UTC del 10-09-2026: las 10:00 en Bogotá, el mismo día. */
  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CommissionAccrualService devengo;

  private UUID cliente;
  private UUID agente;
  private UUID director;
  private UUID manager;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    cliente = persona("ca-cliente", null);
    agente = persona("ca-agente", AGENTE);
    director = persona("ca-director", DIRECTOR);
    manager = persona("ca-manager", MANAGER);
    superior(agente, director, "2026-01-01T00:00:00Z", null);
    superior(director, manager, "2026-01-01T00:00:00Z", null);
    producto = producto("CA_BOT", "100.00", USD);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // Cuándo devenga
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-154 — confirmar una venta devenga EN EL MOMENTO una comisión por cada nivel con tasa,"
          + " con lo que aplicó, y la línea queda DEVENGADA")
  void confirmarDevenga() throws Exception {
    UUID tasaAgente = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "2.50");
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));

    confirmar(venta);

    UUID linea = lineaDe(venta);
    assertThat(desenlace(linea)).isEqualTo("DEVENGADA");
    List<Map<String, Object>> filas = comisionesDe(linea);
    assertThat(filas).hasSize(3);
    Map<String, Object> delAgente = filas.get(0);
    assertThat(delAgente.get("user_id")).isEqualTo(agente);
    assertThat(((Number) delAgente.get("chain_level")).intValue()).isZero();
    assertThat(delAgente.get("source")).isEqualTo("ROL");
    assertThat(delAgente.get("rate_id")).isEqualTo(tasaAgente);
    assertThat(delAgente.get("rate_type")).isEqualTo("PORCENTAJE");
    assertThat((BigDecimal) delAgente.get("percentage")).isEqualByComparingTo("10.00");
    assertThat(importe(delAgente.get("commission_amount"))).isEqualByComparingTo("10.0000");
    assertThat(delAgente.get("resolved_on").toString()).isEqualTo("2026-09-10");
    assertThat(delAgente.get("accrued_at")).isNotNull();
    assertThat(filas.get(1).get("user_id")).isEqualTo(director);
    assertThat(importe(filas.get(1).get("commission_amount"))).isEqualByComparingTo("5");
    assertThat(filas.get(2).get("user_id")).isEqualTo(manager);
    assertThat(importe(filas.get(2).get("commission_amount"))).isEqualByComparingTo("2.5");
  }

  @Test
  @DisplayName(
      "CA-CM-155 — en una venta con una línea SIN vendedor, la otra devenga al confirmar; la que"
          + " falta devenga ELLA SOLA al asignársele")
  void laLineaSinVendedorEspera() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID otro = producto("CA_OTRO", "50.00", USD);
    CommissionFixtures.sembrarTasaDeRol(jdbc, otro, AGENTE, "10.00");
    clienteDe(cliente, agente);
    UUID venta =
        venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"), linea(otro, null, 1, "50.00"));

    confirmar(venta);

    assertThat(desenlace(lineaDe(venta, producto))).isEqualTo("DEVENGADA");
    assertThat(desenlace(lineaDe(venta, otro))).isNull();

    mvc.perform(
            post("/api/v1/movements/{id}/seller-assignments", venta)
                .contentType("application/json")
                .content(
                    """
                    {"lines":[{"productId":"%s","sellerId":"%s"}]}
                    """
                        .formatted(otro, agente))
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(() -> "movements:assign-sellers")))
        .andExpect(status().isOk());

    assertThat(desenlace(lineaDe(venta, otro))).isEqualTo("DEVENGADA");
    assertThat(comisionesDe(lineaDe(venta, otro))).hasSize(1);
    assertThat(comisionesDe(lineaDe(venta, producto))).hasSize(1);
  }

  @Test
  @DisplayName("CA-CM-156 — una venta NO confirmada no devenga, aunque tenga vendedor")
  void pendienteNoDevenga() {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));

    devengo.accrue(List.of(lineaDe(venta)));

    assertThat(desenlace(lineaDe(venta))).isNull();
    assertThat(comisionesDe(lineaDe(venta))).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Con qué cadena y con qué tasa
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-157 — la cadena es la del DÍA DE LA VENTA: un cambio de superior posterior no cambia"
          + " a quién se paga")
  void laCadenaDeEntonces() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "2.00");
    // El 20-09 el agente pasa a colgar directamente del manager.
    jdbc.update(
        "UPDATE user_supervisors SET ended_at = '2026-09-20T00:00:00Z' WHERE user_id = ?", agente);
    superior(agente, manager, "2026-09-20T00:00:00Z", null);
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));

    confirmar(venta);

    assertThat(comisionesDe(lineaDe(venta)))
        .extracting(f -> f.get("user_id"))
        .containsExactly(agente, director, manager);
  }

  @Test
  @DisplayName("CA-CM-158 — quien no tiene tasa no cobra y NO interrumpe la cadena")
  void quienNoTieneTasaNoCorta() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "2.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));

    confirmar(venta);

    List<Map<String, Object>> filas = comisionesDe(lineaDe(venta));
    assertThat(filas).extracting(f -> f.get("user_id")).containsExactly(agente, manager);
    assertThat(((Number) filas.get(1).get("chain_level")).intValue()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-CM-159 — la tasa es la del día de la venta EN BOGOTÁ: a las 20:00 del 10, la"
          + " personalizada que empieza el 11 no rige")
  void elDiaEnBogota() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaPersonal(jdbc, agente, producto, "20.00", "2026-09-11", null);
    // 01:00 del 11 en UTC: las 20:00 del 10 en Bogotá.
    UUID venta =
        venta(
            OffsetDateTime.of(2026, 9, 11, 1, 0, 0, 0, ZoneOffset.UTC),
            linea(producto, agente, 1, "100.00"));

    confirmar(venta);

    Map<String, Object> fila = comisionesDe(lineaDe(venta)).get(0);
    assertThat(fila.get("source")).isEqualTo("ROL");
    assertThat(fila.get("resolved_on").toString()).isEqualTo("2026-09-10");
  }

  @Test
  @DisplayName("CA-CM-160 — la base es BRUTA y el fijo paga POR UNIDAD")
  void baseBrutaYFijoPorUnidad() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "FIJO", "3.0000");
    // Tres unidades de 100 con 60 de descuento: la línea vale 240, la base es 300.
    UUID venta = venta(VENDIDA_EL, lineaConDescuento(producto, agente, 3, "100.00", "60.00"));

    confirmar(venta);

    List<Map<String, Object>> filas = comisionesDe(lineaDe(venta));
    assertThat(importe(filas.get(0).get("commission_amount"))).isEqualByComparingTo("30");
    assertThat(importe(filas.get(1).get("commission_amount"))).isEqualByComparingTo("9");
  }

  // ---------------------------------------------------------------------------
  // Los tres desenlaces
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-161 — si la cadena pasa del importe de la línea, nadie cobra y la línea queda"
          + " RECHAZADA con la suma y el importe")
  void pasaDelCien() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "60.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "30.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "20.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));

    confirmar(venta);

    UUID linea = lineaDe(venta);
    assertThat(desenlace(linea)).isEqualTo("RECHAZADA");
    assertThat(motivo(linea)).contains("110").contains("100");
    assertThat(comisionesDe(linea)).isEmpty();
  }

  @Test
  @DisplayName("CA-CM-162 — si NADIE tiene tasa, la línea queda SIN_COMISION")
  void sinComision() throws Exception {
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));

    confirmar(venta);

    assertThat(desenlace(lineaDe(venta))).isEqualTo("SIN_COMISION");
    assertThat(comisionesDe(lineaDe(venta))).isEmpty();
  }

  @Test
  @DisplayName(
      "CA-CM-163 — cada comisión se suma al lote ABIERTO de su persona y moneda; en otra moneda,"
          + " otro lote")
  void elLoteAbierto() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID cop = moneda("ZCP");
    UUID enPesos = producto("CA_COP", "1000.00", cop);
    CommissionFixtures.sembrarTasaDeRol(jdbc, enPesos, AGENTE, "10.00");

    confirmar(venta(VENDIDA_EL, linea(producto, agente, 1, "100.00")));
    confirmar(venta(VENDIDA_EL, linea(producto, agente, 2, "100.00")));
    confirmar(ventaEn(cop, VENDIDA_EL, linea(enPesos, agente, 1, "1000.00")));

    List<Map<String, Object>> lotes =
        jdbc.queryForList(
            "SELECT currency_id, status, total_amount, period_end FROM commission_batches"
                + " WHERE user_id = ? ORDER BY total_amount",
            agente);
    assertThat(lotes).hasSize(2);
    assertThat(lotes).allSatisfy(l -> assertThat(l.get("status")).isEqualTo("ABIERTO"));
    assertThat(lotes).allSatisfy(l -> assertThat(l.get("period_end")).isNull());
    assertThat(importe(lotes.get(0).get("total_amount"))).isEqualByComparingTo("30");
    assertThat(importe(lotes.get(1).get("total_amount"))).isEqualByComparingTo("100");
  }

  // ---------------------------------------------------------------------------
  // Concurrencia y fallos
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-164 — dos avisos simultáneos de la misma línea la devengan UNA vez")
  void dosAvisosUnaVez() {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));
    confirmarPorSql(venta);
    UUID linea = lineaDe(venta);

    ConcurrencyHarness.runTogether(2, i -> devengo.accrue(List.of(linea)));

    assertThat(comisionesDe(linea)).hasSize(1);
    assertThat(totalDelLote(agente)).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName(
      "CA-CM-165 — dos comisiones simultáneas de la misma persona, de líneas distintas, suman LAS"
          + " DOS al lote")
  void dosLineasSumanLasDos() {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID una = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));
    UUID otra = venta(VENDIDA_EL, linea(producto, agente, 3, "100.00"));
    confirmarPorSql(una);
    confirmarPorSql(otra);
    List<UUID> lineas = List.of(lineaDe(una), lineaDe(otra));

    ConcurrencyHarness.runTogether(2, i -> devengo.accrue(List.of(lineas.get(i))));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commission_batches WHERE user_id = ?", Integer.class, agente))
        .isEqualTo(1);
    assertThat(totalDelLote(agente)).isEqualByComparingTo("40");
  }

  @Test
  @DisplayName(
      "CA-CM-166 — si devengar FALLA, la venta sigue confirmada, esa línea queda sin desenlace y"
          + " las demás devengan")
  void unFalloNoDeshaceLaVenta() throws Exception {
    // Hasta `V65` lo provocaba una línea cuya comisión no cabía en numeric(14,4). En
    // centésimas bigint ningún dato admitido llega al techo, así que lo provoca una
    // restricción que solo existe en esta prueba: rechaza exactamente la comisión testigo
    // (el 10 % de 1234,50 = 123,45) y se retira al acabar, aquí y en la limpieza. Sigue siendo
    // un fallo de la base, sin dobles: un contexto de Spring más agotaría las conexiones de la
    // suite. La otra línea, de otra vendedora, no se entera.
    UUID enorme = producto("CA_ENORME", "1234.50", USD);
    CommissionFixtures.sembrarTasaDeRol(jdbc, enorme, AGENTE, "10.00");
    UUID vendedoraSola = persona("ca-sola", AGENTE);
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID venta =
        venta(
            VENDIDA_EL,
            linea(enorme, agente, 1, "1234.50"),
            linea(producto, vendedoraSola, 1, "100.00"));

    jdbc.execute(
        "ALTER TABLE commissions ADD CONSTRAINT ck_prueba_devengo_falla"
            + " CHECK (commission_amount <> 12345) NOT VALID");
    try {
      confirmar(venta);
    } finally {
      jdbc.execute("ALTER TABLE commissions DROP CONSTRAINT IF EXISTS ck_prueba_devengo_falla");
    }

    assertThat(
            jdbc.queryForObject("SELECT status FROM movements WHERE id = ?", String.class, venta))
        .isEqualTo("CONFIRMADA");
    assertThat(desenlace(lineaDe(venta, enorme))).isNull();
    assertThat(desenlace(lineaDe(venta, producto))).isEqualTo("DEVENGADA");
  }

  // ---------------------------------------------------------------------------
  // Reintentos
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-167 — una RECHAZADA que se reintenta tras corregir la tasa devenga; si sigue"
          + " pasándose, sigue RECHAZADA con un intento más")
  void reintentoDeRechazada() throws Exception {
    UUID delAgente = CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "60.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "50.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));
    confirmar(venta);
    UUID linea = lineaDe(venta);

    devengo.retryRejected();
    assertThat(desenlace(linea)).isEqualTo("RECHAZADA");
    assertThat(intentos(linea)).isEqualTo(2);

    jdbc.update("UPDATE commission_rates SET percentage = 40.00 WHERE id = ?", delAgente);
    devengo.retryRejected();

    assertThat(desenlace(linea)).isEqualTo("DEVENGADA");
    assertThat(intentos(linea)).isEqualTo(3);
    assertThat(comisionesDe(linea)).hasSize(2);
  }

  @Test
  @DisplayName("CA-CM-168 — una SIN_COMISION no se reintenta, aunque después se registre una tasa")
  void sinComisionEsDefinitiva() throws Exception {
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));
    confirmar(venta);
    UUID linea = lineaDe(venta);
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");

    devengo.accrue(List.of(linea));
    devengo.retryRejected();

    assertThat(desenlace(linea)).isEqualTo("SIN_COMISION");
    assertThat(comisionesDe(linea)).isEmpty();
  }

  @Test
  @DisplayName("CA-CM-169 — una línea de un movimiento que no es una venta no devenga")
  void soloVentas() {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));
    confirmarPorSql(venta);
    // La misma línea, colgando de un movimiento que se hace pasar por bono.
    jdbc.update(
        "UPDATE movements SET movement_type_id = (SELECT id FROM movement_types WHERE code ="
            + " 'BONO'), type_status_id = (SELECT s.id FROM movement_type_statuses s JOIN"
            + " movement_types t ON t.id = s.movement_type_id WHERE t.code = 'BONO') WHERE id = ?",
        venta);

    devengo.accrue(List.of(lineaDe(venta)));

    assertThat(desenlace(lineaDe(venta))).isNull();
  }

  // ---------------------------------------------------------------------------
  // Siembra
  // ---------------------------------------------------------------------------

  // ---------------------------------------------------------------------------
  // La comisión por venta directa (`RN-CM-045`, 29-09-2026; de la tasa de rol desde el
  // 05-10-2026, `RN-CM-050`)
  // ---------------------------------------------------------------------------

  /**
   * La directa de la tasa de rol viva de ese rol sobre el producto, puesta por SQL: el alta de
   * `RF-CM-001` ya tiene sus pruebas.
   */
  private void directa(UUID deProducto, String rol, String tipo, String valor) {
    int filas =
        jdbc.update(
            "UPDATE commission_rates SET direct_rate_type = ?,"
                + " direct_percentage = CASE WHEN ? = 'PORCENTAJE' THEN CAST(? AS numeric) END,"
                + " direct_fixed_amount = CASE WHEN ? = 'FIJO' THEN CAST(? AS bigint) END"
                + " WHERE product_id = ? AND role_id = CAST(? AS uuid) AND deleted_at IS NULL",
            tipo,
            tipo,
            valor,
            tipo,
            centesimas(valor),
            deProducto,
            rol);
    assertThat(filas).as("la tasa de rol donde poner la directa").isEqualTo(1);
  }

  private UUID tasaDe(UUID deProducto, String rol) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_rates WHERE product_id = ? AND role_id = CAST(? AS uuid)"
            + " AND deleted_at IS NULL",
        UUID.class,
        deProducto,
        rol);
  }

  private void tasasDeLaCadena() {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "2.00");
  }

  @Test
  @DisplayName(
      "CA-CM-264 — un DIRECTOR que vende cobra la DIRECTA de su tasa de rol en el nivel 0, y su"
          + " manager su tasa de rol")
  void elDirectorCobraLaDirecta() throws Exception {
    tasasDeLaCadena();
    directa(producto, DIRECTOR, "PORCENTAJE", "8.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, director, 1, "100.00"));

    confirmar(venta);

    UUID linea = lineaDe(venta);
    assertThat(desenlace(linea)).isEqualTo("DEVENGADA");
    List<Map<String, Object>> filas = comisionesDe(linea);
    assertThat(filas).extracting(f -> f.get("user_id")).containsExactly(director, manager);
    Map<String, Object> propia = filas.get(0);
    assertThat(((Number) propia.get("chain_level")).intValue()).isZero();
    assertThat(propia.get("source")).isEqualTo("DIRECTA");
    // `CA-CM-328`: desde el 05-10-2026 apunta a la tasa de rol, no al producto.
    assertThat(propia.get("rate_id")).isEqualTo(tasaDe(producto, DIRECTOR));
    assertThat(propia.get("rate_type")).isEqualTo("PORCENTAJE");
    assertThat((BigDecimal) propia.get("percentage")).isEqualByComparingTo("8");
    assertThat(importe(propia.get("commission_amount"))).isEqualByComparingTo("8");
    assertThat(filas.get(1).get("source")).isEqualTo("ROL");
    assertThat(importe(filas.get(1).get("commission_amount"))).isEqualByComparingTo("2");
  }

  @Test
  @DisplayName("CA-CM-265 — un AGENTE que vende cobra su tasa de rol: nadie cobra la directa")
  void elAgenteNoUsaLaDirecta() throws Exception {
    tasasDeLaCadena();
    directa(producto, DIRECTOR, "PORCENTAJE", "8.00");
    directa(producto, MANAGER, "PORCENTAJE", "8.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, agente, 1, "100.00"));

    confirmar(venta);

    List<Map<String, Object>> filas = comisionesDe(lineaDe(venta));
    assertThat(filas).extracting(f -> f.get("source")).containsExactly("ROL", "ROL", "ROL");
    assertThat(importe(filas.get(0).get("commission_amount"))).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("CA-CM-266 — un DIRECTOR con personalizada vigente cobra la personalizada")
  void laPersonalizadaGanaALaDirecta() throws Exception {
    tasasDeLaCadena();
    directa(producto, DIRECTOR, "PORCENTAJE", "8.00");
    CommissionFixtures.sembrarTasaPersonal(jdbc, director, producto, "20.00", "2026-01-01", null);
    UUID venta = venta(VENDIDA_EL, linea(producto, director, 1, "100.00"));

    confirmar(venta);

    Map<String, Object> propia = comisionesDe(lineaDe(venta)).get(0);
    assertThat(propia.get("source")).isEqualTo("PERSONALIZADA");
    assertThat(importe(propia.get("commission_amount"))).isEqualByComparingTo("20");
  }

  @Test
  @DisplayName("CA-CM-268 — la directa más los superiores por encima del 100 %: RECHAZADA")
  void laDirectaCuentaEnElTope() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "20.00");
    directa(producto, DIRECTOR, "PORCENTAJE", "90.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, director, 1, "100.00"));

    confirmar(venta);

    UUID linea = lineaDe(venta);
    assertThat(desenlace(linea)).isEqualTo("RECHAZADA");
    assertThat(comisionesDe(linea)).isEmpty();
  }

  @Test
  @DisplayName("CA-CM-269 — una directa de cero deja una comisión de cero y la línea DEVENGADA")
  void laDirectaDeCero() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
    directa(producto, DIRECTOR, "PORCENTAJE", "0");
    UUID venta = venta(VENDIDA_EL, linea(producto, director, 1, "100.00"));

    confirmar(venta);

    UUID linea = lineaDe(venta);
    assertThat(desenlace(linea)).isEqualTo("DEVENGADA");
    Map<String, Object> propia = comisionesDe(linea).get(0);
    assertThat(propia.get("source")).isEqualTo("DIRECTA");
    assertThat(importe(propia.get("commission_amount"))).isZero();
  }

  @Test
  @DisplayName("CA-CM-270 — corregir la directa después no cambia lo devengado")
  void corregirLaDirectaNoReescribe() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
    directa(producto, DIRECTOR, "PORCENTAJE", "8.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, director, 1, "100.00"));
    confirmar(venta);

    directa(producto, DIRECTOR, "PORCENTAJE", "50.00");

    Map<String, Object> propia = comisionesDe(lineaDe(venta)).get(0);
    assertThat((BigDecimal) propia.get("percentage")).isEqualByComparingTo("8");
    assertThat(importe(propia.get("commission_amount"))).isEqualByComparingTo("8");
  }

  @Test
  @DisplayName(
      "CA-CM-328 — un DIRECTOR y un MANAGER que venden el mismo producto cobran cada uno la"
          + " directa de su rol")
  void cadaRolCobraSuDirecta() throws Exception {
    tasasDeLaCadena();
    directa(producto, DIRECTOR, "PORCENTAJE", "8.00");
    directa(producto, MANAGER, "FIJO", "3.00");
    UUID delDirector = venta(VENDIDA_EL, linea(producto, director, 1, "100.00"));
    UUID delManager = venta(VENDIDA_EL, linea(producto, manager, 1, "100.00"));

    confirmar(delDirector);
    confirmar(delManager);

    Map<String, Object> deDirector = comisionesDe(lineaDe(delDirector)).get(0);
    assertThat(deDirector.get("source")).isEqualTo("DIRECTA");
    assertThat(deDirector.get("rate_id")).isEqualTo(tasaDe(producto, DIRECTOR));
    assertThat(importe(deDirector.get("commission_amount"))).isEqualByComparingTo("8");
    List<Map<String, Object>> deManager = comisionesDe(lineaDe(delManager));
    assertThat(deManager).hasSize(1);
    assertThat(deManager.get(0).get("source")).isEqualTo("DIRECTA");
    assertThat(deManager.get(0).get("rate_id")).isEqualTo(tasaDe(producto, MANAGER));
    assertThat(importe(deManager.get(0).get("commission_amount"))).isEqualByComparingTo("3");
  }

  @Test
  @DisplayName("CA-CM-329 — la tasa del DIRECTOR sin directa: su venta propia cobra la tasa de rol")
  void sinDirectaCobraLaTasaDeRol() throws Exception {
    tasasDeLaCadena();
    UUID venta = venta(VENDIDA_EL, linea(producto, director, 1, "100.00"));

    confirmar(venta);

    Map<String, Object> propia = comisionesDe(lineaDe(venta)).get(0);
    assertThat(propia.get("user_id")).isEqualTo(director);
    assertThat(propia.get("source")).isEqualTo("ROL");
    assertThat(importe(propia.get("commission_amount"))).isEqualByComparingTo("5");
  }

  @Test
  @DisplayName(
      "CA-CM-330 — un DIRECTOR sin tasa de rol no tiene directa: no cobra en el nivel 0, y su"
          + " manager sí cobra su tasa")
  void sinTasaDeRolNoHayDirecta() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, MANAGER, "2.00");
    UUID venta = venta(VENDIDA_EL, linea(producto, director, 1, "100.00"));

    confirmar(venta);

    List<Map<String, Object>> filas = comisionesDe(lineaDe(venta));
    assertThat(filas).extracting(f -> f.get("user_id")).containsExactly(manager);
    assertThat(filas.get(0).get("source")).isEqualTo("ROL");
  }

  private record Linea(
      UUID producto, UUID vendedor, int cantidad, String precio, String descuento) {}

  private static Linea linea(UUID producto, UUID vendedor, int cantidad, String precio) {
    return new Linea(producto, vendedor, cantidad, precio, "0");
  }

  private static Linea lineaConDescuento(
      UUID producto, UUID vendedor, int cantidad, String precio, String descuento) {
    return new Linea(producto, vendedor, cantidad, precio, descuento);
  }

  private UUID venta(OffsetDateTime vendidaEl, Linea... lineas) {
    return ventaEn(UUID.fromString(USD), vendidaEl, lineas);
  }

  private UUID ventaEn(UUID moneda, OffsetDateTime vendidaEl, Linea... lineas) {
    UUID id = UUID.randomUUID();
    boolean faltaVendedor = false;
    for (Linea l : lineas) {
      faltaVendedor |= l.vendedor() == null;
    }
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount, occurred_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = ?),
                ?, ?, ?, 'PENDIENTE', 10000, 0, 10000, CAST(? AS timestamptz))
        """,
        id,
        VENTA,
        VENTA,
        faltaVendedor ? "VALIDAR_COMISIONES" : "VALIDADO",
        cliente,
        moneda,
        "VTA-CA" + id.toString().substring(0, 6).toUpperCase(),
        vendidaEl.toString());
    PaymentFixtures.pagoDe(jdbc, id, TARJETA);
    for (Linea l : lineas) {
      jdbc.update(
          """
          INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                        quantity, unit_price, line_discount, line_amount,
                                        implementation)
          SELECT ?, ?, p.id, ?, p.name, ?, CAST(? AS bigint), CAST(? AS bigint),
                 ? * CAST(? AS bigint) - CAST(? AS bigint), p.implementation
            FROM products p WHERE p.id = ?
          """,
          UUID.randomUUID(),
          id,
          l.vendedor(),
          l.cantidad(),
          centesimas(l.precio()),
          centesimas(l.descuento()),
          l.cantidad(),
          centesimas(l.precio()),
          centesimas(l.descuento()),
          l.producto());
    }
    return id;
  }

  private void confirmar(UUID venta) throws Exception {
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, venta))
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(() -> "movements:confirm-payment")))
        .andExpect(status().isOk());
  }

  /** Confirma sin pasar por `MV`: para las pruebas que invocan el devengo a mano. */
  private void confirmarPorSql(UUID venta) {
    jdbc.update(
        "UPDATE movements SET status = 'CONFIRMADA', confirmed_at = now() WHERE id = ?", venta);
  }

  private UUID persona(String usuario, String rol) {
    return CommissionFixtures.sembrarPersonaConRol(jdbc, usuario, rol);
  }

  private void superior(UUID persona, UUID jefe, String desde, String hasta) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)"
            + " VALUES (?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz))",
        UUID.randomUUID(),
        persona,
        jefe,
        desde,
        hasta);
  }

  private void clienteDe(UUID elCliente, UUID vendedor) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, 'REGISTRO')",
        elCliente,
        vendedor);
  }

  private UUID producto(String codigo, String precio, Object moneda) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, price, currency_id,"
            + " status) VALUES ('TIENDA', 'MANUAL', ?, ?, 'BOT', ?, CAST(? AS bigint),"
            + " CAST(? AS uuid), 'ACTIVO')",
        id,
        codigo,
        "Producto " + codigo,
        centesimas(precio),
        moneda.toString());
    return id;
  }

  private UUID moneda(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (?, ?, ?, '$', 2, false, true)",
        id,
        codigo,
        "Moneda " + codigo);
    return id;
  }

  // ---------------------------------------------------------------------------
  // Lectura
  // ---------------------------------------------------------------------------

  private UUID lineaDe(UUID venta) {
    return jdbc.queryForObject(
        "SELECT id FROM movement_details WHERE movement_id = ?", UUID.class, venta);
  }

  private UUID lineaDe(UUID venta, UUID deProducto) {
    return jdbc.queryForObject(
        "SELECT id FROM movement_details WHERE movement_id = ? AND product_id = ?",
        UUID.class,
        venta,
        deProducto);
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

  private String motivo(UUID linea) {
    return jdbc.queryForObject(
        "SELECT reason FROM commission_accruals WHERE movement_detail_id = ?", String.class, linea);
  }

  private int intentos(UUID linea) {
    return jdbc.queryForObject(
        "SELECT attempts FROM commission_accruals WHERE movement_detail_id = ?",
        Integer.class,
        linea);
  }

  private List<Map<String, Object>> comisionesDe(UUID linea) {
    return jdbc.queryForList(
        "SELECT * FROM commissions WHERE movement_detail_id = ? ORDER BY chain_level", linea);
  }

  private BigDecimal totalDelLote(UUID persona) {
    return importe(
        jdbc.queryForObject(
            "SELECT total_amount FROM commission_batches WHERE user_id = ? AND status = 'ABIERTO'",
            Long.class,
            persona));
  }

  private void limpiar() {
    jdbc.execute("ALTER TABLE commissions DROP CONSTRAINT IF EXISTS ck_prueba_devengo_falla");
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM user_commission_rates");
    jdbc.update("DELETE FROM commission_rates");
    jdbc.update("DELETE FROM products WHERE code LIKE 'CA\\_%'");
    jdbc.update("DELETE FROM currencies WHERE code = 'ZCP'");
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ca-%')");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ca-%')");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ca-%')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ca-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ca-%'");
  }
}
