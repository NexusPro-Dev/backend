package com.factech.nexus.modules.system.brokers.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.brokers.domain.service.BrokerNotificationClassifier;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
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

/**
 * Los avisos de los brokers (`RF-SP-078`), por la única dirección, la común.
 *
 * <p>El secreto común lo fija {@link IntegrationTestBase}. Ninguna petición lleva sesión: la ruta
 * es pública. Los criterios de la ruta por nombre, retirada el 13-10-2026, se retiraron con ella.
 */
@AutoConfigureMockMvc
@SuppressWarnings("unchecked") // JsonPath.read devuelve lo que se le pida
class BrokerNotificationsIT extends IntegrationTestBase {

  private static final UUID IQOPTION = UUID.fromString("01a081f0-6000-7101-9c4f-5e7adb000001");
  private static final UUID EXNOVA = UUID.fromString("01a081f0-6000-7102-9c4f-5e7adb000002");
  private static final UUID EXOPTION = UUID.fromString("01a081f0-6000-7103-9c4f-5e7adb000003");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BrokerNotificationClassifier clasificador;

  private UUID cliente;

  @BeforeEach
  void limpiarAntes() {
    limpiar();
    // Otras suites vacían `brokers` y lo reponen sin el `advertiser` de `V93`.
    jdbc.update(
        "INSERT INTO brokers (id, name) VALUES (?, 'IQOPTION'), (?, 'EXNOVA'), (?, 'EXOPTION')"
            + " ON CONFLICT DO NOTHING",
        IQOPTION,
        EXNOVA,
        EXOPTION);
    jdbc.update("UPDATE brokers SET advertiser = 'iq_option' WHERE id = ?", IQOPTION);
  }

  /** También al terminar, y reponiendo el broker que alguna prueba desactiva. */
  @AfterEach
  void limpiarDespues() {
    // Las cuentas que crea el aviso de registro, ANTES que la VENDEDOR a la que apuntan.
    jdbc.update(
        "DELETE FROM user_brokers WHERE external_id LIKE 'BN-R-%' OR external_id = '12345'");
    jdbc.update(
        "UPDATE brokers SET is_active = true WHERE id IN (?, ?, ?)", IQOPTION, EXNOVA, EXOPTION);
    // `CA-SP-1013` le carga un `advertiser` a EXNOVA, como lo haría una migración.
    jdbc.update("UPDATE brokers SET advertiser = NULL WHERE id IN (?, ?)", EXNOVA, EXOPTION);
    if (cliente != null) {
      jdbc.update("DELETE FROM user_brokers WHERE user_id = ?", cliente);
      jdbc.update("DELETE FROM users WHERE id = ?", cliente);
      cliente = null;
    }
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM broker_notifications");
    jdbc.update("DELETE FROM request_log WHERE path LIKE '/api/v1/brokers/%notifications'");
  }

  private static final String COMUN = "/api/v1/brokers/notifications";

  /** Un aviso de IQ Option a la dirección común, con el secreto, y esos datos. */
  private static String deIq(String datos) {
    return COMUN + "?advertiser=iq_option&" + datos + "&token=secreto-comun";
  }

  private int guardados() {
    return jdbc.queryForObject("SELECT count(*) FROM broker_notifications", Integer.class);
  }

  private Map<String, Object> elUnico() {
    return jdbc.queryForMap(
        "SELECT broker_id, advertiser, method, CAST(query_params AS text) AS query_params,"
            + " CAST(headers AS text) AS headers, body, content_type, ip_address, received_at"
            + " FROM broker_notifications");
  }

  @Test
  @DisplayName(
      "CA-SP-897 — un aviso con los datos en la dirección se acepta sin sesión y se guarda entero,"
          + " también los parámetros repetidos; la respuesta va vacía")
  void conLosDatosEnLaDireccion() throws Exception {
    mvc.perform(
            get(deIq("event=ftd&trader_id=12345&sub=a&sub=b"))
                .header("User-Agent", "afiliados-iq/1.0")
                .header("X-Forwarded-For", "203.0.113.7"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));

    Map<String, Object> fila = elUnico();
    assertThat(fila.get("broker_id")).isEqualTo(IQOPTION);
    assertThat(fila.get("method")).isEqualTo("GET");
    String consulta = (String) fila.get("query_params");
    assertThat((List<String>) JsonPath.read(consulta, "$.event")).containsExactly("ftd");
    assertThat((List<String>) JsonPath.read(consulta, "$.trader_id")).containsExactly("12345");
    assertThat((List<String>) JsonPath.read(consulta, "$.sub")).containsExactly("a", "b");
    String cabeceras = (String) fila.get("headers");
    assertThat((List<String>) JsonPath.read(cabeceras, "$.user-agent"))
        .containsExactly("afiliados-iq/1.0");
    assertThat(fila.get("body")).isNull();
    assertThat(fila.get("ip_address")).isNotNull();
    assertThat(fila.get("received_at")).isNotNull();
  }

  @Test
  @DisplayName("CA-SP-898 — un aviso con los datos en el cuerpo guarda el cuerpo exacto y su tipo")
  void conLosDatosEnElCuerpo() throws Exception {
    String cuerpo = "{ \"advertiser\" : \"iq_option\",\n  \"trader_id\": 987 }";
    mvc.perform(
            post(COMUN + "?token=secreto-comun")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isOk());

    Map<String, Object> fila = elUnico();
    assertThat(fila.get("broker_id")).isEqualTo(IQOPTION);
    assertThat(fila.get("method")).isEqualTo("POST");
    assertThat(fila.get("body")).isEqualTo(cuerpo);
    assertThat((String) fila.get("content_type")).startsWith("application/json");
    assertThat(fila.get("query_params")).isEqualTo("{}");
  }

  @Test
  @DisplayName(
      "CA-SP-899 — un formulario se guarda tal cual, sin mezclar sus campos con los de la"
          + " dirección")
  void unFormulario() throws Exception {
    mvc.perform(
            post(COMUN + "?origen=panel&token=secreto-comun")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("event=ftd&advertiser=iq_option&trader_id=555"))
        .andExpect(status().isOk());

    Map<String, Object> fila = elUnico();
    assertThat(fila.get("broker_id")).isEqualTo(IQOPTION);
    assertThat(fila.get("body")).isEqualTo("event=ftd&advertiser=iq_option&trader_id=555");
    String consulta = (String) fila.get("query_params");
    assertThat((Map<String, Object>) JsonPath.read(consulta, "$")).containsOnlyKeys("origen");
  }

  @Test
  @DisplayName(
      "CA-SP-900 — sin secreto, con uno equivocado o con dos, el aviso no se autentica y no se"
          + " guarda")
  void sinSecretoOConOtro() throws Exception {
    mvc.perform(get(COMUN + "?advertiser=iq_option&event=ftd"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(COMUN + "?advertiser=iq_option&event=ftd&token=otro"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(COMUN + "?token=secreto-comun&token=secreto-comun"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(COMUN).contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnauthorized());

    assertThat(guardados()).isZero();
  }

  @Test
  @DisplayName("CA-SP-904 — un cuerpo de más de 64 KiB se rechaza y no se guarda")
  void cuerpoDemasiadoGrande() throws Exception {
    byte[] grande = new byte[64 * 1024 + 1];
    java.util.Arrays.fill(grande, (byte) 'a');
    mvc.perform(
            post(COMUN + "?advertiser=iq_option&token=secreto-comun")
                .contentType(MediaType.TEXT_PLAIN)
                .content(grande))
        .andExpect(status().isBadRequest());

    byte[] justo = new byte[64 * 1024];
    java.util.Arrays.fill(justo, (byte) 'b');
    mvc.perform(
            post(COMUN + "?advertiser=iq_option&token=secreto-comun")
                .contentType(MediaType.TEXT_PLAIN)
                .content(justo))
        .andExpect(status().isOk());

    assertThat(guardados()).isOne();
    assertThat(((String) elUnico().get("body")).length()).isEqualTo(64 * 1024);
  }

  @Test
  @DisplayName("CA-SP-905 — el mismo aviso dos veces se guarda dos veces")
  void elMismoAvisoDosVeces() throws Exception {
    for (int vez = 0; vez < 2; vez++) {
      mvc.perform(get(deIq("event=ftd&trader_id=1"))).andExpect(status().isOk());
    }

    assertThat(guardados()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-SP-906 — el secreto no queda escrito: ni en el aviso, ni en el registro de peticiones,"
          + " ni las cabeceras de credenciales")
  void elSecretoNoQuedaEscrito() throws Exception {
    mvc.perform(
            get(COMUN + "?event=ftd&token=secreto-comun&advertiser=iq_option")
                .header("Authorization", "Basic c2VjcmV0bw==")
                .header("Cookie", "sesion=abc")
                .header("Proxy-Authorization", "Basic eHl6"))
        .andExpect(status().isOk());

    Map<String, Object> fila = elUnico();
    String consulta = (String) fila.get("query_params");
    String cabeceras = (String) fila.get("headers");
    assertThat(consulta).doesNotContain("token").doesNotContain("secreto-comun");
    assertThat((Map<String, Object>) JsonPath.read(cabeceras, "$"))
        .doesNotContainKeys("authorization", "cookie", "proxy-authorization");
    assertThat(cabeceras).doesNotContain("c2VjcmV0bw==").doesNotContain("sesion=abc");

    List<String> registradas =
        jdbc.queryForList(
            "SELECT query_string FROM request_log WHERE path = ?", String.class, COMUN);
    assertThat(registradas).containsExactly("event=ftd&token=[OCULTO]&advertiser=iq_option");
  }

  @Test
  @DisplayName(
      "CA-SP-907 — recibir no cambia nada más: la cuenta sigue en REGISTER, sin país, y su titular"
          + " en FTD_PENDIENTE")
  void recibirNoCambiaNadaMas() throws Exception {
    cliente = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, 'bn-cliente', 'bn-cliente@factech.co', 'Cliente', 'Apellido', 'x', false,
                'FTD_PENDIENTE', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        cliente);
    jdbc.update(
        "INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind)"
            + " VALUES (gen_random_uuid(), ?, ?, 'BN-778899', 'CONSUMIDOR')",
        cliente,
        IQOPTION);

    mvc.perform(get(deIq("event=ftd&trader_id=BN-778899&country_code=CO")))
        .andExpect(status().isOk());

    Map<String, Object> cuenta =
        jdbc.queryForMap(
            "SELECT status, country_code FROM user_brokers WHERE user_id = ?", cliente);
    assertThat(cuenta.get("status")).isEqualTo("REGISTER");
    // Un evento que no se interpreta no deja nada en la cuenta (`RN-SP-077`).
    assertThat(cuenta.get("country_code")).isNull();
    assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, cliente))
        .isEqualTo("FTD_PENDIENTE");
    assertThat(guardados()).isOne();
    assertThat((String) elUnico().get("query_params")).contains("BN-778899");
  }

  // ---------------------------------------------------------------------------
  // La dirección común (`RN-SP-069`, 09-10-2026)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-SP-946, CA-SP-1012 — advertiser=iq_option se guarda a nombre de IQOPTION, tal como"
          + " llegó, y con su advertiser en su columna")
  void laDireccionComun() throws Exception {
    // La forma de un aviso real de IQ Option del 09-10-2026.
    mvc.perform(
            get(
                COMUN
                    + "?clickid=&afftrack=DIEGOIQ&trader_id=196581821&advertiser=iq_option"
                    + "&postback_name=PRUEBA&token=secreto-comun"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));

    Map<String, Object> fila = elUnico();
    assertThat(fila.get("broker_id")).isEqualTo(IQOPTION);
    assertThat(fila.get("advertiser")).isEqualTo("iq_option");
    String consulta = (String) fila.get("query_params");
    assertThat((List<String>) JsonPath.read(consulta, "$.advertiser")).containsExactly("iq_option");
    assertThat((List<String>) JsonPath.read(consulta, "$.trader_id")).containsExactly("196581821");
    assertThat(consulta).doesNotContain("secreto-comun");
  }

  @Test
  @DisplayName(
      "CA-SP-947, CA-SP-1012 — advertiser sin distinguir mayúsculas, y también en un formulario o"
          + " en un JSON; se guarda recortado y tal como vino")
  void elAdvertiserEnCualquierParte() throws Exception {
    mvc.perform(get(COMUN + "?advertiser=IQ_Option&token=secreto-comun"))
        .andExpect(status().isOk());
    mvc.perform(
            post(COMUN + "?token=secreto-comun")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("event=ftd&advertiser=iq_option"))
        .andExpect(status().isOk());
    String json = "{\"advertiser\": \"  Iq_Option \", \"event\": \"ftd\"}";
    mvc.perform(
            post(COMUN + "?token=secreto-comun")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isOk());

    assertThat(jdbc.queryForList("SELECT DISTINCT broker_id FROM broker_notifications", UUID.class))
        .containsExactly(IQOPTION);
    assertThat(jdbc.queryForList("SELECT advertiser FROM broker_notifications", String.class))
        .containsExactlyInAnyOrder("IQ_Option", "iq_option", "Iq_Option");
    // El cuerpo se guarda tal cual: leer el advertiser no lo reescribe.
    assertThat(
            jdbc.queryForList(
                "SELECT body FROM broker_notifications WHERE body IS NOT NULL ORDER BY body",
                String.class))
        .containsExactlyInAnyOrder("event=ftd&advertiser=iq_option", json);
  }

  @Test
  @DisplayName(
      "CA-SP-949, CA-SP-950 — sin el secreto común, o con otro, 401 aunque el advertiser sea"
          + " válido; y un advertiser desconocido sin secreto también es 401")
  void sinElSecretoComun() throws Exception {
    mvc.perform(get(COMUN + "?advertiser=iq_option")).andExpect(status().isUnauthorized());
    mvc.perform(get(COMUN + "?advertiser=iq_option&token=secreto-iq"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(COMUN + "?advertiser=no-existe&token=otro"))
        .andExpect(status().isUnauthorized());

    assertThat(guardados()).isZero();
  }

  // ---------------------------------------------------------------------------
  // Ningún aviso se pierde (`RN-SP-076`, 13-10-2026)
  // ---------------------------------------------------------------------------

  private List<Map<String, Object>> sinBroker() {
    return jdbc.queryForList(
        "SELECT advertiser, CAST(query_params AS text) AS query_params, body"
            + " FROM broker_notifications WHERE broker_id IS NULL ORDER BY received_at, id");
  }

  @Test
  @DisplayName(
      "CA-SP-1009 — un advertiser que no está en el catálogo se recibe y se guarda sin broker, con"
          + " ese advertiser, y no se interpreta")
  void unAdvertiserDesconocido() throws Exception {
    mvc.perform(
            get(
                COMUN
                    + "?advertiser=exnova_aff&postback_name=registro-prueba&trader_id=BN-R-20"
                    + "&token=secreto-comun"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));

    List<Map<String, Object>> filas = sinBroker();
    assertThat(filas).hasSize(1);
    assertThat(filas.get(0).get("advertiser")).isEqualTo("exnova_aff");
    assertThat((String) filas.get(0).get("query_params")).contains("BN-R-20");
    // Sin broker no se sabe de qué cuenta habla: el registro no crea ninguna.
    assertThat(cuentasDelBroker()).isZero();
  }

  @Test
  @DisplayName(
      "CA-SP-1010 — sin advertiser, con dos o con uno de más de 100 caracteres: se guarda sin"
          + " broker y sin advertiser")
  void sinAdvertiserUtil() throws Exception {
    mvc.perform(get(COMUN + "?event=ftd&token=secreto-comun")).andExpect(status().isOk());
    mvc.perform(get(COMUN + "?advertiser=iq_option&advertiser=iq_option&token=secreto-comun"))
        .andExpect(status().isOk());
    mvc.perform(get(COMUN + "?advertiser=" + "a".repeat(101) + "&token=secreto-comun"))
        .andExpect(status().isOk());
    // En un cuerpo que no es formulario ni JSON no se busca.
    mvc.perform(
            post(COMUN + "?token=secreto-comun")
                .contentType(MediaType.TEXT_PLAIN)
                .content("advertiser=iq_option"))
        .andExpect(status().isOk());

    List<Map<String, Object>> filas = sinBroker();
    assertThat(filas).hasSize(4);
    assertThat(filas).allSatisfy(fila -> assertThat(fila.get("advertiser")).isNull());
    assertThat(guardados()).isEqualTo(4);
  }

  @Test
  @DisplayName("CA-SP-1011 — con el advertiser de un broker apagado, se guarda sin broker")
  void elDeUnBrokerApagado() throws Exception {
    jdbc.update("UPDATE brokers SET is_active = false WHERE id = ?", IQOPTION);

    mvc.perform(get(deIq("postback_name=registro-prueba&trader_id=BN-R-21")))
        .andExpect(status().isOk());

    List<Map<String, Object>> filas = sinBroker();
    assertThat(filas).hasSize(1);
    assertThat(filas.get(0).get("advertiser")).isEqualTo("iq_option");
    assertThat(cuentasDelBroker()).isZero();
  }

  @Test
  @DisplayName(
      "CA-SP-1013 — al arrancar, los avisos sin broker de un advertiser ya cargado se le asocian y"
          + " se interpretan en orden: el registro crea la cuenta, el depósito la mueve y su"
          + " reentrega no se aplica dos veces; las operaciones se cuentan y se borran")
  void alArrancarSeAsociaYSeInterpreta() throws Exception {
    String exnova = "?advertiser=exnova_aff&trader_id=BN-R-22&token=secreto-comun";
    mvc.perform(get(COMUN + exnova + "&postback_name=registro-prueba&event_id=x-1"))
        .andExpect(status().isOk());
    mvc.perform(get(COMUN + exnova + "&postback_name=deposito-prueba&event_id=x-2&amount=10"))
        .andExpect(status().isOk());
    mvc.perform(get(COMUN + exnova + "&postback_name=deposito-prueba&event_id=x-2&amount=10"))
        .andExpect(status().isOk());
    mvc.perform(get(COMUN + exnova + "&postback_name=operacion-prueba&event_id=x-3"))
        .andExpect(status().isOk());
    mvc.perform(get(COMUN + exnova + "&postback_name=operacion-prueba&event_id=x-4"))
        .andExpect(status().isOk());
    assertThat(sinBroker()).hasSize(5);
    assertThat(cuentasDelBroker()).isZero();

    // Lo que haría una migración: cargar el `advertiser` de EXNOVA. Y arrancar.
    jdbc.update("UPDATE brokers SET advertiser = 'EXNOVA_AFF' WHERE id = ?", EXNOVA);
    clasificador.run(null);

    assertThat(sinBroker()).isEmpty();
    assertThat(jdbc.queryForList("SELECT DISTINCT broker_id FROM broker_notifications", UUID.class))
        .containsExactly(EXNOVA);
    Map<String, Object> cuenta =
        jdbc.queryForMap(
            "SELECT user_id, kind, status, operations_count, deposits_total FROM user_brokers"
                + " WHERE broker_id = ? AND external_id = 'BN-R-22'",
            EXNOVA);
    assertThat(cuenta.get("user_id")).isNull();
    assertThat(cuenta.get("kind")).isEqualTo("CONSUMIDOR");
    assertThat(cuenta.get("status")).isEqualTo("FIRST_DEPOSIT");
    assertThat(((Number) cuenta.get("deposits_total")).longValue()).isEqualTo(1000L);
    assertThat(cuenta.get("operations_count")).isEqualTo(2);
    // Las dos operaciones contadas se borraron (`RN-SP-074`): quedan el registro y los depósitos.
    assertThat(guardados()).isEqualTo(3);

    // Un segundo arranque no encuentra nada que hacer.
    clasificador.run(null);
    assertThat(
            jdbc.queryForObject(
                "SELECT operations_count FROM user_brokers WHERE external_id = 'BN-R-22'",
                Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-SP-1014 — al arrancar, los de un advertiser que sigue sin conocerse quedan sin broker, y"
          + " un aviso que ya tiene broker no se toca")
  void alArrancarLoDesconocidoSigueIgual() throws Exception {
    mvc.perform(get(COMUN + "?advertiser=exoption_aff&event=ftd&token=secreto-comun"))
        .andExpect(status().isOk());
    mvc.perform(get(COMUN + "?advertiser=exnova_aff&event=ftd&token=secreto-comun"))
        .andExpect(status().isOk());
    mvc.perform(get(deIq("event=ftd"))).andExpect(status().isOk());
    // Un aviso con broker cuyo advertiser, por lo que sea, es el de otro.
    jdbc.update(
        "UPDATE broker_notifications SET advertiser = 'exnova_aff'"
            + " WHERE broker_id = ? AND advertiser = 'iq_option'",
        IQOPTION);

    jdbc.update("UPDATE brokers SET advertiser = 'exnova_aff' WHERE id = ?", EXNOVA);
    clasificador.run(null);

    List<Map<String, Object>> filas = sinBroker();
    assertThat(filas).hasSize(1);
    assertThat(filas.get(0).get("advertiser")).isEqualTo("exoption_aff");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM broker_notifications WHERE broker_id = ?",
                Integer.class,
                IQOPTION))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM broker_notifications WHERE broker_id = ?",
                Integer.class,
                EXNOVA))
        .isOne();
  }

  @Test
  @DisplayName(
      "CA-SP-1015 — la ruta con el nombre del broker ya no recibe: con cualquier secreto, no se"
          + " guarda")
  void laRutaPorNombreYaNoRecibe() throws Exception {
    mvc.perform(
            get("/api/v1/brokers/iqoption/notifications?advertiser=iq_option&token=secreto-comun"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/brokers/exnova/notifications?token=secreto-ex")
                .contentType(MediaType.TEXT_PLAIN)
                .content("x"))
        .andExpect(status().isUnauthorized());

    assertThat(guardados()).isZero();
  }

  // ---------------------------------------------------------------------------
  // El aviso de registro (`RN-SP-070`, `RN-SP-072`, 09-10-2026)
  // ---------------------------------------------------------------------------

  private UUID vendedorConAfftrack;

  private UUID cuentaVendedora(String afftrack) {
    cliente = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, 'bn-vendedor', 'bn-vendedor@factech.co', 'Vendedor', 'Apellido', 'x', false,
                'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        cliente);
    vendedorConAfftrack = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind, afftrack)"
            + " VALUES (?, ?, ?, 'BN-V-1', 'VENDEDOR', ?)",
        vendedorConAfftrack,
        cliente,
        IQOPTION,
        afftrack);
    return vendedorConAfftrack;
  }

  private Map<String, Object> laCuenta(String numero) {
    return jdbc.queryForMap(
        "SELECT user_id, kind, status, referrer_account_id FROM user_brokers"
            + " WHERE broker_id = ? AND external_id = ?",
        IQOPTION,
        numero);
  }

  private int cuentasDelBroker() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_brokers WHERE external_id LIKE 'BN-R-%'", Integer.class);
  }

  @Test
  @DisplayName(
      "CA-SP-964 — el aviso de registro crea la cuenta CONSUMIDOR sin titular, con origen en la"
          + " VENDEDOR de su afftrack; el aviso se guarda igual")
  void elAvisoDeRegistroCreaLaCuenta() throws Exception {
    UUID origen = cuentaVendedora("DIEGOIQ");

    mvc.perform(
            get(
                COMUN
                    + "?advertiser=iq_option&postback_name=registro-prueba&trader_id=BN-R-1"
                    + "&afftrack=diegoiq&token=secreto-comun"))
        .andExpect(status().isOk());

    Map<String, Object> cuenta = laCuenta("BN-R-1");
    assertThat(cuenta.get("user_id")).isNull();
    assertThat(cuenta.get("kind")).isEqualTo("CONSUMIDOR");
    assertThat(cuenta.get("status")).isEqualTo("REGISTER");
    assertThat(cuenta.get("referrer_account_id")).isEqualTo(origen);
    assertThat(guardados()).isOne();
  }

  @Test
  @DisplayName(
      "CA-SP-965, CA-SP-947 — con un afftrack desconocido se crea sin origen; los campos también"
          + " valen en un JSON")
  void sinOrigenConocido() throws Exception {
    mvc.perform(
            post(COMUN + "?token=secreto-comun")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"advertiser\":\"iq_option\",\"postback_name\":\"registro-prueba\","
                        + "\"trader_id\":12345,\"afftrack\":\"NADIE\"}"))
        .andExpect(status().isOk());

    Map<String, Object> cuenta = laCuenta("12345");
    assertThat(cuenta.get("user_id")).isNull();
    assertThat(cuenta.get("referrer_account_id")).isNull();
    jdbc.update("DELETE FROM user_brokers WHERE external_id = '12345'");
  }

  @Test
  @DisplayName(
      "CA-SP-966 — el mismo registro dos veces no duplica; a una cuenta sin origen se lo pone, a"
          + " una con origen no se lo cambia, y el titular no se toca")
  void elRegistroRepetidoNoDuplica() throws Exception {
    UUID origen = cuentaVendedora("DIEGOIQ");
    String aviso =
        COMUN
            + "?advertiser=iq_option&postback_name=registro-prueba&trader_id=BN-R-2"
            + "&afftrack=DIEGOIQ&token=secreto-comun";
    mvc.perform(get(aviso)).andExpect(status().isOk());
    mvc.perform(get(aviso)).andExpect(status().isOk());
    assertThat(cuentasDelBroker()).isOne();

    // Declarada antes en la plataforma, sin origen: el aviso se lo pone.
    jdbc.update(
        "INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind)"
            + " VALUES (gen_random_uuid(), ?, ?, 'BN-R-3', 'CONSUMIDOR')",
        cliente,
        IQOPTION);
    mvc.perform(get(aviso.replace("BN-R-2", "BN-R-3"))).andExpect(status().isOk());
    assertThat(laCuenta("BN-R-3").get("referrer_account_id")).isEqualTo(origen);
    assertThat(laCuenta("BN-R-3").get("user_id")).isEqualTo(cliente);
    assertThat(cuentasDelBroker()).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-SP-967, CA-SP-968 — otro evento, o un registro sin trader_id, solo se guardan")
  void otrosAvisosSoloSeGuardan() throws Exception {
    mvc.perform(
            get(
                COMUN
                    + "?advertiser=iq_option&postback_name=ftd&trader_id=BN-R-4"
                    + "&token=secreto-comun"))
        .andExpect(status().isOk());
    mvc.perform(
            get(COMUN + "?advertiser=iq_option&postback_name=registro-prueba&token=secreto-comun"))
        .andExpect(status().isOk());

    assertThat(cuentasDelBroker()).isZero();
    assertThat(guardados()).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // El depósito y la operación (`RN-SP-073`, `RN-SP-074`, 10-10-2026)
  // ---------------------------------------------------------------------------

  private static String aviso(String evento, String numero, String eventId) {
    return COMUN
        + "?advertiser=iq_option&postback_name="
        + evento
        + "&trader_id="
        + numero
        + (eventId == null ? "" : "&event_id=" + eventId)
        + "&token=secreto-comun";
  }

  private Map<String, Object> actividad(String numero) {
    return jdbc.queryForMap(
        "SELECT user_id, status, first_deposit_at, operations_count, first_operation_at,"
            + " last_operation_at FROM user_brokers WHERE broker_id = ? AND external_id = ?",
        IQOPTION,
        numero);
  }

  @Test
  @DisplayName(
      "CA-SP-996, CA-SP-998 — el depósito de una cuenta sin titular la pasa a FIRST_DEPOSIT; el de"
          + " una VENDEDOR o de un número sin cuenta solo se guarda")
  void elDepositoSinTitular() throws Exception {
    cuentaVendedora("DIEGOIQ");
    mvc.perform(get(aviso("registro-prueba", "BN-R-5", "e-1") + "&afftrack=DIEGOIQ"))
        .andExpect(status().isOk());

    mvc.perform(get(aviso("deposito-prueba", "BN-R-5", "e-2"))).andExpect(status().isOk());

    Map<String, Object> cuenta = actividad("BN-R-5");
    assertThat(cuenta.get("status")).isEqualTo("FIRST_DEPOSIT");
    assertThat(cuenta.get("first_deposit_at")).isNotNull();
    assertThat(cuenta.get("user_id")).isNull();

    // La VENDEDOR no tiene FTD (`RN-SP-068`), y un número sin cuenta no la crea.
    mvc.perform(get(aviso("deposito-prueba", "BN-V-1", "e-3"))).andExpect(status().isOk());
    mvc.perform(get(aviso("deposito-prueba", "BN-R-6", "e-4"))).andExpect(status().isOk());
    assertThat(actividad("BN-V-1").get("status")).isEqualTo("REGISTER");
    assertThat(cuentasDelBroker()).isOne();
    assertThat(guardados()).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "CA-SP-997 — un segundo depósito no cambia nada: ni el estado ni el momento del primero")
  void elSegundoDepositoNoCambiaNada() throws Exception {
    mvc.perform(get(aviso("registro-prueba", "BN-R-7", null))).andExpect(status().isOk());
    mvc.perform(get(aviso("deposito-prueba", "BN-R-7", "d-1"))).andExpect(status().isOk());
    Object primero = actividad("BN-R-7").get("first_deposit_at");

    mvc.perform(get(aviso("deposito-prueba", "BN-R-7", "d-2"))).andExpect(status().isOk());

    assertThat(actividad("BN-R-7").get("first_deposit_at")).isEqualTo(primero);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'user_brokers'"
                    + " AND entity_id = (SELECT id FROM user_brokers WHERE external_id = 'BN-R-7')"
                    + " AND changes::text LIKE '%FIRST_DEPOSIT%'",
                Integer.class))
        .isOne();
  }

  @Test
  @DisplayName(
      "CA-SP-999 — cada operación se cuenta, con la primera y la última, en cualquier tipo de"
          + " cuenta, y su aviso se borra; la de un número sin cuenta solo se guarda")
  void lasOperacionesSeCuentan() throws Exception {
    cuentaVendedora("DIEGOIQ");
    mvc.perform(get(aviso("registro-prueba", "BN-R-8", null))).andExpect(status().isOk());
    assertThat(actividad("BN-R-8").get("operations_count")).isEqualTo(0);
    assertThat(actividad("BN-R-8").get("first_operation_at")).isNull();

    mvc.perform(get(aviso("operacion-prueba", "BN-R-8", "o-1"))).andExpect(status().isOk());
    Object primera = actividad("BN-R-8").get("first_operation_at");
    mvc.perform(get(aviso("operacion-prueba", "BN-R-8", "o-2"))).andExpect(status().isOk());
    mvc.perform(get(aviso("operacion-prueba", "BN-V-1", "o-3"))).andExpect(status().isOk());
    mvc.perform(get(aviso("operacion-prueba", "BN-R-9", "o-4"))).andExpect(status().isOk());

    Map<String, Object> cuenta = actividad("BN-R-8");
    assertThat(cuenta.get("operations_count")).isEqualTo(2);
    assertThat(cuenta.get("first_operation_at")).isEqualTo(primera);
    assertThat((java.sql.Timestamp) cuenta.get("last_operation_at"))
        .isAfterOrEqualTo((java.sql.Timestamp) primera);
    // Operar no es depositar.
    assertThat(cuenta.get("status")).isEqualTo("REGISTER");
    assertThat(actividad("BN-V-1").get("operations_count")).isEqualTo(1);
    assertThat(cuentasDelBroker()).isOne();
    // Las tres contadas se borraron; la del número sin cuenta se queda, con el registro.
    assertThat(
            jdbc.queryForList(
                "SELECT query_params -> 'trader_id' ->> 0 FROM broker_notifications"
                    + " WHERE query_params -> 'postback_name' ->> 0 = 'operacion-prueba'",
                String.class))
        .containsExactly("BN-R-9");
  }

  @Test
  @DisplayName(
      "CA-SP-1000 — una reentrega de depósito con el mismo event_id se guarda pero no suma dos"
          + " veces, y en otro broker es otro aviso; la de operación cuenta otra vez, porque el"
          + " aviso contado se borró (15-10-2026)")
  void laReentrega() throws Exception {
    mvc.perform(get(aviso("registro-prueba", "BN-R-10", null))).andExpect(status().isOk());

    mvc.perform(get(aviso("deposito-prueba", "BN-R-10", "repetido") + "&amount=10"))
        .andExpect(status().isOk());
    mvc.perform(get(aviso("deposito-prueba", "BN-R-10", "repetido") + "&amount=10"))
        .andExpect(status().isOk());
    assertThat(centesimas(loQueTrae("BN-R-10").get("deposits_total"))).isEqualTo(1000L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM broker_notifications WHERE event_id = 'repetido'",
                Integer.class))
        .isEqualTo(2);

    // El mismo identificador en OTRO broker es otro aviso: allí no hay cuenta, y se guarda.
    jdbc.update("UPDATE brokers SET advertiser = 'exnova' WHERE id = ?", EXNOVA);
    try {
      mvc.perform(
              get(
                  aviso("deposito-prueba", "BN-R-10", "repetido").replace("iq_option", "exnova")
                      + "&amount=10"))
          .andExpect(status().isOk());
    } finally {
      jdbc.update("UPDATE brokers SET advertiser = NULL WHERE id = ?", EXNOVA);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM broker_notifications WHERE event_id = 'repetido'",
                Integer.class))
        .isEqualTo(3);

    // La operación: el contado se borra, y su reentrega cuenta otra vez.
    mvc.perform(get(aviso("operacion-prueba", "BN-R-10", "op"))).andExpect(status().isOk());
    mvc.perform(get(aviso("operacion-prueba", "BN-R-10", "op"))).andExpect(status().isOk());
    assertThat(actividad("BN-R-10").get("operations_count")).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM broker_notifications WHERE event_id = 'op'", Integer.class))
        .isZero();
  }

  // ---------------------------------------------------------------------------
  // Lo que traen los avisos, en la cuenta (`RN-SP-077`, 15-10-2026)
  // ---------------------------------------------------------------------------

  private Map<String, Object> loQueTrae(String numero) {
    return jdbc.queryForMap(
        "SELECT status, registered_at, country_code, first_deposit_at, first_deposit_amount,"
            + " deposits_total, deposit_currency FROM user_brokers"
            + " WHERE broker_id = ? AND external_id = ?",
        IQOPTION,
        numero);
  }

  /** El momento en que llegó el primer aviso guardado que nombra ese número. */
  private Object llegada(String numero) {
    return jdbc.queryForObject(
        "SELECT min(received_at) FROM broker_notifications"
            + " WHERE query_params -> 'trader_id' ->> 0 = ?",
        Object.class,
        numero);
  }

  private static long centesimas(Object valor) {
    return ((Number) valor).longValue();
  }

  @Test
  @DisplayName(
      "CA-SP-1026 — el registro guarda cuándo llegó y el país, en mayúsculas, también en una cuenta"
          + " ya declarada; una reentrega o un segundo registro no los cambian")
  void elRegistroGuardaFechaYPais() throws Exception {
    mvc.perform(get(aviso("registro-prueba", "BN-R-11", "r-1") + "&country_code=co"))
        .andExpect(status().isOk());
    Object llegada = llegada("BN-R-11");

    Map<String, Object> cuenta = loQueTrae("BN-R-11");
    assertThat(cuenta.get("registered_at")).isNotNull().isEqualTo(llegada);
    assertThat(cuenta.get("country_code")).isEqualTo("CO");

    mvc.perform(get(aviso("registro-prueba", "BN-R-11", "r-1") + "&country_code=PE"))
        .andExpect(status().isOk());
    mvc.perform(get(aviso("registro-prueba", "BN-R-11", "r-2") + "&country_code=PE"))
        .andExpect(status().isOk());
    assertThat(loQueTrae("BN-R-11").get("registered_at")).isEqualTo(llegada);
    assertThat(loQueTrae("BN-R-11").get("country_code")).isEqualTo("CO");

    // Declarada antes en la plataforma: el registro la completa.
    jdbc.update(
        "INSERT INTO user_brokers (id, user_id, broker_id, external_id, kind)"
            + " VALUES (gen_random_uuid(), NULL, ?, 'BN-R-12', 'CONSUMIDOR')",
        IQOPTION);
    assertThat(loQueTrae("BN-R-12").get("registered_at")).isNull();
    mvc.perform(get(aviso("registro-prueba", "BN-R-12", "r-3") + "&country_code=MX"))
        .andExpect(status().isOk());
    assertThat(loQueTrae("BN-R-12").get("registered_at")).isEqualTo(llegada("BN-R-12"));
    assertThat(loQueTrae("BN-R-12").get("country_code")).isEqualTo("MX");
  }

  @Test
  @DisplayName(
      "CA-SP-1027 — el país lo fija el primer aviso que lo trae, también una operación o un"
          + " depósito, y no cambia; un valor que no son dos letras no se guarda")
  void elPaisLoFijaElPrimeroQueLoTrae() throws Exception {
    mvc.perform(get(aviso("registro-prueba", "BN-R-13", null))).andExpect(status().isOk());
    assertThat(loQueTrae("BN-R-13").get("country_code")).isNull();
    // La fecha del registro sí: el país no es lo que la decide.
    assertThat(loQueTrae("BN-R-13").get("registered_at")).isNotNull();

    mvc.perform(get(aviso("operacion-prueba", "BN-R-13", "o-1") + "&country_code=ve"))
        .andExpect(status().isOk());
    assertThat(loQueTrae("BN-R-13").get("country_code")).isEqualTo("VE");
    mvc.perform(get(aviso("deposito-prueba", "BN-R-13", "d-1") + "&country_code=PE"))
        .andExpect(status().isOk());
    assertThat(loQueTrae("BN-R-13").get("country_code")).isEqualTo("VE");

    mvc.perform(get(aviso("registro-prueba", "BN-R-14", null) + "&country_code=COL"))
        .andExpect(status().isOk());
    assertThat(loQueTrae("BN-R-14").get("country_code")).isNull();
    // Y el aviso lo conserva tal como llegó.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM broker_notifications"
                    + " WHERE query_params -> 'country_code' ->> 0 = 'COL'",
                Integer.class))
        .isOne();
  }

  @Test
  @DisplayName(
      "CA-SP-1028, CA-SP-1029 — el depósito que pasa a FIRST_DEPOSIT guarda el primero y lo suma;"
          + " los siguientes suman, una reentrega no, y otra moneda no")
  void losDepositosSeGuardanYSeSuman() throws Exception {
    mvc.perform(get(aviso("registro-prueba", "BN-R-15", null))).andExpect(status().isOk());

    mvc.perform(get(aviso("deposito-prueba", "BN-R-15", "d-1") + "&amount=150.50&currency=usd"))
        .andExpect(status().isOk());
    Map<String, Object> cuenta = loQueTrae("BN-R-15");
    assertThat(cuenta.get("status")).isEqualTo("FIRST_DEPOSIT");
    assertThat(centesimas(cuenta.get("first_deposit_amount"))).isEqualTo(15050L);
    assertThat(centesimas(cuenta.get("deposits_total"))).isEqualTo(15050L);
    assertThat(cuenta.get("deposit_currency")).isEqualTo("USD");

    // Un segundo depósito suma, y no cambia el primero.
    mvc.perform(get(aviso("deposito-prueba", "BN-R-15", "d-2") + "&amount=20&currency=USD"))
        .andExpect(status().isOk());
    // Su reentrega no suma.
    mvc.perform(get(aviso("deposito-prueba", "BN-R-15", "d-2") + "&amount=20&currency=USD"))
        .andExpect(status().isOk());
    // Otra moneda no suma: dos monedas no dan una cifra de ninguna.
    mvc.perform(get(aviso("deposito-prueba", "BN-R-15", "d-3") + "&amount=5&currency=EUR"))
        .andExpect(status().isOk());
    // Sin moneda, se entiende la de la cuenta.
    mvc.perform(get(aviso("deposito-prueba", "BN-R-15", "d-4") + "&amount=1"))
        .andExpect(status().isOk());

    cuenta = loQueTrae("BN-R-15");
    assertThat(centesimas(cuenta.get("first_deposit_amount"))).isEqualTo(15050L);
    assertThat(centesimas(cuenta.get("deposits_total"))).isEqualTo(17150L);
    assertThat(cuenta.get("deposit_currency")).isEqualTo("USD");
  }

  @Test
  @DisplayName(
      "CA-SP-1030 — un importe ausente, que no es un número, cero o negativo no se guarda y el"
          + " depósito se aplica igual; con más de dos decimales se redondea al céntimo")
  void unImporteInvalidoNoSeGuarda() throws Exception {
    mvc.perform(get(aviso("registro-prueba", "BN-R-16", null))).andExpect(status().isOk());

    mvc.perform(get(aviso("deposito-prueba", "BN-R-16", "d-1") + "&amount=abc"))
        .andExpect(status().isOk());
    Map<String, Object> cuenta = loQueTrae("BN-R-16");
    assertThat(cuenta.get("status")).isEqualTo("FIRST_DEPOSIT");
    assertThat(cuenta.get("first_deposit_at")).isNotNull();
    assertThat(cuenta.get("first_deposit_amount")).isNull();
    assertThat(centesimas(cuenta.get("deposits_total"))).isZero();

    mvc.perform(get(aviso("deposito-prueba", "BN-R-16", "d-2") + "&amount=-5"))
        .andExpect(status().isOk());
    mvc.perform(get(aviso("deposito-prueba", "BN-R-16", "d-3") + "&amount=0"))
        .andExpect(status().isOk());
    mvc.perform(get(aviso("deposito-prueba", "BN-R-16", "d-4"))).andExpect(status().isOk());
    assertThat(centesimas(loQueTrae("BN-R-16").get("deposits_total"))).isZero();

    mvc.perform(get(aviso("deposito-prueba", "BN-R-16", "d-5") + "&amount=10.005"))
        .andExpect(status().isOk());
    cuenta = loQueTrae("BN-R-16");
    assertThat(centesimas(cuenta.get("deposits_total"))).isEqualTo(1001L);
    // El primero era el que no trajo importe, y sigue sin él.
    assertThat(cuenta.get("first_deposit_amount")).isNull();
  }
}
