package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.CommissionCleanup;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** `RF-MV-018` — volver a pagar una venta propia pendiente. */
@AutoConfigureMockMvc
class RetryPaymentIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String PSE = "01a061ba-3400-7003-9c4f-5e7ad7000022";
  private static final String GRATIS = "01a08646-7a00-7001-9c4f-5e7adb000001";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID cliente;
  private UUID otro;
  private UUID bot;

  @BeforeEach
  void sembrar() {
    limpiar();
    cliente = persona("rp-cliente");
    otro = persona("rp-otro");
    bot = producto("RP_BOT");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-206 y CA-MV-207 — sobre una venta con el pago rechazado abre otro pendiente, con otro"
          + " método; la venta sigue pendiente")
  void abreOtroPago() throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, "100.00");

    mvc.perform(pagar(venta, PSE, "reintento-0001").with(propio(cliente)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.payments.length()").value(2))
        .andExpect(jsonPath("$.payments[0].status").value("RECHAZADO"))
        .andExpect(jsonPath("$.payments[1].status").value("PENDIENTE"))
        .andExpect(jsonPath("$.payments[1].paymentMethod.code").value("PSE"))
        .andExpect(jsonPath("$.payments[1].amount").value(100.00));
  }

  @Test
  @DisplayName("CA-MV-208 — la misma petición repetida devuelve 200 y no abre otro pago")
  void laMismaPeticion() throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, "100.00");

    mvc.perform(pagar(venta, PSE, "reintento-0002").with(propio(cliente)))
        .andExpect(status().isCreated());
    mvc.perform(pagar(venta, PSE, "reintento-0002").with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments.length()").value(2));

    assertThat(pagosDe(venta)).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-MV-209 — la misma clave con otra venta o con otro método es 409")
  void laMismaClaveConOtraPeticion() throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, "100.00");
    UUID segunda = ventaConPagoRechazado(cliente, "100.00");
    mvc.perform(pagar(venta, PSE, "reintento-0003").with(propio(cliente)))
        .andExpect(status().isCreated());

    mvc.perform(pagar(venta, TARJETA, "reintento-0003").with(propio(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
    mvc.perform(pagar(segunda, PSE, "reintento-0003").with(propio(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    assertThat(pagosDe(segunda)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-MV-210 — con un pago pendiente es 409; dos peticiones a la vez con claves distintas"
          + " abren UN pago")
  void unSoloPendiente() throws Exception {
    UUID pendiente = venta(cliente, "PENDIENTE", "100.00", TARJETA);
    mvc.perform(pagar(pendiente, PSE, "reintento-0004").with(propio(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));

    UUID venta = ventaConPagoRechazado(cliente, "100.00");
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> respuestas = new ArrayList<>();
      for (String clave : List.of("carrera-000A", "carrera-000B")) {
        respuestas.add(
            hilos.submit(
                () -> {
                  salida.await();
                  return mvc.perform(pagar(venta, PSE, clave).with(propio(cliente)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      salida.countDown();
      List<Integer> codigos = new ArrayList<>();
      for (Future<Integer> r : respuestas) {
        codigos.add(r.get());
      }
      assertThat(codigos).containsExactlyInAnyOrder(201, 409);
    } finally {
      hilos.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE movement_id = ? AND status = 'PENDIENTE'",
                Integer.class,
                venta))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("CA-MV-211 — una venta confirmada o anulada es 409 con su estado")
  void noPendiente() throws Exception {
    UUID confirmada = venta(cliente, "CONFIRMADA", "100.00", TARJETA);
    UUID anulada = venta(cliente, "ANULADA", "100.00", TARJETA);

    mvc.perform(pagar(confirmada, PSE, "reintento-0005").with(propio(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CONFIRMADA")));
    mvc.perform(pagar(anulada, PSE, "reintento-0006").with(propio(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ANULADA")));
  }

  @Test
  @DisplayName("CA-MV-212 — una venta ajena es 404, igual que una que no existe")
  void ajena() throws Exception {
    UUID ajena = ventaConPagoRechazado(otro, "100.00");

    mvc.perform(pagar(ajena, PSE, "reintento-0007").with(propio(cliente)))
        .andExpect(status().isNotFound());
    mvc.perform(pagar(UUID.randomUUID(), PSE, "reintento-0008").with(propio(cliente)))
        .andExpect(status().isNotFound());
    assertThat(pagosDe(ajena)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-MV-213 — el método sigue las reglas de registrar; en importe cero, sin método, se abre"
          + " con el gratuito")
  void metodoComoAlRegistrar() throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, "100.00");
    mvc.perform(pagar(venta, UUID.randomUUID().toString(), "reintento-0009").with(propio(cliente)))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(pagar(venta, GRATIS, "reintento-0010").with(propio(cliente)))
        .andExpect(status().is4xxClientError());

    UUID gratis = venta(cliente, "PENDIENTE", "0.00", GRATIS);
    jdbc.update(
        "UPDATE payments SET status = 'RECHAZADO', rejected_at = now() WHERE movement_id = ?",
        gratis);
    mvc.perform(pagar(gratis, null, "reintento-0011").with(propio(cliente)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.payments[1].paymentMethod.code").value("GRATIS"));
  }

  @Test
  @DisplayName("CA-MV-214 — sin clave, o con una clave malformada, es 400 y no toca la venta")
  void claveObligatoria() throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, "100.00");

    mvc.perform(pagar(venta, PSE, null).with(propio(cliente)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(pagar(venta, PSE, "corta").with(propio(cliente)))
        .andExpect(status().isBadRequest());
    mvc.perform(pagar(venta, PSE, "con espacios dentro").with(propio(cliente)))
        .andExpect(status().isBadRequest());
    assertThat(pagosDe(venta)).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-MV-215 — sin movements:retry-payment es 403; sin token, 401")
  void permisos() throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, "100.00");

    mvc.perform(
            pagar(venta, PSE, "reintento-0012")
                .with(user(cliente.toString()).authorities(() -> "movements:read-own")))
        .andExpect(status().isForbidden());
    mvc.perform(pagar(venta, PSE, "reintento-0013")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-MV-217 y CA-MV-222 — el listado publica y filtra por el método del ÚLTIMO pago")
  void elMetodoEsElDelUltimoPago() throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, "100.00");
    mvc.perform(pagar(venta, PSE, "reintento-0014").with(propio(cliente)))
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/v1/movements/mine/shopping")
                .param("paymentMethodId", PSE)
                .with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(venta.toString()));
    mvc.perform(
            get("/api/v1/movements/mine/shopping")
                .param("paymentMethodId", TARJETA)
                .with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  // ---------------------------------------------------------------------------

  private static MockHttpServletRequestBuilder pagar(UUID venta, String metodo, String clave) {
    MockHttpServletRequestBuilder peticion =
        post("/api/v1/movements/mine/{id}/payments", venta)
            .contentType(MediaType.APPLICATION_JSON)
            .content(metodo == null ? "{}" : "{\"paymentMethodId\":\"" + metodo + "\"}");
    return clave == null ? peticion : peticion.header("Idempotency-Key", clave);
  }

  private static RequestPostProcessor propio(UUID persona) {
    return user(persona.toString())
        .authorities(
            () -> "movements:retry-payment",
            () -> "movements:list-own",
            () -> "movements:read-own");
  }

  private int pagosDe(UUID venta) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM payments WHERE movement_id = ?", Integer.class, venta);
  }

  private UUID ventaConPagoRechazado(UUID sujeto, String importe) {
    UUID venta = venta(sujeto, "PENDIENTE", importe, TARJETA);
    jdbc.update(
        "UPDATE payments SET status = 'RECHAZADO', rejected_at = now(),"
            + " rejection_reason = 'Fondos insuficientes' WHERE movement_id = ?",
        venta);
    return venta;
  }

  private UUID venta(UUID sujeto, String estado, String importe, String metodo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id,
                               code, status, total_amount, discount_amount, payable_amount,
                               occurred_at, confirmed_at, voided_at, void_reason)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), ?, ?, CAST(? AS numeric) * 100, 0, CAST(? AS numeric) * 100,
                CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN now() ELSE NULL END,
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
        importe,
        importe,
        OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC).toString(),
        estado,
        estado,
        estado);
    PaymentFixtures.pagoDe(jdbc, id, metodo);
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price, line_amount,
                                      validity_days, implementation)
        SELECT ?, ?, p.id, ?, p.name, p.description, 1, CAST(? AS numeric) * 100, CAST(? AS numeric) * 100,
               p.validity_days, p.implementation FROM products p WHERE p.id = ?
        """,
        UUID.randomUUID(),
        id,
        sujeto,
        importe,
        importe,
        bot);
    return id;
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'RP\\_%'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'rp-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'rp-%'");
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

  private UUID producto(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, ?, 'BOT', ?, 'x', 10000, CAST(? AS uuid), 30,"
            + " 'ACTIVO')",
        id,
        codigo,
        "Producto " + codigo,
        USD);
    return id;
  }
}
