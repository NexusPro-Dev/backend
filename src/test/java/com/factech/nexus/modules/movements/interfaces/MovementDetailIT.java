package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.llenarBilletera;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.movements.PayoutFixtures;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
 * El detalle de cualquier movimiento (`RF-MV-007` · `T-03`): `CA-MV-287` a `CA-MV-292`.
 *
 * <p><b>La venta se siembra por SQL</b>, como en {@code ConfirmSaleIT}, y no por {@code POST
 * /movements} como decía el plan (`tasks.md` §6): esta suite solo lee, y registrar por HTTP exige
 * montar cliente, vendedores y catálogo que no afirman nada. El retiro sí sale por su ruta.
 */
@AutoConfigureMockMvc
class MovementDetailIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = LedgerFixtures.USD;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreditService abonos;

  private UUID administrador;
  private UUID cliente;
  private UUID vendedor;
  private UUID bot;

  @BeforeEach
  void sembrar() {
    limpiar();
    administrador = persona("dm-admin");
    cliente = persona("dm-cliente");
    vendedor = persona("dm-vendedor");
    bot = bot();
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("CA-MV-287 — una venta ajena se devuelve entera: cabecera, pagos y líneas")
  void ventaAjena() throws Exception {
    UUID venta = venta();

    mvc.perform(get("/api/v1/movements/{id}", venta).with(lector(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(venta.toString()))
        .andExpect(
            jsonPath("$.code").value("VTA-" + venta.toString().substring(0, 8).toUpperCase()))
        .andExpect(jsonPath("$.type").value("VENTA"))
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.typeStatus").value("VALIDADO"))
        .andExpect(jsonPath("$.user.id").value(cliente.toString()))
        .andExpect(jsonPath("$.currency.code").value("USD"))
        .andExpect(jsonPath("$.totalAmount").value(100.00))
        .andExpect(jsonPath("$.payments.length()").value(1))
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.lines[0].seller.id").value(vendedor.toString()));
  }

  @Test
  @DisplayName("CA-MV-288 — cada línea dice lo que se vendió, aunque el catálogo haya cambiado")
  void loCopiadoAlVender() throws Exception {
    UUID venta = venta();
    jdbc.update("UPDATE products SET name = 'Renombrado', price = 999.00 WHERE id = ?", bot);

    mvc.perform(get("/api/v1/movements/{id}", venta).with(lector(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].productName").value("Bot del detalle"))
        .andExpect(jsonPath("$.lines[0].unitPrice").value(100.00));
  }

  @Test
  @DisplayName("CA-MV-289 — un retiro se abre con su tipo, su estado y sin líneas")
  void unRetiro() throws Exception {
    llenarBilletera(abonos, cliente, "50.00");
    PayoutFixtures.listaParaRetirar(jdbc, cliente);
    String cuerpo =
        mvc.perform(
                post("/api/v1/movements/mine/withdrawals")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currencyId\":\"" + USD + "\",\"amount\":20.00}")
                    .with(
                        user(cliente.toString()).authorities(() -> "movements:request-withdrawal")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String retiro = JsonPath.read(cuerpo, "$.movement.id");

    mvc.perform(get("/api/v1/movements/{id}", retiro).with(lector(administrador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("RETIRO"))
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.lines").isArray())
        .andExpect(jsonPath("$.lines.length()").value(0));
  }

  @Test
  @DisplayName("CA-MV-290 — sobre un movimiento propio, lo mismo que el detalle propio")
  void loMismoQueElPropio() throws Exception {
    UUID venta = venta();

    String deAdministracion =
        mvc.perform(get("/api/v1/movements/{id}", venta).with(lector(cliente)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    String propio =
        mvc.perform(
                get("/api/v1/movements/mine/{id}", venta)
                    .with(user(cliente.toString()).authorities(() -> "movements:read-own")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(deAdministracion).isEqualTo(propio);
  }

  @Test
  @DisplayName(
      "CA-MV-291 — el inexistente es 404, y lo que no tiene forma de identificador también")
  void noExiste() throws Exception {
    mvc.perform(get("/api/v1/movements/{id}", UUID.randomUUID()).with(lector(administrador)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un movimiento con ese identificador."));
    mvc.perform(get("/api/v1/movements/no-es-un-id").with(lector(administrador)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-MV-292 — sin movements:read-detail es 403, aunque sea propio o se tenga el listado")
  void permisos() throws Exception {
    UUID venta = venta();
    mvc.perform(
            get("/api/v1/movements/{id}", venta)
                .with(user(cliente.toString()).authorities(() -> "movements:read-own")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/movements/{id}", venta)
                .with(user(administrador.toString()).authorities(() -> "movements:read")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/{id}", venta)).andExpect(status().isUnauthorized());

    // Y las rutas literales siguen siendo suyas: `/sales` no se toma por un identificador.
    mvc.perform(
            get("/api/v1/movements/sales")
                .with(user(vendedor.toString()).authorities(() -> "movements:list-sales")))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------

  private static RequestPostProcessor lector(UUID quien) {
    return user(quien.toString()).authorities(() -> "movements:read-detail");
  }

  private UUID venta() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), ?, 'PENDIENTE', 100.00, 0, 100.00, now())
        """,
        id,
        VENTA,
        VENTA,
        cliente,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase());
    PaymentFixtures.pagoDe(jdbc, id, TARJETA);
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price, line_amount,
                                      validity_days, implementation)
        SELECT ?, ?, p.id, ?, p.name, p.description, 1, 100.00, 100.00, p.validity_days,
               p.implementation
          FROM products p WHERE p.id = ?
        """,
        UUID.randomUUID(),
        id,
        vendedor,
        bot);
    return id;
  }

  private UUID bot() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days,"
            + " status, direct_commission_type, direct_commission_percentage)"
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, 'DM_BOT', 'BOT', 'Bot del detalle', 'Un bot',"
            + " NULL, NULL, 100.00, CAST(? AS uuid), 30, 'ACTIVO', 'PORCENTAJE', 0)",
        id,
        USD);
    return id;
  }

  private void limpiar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM products WHERE code LIKE 'DM\\_%'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'dm-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'dm-%'");
  }

  private UUID persona(String username) {
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
    darElSuelo(jdbc, id);
    return id;
  }
}
