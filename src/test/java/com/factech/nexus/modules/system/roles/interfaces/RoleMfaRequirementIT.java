package com.factech.nexus.modules.system.roles.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.auth.infrastructure.MfaSecrets;
import com.factech.nexus.shared.security.AccessTokenIssuer;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.shared.security.Totp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * `RF-SP-077` · `T-06`: exigir el segundo factor a los portadores de un rol, `CA-SP-880` a
 * `CA-SP-891` y `CA-SP-893`. `CA-SP-892` —el perfil— vive en `MfaLoginIT` desde `RF-SP-072`.
 */
@AutoConfigureMockMvc
class RoleMfaRequirementIT extends IntegrationTestBase {

  private static final String ACTOR = "MFA_EXIGE_ACTOR";
  private static final String OBJETIVO = "MFA_EXIGE_OBJETIVO";
  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String TIPO_ACTIVACION =
      "https://nexus.factech.co/errors/activacion-de-segundo-factor-requerida";
  private static final String TIPO_REVERIFICAR =
      "https://nexus.factech.co/errors/reverificacion-requerida";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;
  @Autowired private MfaSecrets secretos;
  @Autowired private AccessTokenIssuer tokens;

  private UUID actor;
  private UUID rolActor;
  private UUID objetivo;
  private UUID persona;

  @BeforeEach
  void preparar() {
    limpiar();
    rolActor = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, 'Quien exige', 'FUNCIONARIO',
                '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', false)
        """,
        rolActor,
        ACTOR);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions
         WHERE code IN ('roles:require-mfa', 'roles:list', 'roles:read', 'audit:read-changes')
        """,
        rolActor);
    objetivo = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, 'A quien se exige', 'FUNCIONARIO', ?, 'ACTIVO', false)
        """,
        objetivo,
        OBJETIVO,
        rolActor);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions WHERE code = 'audit:read-changes'
        """,
        objetivo);
    actor = persona("mfaexige", "mfa.exige@factech.co", rolActor);
    persona = persona("mfaexigida", "mfa.exigida@factech.co", objetivo);
  }

  @AfterEach
  void limpiarAlTerminar() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("UPDATE roles SET requires_mfa = false WHERE code = 'CLIENTE'");
    jdbc.update("DELETE FROM refresh_tokens WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    for (String codigo : List.of(OBJETIVO, ACTOR)) {
      jdbc.update(
          "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)",
          codigo);
      jdbc.update("DELETE FROM roles WHERE code = ?", codigo);
    }
  }

  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-880` — marcar retiene a sus portadores sin factor en la siguiente renovación")
  void marcarRetiene() throws Exception {
    JsonNode sesion = login();
    String antes = sesion.get("accessToken").asText();

    exigir(objetivo, true).andExpect(status().isOk());

    // La sesión no se cierra: el token de antes sigue sirviendo hasta renovar.
    leer(antes).andExpect(status().isOk());
    JsonNode renovada = refrescar(sesion);
    assertThat(renovada.get("mfaEnrollmentRequired").asBoolean()).isTrue();
    leer(renovada.get("accessToken").asText())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_ACTIVACION));
  }

  @Test
  @DisplayName("`CA-SP-881` — desmarcar los libera en la siguiente renovación")
  void desmarcarLibera() throws Exception {
    exigir(objetivo, true).andExpect(status().isOk());
    JsonNode sesion = login();
    assertThat(sesion.get("mfaEnrollmentRequired").asBoolean()).isTrue();

    exigir(objetivo, false).andExpect(status().isOk());

    JsonNode renovada = refrescar(sesion);
    assertThat(renovada.get("mfaEnrollmentRequired").asBoolean()).isFalse();
    leer(renovada.get("accessToken").asText()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-SP-882` — desmarcar la raíz se rechaza y nada cambia")
  void laRaiz() throws Exception {
    exigir(UUID.fromString(SUPERADMIN_ROL), false)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    assertThat(marca(UUID.fromString(SUPERADMIN_ROL))).isTrue();
  }

  @Test
  @DisplayName("`CA-SP-883` — pedir el valor que ya tiene responde igual, sin escribir ni auditar")
  void sinCambioNoEscribe() throws Exception {
    exigir(objetivo, true).andExpect(status().isOk());
    exigir(objetivo, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requiresMfa").value(true));

    assertThat(eventos()).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-SP-884` — se admite sobre un rol de sistema que el actor no porta")
  void rolDeSistema() throws Exception {
    UUID cliente = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'CLIENTE'", UUID.class);

    exigir(cliente, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requiresMfa").value(true));

    assertThat(marca(cliente)).isTrue();
  }

  @Test
  @DisplayName("`CA-SP-885` — sobre un rol que el actor porta, prohibido y nada cambia")
  void rolPropio() throws Exception {
    exigir(rolActor, true).andExpect(status().isForbidden());

    assertThat(marca(rolActor)).isFalse();
  }

  @Test
  @DisplayName("`CA-SP-886` — un rol inexistente responde no encontrado")
  void inexistente() throws Exception {
    exigir(UUID.randomUUID(), true).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`CA-SP-887` — un rol inactivo se marca y no retiene a nadie")
  void rolInactivo() throws Exception {
    jdbc.update("UPDATE roles SET status = 'INACTIVO' WHERE id = ?", objetivo);

    exigir(objetivo, true).andExpect(status().isOk());

    assertThat(login().get("mfaEnrollmentRequired").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("`CA-SP-888` — la respuesta dice cuántos lo portan y cuántos no tienen el factor")
  void aQuienAfecta() throws Exception {
    exigir(objetivo, true)
        .andExpect(jsonPath("$.activeHolders").value(1))
        .andExpect(jsonPath("$.holdersWithoutMfa").value(1));

    jdbc.update(
        """
        INSERT INTO user_mfa_factors (id, user_id, secret_ciphertext, status, confirmed_at)
        VALUES (?, ?, ?, 'ACTIVO', now())
        """,
        UUID.randomUUID(),
        persona,
        secretos.cifrar(Totp.nuevoSecreto(), persona));

    exigir(objetivo, true)
        .andExpect(jsonPath("$.activeHolders").value(1))
        .andExpect(jsonPath("$.holdersWithoutMfa").value(0));
  }

  @Test
  @DisplayName("`CA-SP-889` — se audita alto en seguridad, y en cambios con el antes y el después")
  void seAudita() throws Exception {
    exigir(objetivo, true).andExpect(status().isOk());

    assertThat(eventos()).isEqualTo(1);
    String cambios =
        jdbc.queryForObject(
            """
            SELECT changes::text FROM audit_change_log
             WHERE entity = 'roles' AND entity_id = ? AND changes -> 'requires_mfa' IS NOT NULL
            """,
            String.class,
            objetivo);
    assertThat(cambios).contains("\"before\": false").contains("\"after\": true");
  }

  @Test
  @DisplayName("`CA-SP-890` — sin verificación reciente, prohibido con el aviso de reverificación")
  void sinVerificacionReciente() throws Exception {
    exigirCon(token(Instant.now().minus(Duration.ofMinutes(6))), objetivo, true)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_REVERIFICAR));
    assertThat(marca(objetivo)).isFalse();
  }

  @Test
  @DisplayName(
      "`CA-SP-891` — el listado y el detalle muestran la marca; SUPERADMIN y ADMIN la traen")
  void listadoYDetalle() throws Exception {
    exigir(objetivo, true).andExpect(status().isOk());

    mvc.perform(get("/api/v1/roles/" + objetivo).header("Authorization", "Bearer " + token(null)))
        .andExpect(jsonPath("$.requiresMfa").value(true));
    mvc.perform(
            get("/api/v1/roles/" + SUPERADMIN_ROL).header("Authorization", "Bearer " + token(null)))
        .andExpect(jsonPath("$.requiresMfa").value(true));
    JsonNode listado =
        json.readTree(
            mvc.perform(
                    get("/api/v1/roles")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + token(null)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    listado
        .get("content")
        .forEach(
            rol -> {
              assertThat(rol.has("requiresMfa")).isTrue();
              String codigo = rol.get("code").asText();
              if (codigo.equals("ADMIN") || codigo.equals("SUPERADMIN")) {
                assertThat(rol.get("requiresMfa").asBoolean()).as(codigo).isTrue();
              }
            });
  }

  @Test
  @DisplayName("`CA-SP-893` — sin el permiso, prohibido; sin autenticar, 401")
  void autorizacion() throws Exception {
    mvc.perform(
            patch("/api/v1/roles/" + objetivo + "/mfa-requirement")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"required\":true}"))
        .andExpect(status().isUnauthorized());

    jdbc.update(
        """
        DELETE FROM role_permissions
         WHERE role_id = ?
           AND permission_id = (SELECT id FROM permissions WHERE code = 'roles:require-mfa')
        """,
        rolActor);
    exigir(objetivo, true).andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------

  private UUID persona(String usuario, String correo, UUID rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', ?, false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        usuario,
        correo,
        hasher.hash(CLAVE));
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) VALUES (?, ?, 'FUNCIONARIO')",
        id,
        rol);
    return id;
  }

  private String token(Instant mfa) {
    return tokens.emitir(actor, List.of(ACTOR), false, false, mfa, Instant.now());
  }

  private ResultActions exigir(UUID rol, boolean exigido) throws Exception {
    return exigirCon(token(Instant.now()), rol, exigido);
  }

  private ResultActions exigirCon(String token, UUID rol, boolean exigido) throws Exception {
    return mvc.perform(
        patch("/api/v1/roles/" + rol + "/mfa-requirement")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"required\":" + exigido + "}"));
  }

  private JsonNode login() throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"identifier\":\"mfaexigida\",\"password\":\"" + CLAVE + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode refrescar(JsonNode sesion) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"" + sesion.get("refreshToken").asText() + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private ResultActions leer(String token) throws Exception {
    return mvc.perform(get("/api/v1/audit/changes").header("Authorization", "Bearer " + token));
  }

  private boolean marca(UUID rol) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject("SELECT requires_mfa FROM roles WHERE id = ?", Boolean.class, rol));
  }

  private int eventos() {
    return jdbc.queryForObject(
        """
        SELECT count(*) FROM audit_security_log
         WHERE event_type = 'ROLE_MFA_REQUIREMENT_CHANGED' AND detail ->> 'roleId' = ?
        """,
        Integer.class,
        objetivo.toString());
  }
}
