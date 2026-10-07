package com.factech.nexus.modules.indicators.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.CommissionCleanup;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * `RF-IN-006` · `T-05` — el resumen de líneas de venta (`CA-IN-059` a `CA-IN-066`).
 *
 * <p>Septiembre de 2026, importes en centésimas:
 *
 * <pre>
 *   s1 CONFIRMADA USD  BOT     agente1 ×3  30,00
 *   s2 CONFIRMADA COP  UPGRADE agente2 ×1  1000,00
 *   s3 PENDIENTE  USD  BOT     (nadie) ×2  20,00              ← sin vendedor
 *   s4 CONFIRMADA USD  BOT     agente1 ×1  5,00
 *                  + UPGRADE (nadie) ×4  40,00              ← dos tipos, y sin vendedor
 *   s5 ANULADA    USD  BOT     (nadie) ×1  7,00               ← sin vendedor, pero anulada
 *   s6 PENDIENTE  USD  BOT     agente2 ×1  1,00
 * </pre>
 */
@AutoConfigureMockMvc
class SaleLinesSummaryIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String COP = "01a03336-6d00-7002-9c4f-5e7ad3000002";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  private static final String RUTA = "/api/v1/indicators/sales/lines/summary";
  private static final String PERMISO = "indicators:read-sale-lines-summary";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID funcionario;
  private UUID director;
  private UUID agente1;
  private UUID agente2;
  private UUID bot;
  private UUID upgrade;

  @BeforeEach
  void sembrar() {
    limpiar();
    funcionario = persona("isl-funcionario", ADMIN);
    director = persona("isl-director", DIRECTOR);
    agente1 = persona("isl-agente1", AGENTE);
    agente2 = persona("isl-agente2", AGENTE);
    reponerElSuelo(jdbc);
    bot = producto("ISL_BOT", "BOT");
    upgrade = producto("ISL_UPGRADE", "UPGRADE_MEMBRESIA");
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now())",
        agente1,
        director);

    linea(venta("CONFIRMADA", USD, "2026-09-02T15:00:00Z", 3000), bot, agente1, 3, 3000);
    linea(venta("CONFIRMADA", COP, "2026-09-03T15:00:00Z", 100000), upgrade, agente2, 1, 100000);
    linea(venta("PENDIENTE", USD, "2026-09-04T15:00:00Z", 2000), bot, null, 2, 2000);
    UUID mixta = venta("CONFIRMADA", USD, "2026-09-05T15:00:00Z", 4500);
    linea(mixta, bot, agente1, 1, 500);
    linea(mixta, upgrade, null, 4, 4000);
    linea(venta("ANULADA", USD, "2026-09-06T15:00:00Z", 700), bot, null, 1, 700);
    linea(venta("PENDIENTE", USD, "2026-09-07T15:00:00Z", 100), bot, agente2, 1, 100);
  }

  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-IN-059, CA-IN-067 y CA-IN-068 — lo vendido es lo confirmado, en total y por tipo; una"
          + " venta de dos tipos cuenta en cada uno")
  void loVendido() throws Exception {
    septiembre(funcionario)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sold.total.sales").value(3))
        .andExpect(jsonPath("$.sold.total.lines").value(4))
        .andExpect(jsonPath("$.sold.total.units").value(9))
        .andExpect(jsonPath("$.sold.total.amounts[*].currency.code", contains("COP", "USD")))
        .andExpect(jsonPath("$.sold.total.amounts[1].amount").value(75.0))
        .andExpect(jsonPath("$.sold.byType[*].type", contains("BOT", "UPGRADE_MEMBRESIA")))
        .andExpect(jsonPath("$.sold.byType[0].sales").value(2))
        .andExpect(jsonPath("$.sold.byType[0].lines").value(2))
        .andExpect(jsonPath("$.sold.byType[0].units").value(4))
        .andExpect(jsonPath("$.sold.byType[0].amounts[0].amount").value(35.0))
        // La mixta s4 cuenta en los dos tipos: 2 + 2 ventas por tipo, 3 en el total.
        .andExpect(jsonPath("$.sold.byType[1].sales").value(2))
        .andExpect(jsonPath("$.sold.byType[1].units").value(5))
        .andExpect(jsonPath("$.sold.byType[1].amounts[*].currency.code", contains("COP", "USD")))
        .andExpect(jsonPath("$.granularity").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.buckets").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName(
      "CA-IN-062, CA-IN-063, CA-IN-069 y CA-IN-070 — lo sin vendedor, en total y por tipo, sin las"
          + " anuladas; y ni pendientes ni anuladas en la respuesta")
  void loSinVendedor() throws Exception {
    septiembre(funcionario)
        .andExpect(jsonPath("$.unassigned.total.sales").value(2))
        .andExpect(jsonPath("$.unassigned.total.lines").value(2))
        .andExpect(jsonPath("$.unassigned.total.units").value(6))
        .andExpect(jsonPath("$.unassigned.total.amounts[0].amount").value(60.0))
        .andExpect(jsonPath("$.unassigned.byType[*].type", contains("BOT", "UPGRADE_MEMBRESIA")))
        .andExpect(jsonPath("$.unassigned.byType[0].units").value(2))
        .andExpect(jsonPath("$.unassigned.byType[1].units").value(4))
        .andExpect(jsonPath("$.unassigned.byType[1].amounts[0].amount").value(40.0))
        .andExpect(jsonPath("$.pending").doesNotExist())
        .andExpect(jsonPath("$.voided").doesNotExist())
        .andExpect(jsonPath("$.confirmed").doesNotExist());
  }

  @Test
  @DisplayName("CA-IN-061 — el total de lo vendido es lo confirmado del resumen de ventas")
  void cuadraConElResumenDeVentas() throws Exception {
    String lineas = septiembre(funcionario).andReturn().getResponse().getContentAsString();
    String resumen =
        mvc.perform(
                get("/api/v1/indicators/sales/summary")
                    .param("from", "2026-09-01")
                    .param("to", "2026-09-30")
                    .with(
                        user(funcionario.toString())
                            .authorities(() -> "indicators:read-sales-summary")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (String campo : new String[] {"sales", "lines", "units", "amounts"}) {
      assertThat((Object) JsonPath.read(lineas, "$.sold.total." + campo))
          .as(campo)
          .isEqualTo(JsonPath.read(resumen, "$.confirmed." + campo));
    }
  }

  @Test
  @DisplayName("CA-IN-064 — sin alcance: un director con el permiso ve lo mismo que administración")
  void sinAlcance() throws Exception {
    String deAdmin = septiembre(funcionario).andReturn().getResponse().getContentAsString();
    String deDirector = septiembre(director).andReturn().getResponse().getContentAsString();
    assertThat(deDirector).isEqualTo(deAdmin);
  }

  @Test
  @DisplayName("CA-IN-065 — periodo, moneda y tramos; la suma de los tramos es el total")
  void periodoMonedaYTramos() throws Exception {
    mvc.perform(
            get(RUTA)
                .param("from", "2026-08-01")
                .param("to", "2026-09-30")
                .param("granularity", "MONTH")
                .with(conPermiso(funcionario)))
        .andExpect(jsonPath("$.granularity").value("MONTH"))
        .andExpect(jsonPath("$.buckets[*].start", contains("2026-08-01", "2026-09-01")))
        .andExpect(jsonPath("$.buckets[0].sold.total.sales").value(0))
        .andExpect(jsonPath("$.buckets[0].sold.byType").isEmpty())
        .andExpect(jsonPath("$.buckets[0].unassigned.total.sales").value(0))
        .andExpect(jsonPath("$.buckets[1].sold.total.units").value(9))
        .andExpect(jsonPath("$.buckets[1].sold.byType.length()").value(2))
        .andExpect(jsonPath("$.buckets[1].unassigned.total.units").value(6));

    mvc.perform(get(RUTA).param("currencyId", COP).with(conPermiso(funcionario)))
        .andExpect(jsonPath("$.period.from").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.sold.total.sales").value(1))
        .andExpect(jsonPath("$.sold.byType[*].type", contains("UPGRADE_MEMBRESIA")))
        .andExpect(jsonPath("$.unassigned.total.sales").value(0))
        .andExpect(jsonPath("$.unassigned.byType").isEmpty());

    mvc.perform(
            get(RUTA)
                .param("from", "2026-09-30")
                .param("to", "2026-09-01")
                .param("granularity", "HORA")
                .with(conPermiso(funcionario)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2));
  }

  @Test
  @DisplayName(
      "CA-IN-066 — sin el permiso, 403, también con el listado de líneas o el resumen de ventas;"
          + " sin token, 401; sembrado solo a SUPERADMIN y ADMIN")
  void permisos() throws Exception {
    mvc.perform(get(RUTA).with(user(funcionario.toString()))).andExpect(status().isForbidden());
    for (String otro :
        new String[] {"movements:list-sale-lines", "indicators:read-sales-summary"}) {
      mvc.perform(get(RUTA).with(user(funcionario.toString()).authorities(() -> otro)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());

    assertThat(
            jdbc.queryForList(
                """
                SELECT r.code FROM roles r
                  JOIN role_permissions rp ON rp.role_id = r.id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE p.code = ?
                """,
                String.class,
                PERMISO))
        .containsExactlyInAnyOrder("SUPERADMIN", "ADMIN");
  }

  // ---------------------------------------------------------------------------

  private ResultActions septiembre(UUID actor) throws Exception {
    return mvc.perform(
        get(RUTA).param("from", "2026-09-01").param("to", "2026-09-30").with(conPermiso(actor)));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor conPermiso(
      UUID persona) {
    return user(persona.toString()).authorities(() -> PERMISO);
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM payments");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'ISL\\_%'");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'isl-%') OR supervisor_id IN (SELECT id FROM users WHERE username LIKE 'isl-%')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'isl-%')");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'isl-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'isl-%'");
  }

  private UUID persona(String username, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
    return id;
  }

  /** Un producto del tipo dado; el upgrade va de BECA a BECA, como en las suites afftrack. */
  private UUID producto(String codigo, String tipo) {
    UUID id = UUID.randomUUID();
    String beca = tipo.equals("BOT") ? null : "(SELECT id FROM memberships WHERE code = 'BECA')";
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days,"
            + " status) VALUES ('TIENDA', 'MANUAL', ?, ?, ?, ?, 'Producto de prueba', "
            + (beca == null ? "NULL, NULL" : beca + ", " + beca)
            + ", 10000, CAST(? AS uuid), NULL, 'ACTIVO')",
        id,
        codigo,
        tipo,
        "Producto " + codigo,
        USD);
    return id;
  }

  /** La cabecera; las líneas van aparte. Un estado ANULADA lleva su fecha y su motivo. */
  private UUID venta(String estado, String moneda, String cuando, long centesimas) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at, voided_at, void_reason)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                (SELECT id FROM users WHERE username = 'isl-funcionario'),
                CAST(? AS uuid), ?, ?, ?, 0, ?, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) END,
                CASE WHEN ? = 'ANULADA' THEN CAST(? AS timestamptz) END,
                CASE WHEN ? = 'ANULADA' THEN 'Prueba' END)
        """,
        id,
        VENTA,
        VENTA,
        moneda,
        "ISL-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        centesimas,
        centesimas,
        cuando,
        estado,
        cuando,
        estado,
        cuando,
        estado);
    PaymentFixtures.pagoDe(jdbc, id, TARJETA);
    return id;
  }

  private void linea(UUID venta, UUID producto, UUID vendedor, int unidades, long centesimas) {
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price,
                                      line_amount, validity_days, implementation)
        VALUES (?, ?, ?, ?, 'Bot de las líneas', 'Lo que decía el catálogo', ?, ?, ?, NULL,
                'MANUAL')
        """,
        UUID.randomUUID(),
        venta,
        producto,
        vendedor,
        unidades,
        centesimas / unidades,
        centesimas);
  }
}
