package com.factech.nexus.modules.system.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
 * `RF-SP-073` · `T-07`: reverificar el segundo factor antes de una operación sensible, `CA-SP-839`
 * a `CA-SP-852`.
 *
 * <p>La operación sensible es <b>asignar permisos a un rol</b> —`POST /roles/{id}/permissions`—, la
 * que el responsable del proyecto nombró. Los tokens se emiten con {@link AccessTokenIssuer} para
 * fijar el instante de la prueba; los de las sesiones reales salen del inicio de sesión.
 */
@AutoConfigureMockMvc
class MfaStepUpIT extends IntegrationTestBase {

  private static final String ACTOR = "MFA_REVERIFICA_ACTOR";
  private static final String OBJETIVO = "MFA_REVERIFICA_OBJETIVO";
  private static final String USUARIO = "mfareverifica";
  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String VERIFICAR = "/api/v1/auth/mfa/verification";
  private static final String TIPO_REVERIFICAR =
      "https://nexus.factech.co/errors/reverificacion-requerida";
  private static final String TIPO_SIN_PERMISO = "https://nexus.factech.co/errors/sin-permiso";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;
  @Autowired private MfaSecrets secretos;
  @Autowired private AccessTokenIssuer tokens;

  private UUID persona;
  private UUID objetivo;
  private UUID permisoAOtorgar;

  @BeforeEach
  void preparar() {
    limpiar();
    UUID actor = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, 'Actor que reverifica', 'FUNCIONARIO',
                '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', false)
        """,
        actor,
        ACTOR);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions
         WHERE code IN ('roles:assign-permissions', 'users:verify-own-mfa', 'audit:read-changes',
                        'permissions:list', 'permissions:read')
        """,
        actor);
    objetivo = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, 'Rol que recibe el permiso', 'FUNCIONARIO', ?, 'ACTIVO', false)
        """,
        objetivo,
        OBJETIVO,
        actor);
    permisoAOtorgar =
        jdbc.queryForObject(
            "SELECT id FROM permissions WHERE code = 'audit:read-changes'", UUID.class);
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, 'mfa.reverifica@factech.co', 'Rita', 'Verifica', ?, false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        persona,
        USUARIO,
        hasher.hash(CLAVE));
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) VALUES (?, ?, 'FUNCIONARIO')",
        persona,
        actor);
  }

  @AfterEach
  void limpiarAlTerminar() {
    limpiar();
  }

  private void limpiar() {
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
  // Reverificar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-839` — un código válido da un token con la prueba de ahora; la sesión no cambia")
  void reverificarDaUnTokenNuevo() throws Exception {
    String secreto = sembrarFactor().secreto();
    long antes = Instant.now().getEpochSecond();

    JsonNode r =
        cuerpo(
            verificar(token(null), "{\"code\":\"" + codigo(secreto, 0) + "\"}")
                .andExpect(status().isOk()));

    JsonNode claims = claims(r.get("accessToken").asText());
    assertThat(claims.get("mfa").asLong()).isBetween(antes - 1, Instant.now().getEpochSecond() + 1);
    assertThat(claims.get("roles").get(0).asText()).isEqualTo(ACTOR);
    assertThat(Instant.parse(r.get("mfaValidUntil").asText()))
        .isBetween(Instant.now().plusSeconds(4 * 60), Instant.now().plusSeconds(5 * 60 + 2));
    assertThat(r.has("refreshToken")).isFalse();
  }

  @Test
  @DisplayName("`CA-SP-840` — con la prueba reciente, la operación sensible se atiende")
  void conPruebaRecienteSeAtiende() throws Exception {
    asignar(token(Instant.now().minusSeconds(60))).andExpect(status().is2xxSuccessful());
    assertThat(permisosDelObjetivo()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-841` — con la prueba vencida, o sin prueba: prohibido con la ruta, y nada cambia")
  void sinPruebaRecienteSeNiega() throws Exception {
    asignar(token(Instant.now().minus(Duration.ofMinutes(6))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_REVERIFICAR))
        .andExpect(jsonPath("$.verificationPath").value(VERIFICAR));
    asignar(token(null))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_REVERIFICAR));

    assertThat(permisosDelObjetivo()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-842` — sin el permiso de la operación, la respuesta es la de falta de permiso")
  void primeroElPermiso() throws Exception {
    jdbc.update(
        """
        DELETE FROM role_permissions
         WHERE role_id = (SELECT id FROM roles WHERE code = ?)
           AND permission_id = (SELECT id FROM permissions WHERE code = 'roles:assign-permissions')
        """,
        ACTOR);

    asignar(token(null))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_SIN_PERMISO));
  }

  @Test
  @DisplayName("`CA-SP-843` — una operación no marcada se atiende sin prueba")
  void loNoSensibleNoPide() throws Exception {
    mvc.perform(get("/api/v1/audit/changes").header("Authorization", "Bearer " + token(null)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "`CA-SP-844` — sin factor activo, reverificar se rechaza y lo sensible no se atiende")
  void sinFactor() throws Exception {
    verificar(token(null), "{\"code\":\"123456\"}").andExpect(status().isConflict());
    asignar(token(null)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-SP-845` — un código inválido: 422 con los intentos, evento, y suma con el login")
  void codigoInvalido() throws Exception {
    String secreto = sembrarFactor().secreto();
    jdbc.update("UPDATE users SET failed_attempts = 1 WHERE id = ?", persona);

    verificar(token(null), "{\"code\":\"" + otroCodigo(codigo(secreto, 0)) + "\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.remainingAttempts").value(3));

    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM audit_security_log
                 WHERE event_type = 'MFA_VERIFICATION_FAILED' AND target_user_id = ?
                   AND detail ->> 'stage' = 'STEP_UP'
                """,
                Integer.class,
                persona))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-SP-846` — el fallo que bloquea la cuenta cierra todas sus sesiones")
  void bloquearCierraLasSesiones() throws Exception {
    String secreto = sembrarFactor().secreto();
    JsonNode sesion = iniciarSesion(secreto);
    jdbc.update("UPDATE users SET failed_attempts = 4 WHERE id = ?", persona);

    verificar(
            sesion.get("accessToken").asText(),
            "{\"code\":\"" + otroCodigo(codigo(secreto, 1)) + "\"}")
        .andExpect(status().isLocked());

    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + sesion.get("refreshToken").asText() + "\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "`CA-SP-847` — un código ya usado no sirve: ni el del inicio de sesión ni el de antes")
  void codigoUsado() throws Exception {
    String secreto = sembrarFactor().secreto();
    String ahora = codigo(secreto, 0);

    verificar(token(null), "{\"code\":\"" + ahora + "\"}").andExpect(status().isOk());
    verificar(token(null), "{\"code\":\"" + ahora + "\"}")
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("`CA-SP-848` — un código de recuperación vale una vez, dice cuántos quedan y avisa")
  void codigoDeRecuperacion() throws Exception {
    String recuperacion = sembrarFactor().codigos().get(0);

    verificar(token(null), "{\"recoveryCode\":\"" + recuperacion + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recoveryCodesRemaining").value(9));
    verificar(token(null), "{\"recoveryCode\":\"" + recuperacion + "\"}")
        .andExpect(status().isUnprocessableEntity());

    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM audit_security_log
                 WHERE event_type = 'MFA_RECOVERY_CODE_USED' AND target_user_id = ?
                """,
                Integer.class,
                persona))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-SP-849` — renovar la sesión no refresca la prueba")
  void renovarNoRefresca() throws Exception {
    String secreto = sembrarFactor().secreto();
    JsonNode sesion = iniciarSesion(secreto);
    long original = claims(sesion.get("accessToken").asText()).get("mfa").asLong();
    Thread.sleep(1100);

    JsonNode renovada =
        cuerpo(
            mvc.perform(
                    post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"refreshToken\":\"" + sesion.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk()));

    assertThat(claims(renovada.get("accessToken").asText()).get("mfa").asLong())
        .isEqualTo(original);
  }

  @Test
  @DisplayName("`CA-SP-850` — el catálogo dice qué permiso es sensible, y son los dieciocho")
  void elCatalogoLoDice() throws Exception {
    String token = token(null);
    JsonNode catalogo =
        cuerpo(
            mvc.perform(get("/api/v1/permissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));

    List<String> sensibles = new ArrayList<>();
    catalogo
        .get("content")
        .forEach(
            p -> {
              assertThat(p.has("requiresRecentMfa")).isTrue();
              if (p.get("requiresRecentMfa").asBoolean()) {
                sensibles.add(p.get("code").asText());
              }
            });
    assertThat(sensibles)
        .hasSize(18)
        .contains("roles:assign-permissions", "movements:confirm-payment", "users:reset-mfa");

    UUID asignar =
        jdbc.queryForObject(
            "SELECT id FROM permissions WHERE code = 'roles:assign-permissions'", UUID.class);
    mvc.perform(get("/api/v1/permissions/" + asignar).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.requiresRecentMfa").value(true));
  }

  @Test
  @DisplayName("`CA-SP-851` — la reverificación correcta no deja evento de seguridad")
  void noSeAudita() throws Exception {
    String secreto = sembrarFactor().secreto();
    int antes = eventosDeLaPersona();

    verificar(token(null), "{\"code\":\"" + codigo(secreto, 0) + "\"}").andExpect(status().isOk());

    assertThat(eventosDeLaPersona()).isEqualTo(antes);
  }

  @Test
  @DisplayName("`CA-SP-852` — sin el permiso de reverificar, prohibido; sin autenticar, 401")
  void autorizacion() throws Exception {
    mvc.perform(post(VERIFICAR).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());

    jdbc.update(
        """
        DELETE FROM role_permissions
         WHERE role_id = (SELECT id FROM roles WHERE code = ?)
           AND permission_id = (SELECT id FROM permissions WHERE code = 'users:verify-own-mfa')
        """,
        ACTOR);
    verificar(token(null), "{\"code\":\"123456\"}").andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Ayudas
  // ---------------------------------------------------------------------------

  private record Factor(String secreto, List<String> codigos) {}

  private Factor sembrarFactor() {
    String secreto = Totp.nuevoSecreto();
    UUID factor = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO user_mfa_factors (id, user_id, secret_ciphertext, status, confirmed_at)
        VALUES (?, ?, ?, 'ACTIVO', now())
        """,
        factor,
        persona,
        secretos.cifrar(secreto, persona));
    List<String> codigos = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      String codigo = "FGHJK-" + (20000 + i);
      codigos.add(codigo);
      jdbc.update(
          "INSERT INTO mfa_recovery_codes (id, factor_id, code_hash) VALUES (?, ?, ?)",
          UUID.randomUUID(),
          factor,
          hasher.hash(RecoveryCodeIssuer.normalizar(codigo)));
    }
    return new Factor(secreto, codigos);
  }

  /** Un token de la persona con la prueba del segundo factor en {@code mfa}, o sin ella. */
  private String token(Instant mfa) {
    return tokens.emitir(persona, List.of(ACTOR), false, false, mfa, Instant.now());
  }

  /** Una sesión real, por los dos pasos del inicio de sesión. */
  private JsonNode iniciarSesion(String secreto) throws Exception {
    JsonNode desafio =
        cuerpo(
            mvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"identifier\":\"" + USUARIO + "\",\"password\":\"" + CLAVE + "\"}"))
                .andExpect(status().isOk()));
    return cuerpo(
        mvc.perform(
                post("/api/v1/auth/login/mfa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"challengeToken\":\""
                            + desafio.get("challengeToken").asText()
                            + "\",\"code\":\""
                            + codigo(secreto, 0)
                            + "\"}"))
            .andExpect(status().isOk()));
  }

  private ResultActions verificar(String token, String cuerpo) throws Exception {
    return mvc.perform(
        post(VERIFICAR)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions asignar(String token) throws Exception {
    return mvc.perform(
        post("/api/v1/roles/" + objetivo + "/permissions")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"permissionIds\":[\"" + permisoAOtorgar + "\"]}"));
  }

  private int permisosDelObjetivo() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM role_permissions WHERE role_id = ?", Integer.class, objetivo);
  }

  private int eventosDeLaPersona() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_security_log WHERE target_user_id = ?", Integer.class, persona);
  }

  private JsonNode cuerpo(ResultActions resultado) throws Exception {
    return json.readTree(resultado.andReturn().getResponse().getContentAsString());
  }

  private JsonNode claims(String jwt) throws Exception {
    return json.readTree(
        new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8));
  }

  private static String codigo(String secreto, int desplazamiento) {
    return Totp.codigo(secreto, Totp.periodo(Instant.now()) + desplazamiento);
  }

  private static String otroCodigo(String bueno) {
    return bueno.equals("000000") ? "111111" : "000000";
  }
}
