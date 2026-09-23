package com.factech.nexus.modules.system.exchangerates.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Criterios de aceptación del alta de tasas de cambio (`RF-SP-047`, `CA-SP-530` a `CA-SP-542`).
 *
 * <p><b>Las monedas se insertan por SQL</b> y no por la API, por lo mismo que en `CurrenciesIT`: no
 * hay endpoint de alta de monedas, y es exactamente lo que `RN-SP-010` decide.
 *
 * <p><b>Las fechas son literales y no relativas a hoy.</b> Una vigencia escrita como «hoy más
 * treinta» convierte el calendario en parte de la prueba, y basta con que corra un 31 de diciembre
 * para que el solapamiento que verifica deje de solaparse.
 */
@AutoConfigureMockMvc
class ExchangeRateIT extends IntegrationTestBase {

  /** La moneda por defecto de la siembra, estable en todos los entornos. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private String cop;
  private String eur;

  /**
   * Deja las dos tablas como las encontró, y no solo como las necesita.
   *
   * <p><b>Limpiar solo al empezar no basta</b>: al terminar quedan tasas y monedas que no son la de
   * casa, y la siguiente clase que haga {@code DELETE FROM currencies WHERE is_default = false}
   * —`CurrenciesIT`— revienta con {@code fk_exchange_rates_target} antes de ejecutar una sola
   * prueba. El fallo sale en otra suite, con un mensaje que no menciona a esta.
   *
   * <p>Es la misma lección que {@code PackageTestSupport} tiene escrita para las monedas de prueba,
   * y la que las suites de equipos siguen desde `CA-SP-683`: se limpia al empezar <b>y</b> al
   * terminar. Lo destapó el CI de `RF-SP-070` el 23-09-2026, cuando las clases nuevas del submódulo
   * de Equipos cambiaron el orden de ejecución en Linux.
   */
  @AfterEach
  void dejarLoQueEncontro() {
    // Las tasas primero: apuntan a las monedas que se borran a continuación.
    jdbc.update("DELETE FROM exchange_rates");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
  }

  @BeforeEach
  void dejarElCatalogoLimpio() {
    // Las tasas primero: apuntan a las monedas que se borran a continuación.
    jdbc.update("DELETE FROM exchange_rates");
    jdbc.update("DELETE FROM audit_change_log WHERE entity = 'exchange_rates'");
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
    cop = insertarMoneda("COP", "Peso colombiano", true);
    eur = insertarMoneda("EUR", "Euro", true);
  }

  @Test
  @DisplayName("CA-SP-530 — la tasa nace con sus dos monedas RESUELTAS, no con dos identificadores")
  void altaConMonedasResueltas() throws Exception {
    mvc.perform(alta(tasa(USD, cop, "4150.00000000", "2026-09-01", "\"2026-12-31\"")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("/api/v1/exchange-rates/")))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.sourceCurrency.id").value(USD))
        .andExpect(jsonPath("$.sourceCurrency.code").value("USD"))
        .andExpect(jsonPath("$.sourceCurrency.name").value("Dólar estadounidense"))
        // El campo del que depende el redondeo de toda conversión.
        .andExpect(jsonPath("$.sourceCurrency.decimalPlaces").value(2))
        .andExpect(jsonPath("$.targetCurrency.code").value("COP"))
        .andExpect(jsonPath("$.targetCurrency.name").value("Peso colombiano"))
        .andExpect(jsonPath("$.validFrom").value("2026-09-01"))
        .andExpect(jsonPath("$.validTo").value("2026-12-31"))
        // Por omisión nace activa: no hay ninguna regla que la obligue a nacer
        // apagada, al revés que el producto de `RF-PM-001`.
        .andExpect(jsonPath("$.isActive").value(true));
  }

  @Test
  @DisplayName(
      "CA-SP-531 — sin fecha de fin la tasa es vitalicia, y el campo llega PRESENTE y nulo")
  void tasaVitalicia() throws Exception {
    String cuerpo =
        mvc.perform(alta(tasa(USD, cop, "4150.00000000", "2026-09-01", null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.validTo").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Presente y nulo, no ausente: un campo que falta es indistinguible de uno
    // que el cliente no conoce. Lo sostiene `JsonInclude.ALWAYS`, que se puede
    // perder en cualquier refactor sin que nada más lo note.
    assertThat(cuerpo).contains("\"validTo\":null");
  }

  @Test
  @DisplayName("CA-SP-532 — sin fecha de inicio no hay tasa: no se supone «desde hoy»")
  void sinFechaDeInicio() throws Exception {
    mvc.perform(
            alta(
                """
                {"sourceCurrencyId":"%s","targetCurrencyId":"%s","price":4150.00}
                """
                    .formatted(USD, cop)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"))
        .andExpect(jsonPath("$.errors[0].field").value("validFrom"));
  }

  @Test
  @DisplayName("CA-SP-533 — el fin anterior al inicio se rechaza; el MISMO día se admite")
  void vigenciaAlReves() throws Exception {
    mvc.perform(alta(tasa(USD, cop, "4150.00", "2026-09-30", "\"2026-09-01\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"))
        .andExpect(jsonPath("$.errors[0].field").value("validTo"));

    // Y un solo día es una vigencia legítima: una tasa que rige una jornada es
    // exactamente lo que necesita quien liquida a la tasa del día.
    mvc.perform(alta(tasa(USD, cop, "4150.00", "2026-09-01", "\"2026-09-01\"")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.validFrom").value("2026-09-01"))
        .andExpect(jsonPath("$.validTo").value("2026-09-01"));
  }

  @Test
  @DisplayName("CA-SP-534 — un precio de cero o negativo no es una tasa")
  void precioNoPositivo() throws Exception {
    for (String precio : new String[] {"0", "0.00000000", "-1.50"}) {
      mvc.perform(alta(tasa(USD, cop, precio, "2026-09-01", null)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    }
  }

  @Test
  @DisplayName("CA-SP-535 — los ocho decimales se conservan: 0,00024096 no se guarda redondeado")
  void ochoDecimales() throws Exception {
    // COP → USD. Con los cuatro decimales de un importe, esta tasa se guardaría
    // como 0,0002 —un 17% de error— y con dos, como CERO: una tasa no es un
    // importe, y por eso su escala no la fija ninguna de las dos monedas.
    mvc.perform(alta(tasa(cop, USD, "0.00024096", "2026-09-01", null)))
        .andExpect(status().isCreated());

    assertThat(new BigDecimal(precioEnBase())).isEqualByComparingTo("0.00024096");
  }

  @Test
  @DisplayName("CA-SP-536 — una moneda no se cambia por sí misma")
  void monedaConsigoMisma() throws Exception {
    mvc.perform(alta(tasa(USD, USD, "1.00", "2026-09-01", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
  }

  @Test
  @DisplayName("CA-SP-537 — la moneda inexistente y la desactivada se rechazan, y se distinguen")
  void monedaQueNoProcede() throws Exception {
    String inexistente = UUID.randomUUID().toString();
    String apagada = insertarMoneda("JPY", "Yen japonés", false);

    // Las dos son 422 y no 400: la petición está bien formada, y lo que falla
    // es el estado del catálogo. Lo que las separa es el MENSAJE.
    String noExiste =
        mvc.perform(alta(tasa(USD, inexistente, "1.00", "2026-09-01", null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
            .andExpect(jsonPath("$.errors[0].field").value("targetCurrencyId"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String desactivada =
        mvc.perform(alta(tasa(USD, apagada, "1.00", "2026-09-01", null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(noExiste).contains("no existe");
    assertThat(desactivada).contains("desactivada");
  }

  @Test
  @DisplayName("CA-SP-538 — la que se solapa se rechaza con 409, y NO registra nada")
  void solapamiento() throws Exception {
    mvc.perform(alta(tasa(USD, cop, "4150.00", "2026-09-01", "\"2026-09-30\"")))
        .andExpect(status().isCreated());

    // Los DOS extremos cuentan (`RN-SP-032`): la segunda empieza el mismo día en
    // que termina la primera, y eso ya es un solapamiento de un día.
    mvc.perform(alta(tasa(USD, cop, "4200.00", "2026-09-30", null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    // Y el rechazo no deja a medias: el sistema NO cierra ni recorta la anterior,
    // porque resolver el conflicto es decisión de quien administra.
    assertThat(cuantasTasas()).isOne();
    assertThat(precioEnBase()).startsWith("4150");
  }

  @Test
  @DisplayName("CA-SP-539 — dos vigencias que se TOCAN sin solaparse conviven")
  void vigenciasConsecutivas() throws Exception {
    mvc.perform(alta(tasa(USD, cop, "4150.00", "2026-09-01", "\"2026-09-30\"")))
        .andExpect(status().isCreated());

    // Un día después: no hay un solo día cubierto por las dos.
    mvc.perform(alta(tasa(USD, cop, "4200.00", "2026-10-01", null)))
        .andExpect(status().isCreated());

    assertThat(cuantasTasas()).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-SP-540 — lo que la regla acota es el PAR, no el origen")
  void mismoOrigenDosDestinos() throws Exception {
    mvc.perform(alta(tasa(USD, cop, "4150.00", "2026-09-01", null)))
        .andExpect(status().isCreated());

    // USD → EUR el mismo día. Acotar por origen dejaría a un sistema con dos
    // monedas de cambio sin poder declarar la segunda.
    mvc.perform(alta(tasa(USD, eur, "0.92000000", "2026-09-01", null)))
        .andExpect(status().isCreated());

    assertThat(cuantasTasas()).isEqualTo(2);
  }

  @Test
  @DisplayName("dos tasas INACTIVAS del mismo par sí pueden solaparse")
  void lasInactivasNoEstorban() throws Exception {
    mvc.perform(alta(tasaInactiva(USD, cop, "4150.00", "2026-09-01")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isActive").value(false));

    // La regla acota lo VIGENTE, y una tasa apagada no rige. Si la restricción
    // no llevara su `WHERE is_active`, conservar el histórico de un par sería
    // imposible sin borrarlo.
    mvc.perform(alta(tasaInactiva(USD, cop, "4200.00", "2026-09-01")))
        .andExpect(status().isCreated());

    assertThat(cuantasTasas()).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-SP-541 — la creación queda en la auditoría con el estado inicial completo")
  void auditoriaDelAlta() throws Exception {
    mvc.perform(alta(tasa(USD, cop, "4150.00", "2026-09-01", "\"2026-12-31\"")))
        .andExpect(status().isCreated());

    String cambio =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log"
                + " WHERE entity = 'exchange_rates' AND action = 'CREATE'",
            String.class);

    assertThat(cambio)
        .contains(USD)
        .contains(cop)
        .contains("4150")
        .contains("2026-09-01")
        .contains("2026-12-31")
        .contains("is_active");
  }

  @Test
  @DisplayName("CA-SP-542 — sin `exchange-rates:create` no hay alta, y no queda rastro")
  void sinPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/exchange-rates")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "exchange-rates:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(tasa(USD, cop, "4150.00", "2026-09-01", null)))
        .andExpect(status().isForbidden());

    assertThat(cuantasTasas()).isZero();
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  /**
   * El cuerpo del alta.
   *
   * @param validTo el literal JSON —con sus comillas— o {@code null} para omitir el campo, que es
   *     como se declara una tasa vitalicia
   */
  private static String tasa(
      String origen, String destino, String precio, String desde, String validTo) {
    String fin = validTo == null ? "" : ",\"validTo\":%s".formatted(validTo);
    return """
        {"sourceCurrencyId":"%s","targetCurrencyId":"%s","price":%s,"validFrom":"%s"%s}
        """
        .formatted(origen, destino, precio, desde, fin);
  }

  private static String tasaInactiva(String origen, String destino, String precio, String desde) {
    return """
        {"sourceCurrencyId":"%s","targetCurrencyId":"%s","price":%s,"validFrom":"%s",
         "isActive":false}
        """
        .formatted(origen, destino, precio, desde);
  }

  private MockHttpServletRequestBuilder alta(String cuerpo) {
    return post("/api/v1/exchange-rates")
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private RequestPostProcessor administrador() {
    return user(UUID.randomUUID().toString()).authorities(() -> "exchange-rates:create");
  }

  private int cuantasTasas() {
    return jdbc.queryForObject("SELECT count(*) FROM exchange_rates", Integer.class);
  }

  /** El precio tal como lo guardó la base, sin pasar por ningún redondeo de Java. */
  private String precioEnBase() {
    return jdbc.queryForObject(
        "SELECT price::text FROM exchange_rates ORDER BY valid_from, id LIMIT 1", String.class);
  }

  private String insertarMoneda(String code, String name, boolean activa) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)
        VALUES (?, ?, ?, '#', 2, false, ?)
        """,
        id,
        code,
        name,
        activa);
    return id.toString();
  }
}
