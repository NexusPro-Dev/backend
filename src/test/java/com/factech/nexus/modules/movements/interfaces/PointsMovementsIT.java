package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.PointsFixtures.TARJETA;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.modules.movements.PointsFixtures;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-MV-056` — los movimientos de puntos de todas las personas, su detalle y el comprobante; y
 * `RF-MV-054` — los saldos de una persona. Hasta el 06-10-2026 era {@code PointsAdjustmentListIT}
 * (`RF-MV-053`, retirado).
 */
@AutoConfigureMockMvc
class PointsMovementsIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/movements/points-movements";
  private static final String AJUSTES = "/api/v1/movements/points-adjustments";

  /** La firma de PNG y unos bytes: lo que reconoce `PointsReceipt`. */
  static final byte[] PNG = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48
  };

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreditService abonos;

  private UUID ana;
  private UUID jose;
  private UUID administrador;
  private UUID compra;
  private UUID ajusteDeAna;

  @BeforeEach
  void sembrar() throws Exception {
    limpiar();
    ana = persona("ajl-ana", "Ána María", "Pérez");
    jose = persona("ajl-jose", "José", "Gómez");
    administrador = persona("ajl-admin", "Laura", "Admin");
    PointsFixtures.tasa(jdbc, USD, "100", administrador);

    // Tres ajustes, en este orden: +100 a Ana, +50 a José, −20 a Ana.
    ajusteDeAna = ajustar(ana, "100", "Consignación Bancolombia", "CONS-001", "ajuste-lista-01");
    ajustar(jose, "50", "Pago por Nequi", null, "ajuste-lista-02");
    ajustar(ana, "-20", "Error de digitación", null, "ajuste-lista-03");
    // Y la compra de Ana, la más reciente: 2 USD a 100 = 200 puntos, pendiente.
    compra = comprar(ana, "2.00", "compra-mov-0001");
    // Un bono en la billetera: otro tipo, no debe salir (CA-MV-683).
    LedgerFixtures.llenarBilletera(abonos, ana, "10.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-674, CA-MV-680 y CA-MV-683 — compras y ajustes de todos, los más recientes primero,"
          + " con la persona y quién hizo el ajuste; ni bonos ni documento")
  void listado() throws Exception {
    mvc.perform(get(RUTA).with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[0].id").value(compra.toString()))
        .andExpect(jsonPath("$.content[0].type").value("COMPRA_PUNTOS"))
        .andExpect(jsonPath("$.content[0].status").value("PENDIENTE"))
        .andExpect(jsonPath("$.content[0].points").value(200))
        .andExpect(jsonPath("$.content[0].amount").value(2))
        .andExpect(jsonPath("$.content[0].concept").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].adjustedBy").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].hasReceipt").value(false))
        .andExpect(jsonPath("$.content[1].type").value("AJUSTE_PUNTOS"))
        .andExpect(jsonPath("$.content[1].points").value(-20))
        .andExpect(jsonPath("$.content[1].amount").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.content[3].code").value(Matchers.startsWith("AJP-")))
        .andExpect(jsonPath("$.content[3].status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.content[3].concept").value("Consignación Bancolombia"))
        .andExpect(jsonPath("$.content[3].reference").value("CONS-001"))
        .andExpect(jsonPath("$.content[3].user.id").value(ana.toString()))
        .andExpect(jsonPath("$.content[3].user.fullName").value("Ána María Pérez"))
        .andExpect(jsonPath("$.content[3].user.username").value("ajl-ana"))
        .andExpect(jsonPath("$.content[3].user.email").value("ajl-ana@factech.co"))
        .andExpect(jsonPath("$.content[3].user.document").doesNotExist())
        .andExpect(jsonPath("$.content[3].currency.code").value("USD"))
        .andExpect(jsonPath("$.content[3].adjustedBy.id").value(administrador.toString()))
        .andExpect(jsonPath("$.content[3].adjustedBy.fullName").value("Laura Admin"));

    // Un ajuste sin quién lo hizo —anterior a V73— sale con adjustedBy nulo.
    jdbc.update(
        "UPDATE movements SET recorded_by = NULL WHERE idempotency_key = 'ajuste-lista-02'");
    mvc.perform(get(RUTA).param("userId", jose.toString()).with(lector()))
        .andExpect(jsonPath("$.content[0].adjustedBy").value(Matchers.nullValue()));
  }

  @Test
  @DisplayName(
      "CA-MV-675 y CA-MV-676 — persona, tipo, estado, moneda, periodo y sentido filtran y se"
          + " combinan")
  void filtros() throws Exception {
    mvc.perform(get(RUTA).param("userId", ana.toString()).with(lector()))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get(RUTA).param("type", "ajuste_puntos").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get(RUTA).param("type", "COMPRA_PUNTOS").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("status", "PENDIENTE").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(compra.toString()));
    mvc.perform(get(RUTA).param("currencyId", USD).param("sign", "suma").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get(RUTA).param("userId", ana.toString()).param("sign", "RESTA").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].points").value(-20));
    mvc.perform(get(RUTA).param("currencyId", UUID.randomUUID().toString()).with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            get(RUTA)
                .param("from", "2000-01-01T00:00:00Z")
                .param("to", "2100-01-01T00:00:00Z")
                .with(lector()))
        .andExpect(jsonPath("$.totalElements").value(4));
    mvc.perform(get(RUTA).param("to", "2000-01-01T00:00:00Z").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-677 — la búsqueda va por comprobante, motivo, referencia, nombre, usuario y correo,"
          + " sin acentos ni mayúsculas")
  void busqueda() throws Exception {
    mvc.perform(get(RUTA).param("q", "bancolombia").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "cons-0").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "ana maria").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get(RUTA).param("q", "JOSE").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "ajl-jose@").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "ajp-").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get(RUTA).param("q", "pts-").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "%").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-678 y CA-MV-679 — se ordena por fecha, puntos o comprobante; lo inválido es 400,"
          + " todo junto")
  void ordenYValidaciones() throws Exception {
    mvc.perform(get(RUTA).param("sort", "points,asc").with(lector()))
        .andExpect(jsonPath("$.content[0].points").value(-20))
        .andExpect(jsonPath("$.content[3].points").value(200));
    mvc.perform(get(RUTA).param("sort", "points").with(lector()))
        .andExpect(jsonPath("$.content[0].points").value(200));
    mvc.perform(get(RUTA).param("sort", "occurredAt,asc").with(lector()))
        .andExpect(jsonPath("$.content[0].points").value(100));
    mvc.perform(get(RUTA).param("sort", "code,desc").with(lector())).andExpect(status().isOk());

    mvc.perform(
            get(RUTA)
                .param("sort", "concept")
                .param("sign", "OTRO")
                .param("type", "BONO")
                .param("status", "ANULADA")
                .param("from", "2026-12-01T00:00:00Z")
                .param("to", "2026-01-01T00:00:00Z")
                .with(lector()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(5));
    mvc.perform(get(RUTA).param("sort", "points,arriba").with(lector()))
        .andExpect(status().isBadRequest());
    mvc.perform(get(RUTA).param("size", "100000").with(lector()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-MV-681 — el detalle de una compra trae tasa y pagos; el de un ajuste, quién lo hizo y el"
          + " comprobante; lo que no es de puntos o no existe, 404")
  void detalle() throws Exception {
    mvc.perform(get(RUTA + "/{id}", compra).with(detalleDe()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.movement.type").value("COMPRA_PUNTOS"))
        .andExpect(jsonPath("$.pointsRate.pointsPerUnit").value(100.0))
        .andExpect(jsonPath("$.payments.length()").value(1))
        .andExpect(jsonPath("$.receipt").value(Matchers.nullValue()));

    adjuntar(ajusteDeAna, "consignacion.png", PNG);
    mvc.perform(get(RUTA + "/{id}", ajusteDeAna).with(detalleDe()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.movement.adjustedBy.id").value(administrador.toString()))
        .andExpect(jsonPath("$.movement.hasReceipt").value(true))
        .andExpect(jsonPath("$.pointsRate").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.payments.length()").value(0))
        .andExpect(jsonPath("$.receipt.fileName").value("consignacion.png"))
        .andExpect(jsonPath("$.receipt.contentType").value("image/png"))
        .andExpect(jsonPath("$.receipt.sizeBytes").value(PNG.length))
        .andExpect(jsonPath("$.receipt.sha256").value(Matchers.hasLength(64)));

    UUID bono =
        jdbc.queryForObject(
            "SELECT m.id FROM movements m JOIN movement_types t ON t.id = m.movement_type_id"
                + " WHERE m.user_id = ? AND t.code NOT IN ('COMPRA_PUNTOS', 'AJUSTE_PUNTOS')"
                + " LIMIT 1",
            UUID.class,
            ana);
    mvc.perform(get(RUTA + "/{id}", bono).with(detalleDe())).andExpect(status().isNotFound());
    mvc.perform(get(RUTA + "/{id}", UUID.randomUUID()).with(detalleDe()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-MV-682 — la descarga es el archivo exacto, como adjunto; sin comprobante o sobre una"
          + " compra, 404")
  void descarga() throws Exception {
    mvc.perform(get(RUTA + "/{id}/receipt", ajusteDeAna).with(descargador()))
        .andExpect(status().isNotFound());
    adjuntar(ajusteDeAna, "consignacion.png", PNG);
    mvc.perform(get(RUTA + "/{id}/receipt", ajusteDeAna).with(descargador()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(PNG))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment;")))
        .andExpect(
            header().string("Content-Disposition", Matchers.containsString("consignacion.png")));
    mvc.perform(get(RUTA + "/{id}/receipt", compra).with(descargador()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-MV-684 — cada consulta exige su permiso; sin sesión, 401; la ruta de RF-MV-053 ya no"
          + " lista")
  void permisos() throws Exception {
    RequestPostProcessor otro =
        user(administrador.toString()).authorities(() -> "movements:adjust-points");
    mvc.perform(get(RUTA).with(otro)).andExpect(status().isForbidden());
    mvc.perform(get(RUTA + "/{id}", compra).with(otro)).andExpect(status().isForbidden());
    mvc.perform(get(RUTA + "/{id}/receipt", ajusteDeAna).with(otro))
        .andExpect(status().isForbidden());
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    mvc.perform(get(AJUSTES).with(lector())).andExpect(status().isMethodNotAllowed());
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-054`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-656 y CA-MV-657 — los saldos de la persona indicada, no los de quien pregunta; sin"
          + " cuentas, lista vacía")
  void saldosDeUnaPersona() throws Exception {
    mvc.perform(get("/api/v1/movements/users/{id}/balances", ana).with(saldos()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].currency.code").value("USD"))
        .andExpect(jsonPath("$[0].points").value(80))
        .andExpect(jsonPath("$[0].wallet").value(10))
        .andExpect(jsonPath("$[0].held").value(0));
    mvc.perform(get("/api/v1/movements/users/{id}/balances", administrador).with(saldos()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-658 y CA-MV-659 — inexistente o eliminada, 404; sin permiso, 403; sin sesión, 401")
  void saldosBordes() throws Exception {
    mvc.perform(get("/api/v1/movements/users/{id}/balances", UUID.randomUUID()).with(saldos()))
        .andExpect(status().isNotFound());
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", jose);
    mvc.perform(get("/api/v1/movements/users/{id}/balances", jose).with(saldos()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/movements/users/{id}/balances", ana)
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:read-own-balances")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/users/{id}/balances", ana))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------

  private UUID ajustar(UUID quien, String puntos, String motivo, String referencia, String clave)
      throws Exception {
    MockHttpServletRequestBuilder p =
        post(AJUSTES)
            .header("Idempotency-Key", clave)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"userId\":\"%s\",\"currencyId\":\"%s\",\"points\":%s,\"concept\":\"%s\"%s}"
                    .formatted(
                        quien,
                        USD,
                        puntos,
                        motivo,
                        referencia == null ? "" : ",\"reference\":\"" + referencia + "\""))
            .with(user(administrador.toString()).authorities(() -> "movements:adjust-points"));
    String cuerpo =
        mvc.perform(p)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Instantes distintos y en orden, sin depender del reloj.
    jdbc.update(
        "UPDATE movements SET occurred_at = occurred_at - make_interval(mins => ?)"
            + " WHERE idempotency_key = ?",
        10 - Integer.parseInt(clave.substring(clave.length() - 2)),
        clave);
    return UUID.fromString(JsonPath.read(cuerpo, "$.id"));
  }

  private UUID comprar(UUID quien, String importe, String clave) throws Exception {
    String cuerpo =
        mvc.perform(
                post("/api/v1/movements/mine/points-purchases")
                    .header("Idempotency-Key", clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"currencyId\":\"%s\",\"amount\":%s,\"paymentMethodId\":\"%s\"}"
                            .formatted(USD, importe, TARJETA))
                    .with(user(quien.toString()).authorities(() -> "movements:buy-points")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(cuerpo, "$.id"));
  }

  private void adjuntar(UUID ajuste, String nombre, byte[] bytes) throws Exception {
    mvc.perform(
            multipart(HttpMethod.PUT, AJUSTES + "/{id}/receipt", ajuste)
                .file(new MockMultipartFile("file", nombre, "application/octet-stream", bytes))
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:attach-points-receipt")))
        .andExpect(status().isOk());
  }

  private RequestPostProcessor lector() {
    return user(administrador.toString()).authorities(() -> "movements:list-points-movements");
  }

  private RequestPostProcessor detalleDe() {
    return user(administrador.toString()).authorities(() -> "movements:read-points-movement");
  }

  private RequestPostProcessor descargador() {
    return user(administrador.toString()).authorities(() -> "movements:download-points-receipt");
  }

  private RequestPostProcessor saldos() {
    return user(administrador.toString()).authorities(() -> "movements:read-user-balances");
  }

  private void limpiar() {
    PointsFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'ajl-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ajl-%'");
  }

  private UUID persona(String username, String nombres, String apellidos) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, ?, ?, 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co",
        nombres,
        apellidos);
    darElSuelo(jdbc, id);
    return id;
  }
}
