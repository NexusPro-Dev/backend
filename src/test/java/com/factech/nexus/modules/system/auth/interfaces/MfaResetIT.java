package com.factech.nexus.modules.system.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * `RF-SP-076` · `T-04`: restablecer el segundo factor de otra persona, `CA-SP-869` a `CA-SP-879`; y
 * la enmienda de `RN-SP-065` a `RF-SP-038`, `CA-SP-894` y `CA-SP-895`.
 */
@AutoConfigureMockMvc
class MfaResetIT extends IntegrationTestBase {

  private static final String ACTOR = "MFA_RESTABLECE_ACTOR";
  private static final String OBJETIVO = "MFA_RESTABLECE_OBJETIVO";
  private static final String ADMIN_ROL = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String MOTIVO = "Perdió el teléfono y los códigos; identidad comprobada.";
  private static final String TIPO_REVERIFICAR =
      "https://nexus.factech.co/errors/reverificacion-requerida";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;
  @Autowired private MfaSecrets secretos;
  @Autowired private AccessTokenIssuer tokens;

  private UUID actor;
  private UUID persona;
  private UUID rolObjetivo;

  @BeforeEach
  void preparar() {
    limpiar();
    UUID rolActor = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, 'Quien restablece', 'FUNCIONARIO', ?::uuid, 'ACTIVO', false)
        """,
        rolActor,
        ACTOR,
        ADMIN_ROL);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions
         WHERE code IN ('users:reset-mfa', 'users:reset-password', 'audit:read-changes')
        """,
        rolActor);
    rolObjetivo = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, 'A quien se restablece', 'FUNCIONARIO', ?, 'ACTIVO', false)
        """,
        rolObjetivo,
        OBJETIVO,
        rolActor);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions WHERE code = 'audit:read-changes'
        """,
        rolObjetivo);
    actor = persona("mfarestablece", "mfa.restablece@factech.co", rolActor);
    persona = persona("mfarestablecida", "mfa.restablecida@factech.co", rolObjetivo);
  }

  @AfterEach
  void limpiarAlTerminar() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM user_mfa_factors WHERE user_id = ?", SUPERADMIN);
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
  // Restablecer el segundo factor
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-869` y `CA-SP-870` — activo y pendiente, retirados; entrar pide solo la contraseña")
  void seRestablece() throws Exception {
    sembrarFactor(persona, "ACTIVO");
    sembrarFactor(persona, "PENDIENTE");

    restablecer(persona, MOTIVO).andExpect(status().isNoContent());

    assertThat(
            jdbc.queryForList(
                "SELECT retired_reason FROM user_mfa_factors WHERE user_id = ?",
                String.class,
                persona))
        .containsExactly("RESTABLECIDO", "RESTABLECIDO");
    JsonNode entrada = login("mfarestablecida");
    assertThat(entrada.get("mfaRequired").asBoolean()).isFalse();
    assertThat(entrada.get("accessToken").asText()).isNotBlank();
  }

  @Test
  @DisplayName("`CA-SP-871` — todas las sesiones de la persona quedan cerradas")
  void cierraSusSesiones() throws Exception {
    String refresco = login("mfarestablecida").get("refreshToken").asText();
    sembrarFactor(persona, "ACTIVO");

    restablecer(persona, MOTIVO).andExpect(status().isNoContent());

    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresco + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("`CA-SP-872` — si su rol lo exige, su siguiente inicio de sesión entra retenido")
  void quedaRetenida() throws Exception {
    sembrarFactor(persona, "ACTIVO");
    jdbc.update("UPDATE roles SET requires_mfa = true WHERE id = ?", rolObjetivo);

    restablecer(persona, MOTIVO).andExpect(status().isNoContent());

    assertThat(login("mfarestablecida").get("mfaEnrollmentRequired").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("`CA-SP-873` — sobre uno mismo, prohibido y nada cambia")
  void unoMismo() throws Exception {
    sembrarFactor(actor, "ACTIVO");

    restablecer(actor, MOTIVO).andExpect(status().isForbidden());

    assertThat(estadoDe(actor)).isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName("`CA-SP-874` — sobre quien tiene más privilegios (el superadministrador), prohibido")
  void contencion() throws Exception {
    sembrarFactor(SUPERADMIN, "ACTIVO");

    restablecer(SUPERADMIN, MOTIVO).andExpect(status().isForbidden());

    assertThat(estadoDe(SUPERADMIN)).isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName("`CA-SP-875` — inexistente, no encontrada; sin factor, conflicto")
  void inexistenteYSinFactor() throws Exception {
    restablecer(UUID.randomUUID(), MOTIVO).andExpect(status().isNotFound());
    restablecer(persona, MOTIVO).andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "`CA-SP-876` — sin motivo, en blanco o de más de 500 caracteres: rechazo y nada cambia")
  void motivo() throws Exception {
    sembrarFactor(persona, "ACTIVO");

    restablecer(persona, null).andExpect(status().isBadRequest());
    restablecer(persona, "   ").andExpect(status().isBadRequest());
    restablecer(persona, "x".repeat(501)).andExpect(status().isBadRequest());

    assertThat(estadoDe(persona)).isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName("`CA-SP-877` — sin verificación reciente, prohibido con el aviso de reverificación")
  void sinVerificacionReciente() throws Exception {
    sembrarFactor(persona, "ACTIVO");

    restablecerCon(token(actor, ACTOR, Instant.now().minus(Duration.ofMinutes(6))), persona)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_REVERIFICAR));
    assertThat(estadoDe(persona)).isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName(
      "`CA-SP-878` — evento alto con la persona como objeto, y el motivo en la eliminación")
  void seAudita() throws Exception {
    UUID factor = sembrarFactor(persona, "ACTIVO");

    restablecer(persona, MOTIVO).andExpect(status().isNoContent());

    Map<String, Object> evento =
        jdbc.queryForMap(
            """
            SELECT severity, actor_id FROM audit_security_log
             WHERE event_type = 'MFA_RESET' AND target_user_id = ?
            """,
            persona);
    assertThat(evento).containsEntry("severity", "ALTA").containsEntry("actor_id", actor);
    Map<String, Object> eliminacion =
        jdbc.queryForMap(
            """
            SELECT reason, snapshot::text AS instantanea FROM audit_deletion_log
             WHERE entity = 'user_mfa_factors' AND entity_id = ?
            """,
            factor);
    assertThat(eliminacion).containsEntry("reason", MOTIVO);
    assertThat((String) eliminacion.get("instantanea")).doesNotContain("v1:");
  }

  @Test
  @DisplayName("`CA-SP-879` — sin el permiso, prohibido; sin autenticar, 401")
  void autorizacion() throws Exception {
    mvc.perform(
            post("/api/v1/users/" + persona + "/mfa/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());

    jdbc.update(
        """
        DELETE FROM role_permissions
         WHERE role_id = (SELECT id FROM roles WHERE code = ?)
           AND permission_id = (SELECT id FROM permissions WHERE code = 'users:reset-mfa')
        """,
        ACTOR);
    restablecer(persona, MOTIVO).andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // `RN-SP-065` en `RF-SP-038`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-894` — no se restablece la contraseña de quien tiene más privilegios")
  void contrasenaConContencion() throws Exception {
    String antes =
        jdbc.queryForObject(
            "SELECT password_hash FROM users WHERE id = ?", String.class, SUPERADMIN);

    restablecerContrasena(token(actor, ACTOR, Instant.now()), SUPERADMIN)
        .andExpect(status().isForbidden());

    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, SUPERADMIN))
        .isEqualTo(antes);
  }

  @Test
  @DisplayName(
      "`CA-SP-895` — el superadministrador sí la de un ADMIN; y cualquiera, la de quien abarca")
  void contrasenaSinExceso() throws Exception {
    UUID administrador = persona("mfaadmin", "mfa.admin@factech.co", UUID.fromString(ADMIN_ROL));

    restablecerContrasena(token(SUPERADMIN, "SUPERADMIN", Instant.now()), administrador)
        .andExpect(status().isNoContent());
    restablecerContrasena(token(actor, ACTOR, Instant.now()), persona)
        .andExpect(status().isNoContent());
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
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, id, role_type FROM roles WHERE id = ?",
        id,
        rol);
    return id;
  }

  private UUID sembrarFactor(UUID quien, String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO user_mfa_factors (id, user_id, secret_ciphertext, status, confirmed_at,
                                      pending_expires_at)
        VALUES (?, ?, ?, ?, CASE WHEN ? = 'ACTIVO' THEN now() END,
                CASE WHEN ? = 'PENDIENTE' THEN now() + interval '10 minutes' END)
        """,
        id,
        quien,
        secretos.cifrar(Totp.nuevoSecreto(), quien),
        estado,
        estado,
        estado);
    return id;
  }

  private String estadoDe(UUID quien) {
    return jdbc.queryForObject(
        "SELECT status FROM user_mfa_factors WHERE user_id = ?", String.class, quien);
  }

  private JsonNode login(String usuario) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"identifier\":\"" + usuario + "\",\"password\":\"" + CLAVE + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String token(UUID quien, String rol, Instant mfa) {
    return tokens.emitir(quien, List.of(rol), false, false, mfa, Instant.now());
  }

  private ResultActions restablecer(UUID quien, String motivo) throws Exception {
    return restablecerCon(token(actor, ACTOR, Instant.now()), quien, motivo);
  }

  private ResultActions restablecerCon(String token, UUID quien) throws Exception {
    return restablecerCon(token, quien, MOTIVO);
  }

  private ResultActions restablecerCon(String token, UUID quien, String motivo) throws Exception {
    String cuerpo = motivo == null ? "{}" : json.writeValueAsString(Map.of("reason", motivo));
    return mvc.perform(
        post("/api/v1/users/" + quien + "/mfa/reset")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions restablecerContrasena(String token, UUID quien) throws Exception {
    return mvc.perform(
        post("/api/v1/users/" + quien + "/password-reset")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newPassword\":\"OtraClaveLargaSegura2026\"}"));
  }
}
