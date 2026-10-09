package com.factech.nexus.modules.system.brokers.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Registrar, corregir y borrar cuentas de broker (`RF-SP-053`, `RF-SP-080`, `RF-SP-081`).
 *
 * <p>Dos personas —{@code titular}, un CLIENTE, y {@code otra}, un AGENTE— y administración, que
 * actúa como el superadministrador sembrado. <b>El estado {@code FIRST_DEPOSIT} se siembra por
 * SQL</b>: no hay API que lo mueva (`RF-SP-054`), y es lo que separa al titular de administración
 * (`RN-SP-067`).
 */
@AutoConfigureMockMvc
class ManageBrokerAccountsIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";
  private static final String APAGADO = "ZBROKER-APAGADO-MBA";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID titular;
  private UUID otra;
  private UUID exnova;
  private UUID iqoption;
  private UUID apagado;

  @BeforeEach
  void preparar() {
    limpiar();
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM client_sellers");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_products");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    titular = crearPersona("mba-titular", CLIENTE);
    otra = crearPersona("mba-otra", AGENTE);

    // Repuestos si no están, con su identificador de `V76`: otra suite vacía
    // `brokers` y no los repone.
    exnova = brokerAsegurado("01a081f0-6000-7102-9c4f-5e7adb000002", "EXNOVA");
    iqoption = brokerAsegurado("01a081f0-6000-7101-9c4f-5e7adb000001", "IQOPTION");
    apagado = UUID.randomUUID();
    jdbc.update("INSERT INTO brokers (id, name, is_active) VALUES (?, ?, false)", apagado, APAGADO);
  }

  /**
   * {@code fk_user_brokers_user} es {@code RESTRICT}: una cuenta que sobreviva rompe otras suites.
   */
  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM user_brokers");
    jdbc.update("DELETE FROM brokers WHERE name = ?", APAGADO);
    jdbc.update("DELETE FROM audit_change_log WHERE entity = 'user_brokers'");
    jdbc.update("DELETE FROM audit_deletion_log WHERE entity = 'user_brokers'");
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-053` — registrar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-915` — el titular declara una cuenta suya: 201, REGISTER, sin nombre")
  void elTitularDeclara() throws Exception {
    String cuerpo =
        mvc.perform(
                post("/api/v1/users/me/broker-accounts")
                    .with(comoTitular())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(alta(exnova, "  80000001  ")))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.accountId").value("80000001"))
            .andExpect(jsonPath("$.broker.name").value("EXNOVA"))
            .andExpect(jsonPath("$.status").value("REGISTER"))
            .andExpect(jsonPath("$.kind").value("CONSUMIDOR"))
            .andExpect(
                content().string(org.hamcrest.Matchers.containsString("\"brokerUsername\":null")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(cuerpo, "$.id");

    mvc.perform(
            get("/api/v1/users/me/broker-accounts")
                .with(user(titular.toString()).authorities(() -> "broker-accounts:read-own")))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id))
        .andExpect(jsonPath("$.content[0].kind").value("CONSUMIDOR"));
  }

  @Test
  @DisplayName("`CA-SP-916` — administración declara una cuenta a nombre de otra persona")
  void administracionDeclara() throws Exception {
    mvc.perform(
            post("/api/v1/users/" + otra + "/broker-accounts")
                .with(comoAdmin("broker-accounts:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(iqoption, "80000002")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("REGISTER"))
        // `CA-SP-938`: el tipo es el del titular —un agente—, no el de quien declara.
        .andExpect(jsonPath("$.kind").value("VENDEDOR"));
    assertThat(cuentasDe(otra)).containsExactly("80000002");
  }

  @Test
  @DisplayName(
      "`CA-SP-938` — el tipo sale del rol del titular, y queda en la fila y en la auditoría")
  void elTipoSaleDelRolDelTitular() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/broker-accounts")
                .with(user(otra.toString()).authorities(() -> "broker-accounts:create-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000030")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kind").value("VENDEDOR"));
    mvc.perform(
            post("/api/v1/users/me/broker-accounts")
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000031")))
        .andExpect(jsonPath("$.kind").value("CONSUMIDOR"));

    assertThat(
            jdbc.queryForList("SELECT kind FROM user_brokers ORDER BY external_id", String.class))
        .containsExactly("VENDEDOR", "CONSUMIDOR");
    assertThat(
            jdbc.queryForList(
                "SELECT changes::text FROM audit_change_log WHERE entity = 'user_brokers'"
                    + " AND action = 'CREATE'",
                String.class))
        .anySatisfy(c -> assertThat(c).contains("\"kind\": \"VENDEDOR\""));
  }

  @Test
  @DisplayName("`CA-SP-939` — quien es vendedor y también cliente declara cuentas VENDEDOR")
  void vendedorYClienteEsVendedor() throws Exception {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        otra,
        CLIENTE);

    mvc.perform(
            post("/api/v1/users/" + otra + "/broker-accounts")
                .with(comoAdmin("broker-accounts:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000032")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kind").value("VENDEDOR"));
  }

  @Test
  @DisplayName(
      "`CA-SP-940` — sin rol vendedor ni consumidor es 422 por las dos rutas, y nada queda")
  void sinTipoEs422() throws Exception {
    // El superadministrador solo porta un rol FUNCIONARIO.
    mvc.perform(
            post("/api/v1/users/me/broker-accounts")
                .with(user(SUPERADMIN.toString()).authorities(() -> "broker-accounts:create-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000033")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-011"));
    mvc.perform(
            post("/api/v1/users/" + SUPERADMIN + "/broker-accounts")
                .with(comoAdmin("broker-accounts:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000034")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-011"));

    assertThat(cuentasDe(SUPERADMIN)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'user_brokers'",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("`CA-SP-917` — una cuenta ya declarada, por quien sea, es 409 y no registra nada")
  void yaDeclarada() throws Exception {
    declarar(otra, exnova, "80000003", "REGISTER");

    mvc.perform(
            post("/api/v1/users/me/broker-accounts")
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000003")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-009"));
    // Y la misma persona tampoco puede declararla dos veces.
    mvc.perform(
            post("/api/v1/users/" + otra + "/broker-accounts")
                .with(comoAdmin("broker-accounts:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000003")))
        .andExpect(status().isConflict());
    assertThat(cuentasDe(titular)).isEmpty();
    assertThat(cuentasDe(otra)).containsExactly("80000003");
  }

  @Test
  @DisplayName("`CA-SP-918` — broker inexistente o apagado: 422 EX-008, el mismo para los dos")
  void brokerQueNoProcede() throws Exception {
    for (UUID broker : List.of(apagado, UUID.randomUUID())) {
      mvc.perform(
              post("/api/v1/users/me/broker-accounts")
                  .with(comoTitular())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(alta(broker, "80000004")))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.errors[0].code").value("EX-008"));
    }
    assertThat(cuentasDe(titular)).isEmpty();
  }

  @Test
  @DisplayName("`CA-SP-919` — sin broker, sin identificador, en blanco o largo: 400 con todo")
  void validaciones() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/broker-accounts")
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-012"))
        .andExpect(jsonPath("$.errors[1].code").value("VAL-013"));
    mvc.perform(
            post("/api/v1/users/me/broker-accounts")
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "   ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-013"));
    mvc.perform(
            post("/api/v1/users/me/broker-accounts")
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "9".repeat(81))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-015"));
    assertThat(cuentasDe(titular)).isEmpty();
  }

  @Test
  @DisplayName("`CA-SP-920` — administración sobre una persona inexistente o eliminada: 404")
  void personaQueNoExiste() throws Exception {
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", otra);
    for (UUID persona : List.of(UUID.randomUUID(), otra)) {
      mvc.perform(
              post("/api/v1/users/" + persona + "/broker-accounts")
                  .with(comoAdmin("broker-accounts:create"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(alta(exnova, "80000005")))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  @DisplayName(
      "`CA-SP-921`, `CA-SP-929`, `CA-SP-935` — sin el permiso de cada ruta 403; sin token 401")
  void permisos() throws Exception {
    UUID cuenta = declarar(titular, exnova, "80000006", "REGISTER");
    List<MockHttpServletRequestBuilder> propias =
        List.of(
            post("/api/v1/users/me/broker-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000007")),
            patch("/api/v1/users/me/broker-accounts/" + cuenta)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"80000008\"}"),
            delete("/api/v1/users/me/broker-accounts/" + cuenta));
    List<MockHttpServletRequestBuilder> ajenas =
        List.of(
            post("/api/v1/users/" + titular + "/broker-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000007")),
            patch("/api/v1/users/" + titular + "/broker-accounts/" + cuenta)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"80000008\"}"),
            delete("/api/v1/users/" + titular + "/broker-accounts/" + cuenta));

    // El propio no abre la ruta ajena, ni el amplio la propia (`RN-SEG-014`).
    for (MockHttpServletRequestBuilder p : propias) {
      mvc.perform(
              p.with(
                  comoAdmin(
                      "broker-accounts:create",
                      "broker-accounts:update",
                      "broker-accounts:delete")))
          .andExpect(status().isForbidden());
    }
    for (MockHttpServletRequestBuilder p : ajenas) {
      mvc.perform(p.with(comoTitular())).andExpect(status().isForbidden());
    }
    mvc.perform(post("/api/v1/users/me/broker-accounts")).andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/users/me/broker-accounts/" + cuenta))
        .andExpect(status().isUnauthorized());
    assertThat(cuentasDe(titular)).containsExactly("80000006");
  }

  @Test
  @DisplayName("`CA-SP-922` — el alta queda auditada, con quién la hizo")
  void altaAuditada() throws Exception {
    String cuerpo =
        mvc.perform(
                post("/api/v1/users/me/broker-accounts")
                    .with(comoTitular())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(alta(exnova, "80000009")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(JsonPath.read(cuerpo, "$.id"));

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT action, actor_id, changes::text AS changes FROM audit_change_log"
                + " WHERE entity = 'user_brokers' AND entity_id = ?",
            id);
    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat(fila.get("actor_id")).isEqualTo(titular);
    assertThat((String) fila.get("changes")).contains("80000009").contains("REGISTER");
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-080` — corregir
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-923` — el titular corrige el identificador; broker, estado y nombre quedan")
  void elTitularCorrige() throws Exception {
    UUID cuenta = declarar(titular, exnova, "80000010", "REGISTER");

    mvc.perform(
            patch("/api/v1/users/me/broker-accounts/" + cuenta)
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\" 80000011 \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(cuenta.toString()))
        .andExpect(jsonPath("$.accountId").value("80000011"))
        .andExpect(jsonPath("$.broker.name").value("EXNOVA"))
        .andExpect(jsonPath("$.status").value("REGISTER"));
    assertThat(cuentasDe(titular)).containsExactly("80000011");
  }

  @Test
  @DisplayName("`CA-SP-924`, `CA-SP-932` — el titular no corrige ni borra una cuenta con depósito")
  void elTitularNoTocaLaConDeposito() throws Exception {
    UUID cuenta = declarar(titular, exnova, "80000012", "FIRST_DEPOSIT");

    mvc.perform(
            patch("/api/v1/users/me/broker-accounts/" + cuenta)
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"80000013\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-010"));
    mvc.perform(delete("/api/v1/users/me/broker-accounts/" + cuenta).with(comoTitular()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-010"));
    assertThat(cuentasDe(titular)).containsExactly("80000012");
  }

  @Test
  @DisplayName("`CA-SP-925` — administración corrige la de otra persona, también con depósito")
  void administracionCorrige() throws Exception {
    UUID cuenta = declarar(titular, exnova, "80000014", "FIRST_DEPOSIT");
    jdbc.update("UPDATE user_brokers SET broker_username = 'cperez' WHERE id = ?", cuenta);

    mvc.perform(
            patch("/api/v1/users/" + titular + "/broker-accounts/" + cuenta)
                .with(comoAdmin("broker-accounts:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"80000015\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value("80000015"))
        .andExpect(jsonPath("$.status").value("FIRST_DEPOSIT"))
        .andExpect(jsonPath("$.brokerUsername").value("cperez"));
  }

  @Test
  @DisplayName("`CA-SP-926` — un identificador ya declarado en ese broker: 409 y nada cambia")
  void correccionQueChoca() throws Exception {
    declarar(otra, exnova, "80000016", "REGISTER");
    UUID cuenta = declarar(titular, exnova, "80000017", "REGISTER");

    mvc.perform(
            patch("/api/v1/users/me/broker-accounts/" + cuenta)
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"80000016\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-009"));
    assertThat(cuentasDe(titular)).containsExactly("80000017");
  }

  @Test
  @DisplayName("`CA-SP-927`, `CA-SP-934` — la cuenta ajena, inexistente o de otra persona: 404")
  void cuentaQueNoEsDeEsaPersona() throws Exception {
    UUID ajena = declarar(otra, exnova, "80000018", "REGISTER");

    // El titular sobre la de otra persona, por la ruta propia.
    mvc.perform(
            patch("/api/v1/users/me/broker-accounts/" + ajena)
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"80000019\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/users/me/broker-accounts/" + ajena).with(comoTitular()))
        .andExpect(status().isNotFound());
    // Administración con la persona equivocada en la ruta, o una cuenta inexistente.
    mvc.perform(
            delete("/api/v1/users/" + titular + "/broker-accounts/" + ajena)
                .with(comoAdmin("broker-accounts:delete")))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/users/" + otra + "/broker-accounts/" + UUID.randomUUID())
                .with(comoAdmin("broker-accounts:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"80000019\"}"))
        .andExpect(status().isNotFound());
    assertThat(cuentasDe(otra)).containsExactly("80000018");
  }

  @Test
  @DisplayName("`CA-SP-928` — sin identificador, en blanco o de más de 80: 400")
  void validacionesDeLaCorreccion() throws Exception {
    UUID cuenta = declarar(titular, exnova, "80000020", "REGISTER");
    for (String cuerpo :
        List.of("{}", "{\"accountId\":\"  \"}", "{\"accountId\":\"" + "9".repeat(81) + "\"}")) {
      mvc.perform(
              patch("/api/v1/users/me/broker-accounts/" + cuenta)
                  .with(comoTitular())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(cuerpo))
          .andExpect(status().isBadRequest());
    }
    assertThat(cuentasDe(titular)).containsExactly("80000020");
  }

  @Test
  @DisplayName("`CA-SP-930` — la corrección queda auditada, antes y después; la misma no escribe")
  void correccionAuditada() throws Exception {
    UUID cuenta = declarar(titular, exnova, "80000021", "REGISTER");

    // `FA-001`: el mismo identificador, sin auditoría.
    mvc.perform(
            patch("/api/v1/users/me/broker-accounts/" + cuenta)
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"80000021\"}"))
        .andExpect(status().isOk());
    assertThat(auditoriasDeCambio(cuenta)).isEmpty();

    mvc.perform(
            patch("/api/v1/users/me/broker-accounts/" + cuenta)
                .with(comoTitular())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"80000022\"}"))
        .andExpect(status().isOk());
    assertThat(auditoriasDeCambio(cuenta))
        .singleElement()
        .satisfies(c -> assertThat(c).contains("80000021").contains("80000022"));
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-081` — borrar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-931` — el titular borra la suya: 204, y la cuenta se puede volver a declarar")
  void elTitularBorra() throws Exception {
    UUID cuenta = declarar(titular, exnova, "80000023", "REGISTER");

    mvc.perform(delete("/api/v1/users/me/broker-accounts/" + cuenta).with(comoTitular()))
        .andExpect(status().isNoContent());
    assertThat(cuentasDe(titular)).isEmpty();

    mvc.perform(
            post("/api/v1/users/" + otra + "/broker-accounts")
                .with(comoAdmin("broker-accounts:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(alta(exnova, "80000023")))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("`CA-SP-933`, `CA-SP-936` — administración borra con depósito, y queda auditado")
  void administracionBorraYQuedaAuditado() throws Exception {
    UUID cuenta = declarar(titular, exnova, "80000024", "FIRST_DEPOSIT");

    mvc.perform(
            delete("/api/v1/users/" + titular + "/broker-accounts/" + cuenta)
                .with(comoAdmin("broker-accounts:delete")))
        .andExpect(status().isNoContent());
    assertThat(cuentasDe(titular)).isEmpty();

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT deletion_type, reason, snapshot::text AS snapshot FROM audit_deletion_log"
                + " WHERE entity = 'user_brokers' AND entity_id = ?",
            cuenta);
    assertThat(fila.get("deletion_type")).isEqualTo("PHYSICAL");
    assertThat((String) fila.get("reason")).contains("administración");
    assertThat((String) fila.get("snapshot"))
        .contains("80000024")
        .contains("EXNOVA")
        .contains("FIRST_DEPOSIT")
        .contains("CONSUMIDOR");
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private RequestPostProcessor comoTitular() {
    return user(titular.toString())
        .authorities(
            () -> "broker-accounts:create-own",
            () -> "broker-accounts:update-own",
            () -> "broker-accounts:delete-own");
  }

  private static RequestPostProcessor comoAdmin(String... permisos) {
    return user(SUPERADMIN.toString())
        .authorities(
            java.util.Arrays.stream(permisos)
                .map(p -> (org.springframework.security.core.GrantedAuthority) () -> p)
                .toList());
  }

  private static String alta(UUID broker, String cuenta) {
    return "{\"brokerId\":\"" + broker + "\",\"accountId\":\"" + cuenta + "\"}";
  }

  private List<String> cuentasDe(UUID persona) {
    return jdbc.queryForList(
        "SELECT external_id FROM user_brokers WHERE user_id = ? ORDER BY external_id",
        String.class,
        persona);
  }

  private List<String> auditoriasDeCambio(UUID cuenta) {
    return jdbc.queryForList(
        "SELECT changes::text FROM audit_change_log WHERE entity = 'user_brokers'"
            + " AND entity_id = ? AND action = 'UPDATE'",
        String.class,
        cuenta);
  }

  private UUID declarar(UUID persona, UUID broker, String cuenta, String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO user_brokers (id, user_id, broker_id, external_id, status, kind)
        VALUES (?, ?, ?, ?, ?,
                CASE WHEN EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = ?
                              AND ur.role_type = 'VENDEDOR')
                THEN 'VENDEDOR' ELSE 'CONSUMIDOR' END)
        """,
        id,
        persona,
        broker,
        cuenta,
        estado,
        persona);
    return id;
  }

  private UUID crearPersona(String username, String rol) {
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
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        id,
        rol);
    return id;
  }

  private UUID brokerAsegurado(String id, String nombre) {
    jdbc.update(
        "INSERT INTO brokers (id, name) VALUES (CAST(? AS uuid), ?) ON CONFLICT DO NOTHING",
        id,
        nombre);
    return jdbc.queryForObject("SELECT id FROM brokers WHERE name = ?", UUID.class, nombre);
  }
}
