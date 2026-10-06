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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * `RF-SP-074` · `T-03`: regenerar los propios códigos de recuperación, `CA-SP-853` a `CA-SP-858`.
 */
@AutoConfigureMockMvc
class RecoveryCodeRegenerationIT extends IntegrationTestBase {

  private static final String ROL = "MFA_CODIGOS_PRUEBA";
  private static final String REGENERAR = "/api/v1/users/me/mfa/recovery-codes";
  private static final String TIPO_REVERIFICAR =
      "https://nexus.factech.co/errors/reverificacion-requerida";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;
  @Autowired private MfaSecrets secretos;
  @Autowired private AccessTokenIssuer tokens;

  private UUID persona;

  @BeforeEach
  void preparar() {
    limpiar();
    UUID rol = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, 'Códigos de prueba', 'FUNCIONARIO',
                '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', false)
        """,
        rol,
        ROL);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions
         WHERE code IN ('users:regenerate-own-recovery-codes', 'users:verify-own-mfa')
        """,
        rol);
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, 'mfacodigos', 'mfa.codigos@factech.co', 'Ciro', 'Codigos', ?, false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        persona,
        hasher.hash("ClaveLargaYSegura2026"));
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
  @DisplayName(
      "`CA-SP-853` — con verificación reciente devuelve diez códigos nuevos, guardados resumidos")
  void diezNuevos() throws Exception {
    sembrarFactor();

    JsonNode r = cuerpo(regenerar(reciente()).andExpect(status().isCreated()));

    Set<String> nuevos = new HashSet<>();
    r.get("recoveryCodes").forEach(c -> nuevos.add(c.asText()));
    assertThat(nuevos).hasSize(10).allMatch(c -> c.matches("[0-9A-Z]{5}-[0-9A-Z]{5}"));
    List<String> vigentes =
        jdbc.queryForList(
            """
            SELECT c.code_hash FROM mfa_recovery_codes c
              JOIN user_mfa_factors f ON f.id = c.factor_id
             WHERE f.user_id = ? AND c.used_at IS NULL AND c.superseded_at IS NULL
            """,
            String.class,
            persona);
    assertThat(vigentes).hasSize(10).noneMatch(nuevos::contains);
  }

  @Test
  @DisplayName("`CA-SP-854` — los anteriores, usados o no, dejan de servir; los nuevos sirven")
  void losAnterioresNoSirven() throws Exception {
    List<String> viejos = sembrarFactor();
    usarUno(viejos.get(0));

    String nuevo =
        cuerpo(regenerar(reciente()).andExpect(status().isCreated()))
            .get("recoveryCodes")
            .get(0)
            .asText();

    verificarCon(viejos.get(1)).andExpect(status().isUnprocessableEntity());
    verificarCon(nuevo).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM mfa_recovery_codes c
                  JOIN user_mfa_factors f ON f.id = c.factor_id
                 WHERE f.user_id = ? AND c.used_at IS NOT NULL AND c.superseded_at IS NULL
                """,
                Integer.class,
                persona))
        .as("el código usado conserva su historia: no se marca como anulado")
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-SP-855` — sin verificación reciente, prohibido, y los anteriores siguen sirviendo")
  void sinVerificacionReciente() throws Exception {
    List<String> viejos = sembrarFactor();

    regenerar(token(Instant.now().minus(Duration.ofMinutes(6))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_REVERIFICAR));

    verificarCon(viejos.get(0)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-SP-856` — sin factor activo se rechaza")
  void sinFactor() throws Exception {
    regenerar(reciente()).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("`CA-SP-857` — queda un evento de severidad alta, sin los códigos")
  void seAudita() throws Exception {
    sembrarFactor();
    JsonNode r = cuerpo(regenerar(reciente()).andExpect(status().isCreated()));

    assertThat(
            jdbc.queryForObject(
                """
                SELECT severity FROM audit_security_log
                 WHERE event_type = 'MFA_RECOVERY_CODES_REGENERATED' AND target_user_id = ?
                """,
                String.class,
                persona))
        .isEqualTo("ALTA");
    String todo =
        jdbc.queryForObject(
            "SELECT coalesce(string_agg(detail::text, ' '), '') FROM audit_security_log",
            String.class);
    r.get("recoveryCodes").forEach(c -> assertThat(todo).doesNotContain(c.asText()));
  }

  @Test
  @DisplayName("`CA-SP-858` — sin el permiso, prohibido; sin autenticar, 401")
  void autorizacion() throws Exception {
    mvc.perform(post(REGENERAR)).andExpect(status().isUnauthorized());

    jdbc.update(
        """
        DELETE FROM role_permissions
         WHERE role_id = (SELECT id FROM roles WHERE code = ?)
           AND permission_id = (SELECT id FROM permissions
                                 WHERE code = 'users:regenerate-own-recovery-codes')
        """,
        ROL);
    regenerar(reciente()).andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------

  private List<String> sembrarFactor() {
    UUID factor = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO user_mfa_factors (id, user_id, secret_ciphertext, status, confirmed_at)
        VALUES (?, ?, ?, 'ACTIVO', now())
        """,
        factor,
        persona,
        secretos.cifrar(Totp.nuevoSecreto(), persona));
    List<String> codigos = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      String codigo = "MNPQR-" + (30000 + i);
      codigos.add(codigo);
      jdbc.update(
          "INSERT INTO mfa_recovery_codes (id, factor_id, code_hash) VALUES (?, ?, ?)",
          UUID.randomUUID(),
          factor,
          hasher.hash(RecoveryCodeIssuer.normalizar(codigo)));
    }
    return codigos;
  }

  private void usarUno(String codigo) throws Exception {
    verificarCon(codigo).andExpect(status().isOk());
  }

  private ResultActions verificarCon(String codigo) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/mfa/verification")
            .header("Authorization", "Bearer " + token(null))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"recoveryCode\":\"" + codigo + "\"}"));
  }

  private ResultActions regenerar(String token) throws Exception {
    return mvc.perform(post(REGENERAR).header("Authorization", "Bearer " + token));
  }

  private String reciente() {
    return token(Instant.now());
  }

  private String token(Instant mfa) {
    return tokens.emitir(persona, List.of(ROL), false, false, mfa, Instant.now());
  }

  private JsonNode cuerpo(ResultActions resultado) throws Exception {
    return json.readTree(resultado.andReturn().getResponse().getContentAsString());
  }
}
