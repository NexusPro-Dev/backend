package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.products.interfaces.ProductLinkTestSupport;
import com.factech.nexus.testing.CommissionCleanup;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-MV-010` — activar un producto comprado de implementación manual: <b>lo hace quien lo compró,
 * y activarlo es entregarlo</b> (`RN-MV-048`).
 *
 * <p>Las ventas se siembran por SQL en la forma que `RF-MV-003` deja —confirmada, con la línea
 * manual pendiente—, como en `MyProductsIT`. La confirmación se fecha en agosto a propósito: es lo
 * que deja ver que la vigencia corre desde la activación y no desde el pago.
 */
@AutoConfigureMockMvc
class ActivateMyProductIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final String ORO = "01a04ad0-e800-7004-9c4f-5e7ad7000004";
  private static final String PLATINO = "01a04ad0-e800-7003-9c4f-5e7ad7000003";
  private static final String VIP = "01a04ad0-e800-7002-9c4f-5e7ad7000002";
  private static final String BECA = "01a04ad0-e800-7001-9c4f-5e7ad7000001";

  private static final OffsetDateTime CONFIRMADA_EL =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID cliente;
  private UUID otro;
  private UUID vendedor;

  /** `AP_MANUAL`: bot manual, 30 días. */
  private UUID botManual;

  /** `AP_AUTO`: bot automático, 15 días. */
  private UUID botAutomatico;

  @BeforeEach
  void sembrar() {
    limpiar();
    reponerLaCadena();
    cliente = persona("ap-cliente");
    otro = persona("ap-otro");
    vendedor = persona("ap-vendedor");
    botManual = producto("AP_MANUAL", "BOT", "MANUAL", null, 30);
    botAutomatico = producto("AP_AUTO", "BOT", "AUTOMATICA", null, 15);
  }

  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // Activar es entregar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-275 — la línea manual propia se activa: ACTIVO, desde ahora y hasta + vigencia")
  void activa() throws Exception {
    UUID linea = lineaDe(venta(cliente, "CONFIRMADA", botManual));
    OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC);

    mvc.perform(activar(linea).with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lineId").value(linea.toString()))
        .andExpect(jsonPath("$.state").value("ACTIVO"))
        .andExpect(jsonPath("$.implementation").value("MANUAL"))
        .andExpect(jsonPath("$.deliveredAt").isNotEmpty())
        .andExpect(jsonPath("$.validUntil").isNotEmpty());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT delivery_status, delivered_at FROM movement_details WHERE id = ?", linea);
    assertThat(fila.get("delivery_status")).isEqualTo("ENTREGADA");
    OffsetDateTime entregada = instante(fila.get("delivered_at"));
    assertThat(entregada).isAfterOrEqualTo(antes.minusSeconds(1));

    // LA PERSONA LO TIENE (`RN-MV-036`): una posesión, con la vigencia desde la activación.
    Map<String, Object> posesion =
        jdbc.queryForMap(
            "SELECT user_id, started_at, ends_at FROM user_products WHERE movement_detail_id = ?",
            linea);
    assertThat(posesion.get("user_id")).isEqualTo(cliente);
    assertThat(instante(posesion.get("ends_at")))
        .isEqualTo(instante(posesion.get("started_at")).plusDays(30));
  }

  @Test
  @DisplayName("CA-MV-276 — la vigencia corre desde la activación, no desde la confirmación")
  void desdeLaActivacion() throws Exception {
    // Confirmada el 1 de agosto: contada desde ahí, 30 días ya habrían vencido.
    UUID linea = lineaDe(venta(cliente, "CONFIRMADA", botManual));

    mvc.perform(activar(linea).with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ACTIVO"));

    OffsetDateTime hasta =
        instante(
            jdbc.queryForObject(
                "SELECT ends_at FROM user_products WHERE movement_detail_id = ?",
                Object.class,
                linea));
    assertThat(hasta).isAfter(CONFIRMADA_EL.plusDays(30));
    assertThat(Duration.between(OffsetDateTime.now(ZoneOffset.UTC), hasta).toDays())
        .isBetween(29L, 30L);
  }

  @Test
  @DisplayName("CA-MV-277 — un upgrade manual concede el nivel; si baja, queda RETENIDO")
  void upgradeManual() throws Exception {
    UUID aVip = producto("AP_VIP", "UPGRADE_MEMBRESIA", "MANUAL", VIP, 30);

    // Desde la BECA del suelo, VIP sube: se concede.
    UUID sube = lineaDe(venta(cliente, "CONFIRMADA", aVip));
    mvc.perform(activar(sube).with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ACTIVO"));
    assertThat(vigenteDe(cliente)).isEqualTo("VIP");

    // Con PLATINO puesto por otra vía, VIP bajaría: se retiene, con su motivo.
    darMembresia(otro, PLATINO);
    UUID baja = lineaDe(venta(otro, "CONFIRMADA", aVip));
    mvc.perform(activar(baja).with(propio(otro)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("RETENIDO"))
        .andExpect(
            jsonPath("$.deliveryNote").value(org.hamcrest.Matchers.containsString("PLATINO")));
    assertThat(vigenteDe(otro)).isEqualTo("PLATINO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_products WHERE movement_detail_id = ?",
                Integer.class,
                baja))
        .isZero();
  }

  // ---------------------------------------------------------------------------
  // Solo quien compró, solo lo manual, solo una vez
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-278 — la línea ajena es 404, igual que una inexistente, con cualquier permiso")
  void loAjenoNoExiste() throws Exception {
    UUID linea = lineaDe(venta(cliente, "CONFIRMADA", botManual));
    RequestPostProcessor administrador =
        user(otro.toString())
            .authorities(
                () -> "movements:activate-own-product",
                () -> "movements:read",
                () -> "movements:confirm",
                () -> "movements:list-sale-lines");

    String ajena =
        mvc.perform(activar(linea).with(administrador))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String inexistente =
        mvc.perform(activar(UUID.randomUUID()).with(administrador))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // La misma respuesta, salvo la ruta que cada una pidió.
    assertThat(sinRuta(ajena)).isEqualTo(sinRuta(inexistente));
    assertThat(entregaDe(linea)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName("CA-MV-279 — una venta pendiente de pago o anulada es 409 con su estado")
  void ventaNoConfirmada() throws Exception {
    UUID pendiente = lineaDe(venta(cliente, "PENDIENTE", botManual));
    UUID anulada = lineaDe(venta(cliente, "ANULADA", botManual));

    mvc.perform(activar(pendiente).with(propio(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("PENDIENTE")));
    mvc.perform(activar(anulada).with(propio(cliente)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("ANULADA")));

    assertThat(entregaDe(pendiente)).isEqualTo("PENDIENTE");
    assertThat(entregaDe(anulada)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName("CA-MV-280 — una línea automática es 409: no se activa lo que se entrega solo")
  void automatica() throws Exception {
    UUID linea = lineaDe(venta(cliente, "CONFIRMADA", botAutomatico));

    mvc.perform(activar(linea).with(propio(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    assertThat(entregaDe(linea)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName("CA-MV-281 — la segunda activación es 409 y no escribe una segunda posesión")
  void laSegundaNoEntregaOtraVez() throws Exception {
    UUID linea = lineaDe(venta(cliente, "CONFIRMADA", botManual));

    mvc.perform(activar(linea).with(propio(cliente))).andExpect(status().isOk());
    mvc.perform(activar(linea).with(propio(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("ENTREGADA")));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_products WHERE movement_detail_id = ?",
                Integer.class,
                linea))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("CA-MV-282 — activada, trae el cupón del bot; antes de activarla, no")
  void elCuponLlegaConLaActivacion() throws Exception {
    ProductLinkTestSupport.enlace(jdbc, botManual, "CUPON_BOT", "https://t.me/apbot", "cupon-7");
    UUID linea = lineaDe(venta(cliente, "CONFIRMADA", botManual));

    mvc.perform(get("/api/v1/movements/mine/products").with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].lineId").value(linea.toString()))
        .andExpect(jsonPath("$.content[0].state").value("PENDIENTE_ACTIVACION"))
        .andExpect(jsonPath("$.content[0].couponUrl").doesNotExist());

    mvc.perform(activar(linea).with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.couponUrl").value("https://t.me/apbot/cupon-7"));
  }

  @Test
  @DisplayName(
      "CA-MV-283 — sin el permiso 403, sin token 401, identificador malformado 400; auditado")
  void accesoYAuditoria() throws Exception {
    UUID linea = lineaDe(venta(cliente, "CONFIRMADA", botManual));

    mvc.perform(activar(linea)).andExpect(status().isUnauthorized());
    // Con todo lo propio de MV MENOS el de activar.
    mvc.perform(
            activar(linea)
                .with(
                    user(cliente.toString())
                        .authorities(
                            () -> "movements:read-own-products",
                            () -> "movements:read-own",
                            () -> "movements:list-own")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/movements/mine/products/{lineId}/activation", "no-es-un-uuid")
                .with(propio(cliente)))
        .andExpect(status().isBadRequest());
    assertThat(entregaDe(linea)).isEqualTo("PENDIENTE");

    mvc.perform(activar(linea).with(propio(cliente))).andExpect(status().isOk());
    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE module = 'MV'"
                + " AND entity = 'movement_details' AND entity_id = ? AND action = 'UPDATE'",
            String.class,
            linea);
    assertThat(cambios).contains("PENDIENTE").contains("ENTREGADA").contains("activated_at");
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private static MockHttpServletRequestBuilder activar(UUID linea) {
    return post("/api/v1/movements/mine/products/{lineId}/activation", linea);
  }

  private static RequestPostProcessor propio(UUID persona) {
    return user(persona.toString())
        .authorities(() -> "movements:read-own-products", () -> "movements:activate-own-product");
  }

  private static String sinRuta(String cuerpo) {
    return cuerpo
        .replaceAll("\"instance\":\"[^\"]*\"", "")
        .replaceAll("\"correlationId\":(\"[^\"]*\"|null)", "");
  }

  private String entregaDe(UUID linea) {
    return jdbc.queryForObject(
        "SELECT delivery_status FROM movement_details WHERE id = ?", String.class, linea);
  }

  private UUID lineaDe(UUID venta) {
    return jdbc.queryForObject(
        "SELECT id FROM movement_details WHERE movement_id = ?", UUID.class, venta);
  }

  /** El código de la membresía abierta. */
  private String vigenteDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT m.code FROM user_products um JOIN memberships m ON m.id = um.membership_id"
            + " WHERE um.user_id = ? AND um.closed_at IS NULL",
        String.class,
        persona);
  }

  /** Cierra la membresía abierta y abre la dada, como si otra vía la hubiera concedido. */
  private void darMembresia(UUID persona, String membresia) {
    jdbc.update(
        "UPDATE user_products SET closed_at = now() WHERE user_id = ? AND closed_at IS NULL"
            + " AND membership_id IS NOT NULL",
        persona);
    jdbc.update(
        "INSERT INTO user_products (id, user_id, membership_id, started_at, ends_at)"
            + " VALUES (gen_random_uuid(), ?, CAST(? AS uuid), now(), NULL)",
        persona,
        membresia);
  }

  private static OffsetDateTime instante(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof OffsetDateTime momento) {
      return momento.withOffsetSameInstant(ZoneOffset.UTC);
    }
    if (valor instanceof java.sql.Timestamp marca) {
      return marca.toInstant().atOffset(ZoneOffset.UTC);
    }
    throw new IllegalStateException("Tipo temporal inesperado: " + valor.getClass());
  }

  // Las posesiones van PRIMERO: `fk_user_products_product` es RESTRICT, y un
  // `DELETE FROM products` con una posesión colgando falla lejos de aquí.
  private void limpiar() {
    jdbc.update("DELETE FROM user_products WHERE movement_detail_id IS NOT NULL");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'ap-%')");
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_detail_discounts");
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    ProductLinkTestSupport.limpiar(jdbc);
    jdbc.update("DELETE FROM products WHERE code LIKE 'AP\\_%'");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ap-%'");
  }

  /** La cadena entera de `V9`, porque las pruebas de `SP` la dejan como quieren. */
  private void reponerLaCadena() {
    jdbc.update("DELETE FROM user_products");
    jdbc.update("DELETE FROM memberships");
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color)
        VALUES
          (CAST(? AS uuid), 'ORO', 'Oro', 'Nivel más alto.', NULL, 1, 'FFB300'),
          (CAST(? AS uuid), 'PLATINO', 'Platino', 'Nivel intermedio.', CAST(? AS uuid), 2, 'B0BEC5'),
          (CAST(? AS uuid), 'VIP', 'VIP', 'Primer nivel de pago.', CAST(? AS uuid), 3, '7E57C2'),
          (CAST(? AS uuid), 'BECA', 'Beca', 'Nivel de entrada.', CAST(? AS uuid), 4, '9E9E9E')
        """,
        ORO,
        PLATINO,
        ORO,
        VIP,
        PLATINO,
        BECA,
        VIP);
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    darElSuelo(jdbc, id);
    return id;
  }

  private UUID producto(
      String codigo, String tipo, String implementacion, String destino, Integer vigencia) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', ?, ?, ?, ?, ?, 'De prueba', CAST(? AS uuid), CAST(? AS uuid),"
            + " 100.00, CAST(? AS uuid), ?, 'ACTIVO')",
        implementacion,
        id,
        codigo,
        tipo,
        "Producto " + codigo,
        destino == null ? null : BECA,
        destino,
        USD,
        vigencia);
    return id;
  }

  /** Una venta con una línea, pendiente de entrega, como `RF-MV-003` la deja. */
  private UUID venta(UUID sujeto, String estado, UUID producto) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at,
                               voided_at, void_reason)
        VALUES (?, CAST(? AS uuid), (SELECT s.id FROM movement_type_statuses s WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'), ?, CAST(? AS uuid), ?, ?,
                100.00, 0, 100.00, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN now() ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN 'Sembrada anulada' ELSE NULL END)
        """,
        id,
        VENTA,
        VENTA,
        sujeto,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        CONFIRMADA_EL.toString(),
        estado,
        CONFIRMADA_EL.toString(),
        estado,
        estado);
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
        producto);
    return id;
  }
}
