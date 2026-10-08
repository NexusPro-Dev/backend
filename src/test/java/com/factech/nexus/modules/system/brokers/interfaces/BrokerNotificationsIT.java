package com.factech.nexus.modules.system.brokers.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * Los avisos de los brokers (`RF-SP-078`): `CA-SP-897` a `CA-SP-907`.
 *
 * <p>Los secretos de {@code IQOPTION} y {@code EXNOVA} los fija {@link IntegrationTestBase}; {@code
 * EXOPTION} queda sin secreto, para `CA-SP-902`. Ninguna petición lleva sesión: la ruta es pública.
 */
@AutoConfigureMockMvc
@SuppressWarnings("unchecked") // JsonPath.read devuelve lo que se le pida
class BrokerNotificationsIT extends IntegrationTestBase {

  private static final UUID IQOPTION = UUID.fromString("01a081f0-6000-7101-9c4f-5e7adb000001");
  private static final UUID EXNOVA = UUID.fromString("01a081f0-6000-7102-9c4f-5e7adb000002");
  private static final UUID EXOPTION = UUID.fromString("01a081f0-6000-7103-9c4f-5e7adb000003");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID cliente;

  @BeforeEach
  void limpiarAntes() {
    limpiar();
  }

  /** También al terminar, y reponiendo el broker que alguna prueba desactiva. */
  @AfterEach
  void limpiarDespues() {
    jdbc.update(
        "UPDATE brokers SET is_active = true WHERE id IN (?, ?, ?)", IQOPTION, EXNOVA, EXOPTION);
    if (cliente != null) {
      jdbc.update("DELETE FROM user_brokers WHERE user_id = ?", cliente);
      jdbc.update("DELETE FROM users WHERE id = ?", cliente);
      cliente = null;
    }
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM broker_notifications");
    jdbc.update("DELETE FROM request_log WHERE path LIKE '/api/v1/brokers/%/notifications'");
  }

  private static String ruta(UUID broker) {
    return "/api/v1/brokers/" + broker + "/notifications";
  }

  private int guardados() {
    return jdbc.queryForObject("SELECT count(*) FROM broker_notifications", Integer.class);
  }

  private Map<String, Object> elUnico() {
    return jdbc.queryForMap(
        "SELECT broker_id, method, CAST(query_params AS text) AS query_params,"
            + " CAST(headers AS text) AS headers, body, content_type, ip_address, received_at"
            + " FROM broker_notifications");
  }

  @Test
  @DisplayName(
      "CA-SP-897 — un aviso con los datos en la dirección se acepta sin sesión y se guarda entero,"
          + " también los parámetros repetidos; la respuesta va vacía")
  void conLosDatosEnLaDireccion() throws Exception {
    mvc.perform(
            get(ruta(IQOPTION) + "?event=ftd&trader_id=12345&sub=a&sub=b&token=secreto-iq")
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
    String cuerpo = "{ \"event\" : \"registration\",\n  \"trader_id\": 987 }";
    mvc.perform(
            post(ruta(EXNOVA) + "?token=secreto-ex")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isOk());

    Map<String, Object> fila = elUnico();
    assertThat(fila.get("broker_id")).isEqualTo(EXNOVA);
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
            post(ruta(IQOPTION) + "?origen=panel&token=secreto-iq")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("event=ftd&amount=50&trader_id=555"))
        .andExpect(status().isOk());

    Map<String, Object> fila = elUnico();
    assertThat(fila.get("body")).isEqualTo("event=ftd&amount=50&trader_id=555");
    String consulta = (String) fila.get("query_params");
    assertThat((Map<String, Object>) JsonPath.read(consulta, "$")).containsOnlyKeys("origen");
  }

  @Test
  @DisplayName(
      "CA-SP-900 — sin secreto, con uno equivocado o con dos, el aviso no se autentica y no se"
          + " guarda")
  void sinSecretoOConOtro() throws Exception {
    mvc.perform(get(ruta(IQOPTION) + "?event=ftd")).andExpect(status().isUnauthorized());
    mvc.perform(get(ruta(IQOPTION) + "?event=ftd&token=otro")).andExpect(status().isUnauthorized());
    mvc.perform(get(ruta(IQOPTION) + "?token=secreto-iq&token=secreto-iq"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(ruta(IQOPTION)).contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnauthorized());

    assertThat(guardados()).isZero();
  }

  @Test
  @DisplayName("CA-SP-901 — el secreto de un broker no sirve para otro")
  void elSecretoDeUnoNoSirveParaOtro() throws Exception {
    mvc.perform(get(ruta(IQOPTION) + "?event=ftd&token=secreto-ex"))
        .andExpect(status().isUnauthorized());

    assertThat(guardados()).isZero();
  }

  @Test
  @DisplayName(
      "CA-SP-902 — un broker sin secreto configurado responde servicio no disponible y no guarda"
          + " nada")
  void sinSecretoConfigurado() throws Exception {
    mvc.perform(get(ruta(EXOPTION) + "?event=ftd&token=cualquiera"))
        .andExpect(status().isServiceUnavailable());

    assertThat(guardados()).isZero();
  }

  @Test
  @DisplayName(
      "CA-SP-903 — un broker que no existe, o que no está activo, responde no encontrado y no"
          + " guarda nada")
  void brokerInexistenteOInactivo() throws Exception {
    mvc.perform(get(ruta(UUID.randomUUID()) + "?token=secreto-iq"))
        .andExpect(status().isNotFound());

    jdbc.update("UPDATE brokers SET is_active = false WHERE id = ?", EXNOVA);
    mvc.perform(get(ruta(EXNOVA) + "?token=secreto-ex")).andExpect(status().isNotFound());

    assertThat(guardados()).isZero();
  }

  @Test
  @DisplayName("CA-SP-904 — un cuerpo de más de 64 KiB se rechaza y no se guarda")
  void cuerpoDemasiadoGrande() throws Exception {
    byte[] grande = new byte[64 * 1024 + 1];
    java.util.Arrays.fill(grande, (byte) 'a');
    mvc.perform(
            post(ruta(IQOPTION) + "?token=secreto-iq")
                .contentType(MediaType.TEXT_PLAIN)
                .content(grande))
        .andExpect(status().isBadRequest());

    byte[] justo = new byte[64 * 1024];
    java.util.Arrays.fill(justo, (byte) 'b');
    mvc.perform(
            post(ruta(IQOPTION) + "?token=secreto-iq")
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
      mvc.perform(get(ruta(IQOPTION) + "?event=ftd&trader_id=1&token=secreto-iq"))
          .andExpect(status().isOk());
    }

    assertThat(guardados()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-SP-906 — el secreto no queda escrito: ni en el aviso, ni en el registro de peticiones,"
          + " ni las cabeceras de credenciales")
  void elSecretoNoQuedaEscrito() throws Exception {
    mvc.perform(
            get(ruta(EXNOVA) + "?event=ftd&token=secreto-ex&trader_id=7")
                .header("Authorization", "Basic c2VjcmV0bw==")
                .header("Cookie", "sesion=abc")
                .header("Proxy-Authorization", "Basic eHl6"))
        .andExpect(status().isOk());

    Map<String, Object> fila = elUnico();
    String consulta = (String) fila.get("query_params");
    String cabeceras = (String) fila.get("headers");
    assertThat(consulta).doesNotContain("token").doesNotContain("secreto-ex");
    assertThat((Map<String, Object>) JsonPath.read(cabeceras, "$"))
        .doesNotContainKeys("authorization", "cookie", "proxy-authorization");
    assertThat(cabeceras).doesNotContain("c2VjcmV0bw==").doesNotContain("sesion=abc");

    List<String> registradas =
        jdbc.queryForList(
            "SELECT query_string FROM request_log WHERE path = ?", String.class, ruta(EXNOVA));
    assertThat(registradas).containsExactly("event=ftd&token=[OCULTO]&trader_id=7");
  }

  @Test
  @DisplayName(
      "CA-SP-907 — recibir no cambia nada más: la cuenta sigue en REGISTER sin usuario del broker"
          + " y su titular en FTD_PENDIENTE")
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
        "INSERT INTO user_brokers (id, user_id, broker_id, external_id)"
            + " VALUES (gen_random_uuid(), ?, ?, 'BN-778899')",
        cliente,
        IQOPTION);

    mvc.perform(
            get(
                ruta(IQOPTION)
                    + "?event=ftd&trader_id=BN-778899&username=bn_trader&token=secreto-iq"))
        .andExpect(status().isOk());

    Map<String, Object> cuenta =
        jdbc.queryForMap(
            "SELECT status, broker_username FROM user_brokers WHERE user_id = ?", cliente);
    assertThat(cuenta.get("status")).isEqualTo("REGISTER");
    assertThat(cuenta.get("broker_username")).isNull();
    assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, cliente))
        .isEqualTo("FTD_PENDIENTE");
    assertThat(guardados()).isOne();
    assertThat((String) elUnico().get("query_params")).contains("BN-778899");
  }
}
