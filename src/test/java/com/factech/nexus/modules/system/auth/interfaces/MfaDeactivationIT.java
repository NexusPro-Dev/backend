package com.factech.nexus.modules.system.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.auth.domain.service.RecoveryCodeIssuer;
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

/** `RF-SP-075` · `T-03`: desactivar el propio segundo factor, `CA-SP-859` a `CA-SP-868`. */
@AutoConfigureMockMvc
class MfaDeactivationIT extends IntegrationTestBase {

  private static final String ROL = "MFA_DESACTIVA_PRUEBA";
  private static final String USUARIO = "mfadesactiva";
  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String DESACTIVAR = "/api/v1/users/me/mfa/deactivation";
  private static final String TIPO_REVERIFICAR =
      "https://nexus.factech.co/errors/reverificacion-requerida";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;
  @Autowired private MfaSecrets secretos;
  @Autowired private AccessTokenIssuer tokens;

  private UUID persona;
  private UUID rol;
  private String secreto;

  @BeforeEach
  void preparar() {
    limpiar();
    rol = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, 'Desactivar de prueba', 'FUNCIONARIO',
                '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', false)
        """,
        rol,
        ROL);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions
         WHERE code IN ('users:disable-own-mfa', 'users:verify-own-mfa')
        """,
        rol);
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, 'mfa.desactiva@factech.co', 'Dora', 'Retira', ?, false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        persona,
        USUARIO,
        hasher.hash(CLAVE));
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) VALUES (?, ?, 'FUNCIONARIO')",
        persona,
        rol);
  }

  @AfterEach
  void limpiarAlTerminar() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM refresh_tokens WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", ROL);
    jdbc.update("DELETE FROM roles WHERE code = ?", ROL);
  }

  @Test
  @DisplayName("`CA-SP-859` — con verificación reciente y la contraseña, el factor se retira")
  void seDesactiva() throws Exception {
    sembrarFactor();

    JsonNode sesion = cuerpo(desactivar(reciente(), CLAVE).andExpect(status().isOk()));

    assertThat(sesion.get("accessToken").asText()).isNotBlank();
    assertThat(sesion.get("refreshToken").asText()).isNotBlank();
    assertThat(estados()).containsExactly("RETIRADO");
    JsonNode entrada =
        cuerpo(
            mvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales()))
                .andExpect(status().isOk()));
    assertThat(entrada.get("mfaRequired").asBoolean()).isFalse();
    assertThat(entrada.get("accessToken").asText()).isNotBlank();
  }

  @Test
  @DisplayName("`CA-SP-860` — los códigos y un pendiente, si lo había, dejan de servir")
  void codigosYPendiente() throws Exception {
    String codigo = sembrarFactor();
    jdbc.update(
        """
        INSERT INTO user_mfa_factors (id, user_id, secret_ciphertext, status, pending_expires_at)
        VALUES (?, ?, ?, 'PENDIENTE', now() + interval '10 minutes')
        """,
        UUID.randomUUID(),
        persona,
        secretos.cifrar(Totp.nuevoSecreto(), persona));

    desactivar(reciente(), CLAVE).andExpect(status().isOk());

    assertThat(estados()).containsOnly("RETIRADO").hasSize(2);
    mvc.perform(
            post("/api/v1/auth/mfa/verification")
                .header("Authorization", "Bearer " + token(null))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recoveryCode\":\"" + codigo + "\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "`CA-SP-861` — las demás sesiones quedan cerradas; este dispositivo sigue, con la nueva")
  void lasDemasSesionesSeCierran() throws Exception {
    sembrarFactor();
    JsonNode una = iniciarSesion(0);
    JsonNode otra = iniciarSesion(1);

    JsonNode nueva =
        cuerpo(desactivar(una.get("accessToken").asText(), CLAVE).andExpect(status().isOk()));

    refrescar(una.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    refrescar(otra.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    refrescar(nueva.get("refreshToken").asText()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-SP-862` — si un rol activo suyo lo exige, conflicto y nada cambia")
  void suRolLoExige() throws Exception {
    sembrarFactor();
    jdbc.update("UPDATE roles SET requires_mfa = true WHERE id = ?", rol);

    desactivar(reciente(), CLAVE).andExpect(status().isConflict());

    assertThat(estados()).containsExactly("ACTIVO");
  }

  @Test
  @DisplayName(
      "`CA-SP-863` — con la contraseña incorrecta, 422, nada cambia y cuenta para el bloqueo")
  void contrasenaIncorrecta() throws Exception {
    sembrarFactor();

    desactivar(reciente(), "NoEsLaClave2026xx")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    assertThat(estados()).containsExactly("ACTIVO");
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_attempts FROM users WHERE id = ?", Integer.class, persona))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-SP-864` — el fallo que bloquea la cuenta cierra todas sus sesiones")
  void bloquearCierraTodo() throws Exception {
    sembrarFactor();
    JsonNode sesion = iniciarSesion(0);
    jdbc.update("UPDATE users SET failed_attempts = 4 WHERE id = ?", persona);

    desactivar(reciente(), "NoEsLaClave2026xx").andExpect(status().isLocked());

    refrescar(sesion.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    assertThat(estados()).containsExactly("ACTIVO");
  }

  @Test
  @DisplayName("`CA-SP-865` — sin verificación reciente, prohibido con el aviso de reverificación")
  void sinVerificacionReciente() throws Exception {
    sembrarFactor();

    desactivar(token(Instant.now().minus(Duration.ofMinutes(6))), CLAVE)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_REVERIFICAR));
    assertThat(estados()).containsExactly("ACTIVO");
  }

  @Test
  @DisplayName("`CA-SP-866` — sin factor activo, conflicto")
  void sinFactor() throws Exception {
    desactivar(reciente(), CLAVE).andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "`CA-SP-867` — evento de severidad alta, y el factor sigue en el historial con su motivo")
  void seAuditaYQuedaElHistorial() throws Exception {
    sembrarFactor();

    desactivar(reciente(), CLAVE).andExpect(status().isOk());

    Map<String, Object> factor =
        jdbc.queryForMap(
            "SELECT status, retired_reason FROM user_mfa_factors WHERE user_id = ?", persona);
    assertThat(factor)
        .containsEntry("status", "RETIRADO")
        .containsEntry("retired_reason", "DESACTIVADO");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT severity FROM audit_security_log
                 WHERE event_type = 'MFA_DISABLED' AND outcome = 'SUCCESS' AND target_user_id = ?
                """,
                String.class,
                persona))
        .isEqualTo("ALTA");
  }

  @Test
  @DisplayName("`CA-SP-868` — sin el permiso, prohibido; sin autenticar, 401")
  void autorizacion() throws Exception {
    mvc.perform(post(DESACTIVAR).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());

    jdbc.update(
        """
        DELETE FROM role_permissions
         WHERE role_id = ?
           AND permission_id = (SELECT id FROM permissions WHERE code = 'users:disable-own-mfa')
        """,
        rol);
    desactivar(reciente(), CLAVE).andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------

  /** Un factor activo con un código de recuperación conocido; devuelve el código. */
  private String sembrarFactor() {
    secreto = Totp.nuevoSecreto();
    UUID factor = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO user_mfa_factors (id, user_id, secret_ciphertext, status, confirmed_at)
        VALUES (?, ?, ?, 'ACTIVO', now())
        """,
        factor,
        persona,
        secretos.cifrar(secreto, persona));
    String codigo = "STVWX-40000";
    jdbc.update(
        "INSERT INTO mfa_recovery_codes (id, factor_id, code_hash) VALUES (?, ?, ?)",
        UUID.randomUUID(),
        factor,
        hasher.hash(RecoveryCodeIssuer.normalizar(codigo)));
    return codigo;
  }

  private String credenciales() {
    return "{\"identifier\":\"" + USUARIO + "\",\"password\":\"" + CLAVE + "\"}";
  }

  /** Una sesión real por los dos pasos; {@code periodo} desplaza el código para no repetirlo. */
  private JsonNode iniciarSesion(int periodo) throws Exception {
    JsonNode desafio =
        cuerpo(
            mvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales()))
                .andExpect(status().isOk()));
    String codigo = Totp.codigo(secreto, Totp.periodo(Instant.now()) + periodo);
    return cuerpo(
        mvc.perform(
                post("/api/v1/auth/login/mfa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"challengeToken\":\""
                            + desafio.get("challengeToken").asText()
                            + "\",\"code\":\""
                            + codigo
                            + "\"}"))
            .andExpect(status().isOk()));
  }

  private ResultActions desactivar(String token, String clave) throws Exception {
    return mvc.perform(
        post(DESACTIVAR)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"" + clave + "\"}"));
  }

  private ResultActions refrescar(String refresco) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"" + refresco + "\"}"));
  }

  private String reciente() {
    return token(Instant.now());
  }

  private String token(Instant mfa) {
    return tokens.emitir(persona, List.of(ROL), false, false, mfa, Instant.now());
  }

  private List<String> estados() {
    return jdbc.queryForList(
        "SELECT status FROM user_mfa_factors WHERE user_id = ? ORDER BY created_at",
        String.class,
        persona);
  }

  private JsonNode cuerpo(ResultActions resultado) throws Exception {
    return json.readTree(resultado.andReturn().getResponse().getContentAsString());
  }
}
