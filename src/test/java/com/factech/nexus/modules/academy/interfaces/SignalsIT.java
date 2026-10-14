package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.academy.domain.models.Signal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Las señales de trading (`RN-AC-035`, `RF-AC-067` a `RF-AC-072`). */
@AutoConfigureMockMvc
class SignalsIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/signals";
  private static final UUID BECA = UUID.fromString("01a04ad0-e800-7001-9c4f-5e7ad7000001");
  private static final UUID VIP = UUID.fromString("01a04ad0-e800-7002-9c4f-5e7ad7000002");
  private static final String CABECERA =
      "currency_pair;signal_time;signal_type;direction;subscription_type;memberships\n";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID admin;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    admin = CourseTestSupport.persona(jdbc, "Ada", "Admin", ProgressTestSupport.ADMIN);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-339` — V108: seis permisos; los cinco de administración solo a SUPERADMIN y ADMIN;"
          + " signals:view donde está live-sessions:learn; catálogo de 250")
  void permisos() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class))
        .isEqualTo(250);
    assertThat(
            jdbc.queryForList(
                "SELECT code FROM permissions WHERE code LIKE 'signals:%' ORDER BY code",
                String.class))
        .containsExactly(
            "signals:create",
            "signals:create-batch",
            "signals:delete",
            "signals:import",
            "signals:list",
            "signals:view");
    assertThat(
            jdbc.queryForList(
                "SELECT DISTINCT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id"
                    + " JOIN permissions p ON p.id = rp.permission_id WHERE p.code IN"
                    + " ('signals:list', 'signals:create', 'signals:create-batch',"
                    + " 'signals:import', 'signals:delete') ORDER BY r.code",
                String.class))
        .containsExactly("ADMIN", "SUPERADMIN");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id ="
                    + " rp.permission_id WHERE p.code = 'live-sessions:learn' AND NOT EXISTS"
                    + " (SELECT 1 FROM role_permissions v JOIN permissions q ON q.id ="
                    + " v.permission_id WHERE q.code = 'signals:view' AND v.role_id ="
                    + " rp.role_id)",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-341`, `CA-AC-350` — registrar una: 201, par en mayúsculas, HH:mm, visibleUntil la"
          + " medianoche de Bogotá de su día, audita CREATE; PERSONALIZED con sus membresías")
  void registraUna() throws Exception {
    String cuerpo =
        registrar(senal(" eur/usd ", "10:30", 1, "free", null))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currencyPair").value("EUR/USD"))
            .andExpect(jsonPath("$.signalTime").value("10:30"))
            .andExpect(jsonPath("$.signalType").value(1))
            .andExpect(jsonPath("$.subscriptionType").value("FREE"))
            .andExpect(jsonPath("$.memberships", hasSize(0)))
            .andExpect(jsonPath("$.visible").value(true))
            .andExpect(jsonPath("$.deletedAt").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    OffsetDateTime creada = OffsetDateTime.parse(JsonPath.read(cuerpo, "$.createdAt"));
    OffsetDateTime hasta = OffsetDateTime.parse(JsonPath.read(cuerpo, "$.visibleUntil"));
    assertThat(hasta.toInstant())
        .isEqualTo(Signal.visibleUntil(creada, ZoneId.of("America/Bogota")).toInstant());
    UUID id = UUID.fromString(JsonPath.read(cuerpo, "$.id"));
    assertThat(
            jdbc.queryForObject(
                "SELECT changes::text FROM audit_change_log WHERE module = 'AC' AND entity ="
                    + " 'signals' AND entity_id = ? AND action = 'CREATE'",
                String.class,
                id))
        .contains("EUR/USD")
        .contains("10:30");

    registrar(senal("XAU/USD", "08:00", 2, "PERSONALIZED", "[\"" + VIP + "\",\"" + VIP + "\"]"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.subscriptionType").value("PERSONALIZED"))
        .andExpect(jsonPath("$.memberships[*].code", contains("VIP")));
  }

  @Test
  @DisplayName(
      "`CA-AC-341` — registrar una inválida: VAL-001 a VAL-006 con su campo, todos juntos; una"
          + " membresía inexistente, 422 EX-002; no se registra nada")
  void rechazaUna() throws Exception {
    registrar("{}")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath(
                "$.errors[*].field",
                contains(
                    "currencyPair", "signalTime", "signalType", "direction", "subscriptionType")))
        .andExpect(
            jsonPath(
                "$.errors[*].code",
                contains("VAL-001", "VAL-002", "VAL-003", "VAL-011", "VAL-004")));
    registrar(senal("EUR USD", "25:00", -1, "gold", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasSize(4)));
    registrar(senal("EUR/USD", "10:00", 1, "PERSONALIZED", "[]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("membershipIds"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
    registrar(senal("EUR/USD", "10:00", 1, "PREMIUM", "[\"" + VIP + "\"]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));
    registrar(senal("EUR/USD", "10:00", 1, "PERSONALIZED", "[\"" + UUID.randomUUID() + "\"]"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("membershipIds"))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM signals", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-342` — registrar varias: todas en orden; con una mala, 400 con [i].campo y ninguna"
          + " registrada; vacío VAL-007; 501 VAL-008; membresía inexistente 422 en su posición")
  void registraVarias() throws Exception {
    lote("["
            + senal("EUR/USD", "10:00", 1, "FREE", null)
            + ","
            + senal("GBP/USD", "11:00", 2, "PREMIUM", null)
            + "]")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[*].currencyPair", contains("EUR/USD", "GBP/USD")));

    lote("["
            + senal("AUD/USD", "10:00", 1, "FREE", null)
            + ","
            + senal("NZD/USD", "1030", 1, "FREE", null)
            + ","
            + senal("", "10:00", 1, "FREE", null)
            + "]")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", contains("[1].signalTime", "[2].currencyPair")));
    lote("["
            + senal("AUD/USD", "10:00", 1, "FREE", null)
            + ","
            + senal("NZD/USD", "10:00", 1, "PERSONALIZED", "[\"" + UUID.randomUUID() + "\"]")
            + "]")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("[1].membershipIds"));
    lote("[]")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));
    StringBuilder muchas = new StringBuilder("[");
    for (int i = 0; i < 501; i++) {
      muchas.append(i == 0 ? "" : ",").append(senal("EUR/USD", "10:00", 1, "FREE", null));
    }
    lote(muchas.append("]").toString())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM signals", Integer.class)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-AC-343` — importar un CSV: todas con sus membresías por código; con errores,"
          + " lines[n].columna y ninguna; código inexistente 422; sin cabecera VAL-010; sin archivo"
          + " VAL-009")
  void importa() throws Exception {
    importar(CABECERA + "eur/usd;10:30;1;sube;free;\nXAU/USD;9:00;2;PUT;personalized;oro|vip\n")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[1].memberships[*].code", containsInAnyOrder("ORO", "VIP")))
        .andExpect(jsonPath("$[1].signalTime").value("09:00"))
        .andExpect(jsonPath("$[*].direction", contains("UP", "DOWN")));

    importar(
            CABECERA
                + "EUR/USD;10:30;1;UP;FREE;\nGBP/USD;10:30;uno;UP;FREE;\n\nUSD/JPY;10:30;1;UP;FREE;ORO\n")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field", contains("lines[3].signal_type", "lines[5].memberships")))
        .andExpect(jsonPath("$.errors[*].code", contains("VAL-003", "VAL-006")));
    importar(CABECERA + "EUR/USD;10:30;1;UP;PERSONALIZED;DIAMANTE\n")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("lines[2].memberships"))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    importar("par;hora\nEUR/USD;10:00\n")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("file"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-010"));
    mvc.perform(multipart(RUTA + "/import").with(con(admin, "signals:import")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM signals", Integer.class)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-AC-344` — consultar: las más nuevas primero; filtros subscriptionType, visible e"
          + " includeDeleted; visible y deletedAt en cada una")
  void consulta() throws Exception {
    UUID vieja = idDe(registrar(senal("EUR/USD", "10:00", 1, "FREE", null)));
    UUID premium = idDe(registrar(senal("GBP/USD", "10:00", 1, "PREMIUM", null)));
    UUID retirada = idDe(registrar(senal("USD/JPY", "10:00", 1, "FREE", null)));
    vencer(vieja);
    retirar(retirada).andExpect(status().isNoContent());

    mvc.perform(get(RUTA).with(con("signals:list")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[*].id", contains(premium.toString(), vieja.toString())))
        .andExpect(jsonPath("$.content[*].visible", contains(true, false)));
    mvc.perform(get(RUTA).param("includeDeleted", "true").with(con("signals:list")))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].id").value(retirada.toString()))
        .andExpect(jsonPath("$.content[0].deletedAt").isNotEmpty());
    mvc.perform(get(RUTA).param("visible", "true").with(con("signals:list")))
        .andExpect(jsonPath("$.content[*].id", contains(premium.toString())));
    mvc.perform(get(RUTA).param("visible", "false").with(con("signals:list")))
        .andExpect(jsonPath("$.content[*].id", contains(vieja.toString())));
    mvc.perform(get(RUTA).param("subscriptionType", "premium").with(con("signals:list")))
        .andExpect(jsonPath("$.content[*].id", contains(premium.toString())));
    mvc.perform(get(RUTA).param("subscriptionType", "gold").with(con("signals:list")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
  }

  @Test
  @DisplayName(
      "`CA-AC-345` — retirar: 204 y audita con el motivo; otra vez, 409 EX-002; inexistente, 404;"
          + " sin motivo, 400")
  void retira() throws Exception {
    UUID id = idDe(registrar(senal("EUR/USD", "10:00", 1, "FREE", null)));
    mvc.perform(
            post(RUTA + "/" + id + "/deletion")
                .with(con(admin, "signals:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    retirar(id).andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE module = 'AC' AND entity ="
                    + " 'signals' AND entity_id = ?",
                Integer.class,
                id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM signals WHERE id = ?", Boolean.class, id))
        .isTrue();
    retirar(id)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    retirar(UUID.randomUUID()).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-346` — las vigentes: BECA ve las FREE; VIP también las PREMIUM y las PERSONALIZED"
          + " de VIP; ni vencidas ni retiradas; sin membresía vigente, ninguna")
  void vigentes() throws Exception {
    // `CA-AC-350`: a las 00:00 su hora ya pasó siempre, y se ve igual porque se creó hoy.
    UUID free = idDe(registrar(senal("EUR/USD", "00:00", 1, "FREE", null)));
    UUID premium = idDe(registrar(senal("GBP/USD", "10:00", 1, "PREMIUM", null)));
    UUID paraVip =
        idDe(registrar(senal("XAU/USD", "10:00", 1, "PERSONALIZED", "[\"" + VIP + "\"]")));
    UUID paraBeca =
        idDe(registrar(senal("BTC/USD", "10:00", 1, "PERSONALIZED", "[\"" + BECA + "\"]")));
    UUID vencida = idDe(registrar(senal("USD/JPY", "10:00", 1, "FREE", null)));
    UUID retirada = idDe(registrar(senal("USD/CAD", "10:00", 1, "FREE", null)));
    vencer(vencida);
    retirar(retirada).andExpect(status().isNoContent());

    UUID alumno = ClassroomTestSupport.alumno(jdbc);
    vigentesDe(alumno)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", containsInAnyOrder(free.toString(), paraBeca.toString())))
        .andExpect(jsonPath("$[*].signalTime", containsInAnyOrder("00:00", "10:00")))
        .andExpect(jsonPath("$[0].direction").value("UP"))
        .andExpect(jsonPath("$[0].memberships").doesNotExist());

    ClassroomTestSupport.conMembresia(jdbc, alumno, VIP);
    vigentesDe(alumno)
        .andExpect(
            jsonPath(
                "$[*].id",
                containsInAnyOrder(free.toString(), premium.toString(), paraVip.toString())));

    ClassroomTestSupport.conMembresiaVencida(jdbc, alumno);
    vigentesDe(alumno).andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-AC-348` — la dirección: obligatoria en una, varias y el CSV; los sinónimos se guardan"
          + " UP o DOWN y así salen; sin ella u otra, VAL-011 con su posición")
  void direccion() throws Exception {
    UUID baja =
        idDe(
            registrar(senalCon("EUR/USD", "10:00", 1, "baja", "FREE", null))
                .andExpect(jsonPath("$.direction").value("DOWN")));
    assertThat(
            jdbc.queryForObject("SELECT direction FROM signals WHERE id = ?", String.class, baja))
        .isEqualTo("DOWN");
    lote("["
            + senalCon("GBP/USD", "10:00", 1, "call", "FREE", null)
            + ","
            + senalCon("USD/JPY", "10:00", 1, "Sell", "FREE", null)
            + "]")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[*].direction", contains("UP", "DOWN")));
    mvc.perform(get(RUTA).with(con("signals:list")))
        .andExpect(jsonPath("$.content[*].direction", containsInAnyOrder("DOWN", "UP", "DOWN")));

    registrar(
            "{\"currencyPair\":\"EUR/USD\",\"signalTime\":\"10:00\",\"signalType\":1,"
                + "\"subscriptionType\":\"FREE\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", contains("direction")))
        .andExpect(jsonPath("$.errors[*].code", contains("VAL-011")));
    lote("["
            + senal("EUR/USD", "10:00", 1, "FREE", null)
            + ","
            + senalCon("EUR/USD", "10:00", 1, "lateral", "FREE", null)
            + "]")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", contains("[1].direction")));
    importar(CABECERA + "EUR/USD;10:00;1;;FREE;\n")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("lines[2].direction"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-011"));
    importar("currency_pair;signal_time;signal_type;subscription_type\nEUR/USD;10:00;1;FREE\n")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-010"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM signals", Integer.class)).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "`CA-AC-349` — V109: una señal sin dirección solo puede existir retirada"
          + " (ck_signals_direction)")
  void sinDireccionSoloRetirada() {
    String insertar =
        "INSERT INTO signals (id, currency_pair, signal_time, signal_type, subscription_type,"
            + " visible_until, created_at, deleted_at, direction) VALUES (?, 'EUR/USD', '10:00', 1,"
            + " 'FREE', now() + interval '1 hour', now(), %s, %s)";
    assertThatThrownBy(() -> jdbc.update(insertar.formatted("NULL", "NULL"), UUID.randomUUID()))
        .hasMessageContaining("ck_signals_direction");
    assertThatThrownBy(() -> jdbc.update(insertar.formatted("NULL", "'LEFT'"), UUID.randomUUID()))
        .hasMessageContaining("ck_signals_direction");
    jdbc.update(insertar.formatted("now()", "NULL"), UUID.randomUUID());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM signals WHERE direction IS NULL", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-AC-347` — cada ruta exige su permiso")
  void cadaRutaSuPermiso() throws Exception {
    mvc.perform(get(RUTA).with(con("signals:view"))).andExpect(status().isForbidden());
    mvc.perform(get(RUTA + "/available").with(con("signals:list")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(RUTA)
                .with(con("signals:create-batch"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(senal("EUR/USD", "10:00", 1, "FREE", null)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(RUTA + "/batch")
                .with(con("signals:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
        .andExpect(status().isForbidden());
    mvc.perform(multipart(RUTA + "/import").file(csv(CABECERA)).with(con("signals:create")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(RUTA + "/" + UUID.randomUUID() + "/deletion")
                .with(con("signals:list"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No\"}"))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------

  private static String senal(
      String par, String hora, int tipo, String suscripcion, String membresias) {
    return senalCon(par, hora, tipo, "UP", suscripcion, membresias);
  }

  private static String senalCon(
      String par, String hora, int tipo, String direccion, String suscripcion, String membresias) {
    return "{\"currencyPair\":\"%s\",\"signalTime\":\"%s\",\"signalType\":%d,\"direction\":\"%s\",\"subscriptionType\":\"%s\"%s}"
        .formatted(
            par,
            hora,
            tipo,
            direccion,
            suscripcion,
            membresias == null ? "" : ",\"membershipIds\":" + membresias);
  }

  private static MockMultipartFile csv(String contenido) {
    return new MockMultipartFile(
        "file", "senales.csv", "text/csv", contenido.getBytes(StandardCharsets.UTF_8));
  }

  private ResultActions registrar(String cuerpo) throws Exception {
    return mvc.perform(
        post(RUTA)
            .with(con(admin, "signals:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions lote(String cuerpo) throws Exception {
    return mvc.perform(
        post(RUTA + "/batch")
            .with(con(admin, "signals:create-batch"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions importar(String contenido) throws Exception {
    return mvc.perform(
        multipart(RUTA + "/import").file(csv(contenido)).with(con(admin, "signals:import")));
  }

  private ResultActions retirar(UUID id) throws Exception {
    return mvc.perform(
        post(RUTA + "/" + id + "/deletion")
            .with(con(admin, "signals:delete"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"Ya no aplica\"}"));
  }

  private ResultActions vigentesDe(UUID alumno) throws Exception {
    return mvc.perform(get(RUTA + "/available").with(con(alumno, "signals:view")));
  }

  /** La deja vencida: creada hace dos días, se mostró hasta ayer. */
  private void vencer(UUID id) {
    jdbc.update(
        "UPDATE signals SET created_at = now() - interval '2 days', visible_until = now() -"
            + " interval '1 day' WHERE id = ?",
        id);
  }

  private UUID idDe(ResultActions respuesta) throws Exception {
    return UUID.fromString(
        JsonPath.read(
            respuesta
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id"));
  }
}
