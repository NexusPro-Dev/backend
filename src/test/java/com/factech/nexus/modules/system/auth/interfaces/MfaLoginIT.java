package com.factech.nexus.modules.system.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.auth.domain.service.RecoveryCodeIssuer;
import com.factech.nexus.modules.system.auth.infrastructure.MfaSecrets;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.shared.security.Totp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
 * `RF-SP-072` · `T-08`: el inicio de sesión en dos pasos (`CA-SP-820` a `CA-SP-838`, salvo
 * `CA-SP-832`, que vive en `RateLimitIT`) y el estado del factor en el perfil (`CA-SP-892`,
 * adelantado de `RF-SP-077`).
 */
@AutoConfigureMockMvc
class MfaLoginIT extends IntegrationTestBase {

  private static final String ROL = "MFA_LOGIN_PRUEBA";
  private static final String USUARIO = "mfalogin";
  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String CUALQUIERA = "/api/v1/audit/changes";
  private static final String TIPO_ACTIVACION =
      "https://nexus.factech.co/errors/activacion-de-segundo-factor-requerida";
  private static final String TIPO_CONTRASENA =
      "https://nexus.factech.co/errors/cambio-de-contrasena-requerido";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;
  @Autowired private MfaSecrets secretos;

  private UUID persona;
  private UUID rol;

  @BeforeEach
  void preparar() {
    limpiar();
    rol = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, 'Segundo paso de prueba', 'FUNCIONARIO',
                '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', false)
        """,
        rol,
        ROL);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions
         WHERE code IN ('users:start-own-mfa', 'users:confirm-own-mfa', 'users:read-own-profile',
                        'audit:read-changes')
        """,
        rol);
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, 'mfa.login@factech.co', 'Lia', 'Pasos', ?, false, 'ACTIVO',
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

  // ---------------------------------------------------------------------------
  // El primer paso
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-820` — con factor activo, la contraseña da un desafío y ningún token")
  void conFactorLaContrasenaDaUnDesafio() throws Exception {
    sembrarFactor();
    jdbc.update("UPDATE users SET failed_attempts = 2 WHERE id = ?", persona);

    JsonNode r = paso1();

    assertThat(r.get("mfaRequired").asBoolean()).isTrue();
    assertThat(r.get("challengeToken").asText()).isNotBlank();
    assertThat(r.get("challengeExpiresIn").asLong()).isEqualTo(300);
    assertThat(r.get("accessToken").isNull()).isTrue();
    assertThat(r.get("refreshToken").isNull()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_attempts FROM users WHERE id = ?", Integer.class, persona))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT last_login_at IS NULL FROM users WHERE id = ?", Boolean.class, persona))
        .isTrue();
    assertThat(eventos("LOGIN_SUCCESS")).isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-821` — sin factor activo, o con uno solo pendiente, la contraseña abre sesión")
  void sinFactorActivoComoSiempre() throws Exception {
    JsonNode sinNada = paso1();
    assertThat(sinNada.get("mfaRequired").asBoolean()).isFalse();
    assertThat(sinNada.get("accessToken").asText()).isNotBlank();

    jdbc.update(
        """
        INSERT INTO user_mfa_factors (id, user_id, secret_ciphertext, status, pending_expires_at)
        VALUES (?, ?, ?, 'PENDIENTE', now() + interval '10 minutes')
        """,
        UUID.randomUUID(),
        persona,
        secretos.cifrar(Totp.nuevoSecreto(), persona));
    assertThat(paso1().get("mfaRequired").asBoolean()).isFalse();
  }

  // ---------------------------------------------------------------------------
  // El segundo paso
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-SP-822` — el desafío y un código válido abren la sesión con la prueba de ahora")
  void elSegundoPasoAbreLaSesion() throws Exception {
    String secreto = sembrarFactor().secreto();
    jdbc.update("UPDATE users SET failed_attempts = 2 WHERE id = ?", persona);
    long antes = Instant.now().getEpochSecond();

    JsonNode sesion = sesionDe(paso2(paso1(), codigo(secreto, 0)).andExpect(status().isOk()));

    long mfa = claims(sesion.get("accessToken").asText()).get("mfa").asLong();
    assertThat(mfa).isBetween(antes - 1, Instant.now().getEpochSecond() + 1);
    assertThat(sesion.get("refreshToken").asText()).isNotBlank();
    assertThat(sesion.get("recoveryCodesRemaining").isNull()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_attempts FROM users WHERE id = ?", Integer.class, persona))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT mfa_verified_at IS NOT NULL FROM refresh_tokens WHERE user_id = ?",
                Boolean.class,
                persona))
        .isTrue();
    assertThat(eventos("LOGIN_SUCCESS")).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-SP-823` — un código inválido: credenciales inválidas, intentos y evento propio")
  void unCodigoInvalido() throws Exception {
    String secreto = sembrarFactor().secreto();
    String malo = otroCodigo(codigo(secreto, 0));

    paso2(paso1(), malo)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.remainingAttempts").value(4));

    assertThat(eventos("MFA_VERIFICATION_FAILED")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-SP-824` — los fallos del código y de la contraseña suman, y al quinto se bloquea")
  void losFallosSuman() throws Exception {
    String secreto = sembrarFactor().secreto();
    contrasenaMala();
    contrasenaMala();
    JsonNode desafio = paso1();
    String malo = otroCodigo(codigo(secreto, 0));

    paso2(desafio, malo).andExpect(jsonPath("$.remainingAttempts").value(2));
    paso2(desafio, malo).andExpect(jsonPath("$.remainingAttempts").value(1));
    paso2(desafio, malo)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.unlockAt").exists());

    assertThat(eventos("ACCOUNT_LOCKED")).isEqualTo(1);
    login(CLAVE).andExpect(status().isLocked());
  }

  @Test
  @DisplayName("`CA-SP-825` — al quinto fallo el desafío muere: el código correcto ya no abre nada")
  void elDesafioMuereAlQuinto() throws Exception {
    String secreto = sembrarFactor().secreto();
    JsonNode desafio = paso1();
    String malo = otroCodigo(codigo(secreto, 0));
    for (int i = 0; i < 5; i++) {
      // Se repone el contador de la cuenta para aislar el del desafío.
      jdbc.update("UPDATE users SET failed_attempts = 0 WHERE id = ?", persona);
      paso2(desafio, malo).andExpect(status().isUnauthorized());
    }
    jdbc.update("UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?", persona);

    paso2(desafio, codigo(secreto, 0))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.remainingAttempts").doesNotExist());
  }

  @Test
  @DisplayName("`CA-SP-826` — inexistente, caducado y consumido reciben la misma respuesta")
  void desafiosMuertosIndistinguibles() throws Exception {
    String secreto = sembrarFactor().secreto();

    String inexistente = cuerpoSinVariables(paso2Con("no-existe", codigo(secreto, 0), null));

    JsonNode caducado = paso1();
    jdbc.update(
        "UPDATE mfa_challenges SET expires_at = now() - interval '1 second',"
            + " created_at = now() - interval '6 minutes' WHERE user_id = ?",
        persona);
    String vencido = cuerpoSinVariables(paso2(caducado, codigo(secreto, 0)));

    JsonNode usado = paso1();
    paso2(usado, codigo(secreto, 0)).andExpect(status().isOk());
    String consumido = cuerpoSinVariables(paso2(usado, codigo(secreto, 1)));

    assertThat(vencido).isEqualTo(inexistente);
    assertThat(consumido).isEqualTo(inexistente);
  }

  @Test
  @DisplayName("`CA-SP-827` — un código ya usado no vale otra vez; el del periodo siguiente, sí")
  void unSoloUso() throws Exception {
    // La tolerancia de un periodo a cada lado, y el rechazo de dos, los prueba
    // `TotpTest` con un reloj fijo: aquí, con el reloj real, un desplazamiento de
    // dos periodos podría cruzar el borde de los treinta segundos a mitad de la
    // prueba y volverse uno. Lo que solo puede probarse aquí es que el periodo
    // usado queda anotado en el factor, y esto aguanta el cruce.
    String secreto = sembrarFactor().secreto();
    String ahora = codigo(secreto, 0);

    paso2(paso1(), ahora).andExpect(status().isOk());
    paso2(paso1(), ahora).andExpect(status().isUnauthorized());
    paso2(paso1(), codigo(secreto, 1)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-SP-828` — un código de recuperación entra una vez, dice cuántos quedan y avisa")
  void codigoDeRecuperacion() throws Exception {
    String recuperacion = sembrarFactor().codigos().get(3);

    JsonNode sesion =
        sesionDe(
            paso2Con(paso1().get("challengeToken").asText(), null, recuperacion)
                .andExpect(status().isOk()));

    assertThat(sesion.get("recoveryCodesRemaining").asInt()).isEqualTo(9);
    assertThat(eventos("MFA_RECOVERY_CODE_USED")).isEqualTo(1);
    // Minúsculas y sin guion también valen: se normaliza antes de comparar.
    paso2Con(
            paso1().get("challengeToken").asText(),
            null,
            recuperacion.toLowerCase().replace("-", ""))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("`CA-SP-829` — si la cuenta se desactiva entre los dos pasos, no hay sesión")
  void desactivadaEntreLosPasos() throws Exception {
    String secreto = sembrarFactor().secreto();
    JsonNode desafio = paso1();
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", persona);

    paso2(desafio, codigo(secreto, 0)).andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, persona))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-SP-830` y `CA-SP-831` — dos desafíos a la vez valen, y solo se guarda su resumen")
  void dosDesafios() throws Exception {
    String secreto = sembrarFactor().secreto();
    JsonNode uno = paso1();
    JsonNode dos = paso1();

    List<String> guardados =
        jdbc.queryForList(
            "SELECT challenge_hash FROM mfa_challenges WHERE user_id = ?", String.class, persona);
    assertThat(guardados)
        .hasSize(2)
        .doesNotContain(uno.get("challengeToken").asText(), dos.get("challengeToken").asText());

    paso2(uno, codigo(secreto, 0)).andExpect(status().isOk());
    paso2(dos, codigo(secreto, 1)).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "`CA-SP-833` — renovar la sesión conserva el instante de la verificación sin moverlo")
  void elRefrescoNoRenuevaLaPrueba() throws Exception {
    String secreto = sembrarFactor().secreto();
    JsonNode sesion = sesionDe(paso2(paso1(), codigo(secreto, 0)));
    long original = claims(sesion.get("accessToken").asText()).get("mfa").asLong();
    Thread.sleep(1100);

    JsonNode renovada =
        json.readTree(
            mvc.perform(
                    post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"refreshToken\":\"" + sesion.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(claims(renovada.get("accessToken").asText()).get("mfa").asLong())
        .isEqualTo(original);
  }

  // ---------------------------------------------------------------------------
  // La obligación del rol
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-834` a `CA-SP-836` — obligado y sin factor: entra, queda retenido y tiene salida")
  void laObligacionRetiene() throws Exception {
    obligar();
    JsonNode sesion = paso1();
    assertThat(sesion.get("mfaRequired").asBoolean()).isFalse();
    assertThat(sesion.get("mfaEnrollmentRequired").asBoolean()).isTrue();
    String token = sesion.get("accessToken").asText();

    mvc.perform(get(CUALQUIERA).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_ACTIVACION))
        .andExpect(jsonPath("$.enrollmentPath").value("/api/v1/users/me/mfa/totp"));

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/users/me/mfa/totp").header("Authorization", "Bearer " + token))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + sesion.get("refreshToken").asText() + "\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName(
      "`CA-SP-837` — con la contraseña provisional y la obligación pendientes, manda la contraseña")
  void mandaLaContrasena() throws Exception {
    obligar();
    jdbc.update(
        """
        UPDATE users SET must_change_password = true,
                         provisional_password_expires_at = now() + interval '1 day'
         WHERE id = ?
        """,
        persona);
    String token = paso1().get("accessToken").asText();

    mvc.perform(get(CUALQUIERA).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.type").value(TIPO_CONTRASENA));
    mvc.perform(post("/api/v1/users/me/mfa/totp").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TIPO_CONTRASENA));
  }

  @Test
  @DisplayName(
      "`CA-SP-838` y `CA-SP-892` — activar y renovar levanta la retención; el perfil lo dice")
  void activarLevantaLaRetencion() throws Exception {
    obligar();
    JsonNode sesion = paso1();
    String token = sesion.get("accessToken").asText();

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.mfa.enabled").value(false))
        .andExpect(jsonPath("$.mfa.required").value(true))
        .andExpect(jsonPath("$.mfa.enabledAt").doesNotExist());

    String secreto =
        json.readTree(
                mvc.perform(
                        post("/api/v1/users/me/mfa/totp")
                            .header("Authorization", "Bearer " + token))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("secret")
            .asText();
    mvc.perform(
            post("/api/v1/users/me/mfa/totp/confirmation")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + codigo(secreto, 0) + "\"}"))
        .andExpect(status().isOk());

    JsonNode renovada =
        json.readTree(
            mvc.perform(
                    post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"refreshToken\":\"" + sesion.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(renovada.get("mfaEnrollmentRequired").asBoolean()).isFalse();
    String libre = renovada.get("accessToken").asText();

    mvc.perform(get(CUALQUIERA).header("Authorization", "Bearer " + libre))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + libre))
        .andExpect(jsonPath("$.mfa.enabled").value(true))
        .andExpect(jsonPath("$.mfa.enabledAt").exists());
  }

  // ---------------------------------------------------------------------------
  // Ayudas
  // ---------------------------------------------------------------------------

  private record Factor(String secreto, List<String> codigos) {}

  /** Un factor activo y diez códigos de recuperación, sembrados como los deja `RF-SP-071`. */
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
      String codigo = "ABCDE-" + (10000 + i);
      codigos.add(codigo);
      jdbc.update(
          "INSERT INTO mfa_recovery_codes (id, factor_id, code_hash) VALUES (?, ?, ?)",
          UUID.randomUUID(),
          factor,
          hasher.hash(RecoveryCodeIssuer.normalizar(codigo)));
    }
    return new Factor(secreto, codigos);
  }

  private void obligar() {
    jdbc.update("UPDATE roles SET requires_mfa = true WHERE id = ?", rol);
  }

  private ResultActions login(String clave) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"identifier\":\"" + USUARIO + "\",\"password\":\"" + clave + "\"}"));
  }

  private JsonNode paso1() throws Exception {
    return json.readTree(
        login(CLAVE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private void contrasenaMala() throws Exception {
    login("NoEsLaClave2026xx").andExpect(status().isUnauthorized());
  }

  private ResultActions paso2(JsonNode desafio, String codigo) throws Exception {
    return paso2Con(desafio.get("challengeToken").asText(), codigo, null);
  }

  private ResultActions paso2Con(String desafio, String codigo, String recuperacion)
      throws Exception {
    StringBuilder cuerpo = new StringBuilder("{\"challengeToken\":\"" + desafio + "\"");
    if (codigo != null) {
      cuerpo.append(",\"code\":\"").append(codigo).append('"');
    }
    if (recuperacion != null) {
      cuerpo.append(",\"recoveryCode\":\"").append(recuperacion).append('"');
    }
    cuerpo.append('}');
    return mvc.perform(
        post("/api/v1/auth/login/mfa")
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo.toString()));
  }

  private JsonNode sesionDe(ResultActions resultado) throws Exception {
    return json.readTree(resultado.andReturn().getResponse().getContentAsString());
  }

  private String cuerpoSinVariables(ResultActions resultado) throws Exception {
    JsonNode cuerpo = sesionDe(resultado.andExpect(status().isUnauthorized()));
    ((com.fasterxml.jackson.databind.node.ObjectNode) cuerpo).remove("correlationId");
    return cuerpo.toString();
  }

  private JsonNode claims(String jwt) throws Exception {
    String carga = jwt.split("\\.")[1];
    return json.readTree(new String(Base64.getUrlDecoder().decode(carga), StandardCharsets.UTF_8));
  }

  private static String codigo(String secreto, int desplazamiento) {
    return Totp.codigo(secreto, Totp.periodo(Instant.now()) + desplazamiento);
  }

  private static String otroCodigo(String bueno) {
    return bueno.equals("000000") ? "111111" : "000000";
  }

  private int eventos(String tipo) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FROM audit_security_log
         WHERE event_type = ? AND target_user_id = ?
        """,
        Integer.class,
        tipo,
        persona);
  }
}
