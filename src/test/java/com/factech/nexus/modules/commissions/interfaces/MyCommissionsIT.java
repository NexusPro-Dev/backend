package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.movements.PaymentFixtures;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Todas mis comisiones, sin pasar por los lotes (`RF-CM-026`, `CA-CM-347` a `CA-CM-355`). La
 * cadena: dos agentes con el mismo director; las comisiones nacen confirmando ventas por la API de
 * `MV`. La fila {@code POR_AFFTRACK} se prueba en {@code AfftrackSettlementIT}, que sabe sembrarla.
 */
@AutoConfigureMockMvc
class MyCommissionsIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);
  private static final String MIAS = "commission-batches:list-own-commissions";
  private static final String RUTA = "/api/v1/commission-batches/mine/commissions";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;

  private UUID admin;
  private UUID cliente;
  private UUID agente;
  private UUID otro;
  private UUID director;
  private UUID producto;
  private UUID segundo;

  @BeforeEach
  void sembrar() {
    limpiar();
    admin = SettlementFixtures.persona(jdbc, "mc-admin", null);
    cliente = SettlementFixtures.persona(jdbc, "mc-cliente", null);
    agente = SettlementFixtures.persona(jdbc, "mc-agente", AGENTE);
    otro = SettlementFixtures.persona(jdbc, "mc-otro", AGENTE);
    director = SettlementFixtures.persona(jdbc, "mc-director", DIRECTOR);
    SettlementFixtures.superior(jdbc, agente, director);
    SettlementFixtures.superior(jdbc, otro, director);
    vincular(agente, "REGISTRO");
    vincular(otro, "HOTLINK");
    producto = SettlementFixtures.producto(jdbc, "MC1", "100.00");
    segundo = SettlementFixtures.producto(jdbc, "MC2", "200.00");
    for (UUID p : new UUID[] {producto, segundo}) {
      CommissionFixtures.sembrarTasaDeRol(jdbc, p, AGENTE, "10.00");
      CommissionFixtures.sembrarTasaDeRol(jdbc, p, DIRECTOR, "5.00");
    }
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("CA-CM-347 — solo las mías, de todos mis lotes y estados, la más reciente primero")
  void soloLasMias() throws Exception {
    UUID primera = confirmada(agente, producto);
    confirmada(otro, producto);
    cierre.closeManually(admin);
    UUID segunda = confirmada(agente, producto);

    mvc.perform(get(RUTA).with(propio(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.sort").value("accruedAt,desc"))
        .andExpect(jsonPath("$.content[0].commission.movementId").value(segunda.toString()))
        .andExpect(jsonPath("$.content[0].batch.status").value("ABIERTO"))
        .andExpect(jsonPath("$.content[1].commission.movementId").value(primera.toString()))
        .andExpect(jsonPath("$.content[1].batch.status").value("PENDIENTE"));
  }

  @Test
  @DisplayName(
      "CA-CM-348 — cada una trae su lote, su moneda y el cliente, con la forma de la comisión del"
          + " detalle")
  void cadaUnaConSuLote() throws Exception {
    UUID venta = confirmada(agente, producto);
    UUID lote = loteDe(agente);

    mvc.perform(get(RUTA).with(propio(agente)))
        .andExpect(jsonPath("$.content[0].batch.id").value(lote.toString()))
        .andExpect(jsonPath("$.content[0].batch.code").isNotEmpty())
        .andExpect(jsonPath("$.content[0].batch.status").value("ABIERTO"))
        .andExpect(jsonPath("$.content[0].currency.id").value(SettlementFixtures.USD))
        .andExpect(jsonPath("$.content[0].currency.code").value("USD"))
        .andExpect(jsonPath("$.content[0].client.id").value(cliente.toString()))
        .andExpect(jsonPath("$.content[0].client.username").value("st-mc-cliente"))
        .andExpect(jsonPath("$.content[0].commission.movementId").value(venta.toString()))
        .andExpect(jsonPath("$.content[0].commission.movementCode").isNotEmpty())
        .andExpect(jsonPath("$.content[0].commission.productId").value(producto.toString()))
        .andExpect(jsonPath("$.content[0].commission.productName").value("Producto MC1"))
        .andExpect(jsonPath("$.content[0].commission.chainLevel").value(0))
        .andExpect(jsonPath("$.content[0].commission.commissionKind").value("POR_VENTA"))
        .andExpect(jsonPath("$.content[0].commission.commissionAmount").value(10.0))
        .andExpect(jsonPath("$.content[0].commission.withdrawnFrom").doesNotExist());
  }

  @Test
  @DisplayName(
      "CA-CM-349 — el superior ve lo que cobra por cada subordinado, con su nivel y el cliente;"
          + " el subordinado no ve lo del superior")
  void elSuperiorVeLoSuyo() throws Exception {
    confirmada(agente, producto);
    confirmada(otro, producto);

    mvc.perform(get(RUTA).with(propio(director)))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].commission.chainLevel").value(1))
        .andExpect(jsonPath("$.content[1].commission.chainLevel").value(1))
        .andExpect(jsonPath("$.content[0].commission.commissionAmount").value(5.0))
        .andExpect(jsonPath("$.content[0].client.id").value(cliente.toString()));
    mvc.perform(get(RUTA).with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].commission.chainLevel").value(0));
  }

  @Test
  @DisplayName(
      "CA-CM-350 — con estado, solo las de lotes en ese estado; tras pagarse, salen PAGADO")
  void porEstado() throws Exception {
    confirmada(agente, producto);
    cierre.closeManually(admin);
    UUID pendiente = loteDe(agente);

    mvc.perform(get(RUTA).param("status", "PENDIENTE").with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].batch.id").value(pendiente.toString()));
    mvc.perform(get(RUTA).param("status", "abierto").with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(
            post("/api/v1/commission-batches/{id}/payment", pendiente)
                .with(como("commission-batches:pay")))
        .andExpect(status().isOk());

    mvc.perform(get(RUTA).param("status", "PAGADO").with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].batch.status").value("PAGADO"));
    mvc.perform(get(RUTA).param("status", "PENDIENTE").with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("CA-CM-351 — moneda, producto y clase se combinan")
  void filtrosCombinados() throws Exception {
    confirmada(agente, producto);
    confirmada(agente, segundo);

    mvc.perform(get(RUTA).param("productId", segundo.toString()).with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].commission.productId").value(segundo.toString()))
        .andExpect(jsonPath("$.content[0].commission.commissionAmount").value(20.0));
    mvc.perform(
            get(RUTA)
                .param("currencyId", SettlementFixtures.USD)
                .param("productId", producto.toString())
                .param("commissionKind", "POR_VENTA")
                .with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].commission.productId").value(producto.toString()));
    mvc.perform(get(RUTA).param("currencyId", UUID.randomUUID().toString()).with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(RUTA).param("commissionKind", "POR_AFFTRACK").with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(RUTA).param("commissionKind", "por_venta").with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName(
      "CA-CM-361 y CA-CM-362 — con cliente, solo las de sus ventas, combinable; uno inexistente da"
          + " una página vacía, y uno mal formado, 400")
  void porCliente() throws Exception {
    UUID otroCliente = SettlementFixtures.persona(jdbc, "mc-cliente2", null);
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, 'REGISTRO')",
        otroCliente,
        agente);
    UUID suya = confirmadaPara(otroCliente, agente, segundo);
    confirmada(agente, producto);
    confirmada(agente, segundo);

    mvc.perform(get(RUTA).param("clientId", otroCliente.toString()).with(propio(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].commission.movementId").value(suya.toString()))
        .andExpect(jsonPath("$.content[0].client.id").value(otroCliente.toString()));
    mvc.perform(get(RUTA).param("clientId", cliente.toString()).with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(
            get(RUTA)
                .param("clientId", cliente.toString())
                .param("productId", segundo.toString())
                .with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(1));
    // El superior también filtra por el cliente de la venta que comisiona.
    mvc.perform(get(RUTA).param("clientId", otroCliente.toString()).with(propio(director)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].commission.chainLevel").value(1));

    mvc.perform(get(RUTA).param("clientId", UUID.randomUUID().toString()).with(propio(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(RUTA).param("clientId", "no-es-uuid").with(propio(agente)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-CM-352 — desde y hasta acotan por el devengo, los dos incluidos; los filtros inválidos"
          + " salen TODOS juntos")
  void fechasYValidacion() throws Exception {
    confirmada(agente, producto);
    OffsetDateTime devengo =
        jdbc.queryForObject(
            "SELECT accrued_at FROM commissions WHERE user_id = ?", OffsetDateTime.class, agente);
    String exacto = devengo.withOffsetSameInstant(ZoneOffset.UTC).toString();

    mvc.perform(get(RUTA).param("from", exacto).param("to", exacto).with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("to", devengo.minusSeconds(1).toString()).with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(RUTA).param("from", devengo.plusSeconds(1).toString()).with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(
            get(RUTA)
                .param("status", "CERRADO")
                .param("commissionKind", "POR_BONO")
                .param("from", "2026-10-02T00:00:00Z")
                .param("to", "2026-10-01T00:00:00Z")
                .with(propio(agente)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(3));
  }

  @Test
  @DisplayName(
      "CA-CM-353 — una retirada sale UNA vez, en su lote abierto, diciendo de qué pendiente salió")
  void laRetiradaUnaVez() throws Exception {
    confirmada(agente, producto);
    confirmada(agente, producto); // que el pendiente no se quede vacío y se borre (RN-CM-052)
    cierre.closeManually(admin);
    UUID pendiente = loteDe(agente);
    UUID comision =
        jdbc.queryForObject(
            "SELECT id FROM commissions WHERE batch_id = ? ORDER BY id LIMIT 1",
            UUID.class,
            pendiente);
    mvc.perform(
            post(
                    "/api/v1/commission-batches/{id}/commissions/{commissionId}/withdrawal",
                    pendiente,
                    comision)
                .with(como("commission-batches:withdraw-commission")))
        .andExpect(status().isOk());

    mvc.perform(get(RUTA).with(propio(agente))).andExpect(jsonPath("$.totalElements").value(2));
    UUID abierto =
        jdbc.queryForObject(
            "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'ABIERTO'",
            UUID.class,
            agente);
    mvc.perform(get(RUTA).param("status", "ABIERTO").with(propio(agente)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].commission.id").value(comision.toString()))
        .andExpect(jsonPath("$.content[0].batch.status").value("ABIERTO"))
        .andExpect(jsonPath("$.content[0].batch.id").value(abierto.toString()))
        .andExpect(
            jsonPath("$.content[0].commission.withdrawnFrom.id").value(pendiente.toString()));
  }

  @Test
  @DisplayName(
      "CA-CM-354 — tras corregir el vendedor, la vieja deja de salir y la nueva sale en la lista"
          + " de quien la cobra ahora")
  void trasCorregirElVendedor() throws Exception {
    UUID venta = confirmada(agente, producto);

    mvc.perform(
            post("/api/v1/movements/{id}/seller-assignments", venta)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"lines\":[{\"productId\":\"%s\",\"sellerId\":\"%s\"}]}"
                        .formatted(producto, otro))
                .with(user(admin.toString()).authorities(() -> "movements:assign-sellers")))
        .andExpect(status().isOk());

    mvc.perform(get(RUTA).with(propio(agente))).andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(RUTA).with(propio(otro)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].commission.movementId").value(venta.toString()));
    mvc.perform(get(RUTA).with(propio(director))).andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName(
      "CA-CM-355 — sin el permiso, 403; todo rol que ve sus lotes lo porta desde su siembra")
  void permiso() throws Exception {
    mvc.perform(
            get(RUTA)
                .with(user(agente.toString()).authorities(() -> "commission-batches:list-own")))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM (
                  SELECT rp.role_id FROM role_permissions rp
                    JOIN permissions p ON p.id = rp.permission_id
                   WHERE p.code = 'commission-batches:list-own'
                  EXCEPT
                  SELECT rp.role_id FROM role_permissions rp
                    JOIN permissions p ON p.id = rp.permission_id
                   WHERE p.code = 'commission-batches:list-own-commissions') AS faltan
                """,
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id ="
                    + " rp.permission_id WHERE rp.role_id = CAST(? AS uuid) AND p.code = ?",
                Integer.class,
                AGENTE,
                MIAS))
        .isEqualTo(1);
  }

  private UUID confirmada(UUID vendedor, UUID deProducto) throws Exception {
    return confirmadaPara(cliente, vendedor, deProducto);
  }

  private UUID confirmadaPara(UUID comprador, UUID vendedor, UUID deProducto) throws Exception {
    UUID venta =
        SettlementFixtures.venta(
            jdbc, comprador, VENDIDA_EL, linea(deProducto, vendedor, 1, precioDe(deProducto)));
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, venta))
                .with(como("movements:confirm-payment")))
        .andExpect(status().isOk());
    return venta;
  }

  private String precioDe(UUID deProducto) {
    return deProducto.equals(segundo) ? "200.00" : "100.00";
  }

  private UUID loteDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? ORDER BY period_start DESC LIMIT 1",
        UUID.class,
        persona);
  }

  private void vincular(UUID vendedor, String origen) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, ?)",
        cliente,
        vendedor,
        origen);
  }

  private void limpiar() {
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'st-mc-%')");
    SettlementFixtures.limpiar(jdbc);
  }

  private static RequestPostProcessor propio(UUID persona) {
    return user(persona.toString()).authorities(() -> MIAS);
  }

  private static RequestPostProcessor como(String permiso) {
    return user(UUID.randomUUID().toString()).authorities(() -> permiso);
  }
}
