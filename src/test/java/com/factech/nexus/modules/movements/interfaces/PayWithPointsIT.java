package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static com.factech.nexus.modules.movements.PointsFixtures.POINTS;
import static com.factech.nexus.modules.movements.PointsFixtures.TARJETA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.movements.PointsFixtures;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** `RF-MV-030` — pagar una compra con puntos. */
@AutoConfigureMockMvc
class PayWithPointsIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID comprador;
  private UUID vendedor;
  private UUID administrador;
  private UUID bot;

  @BeforeEach
  void sembrar() {
    limpiar();
    UUID rolVendedor = rol("PW_VENDEDOR", "VENDEDOR");
    comprador = persona("pw-comprador");
    vendedor = persona("pw-vendedor");
    administrador = persona("pw-admin");
    darRol(vendedor, rolVendedor);
    colgarDe(comprador, vendedor);
    bot = bot("PW_BOT", "10.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-332 y CA-MV-334 — por el enlace de un vendedor, con puntos: confirmada, con su pago"
          + " confirmado, lo comprado entregado y la comisión avisada")
  void porElEnlace() throws Exception {
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
    darPuntos(comprador, "20.00"); // 2000 puntos

    String cuerpo =
        mvc.perform(
                post("/api/v1/hotlinks/{u}/{c}/purchases", "pw-vendedor", "PW_BOT")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"paymentMethodId\":\"" + POINTS + "\"}")
                    .with(user(comprador.toString()).authorities(() -> "products:buy-by-hotlink")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CONFIRMADA"))
            .andExpect(jsonPath("$.confirmedAt").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID venta = UUID.fromString(JsonPath.read(cuerpo, "$.id"));

    assertThat(estadoDe(venta)).isEqualTo("CONFIRMADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM payments WHERE movement_id = ?", String.class, venta))
        .isEqualTo("CONFIRMADO");
    assertThat(
            jdbc.queryForObject(
                "SELECT delivery_status FROM movement_details WHERE movement_id = ?",
                String.class,
                venta))
        .isEqualTo("ENTREGADA");
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("1000.00");

    // CA-MV-334: comisiones recibió el aviso de siempre y dejó el desenlace de la línea.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commission_accruals a"
                    + " JOIN movement_details d ON d.id = a.movement_detail_id"
                    + " WHERE d.movement_id = ?",
                Integer.class,
                venta))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-MV-333 y CA-MV-336 — volver a pagar con puntos confirma en el acto, descontando hacia"
          + " arriba: 10.00 a 0.3331 cuestan 3.34, en dos asientos de PAGO que llevan el pago")
  void reintentoConPuntos() throws Exception {
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
    darPuntos(comprador, "1.00"); // 100 puntos
    PointsFixtures.tasa(jdbc, USD, "0.3331", administrador);
    UUID venta = ventaConPagoRechazado(comprador, "10.00");

    mvc.perform(reintento(venta, POINTS, "pw-reintento-01"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.payments.length()").value(2))
        .andExpect(jsonPath("$.payments[1].status").value("CONFIRMADO"))
        .andExpect(jsonPath("$.payments[1].paymentMethod.code").value("POINTS"))
        .andExpect(jsonPath("$.payments[1].points").value(3.34))
        .andExpect(jsonPath("$.payments[0].points").doesNotExist());

    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("96.66");
    assertThat(emitidos()).isEqualByComparingTo("-96.66");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_entries WHERE movement_id = ? AND event = 'PAGO'"
                    + " AND payment_id IS NOT NULL",
                Integer.class,
                venta))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("CA-MV-335 — un paquete comprado con puntos se confirma igual")
  void paquete() throws Exception {
    PointsFixtures.tasa(jdbc, USD, "1", administrador);
    darPuntos(comprador, "50.00");
    UUID paquete = paquete("PW_PAQ");
    // Un paquete lleva al menos dos productos (`RN-PM-039`): 10.00 + 5.00.
    UUID otroBot = bot("PW_BOT2", "5.00");
    int orden = 0;
    for (UUID producto : List.of(bot, otroBot)) {
      jdbc.update(
          "INSERT INTO product_package_items (package_id, product_id, discount_type,"
              + " discount_value, created_at) VALUES (?, ?, 'FIJO', 0, ?)",
          paquete,
          producto,
          BASE.plusSeconds(++orden));
    }

    mvc.perform(
            post("/api/v1/packages/PW_PAQ/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"" + POINTS + "\"}")
                .with(user(comprador.toString()).authorities(() -> "packages:buy")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.confirmedAt").exists());
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("35.00");
  }

  @Test
  @DisplayName(
      "CA-MV-337 — si no alcanzan: 409 y no queda nada escrito, ni la venta por el enlace ni el"
          + " pago nuevo del reintento")
  void noAlcanza() throws Exception {
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
    darPuntos(comprador, "0.05"); // 5 puntos
    long ventasAntes = ventas();

    mvc.perform(
            post("/api/v1/hotlinks/{u}/{c}/purchases", "pw-vendedor", "PW_BOT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"" + POINTS + "\"}")
                .with(user(comprador.toString()).authorities(() -> "products:buy-by-hotlink")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-052"));
    assertThat(ventas()).isEqualTo(ventasAntes);

    UUID venta = ventaConPagoRechazado(comprador, "10.00");
    mvc.perform(reintento(venta, POINTS, "pw-reintento-02")).andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE movement_id = ?", Integer.class, venta))
        .isEqualTo(1);
    assertThat(estadoDe(venta)).isEqualTo("PENDIENTE");
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("5.00");
  }

  @Test
  @DisplayName(
      "CA-MV-338 y CA-MV-339 — con puntos de otra moneda, o sin tasa en la de la venta: 409, y"
          + " nada queda escrito")
  void monedaYTasa() throws Exception {
    UUID otra = PointsFixtures.moneda(jdbc, "ZZP", true);
    PointsFixtures.tasa(jdbc, otra.toString(), "100", administrador);
    darPuntosEn(comprador, otra.toString(), "50.00");
    UUID venta = ventaConPagoRechazado(comprador, "10.00");

    // CA-MV-339: USD todavía sin tasa.
    mvc.perform(reintento(venta, POINTS, "pw-reintento-03"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-050"));

    // CA-MV-338: con tasa en USD, los 5000 puntos de ZZP no sirven.
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
    mvc.perform(reintento(venta, POINTS, "pw-reintento-04"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-052"));
    assertThat(estadoDe(venta)).isEqualTo("PENDIENTE");
    assertThat(
            jdbc.queryForObject(
                "SELECT balance FROM accounts WHERE user_id = ? AND kind = 'PUNTOS'"
                    + " AND currency_id = ?",
                BigDecimal.class,
                comprador,
                otra))
        .isEqualByComparingTo("5000.00");
  }

  @Test
  @DisplayName(
      "CA-MV-340 y CA-MV-341 — dos compras a la vez que juntas superan el saldo: una pasa; el"
          + " saldo nunca es negativo; y la misma petición descuenta una vez")
  void concurrenciaEIdempotencia() throws Exception {
    PointsFixtures.tasa(jdbc, USD, "10", administrador);
    darPuntos(comprador, "10.00"); // 100 puntos; cada venta de 6.00 cuesta 60
    UUID una = ventaConPagoRechazado(comprador, "6.00");
    UUID otra = ventaConPagoRechazado(comprador, "6.00");

    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    List<Integer> estados = new ArrayList<>();
    try {
      Future<Integer> a =
          hilos.submit(
              () -> {
                salida.await();
                return mvc.perform(reintento(una, POINTS, "pw-carrera-01"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      Future<Integer> b =
          hilos.submit(
              () -> {
                salida.await();
                return mvc.perform(reintento(otra, POINTS, "pw-carrera-02"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      salida.countDown();
      estados.add(a.get());
      estados.add(b.get());
    } finally {
      hilos.shutdownNow();
    }
    assertThat(estados).containsExactlyInAnyOrder(201, 409);
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("40.00");

    // CA-MV-341
    darPuntos(comprador, "10.00");
    UUID tercera = ventaConPagoRechazado(comprador, "1.00");
    mvc.perform(reintento(tercera, POINTS, "pw-repetida-01")).andExpect(status().isCreated());
    mvc.perform(reintento(tercera, POINTS, "pw-repetida-01")).andExpect(status().isOk());
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("130.00");
  }

  @Test
  @DisplayName(
      "CA-MV-342 — un funcionario que registra la venta de otra persona con puntos: 409, y nada"
          + " queda escrito")
  void funcionarioNo() throws Exception {
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
    darPuntos(comprador, "20.00");
    long antes = ventas();
    mvc.perform(
            post("/api/v1/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\"%s\",\"paymentMethodId\":\"%s\",\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}"
                        .formatted(comprador, POINTS, bot))
                .with(user(administrador.toString()).authorities(() -> "movements:create")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-052"));
    assertThat(ventas()).isEqualTo(antes);
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("2000.00");
  }

  @Test
  @DisplayName(
      "CA-MV-357 — un funcionario que registra una venta A SU PROPIO NOMBRE la paga con sus puntos"
          + " y queda confirmada")
  void funcionarioASuNombre() throws Exception {
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
    darPuntos(comprador, "20.00");
    mvc.perform(
            post("/api/v1/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\"%s\",\"paymentMethodId\":\"%s\",\"lines\":[{\"productId\":\"%s\",\"quantity\":1}]}"
                        .formatted(comprador, POINTS, bot))
                .with(user(comprador.toString()).authorities(() -> "movements:create")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isLessThan(new java.math.BigDecimal("2000.00"));
  }

  @Test
  @DisplayName(
      "CA-MV-343 — la migración rechaza los pagos POINTS pendientes; la venta sigue pendiente y"
          + " se vuelve a pagar")
  void migracion() throws Exception {
    UUID venta = venta(comprador, "10.00", POINTS);
    assertThat(estadoDelPago(venta)).isEqualTo("PENDIENTE");

    // La sentencia de V58 tal como está escrita en la migración, no una copia.
    jdbc.update(sentenciaDeLaMigracion());

    assertThat(estadoDelPago(venta)).isEqualTo("RECHAZADO");
    assertThat(
            jdbc.queryForObject(
                "SELECT rejection_reason FROM payments WHERE movement_id = ?", String.class, venta))
        .contains("vuelve a pagar");
    assertThat(estadoDe(venta)).isEqualTo("PENDIENTE");
    mvc.perform(reintento(venta, TARJETA, "pw-migracion-01")).andExpect(status().isCreated());
  }

  // ---------------------------------------------------------------------------

  /** Compra puntos por la API y los confirma: el saldo es la copia de sus asientos. */
  private void darPuntos(UUID quien, String importe) throws Exception {
    darPuntosEn(quien, USD, importe);
  }

  private void darPuntosEn(UUID quien, String moneda, String importe) throws Exception {
    String cuerpo =
        mvc.perform(
                post("/api/v1/movements/mine/points-purchases")
                    .header("Idempotency-Key", "pw-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"currencyId\":\"%s\",\"amount\":%s,\"paymentMethodId\":\"%s\"}"
                            .formatted(moneda, importe, TARJETA))
                    .with(user(quien.toString()).authorities(() -> "movements:buy-points")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(
            post(
                    "/api/v1/movements/{id}/points-purchase-confirmation",
                    (String) JsonPath.read(cuerpo, "$.id"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:confirm-points-purchase")))
        .andExpect(status().isOk());
  }

  private MockHttpServletRequestBuilder reintento(UUID venta, String metodo, String clave) {
    return post("/api/v1/movements/mine/{id}/payments", venta)
        .header("Idempotency-Key", clave)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"paymentMethodId\":\"" + metodo + "\"}")
        .with(
            user(comprador.toString())
                .authorities(
                    () -> "movements:retry-payment",
                    () -> "movements:list-own",
                    () -> "movements:read-own"));
  }

  private String sentenciaDeLaMigracion() throws Exception {
    String sql =
        new String(
            new ClassPathResource("db/migration/V58__mv_puntos.sql")
                .getInputStream()
                .readAllBytes(),
            StandardCharsets.UTF_8);
    Matcher m = Pattern.compile("UPDATE payments p[\\s\\S]*?;").matcher(sql);
    assertThat(m.find()).as("V58 rechaza los pagos POINTS pendientes").isTrue();
    return m.group().replaceAll(";$", "");
  }

  private UUID ventaConPagoRechazado(UUID sujeto, String importe) {
    UUID venta = venta(sujeto, importe, TARJETA);
    jdbc.update(
        "UPDATE payments SET status = 'RECHAZADO', rejected_at = now(),"
            + " rejection_reason = 'Fondos insuficientes' WHERE movement_id = ?",
        venta);
    return venta;
  }

  private UUID venta(UUID sujeto, String importe, String metodo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id,
                               code, status, total_amount, discount_amount, payable_amount,
                               occurred_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), ?, 'PENDIENTE', CAST(? AS numeric), 0, CAST(? AS numeric),
                ?)
        """,
        id,
        VENTA,
        VENTA,
        sujeto,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        importe,
        importe,
        BASE);
    PaymentFixtures.pagoDe(jdbc, id, metodo);
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price, line_amount,
                                      validity_days, implementation)
        SELECT ?, ?, p.id, ?, p.name, p.description, 1, CAST(? AS numeric), CAST(? AS numeric),
               p.validity_days, p.implementation FROM products p WHERE p.id = ?
        """,
        UUID.randomUUID(),
        id,
        vendedor,
        importe,
        importe,
        bot);
    return id;
  }

  private String estadoDe(UUID movimiento) {
    return jdbc.queryForObject(
        "SELECT status FROM movements WHERE id = ?", String.class, movimiento);
  }

  private String estadoDelPago(UUID venta) {
    return jdbc.queryForObject(
        "SELECT status FROM payments WHERE movement_id = ?", String.class, venta);
  }

  private long ventas() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM movements m JOIN movement_types t ON t.id = m.movement_type_id"
            + " WHERE t.code = 'VENTA'",
        Long.class);
  }

  private BigDecimal emitidos() {
    return jdbc.queryForObject(
        "SELECT balance FROM accounts WHERE user_id IS NULL AND kind = 'PUNTOS_EMITIDOS'"
            + " AND currency_id = CAST(? AS uuid)",
        BigDecimal.class,
        USD);
  }

  // ---------------------------------------------------------------- siembra

  /** Con padre: `uq_roles_single_root` admite un solo rol raíz (ver `BuyByHotlinkIT`). */
  private UUID rol(String codigo, String tipo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO roles (id, code, name, role_type, parent_role_id)"
            + " VALUES (?, ?, ?, ?, (SELECT r.id FROM roles r WHERE r.code = 'MANAGER'))",
        id,
        codigo,
        codigo,
        tipo);
    return id;
  }

  private void darRol(UUID persona, UUID rol) {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?",
        persona,
        rol);
  }

  private void colgarDe(UUID cliente, UUID quien) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)"
            + " VALUES (?, ?, 'REGISTRO', NULL, ?)",
        cliente,
        quien,
        BASE);
  }

  /** Un bot AUTOMÁTICO de alcance `AMBOS`: se entrega al confirmar, y se ofrece por el enlace. */
  private UUID bot(String codigo, String precio) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO products (id, code, type, name, description, price, currency_id,
                              validity_days, status, scope, implementation, created_at, updated_at)
        VALUES (?, ?, 'BOT', ?, 'Sembrado por PayWithPointsIT', CAST(? AS numeric),
                CAST(? AS uuid), 30, 'ACTIVO', 'AMBOS', 'AUTOMATICA', ?, ?)
        """,
        id,
        codigo,
        codigo,
        precio,
        USD,
        BASE,
        BASE);
    return id;
  }

  private UUID paquete(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_packages (id, code, name, description, currency_id, status, scope,"
            + " valid_from) VALUES (?, ?, ?, 'x', CAST(? AS uuid), 'ACTIVO', 'AMBOS',"
            + " (now() AT TIME ZONE 'UTC')::date)",
        id,
        codigo,
        "Paquete " + codigo,
        USD);
    return id;
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Ana', 'Ruiz', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@nexus.test");
    darElSuelo(jdbc, id);
    return id;
  }

  private void limpiar() {
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'pw-%')"
            + " OR seller_id IN (SELECT id FROM users WHERE username LIKE 'pw-%')");
    PointsFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM product_package_items WHERE package_id IN"
            + " (SELECT id FROM product_packages WHERE code LIKE 'PW\\_%')");
    jdbc.update("DELETE FROM product_packages WHERE code LIKE 'PW\\_%'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'pw-%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'PW\\_%'");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'pw-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'pw-%'");
    jdbc.update("DELETE FROM roles WHERE code LIKE 'PW\\_%'");
  }
}
