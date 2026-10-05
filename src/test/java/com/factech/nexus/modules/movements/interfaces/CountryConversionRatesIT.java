package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PointsFixtures;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** `RF-MV-046` — fijar la conversión de un país; y `RF-MV-047` — consultar las vigentes. */
@AutoConfigureMockMvc
class CountryConversionRatesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private UUID administrador;
  private UUID cliente;
  private UUID pais;
  private UUID otroPais;
  private UUID paisInactivo;
  private UUID local;
  private UUID otraLocal;
  private UUID localInactiva;

  @BeforeEach
  void sembrar() {
    limpiar();
    administrador = persona("ccr-admin");
    cliente = persona("ccr-cliente");
    pais = pais("ZCA", true);
    otroPais = pais("ZCB", true);
    paisInactivo = pais("ZCI", false);
    local = PointsFixtures.moneda(jdbc, "ZZL", true);
    otraLocal = PointsFixtures.moneda(jdbc, "ZZM", true);
    localInactiva = PointsFixtures.moneda(jdbc, "ZZX", false);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-046`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-548 y CA-MV-549 — fijar deja la conversión vigente con la base del sistema; otra la"
          + " sustituye y la anterior sigue escrita sin cambios")
  void fijaYSustituye() throws Exception {
    mvc.perform(fijar(pais, local, "4150", "3950"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.country.code").value("ZCA"))
        .andExpect(jsonPath("$.currency.code").value("ZZL"))
        // CA-MV-548: la base es la moneda por omisión, copiada.
        .andExpect(jsonPath("$.baseCurrency.code").value("USD"))
        .andExpect(jsonPath("$.payInPrice").value(4150.0))
        .andExpect(jsonPath("$.payoutPrice").value(3950.0))
        .andExpect(jsonPath("$.validFrom").exists());
    assertThat(
            jdbc.queryForObject(
                "SELECT CAST(base_currency_id AS text) FROM country_conversion_rates"
                    + " WHERE country_id = ?",
                String.class,
                pais))
        .isEqualTo(USD);

    mvc.perform(fijar(pais, local, "4180.5", "3960")).andExpect(status().isCreated());

    // CA-MV-549: dos filas, y la primera intacta.
    List<Map<String, Object>> filas = filas(pais);
    assertThat(filas).hasSize(2);
    assertThat((BigDecimal) filas.get(0).get("pay_in_price")).isEqualByComparingTo("4150");
    assertThat((BigDecimal) filas.get(0).get("payout_price")).isEqualByComparingTo("3950");
    mvc.perform(consultar(cliente).param("countryId", pais.toString()))
        .andExpect(jsonPath("$[0].payInPrice").value(4180.5))
        .andExpect(jsonPath("$[0].payoutPrice").value(3960.0));
  }

  @Test
  @DisplayName(
      "CA-MV-550 — fijar la que rige responde 200 sin escribir; cambiar solo un precio sí escribe")
  void laMismaNoEscribe() throws Exception {
    mvc.perform(fijar(pais, local, "4150", "3950")).andExpect(status().isCreated());
    int auditoriasAntes = auditorias();

    mvc.perform(fijar(pais, local, "4150.0000", "3950.00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payInPrice").value(4150.0));
    assertThat(filas(pais)).hasSize(1);
    assertThat(auditorias()).isEqualTo(auditoriasAntes);

    mvc.perform(fijar(pais, local, "4150", "3900")).andExpect(status().isCreated());
    assertThat(filas(pais)).hasSize(2);
  }

  @Test
  @DisplayName(
      "CA-MV-551 — precios cero, negativos, con cinco decimales o ausentes: 400, los errores"
          + " juntos, y nada cambia")
  void preciosMalos() throws Exception {
    for (String malo : new String[] {"0", "-1", "1.23456", "12345678901"}) {
      mvc.perform(fijar(pais, local, malo, "3950"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("payInPrice"));
      mvc.perform(fijar(pais, local, "4150", malo))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("payoutPrice"));
    }
    mvc.perform(
            post("/api/v1/movements/conversion-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(fijador(administrador)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(4));
    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-552 y CA-MV-553 — país o moneda inexistentes: 422; inactivos o moneda igual a la"
          + " base: 409. Nada cambia")
  void paisesYMonedas() throws Exception {
    mvc.perform(fijar(UUID.randomUUID(), local, "1", "1"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].field").value("countryId"));
    mvc.perform(fijar(pais, UUID.randomUUID(), "1", "1"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].field").value("currencyId"));
    mvc.perform(fijar(paisInactivo, local, "1", "1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(jsonPath("$.errors[0].field").value("countryId"));
    mvc.perform(fijar(pais, localInactiva, "1", "1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(jsonPath("$.errors[0].field").value("currencyId"));
    // CA-MV-553
    mvc.perform(fijar(pais, UUID.fromString(USD), "1", "1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-554 a CA-MV-556 — permiso, auditoría con quién la fijó y lo anterior, y los otros"
          + " países intactos")
  void permisoAuditoriaYAislamiento() throws Exception {
    mvc.perform(
            post("/api/v1/movements/conversion-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(pais, local, "1", "1"))
                .with(user(administrador.toString()).authorities(() -> "movements:read")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/movements/conversion-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(pais, local, "1", "1")))
        .andExpect(status().isUnauthorized());

    mvc.perform(fijar(otroPais, otraLocal, "20", "19")).andExpect(status().isCreated());
    mvc.perform(fijar(pais, local, "4100", "3900")).andExpect(status().isCreated());
    mvc.perform(fijar(pais, local, "4200", "3950")).andExpect(status().isCreated());

    // CA-MV-555: la segunda del país recuerda la primera, con quién la fijó.
    String cambios =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log"
                + " WHERE entity = 'country_conversion_rates' AND action = 'CREATE'"
                + " AND actor_id = ? ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            administrador);
    assertThat(cambios).contains("4100.0000").contains("4200.0000").contains("3950.0000");

    // CA-MV-556
    List<Map<String, Object>> delOtro = filas(otroPais);
    assertThat(delOtro).hasSize(1);
    assertThat((BigDecimal) delOtro.get(0).get("pay_in_price")).isEqualByComparingTo("20");
  }

  @Test
  @DisplayName("el esquema de V67: un precio de cero o la moneda local igual a la base se rechazan")
  void losCheckDeV67() {
    assertThat(catchThrowable(() -> sembrada(local, "0", "1")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(catchThrowable(() -> sembrada(local, "1", "0")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(catchThrowable(() -> sembrada(UUID.fromString(USD), "1", "1")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(catchThrowable(() -> sembrada(local, "1", "1"))).isNull();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-047`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-557 a CA-MV-559 — una fila por país activo con conversión, solo la última, por"
          + " código; vacía sin ninguna")
  void vigentes() throws Exception {
    mvc.perform(consultar(cliente))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.country.code =~ /ZC./)]").isEmpty());

    sembrar(otroPais, otraLocal, "20", "19", "2026-01-01T00:00:00Z");
    sembrar(pais, local, "4000", "3800", "2026-01-01T00:00:00Z");
    sembrar(pais, local, "4150", "3950", "2026-02-01T00:00:00Z");
    // Inactivo con conversión: no sale.
    sembrar(paisInactivo, local, "1", "1", "2026-01-01T00:00:00Z");

    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    mvc.perform(consultar(cliente))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.country.code =~ /ZC./)].country.code")
                .value(org.hamcrest.Matchers.contains("ZCA", "ZCB")))
        .andExpect(
            jsonPath("$[?(@.country.code == 'ZCA')].payInPrice")
                .value(org.hamcrest.Matchers.contains(4150.0)))
        .andExpect(
            jsonPath("$[?(@.country.code == 'ZCA')].payoutPrice")
                .value(org.hamcrest.Matchers.contains(3950.0)))
        .andExpect(
            jsonPath("$[?(@.country.code == 'ZCA')].currency.code")
                .value(org.hamcrest.Matchers.contains("ZZL")))
        .andExpect(
            jsonPath("$[?(@.country.code == 'ZCA')].baseCurrency.code")
                .value(org.hamcrest.Matchers.contains("USD")));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-MV-560 — pedido un país, solo el suyo; uno sin conversión da la lista vacía")
  void unSoloPais() throws Exception {
    sembrar(pais, local, "4150", "3950", "2026-01-01T00:00:00Z");
    sembrar(otroPais, otraLocal, "20", "19", "2026-01-01T00:00:00Z");
    UUID sinConversion = pais("ZCN", true);

    mvc.perform(consultar(cliente).param("countryId", pais.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].country.code").value("ZCA"));
    mvc.perform(consultar(cliente).param("countryId", sinConversion.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(consultar(cliente).param("countryId", "no-es-un-uuid"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-MV-561 — un cliente la consulta con su permiso; sin él es 403, y sin token es 401")
  void permisoDeConsulta() throws Exception {
    // Lo porta el rol CLIENTE por su tipo (V67), no solo una autoridad de prueba.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id"
                    + " JOIN permissions p ON p.id = rp.permission_id"
                    + " WHERE r.role_type = 'CONSUMIDOR'"
                    + " AND p.code = 'movements:read-conversion-rates'",
                Integer.class))
        .isPositive();
    mvc.perform(consultar(cliente)).andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/movements/conversion-rates")
                .with(user(cliente.toString()).authorities(() -> "movements:set-conversion-rate")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/conversion-rates")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder fijar(
      UUID elPais, UUID moneda, String cobro, String retiro) {
    return post("/api/v1/movements/conversion-rates")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(elPais, moneda, cobro, retiro))
        .with(fijador(administrador));
  }

  private static MockHttpServletRequestBuilder consultar(UUID quien) {
    return get("/api/v1/movements/conversion-rates")
        .with(user(quien.toString()).authorities(() -> "movements:read-conversion-rates"));
  }

  private static String cuerpo(UUID elPais, UUID moneda, String cobro, String retiro) {
    return "{\"countryId\":\"%s\",\"currencyId\":\"%s\",\"payInPrice\":%s,\"payoutPrice\":%s}"
        .formatted(elPais, moneda, cobro, retiro);
  }

  private static RequestPostProcessor fijador(UUID quien) {
    return user(quien.toString()).authorities(() -> "movements:set-conversion-rate");
  }

  private void sembrar(UUID elPais, UUID moneda, String cobro, String retiro, String desde) {
    jdbc.update(
        "INSERT INTO country_conversion_rates (id, country_id, currency_id, base_currency_id,"
            + " pay_in_price, payout_price, valid_from, created_by)"
            + " VALUES (gen_random_uuid(), ?, ?, CAST(? AS uuid), CAST(? AS numeric),"
            + " CAST(? AS numeric), CAST(? AS timestamptz), ?)",
        elPais,
        moneda,
        USD,
        cobro,
        retiro,
        desde,
        administrador);
  }

  private void sembrada(UUID moneda, String cobro, String retiro) {
    sembrar(pais, moneda, cobro, retiro, "2026-0" + (1 + cuantas()) + "-01T00:00:00Z");
  }

  private List<Map<String, Object>> filas(UUID elPais) {
    return jdbc.queryForList(
        "SELECT pay_in_price, payout_price FROM country_conversion_rates WHERE country_id = ?"
            + " ORDER BY valid_from",
        elPais);
  }

  private int cuantas() {
    return jdbc.queryForObject("SELECT count(*) FROM country_conversion_rates", Integer.class);
  }

  private int auditorias() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'country_conversion_rates'",
        Integer.class);
  }

  private UUID pais(String codigo, boolean activo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO countries (id, code, name, is_active) VALUES (?, ?, ?, ?)",
        id,
        codigo,
        "País " + codigo,
        activo);
    return id;
  }

  /** Las conversiones antes que los países y las monedas que referencian. */
  private void limpiar() {
    jdbc.update("DELETE FROM country_conversion_rates");
    PointsFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'ccr-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ccr-%'");
    jdbc.update("DELETE FROM countries WHERE code LIKE 'ZC_'");
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
