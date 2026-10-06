package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.PointsFixtures.TARJETA;
import static com.factech.nexus.modules.movements.interfaces.PointsMovementsIT.PNG;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.movements.PointsFixtures;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-MV-055` — mis movimientos de puntos: mis compras y los ajustes que recibí, su detalle y el
 * comprobante. Hereda los criterios de `RF-MV-031` (retirado el 06-10-2026).
 */
@AutoConfigureMockMvc
class OwnPointsMovementsIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/movements/mine/points-movements";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreditService abonos;
  @Autowired private SessionFactory sessionFactory;

  private UUID yo;
  private UUID otro;
  private UUID administrador;

  @BeforeEach
  void sembrar() {
    limpiar();
    yo = persona("opm-yo", "Ána", "Pérez");
    otro = persona("opm-otro", "Otro", "Cliente");
    administrador = persona("opm-admin", "Laura", "Admin");
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-662, CA-MV-663 y CA-MV-669 — mis compras y mis ajustes, mezclados y los más recientes"
          + " primero; nada ajeno; el ajuste sin quién lo hizo y diciendo si tiene comprobante")
  void lista() throws Exception {
    mvc.perform(get(RUTA).with(mia())).andExpect(jsonPath("$.totalElements").value(0));

    UUID ajuste = ajustar(yo, "30", "Consignación Davivienda", "DAV-77", "opm-ajuste-01", -30);
    UUID pendiente = comprar(yo, "1.00", "opm-compra-01", -20);
    UUID rechazada = comprar(yo, "3.00", "opm-compra-02", -10);
    mvc.perform(rechazar(rechazada, "No entró")).andExpect(status().isOk());
    comprar(otro, "4.00", "opm-compra-03", -5);
    ajustar(otro, "10", "De otro", null, "opm-ajuste-02", -4);
    LedgerFixtures.llenarBilletera(abonos, yo, "5.00");
    adjuntar(ajuste, PNG);

    mvc.perform(get(RUTA).with(mia()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].id").value(rechazada.toString()))
        .andExpect(jsonPath("$.content[0].status").value("RECHAZADA"))
        .andExpect(jsonPath("$.content[0].points").value(300))
        .andExpect(jsonPath("$.content[0].rejectedAt").value(Matchers.notNullValue()))
        .andExpect(jsonPath("$.content[1].id").value(pendiente.toString()))
        .andExpect(jsonPath("$.content[1].amount").value(1))
        .andExpect(jsonPath("$.content[1].confirmedAt").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.content[2].id").value(ajuste.toString()))
        .andExpect(jsonPath("$.content[2].type").value("AJUSTE_PUNTOS"))
        .andExpect(jsonPath("$.content[2].points").value(30))
        .andExpect(jsonPath("$.content[2].concept").value("Consignación Davivienda"))
        .andExpect(jsonPath("$.content[2].reference").value("DAV-77"))
        .andExpect(jsonPath("$.content[2].hasReceipt").value(true))
        .andExpect(jsonPath("$.content[2].adjustedBy").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.content[2].user.id").value(yo.toString()));
  }

  @Test
  @DisplayName(
      "CA-MV-664 a CA-MV-668 — tipo, estado, moneda, periodo, sentido, búsqueda y orden; lo"
          + " inválido es 400, todo junto")
  void filtrosYOrden() throws Exception {
    UUID compra = comprar(yo, "2.00", "opm-compra-11", -20);
    ajustar(yo, "50", "Pago por Nequi", null, "opm-ajuste-11", -15);
    ajustar(yo, "-10", "Corrección", "CORR-1", "opm-ajuste-12", -10);
    mvc.perform(confirmar(compra)).andExpect(status().isOk());

    mvc.perform(get(RUTA).param("type", "COMPRA_PUNTOS").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("type", "AJUSTE_PUNTOS").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get(RUTA).param("status", "confirmada").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get(RUTA).param("currencyId", UUID.randomUUID().toString()).with(mia()))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(RUTA).param("to", "2000-01-01T00:00:00Z").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(RUTA).param("sign", "SUMA").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get(RUTA).param("sign", "RESTA").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].points").value(-10));
    mvc.perform(get(RUTA).param("q", "NEQUI").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "corr-1").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "correccion").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(1));
    // En la lista propia la búsqueda no mira a la persona: sería siempre uno mismo.
    mvc.perform(get(RUTA).param("q", "opm-yo").with(mia()))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(RUTA).param("sort", "points,asc").with(mia()))
        .andExpect(jsonPath("$.content[0].points").value(-10))
        .andExpect(jsonPath("$.content[2].points").value(200));
    mvc.perform(get(RUTA).param("sort", "occurredAt,asc").with(mia()))
        .andExpect(jsonPath("$.content[0].id").value(compra.toString()));

    mvc.perform(
            get(RUTA)
                .param("type", "OTRO")
                .param("status", "ANULADA")
                .param("sign", "X")
                .param("sort", "concept")
                .param("from", "2026-12-01T00:00:00Z")
                .param("to", "2026-01-01T00:00:00Z")
                .with(mia()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(5));
  }

  @Test
  @DisplayName(
      "CA-MV-670 y CA-MV-671 — el detalle de mi compra y de mi ajuste; el ajeno, el que no es de"
          + " puntos y el inexistente responden el mismo 404")
  void detalle() throws Exception {
    UUID compra = comprar(yo, "1.00", "opm-compra-21", -20);
    mvc.perform(rechazar(compra, "Fondos insuficientes")).andExpect(status().isOk());
    UUID ajuste = ajustar(yo, "5", "Bono de bienvenida", null, "opm-ajuste-21", -10);
    adjuntar(ajuste, PNG);

    mvc.perform(get(RUTA + "/{id}", compra).with(detalleMio()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.movement.status").value("RECHAZADA"))
        .andExpect(jsonPath("$.pointsRate.pointsPerUnit").value(100.0))
        .andExpect(jsonPath("$.rejectionReason").value("Fondos insuficientes"))
        .andExpect(jsonPath("$.payments[0].status").value("RECHAZADO"));
    mvc.perform(get(RUTA + "/{id}", ajuste).with(detalleMio()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.movement.adjustedBy").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.receipt.contentType").value("image/png"))
        .andExpect(jsonPath("$.receipt.sizeBytes").value(PNG.length));

    UUID ajeno = ajustar(otro, "5", "Ajeno", null, "opm-ajuste-22", -5);
    LedgerFixtures.llenarBilletera(abonos, yo, "5.00");
    UUID bono =
        jdbc.queryForObject(
            "SELECT m.id FROM movements m JOIN movement_types t ON t.id = m.movement_type_id"
                + " WHERE m.user_id = ? AND t.code NOT IN ('COMPRA_PUNTOS', 'AJUSTE_PUNTOS')"
                + " LIMIT 1",
            UUID.class,
            yo);
    String deAjeno =
        mvc.perform(get(RUTA + "/{id}", ajeno).with(detalleMio()))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String deInexistente =
        mvc.perform(get(RUTA + "/{id}", UUID.randomUUID()).with(detalleMio()))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(get(RUTA + "/{id}", bono).with(detalleMio())).andExpect(status().isNotFound());
    assertThat((String) JsonPath.read(deAjeno, "$.detail"))
        .isEqualTo(JsonPath.read(deInexistente, "$.detail"));
  }

  @Test
  @DisplayName(
      "CA-MV-672 — descargo mi comprobante, exacto y como adjunto; sin comprobante, ajeno o de una"
          + " compra, 404")
  void descarga() throws Exception {
    UUID ajuste = ajustar(yo, "5", "Consignación", null, "opm-ajuste-31", -10);
    UUID ajeno = ajustar(otro, "5", "Ajeno", null, "opm-ajuste-32", -9);
    UUID compra = comprar(yo, "1.00", "opm-compra-31", -8);

    mvc.perform(get(RUTA + "/{id}/receipt", ajuste).with(descargaMia()))
        .andExpect(status().isNotFound());
    adjuntar(ajuste, PNG);
    adjuntar(ajeno, PNG);
    mvc.perform(get(RUTA + "/{id}/receipt", ajuste).with(descargaMia()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(PNG))
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment;")));
    mvc.perform(get(RUTA + "/{id}/receipt", ajeno).with(descargaMia()))
        .andExpect(status().isNotFound());
    mvc.perform(get(RUTA + "/{id}/receipt", compra).with(descargaMia()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-MV-673 — cada consulta exige su permiso; sin sesión, 401; la ruta de RF-MV-031 ya no"
          + " lista; y la página en un número fijo de sentencias")
  void permisosYSentencias() throws Exception {
    UUID compra = comprar(yo, "1.00", "opm-compra-41", -10);
    RequestPostProcessor sinPermiso = user(yo.toString()).authorities(() -> "movements:buy-points");
    mvc.perform(get(RUTA).with(sinPermiso)).andExpect(status().isForbidden());
    mvc.perform(get(RUTA + "/{id}", compra).with(sinPermiso)).andExpect(status().isForbidden());
    mvc.perform(get(RUTA + "/{id}/receipt", compra).with(sinPermiso))
        .andExpect(status().isForbidden());
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    // Ya no lista: la ruta cae en `GET /mine/{id}` (`RF-MV-008`), que rechaza el identificador.
    mvc.perform(get("/api/v1/movements/mine/points-purchases").with(mia()))
        .andExpect(status().isBadRequest());

    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    mvc.perform(get(RUTA).with(mia())).andExpect(status().isOk());
    long conUna = estadisticas.getPrepareStatementCount();
    comprar(yo, "2.00", "opm-compra-42", -9);
    ajustar(yo, "3", "Otro", null, "opm-ajuste-41", -8);
    estadisticas.clear();
    mvc.perform(get(RUTA).with(mia()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(conUna);
  }

  // ---------------------------------------------------------------------------

  /**
   * Ajusta y lo deja {@code minutos} en el pasado (negativo), para ordenar sin depender del reloj.
   */
  private UUID ajustar(
      UUID quien, String puntos, String motivo, String referencia, String clave, int minutos)
      throws Exception {
    String cuerpo =
        mvc.perform(
                post("/api/v1/movements/points-adjustments")
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
                    .with(
                        user(administrador.toString())
                            .authorities(() -> "movements:adjust-points")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(JsonPath.read(cuerpo, "$.id"));
    mover(id, minutos);
    return id;
  }

  private UUID comprar(UUID quien, String importe, String clave, int minutos) throws Exception {
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
    UUID id = UUID.fromString(JsonPath.read(cuerpo, "$.id"));
    mover(id, minutos);
    return id;
  }

  private void mover(UUID movimiento, int minutos) {
    jdbc.update(
        "UPDATE movements SET occurred_at = now() + make_interval(mins => ?) WHERE id = ?",
        minutos,
        movimiento);
  }

  private void adjuntar(UUID ajuste, byte[] bytes) throws Exception {
    mvc.perform(
            multipart(HttpMethod.PUT, "/api/v1/movements/points-adjustments/{id}/receipt", ajuste)
                .file(new MockMultipartFile("file", "soporte.png", "image/png", bytes))
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:attach-points-receipt")))
        .andExpect(status().isOk());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder confirmar(
      UUID compra) {
    return post(
            "/api/v1/movements/payments/{id}/confirmation",
            PaymentFixtures.pagoAConciliar(jdbc, compra))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}")
        .with(user(administrador.toString()).authorities(() -> "movements:confirm-payment"));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rechazar(
      UUID compra, String motivo) {
    return post(
            "/api/v1/movements/payments/{id}/rejection",
            PaymentFixtures.pagoAConciliar(jdbc, compra))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}")
        .with(user(administrador.toString()).authorities(() -> "movements:reject-payment"));
  }

  private RequestPostProcessor mia() {
    return user(yo.toString()).authorities(() -> "movements:list-own-points-movements");
  }

  private RequestPostProcessor detalleMio() {
    return user(yo.toString()).authorities(() -> "movements:read-own-points-movement");
  }

  private RequestPostProcessor descargaMia() {
    return user(yo.toString()).authorities(() -> "movements:download-own-points-receipt");
  }

  private void limpiar() {
    PointsFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'opm-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'opm-%'");
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
