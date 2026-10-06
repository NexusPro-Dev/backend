package com.factech.nexus.modules.system.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.auth.infrastructure.MfaSecrets;
import com.factech.nexus.shared.security.AccessTokenIssuer;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.shared.security.Totp;
import com.factech.nexus.testing.ConcurrencyHarness;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

/** `RF-SP-071` · `T-08`: activar el segundo factor, `CA-SP-807` a `CA-SP-819`. */
@AutoConfigureMockMvc
class MfaEnrollmentIT extends IntegrationTestBase {

  private static final String ROL = "MFA_ACTIVACION_PRUEBA";
  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String INICIAR = "/api/v1/users/me/mfa/totp";
  private static final String CONFIRMAR = "/api/v1/users/me/mfa/totp/confirmation";
  private static final String REVERIFICAR =
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
        VALUES (?, ?, 'Activación de prueba', 'FUNCIONARIO',
                '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', false)
        """,
        rol,
        ROL);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions
         WHERE code IN ('users:start-own-mfa', 'users:confirm-own-mfa', 'users:read-own-profile')
        """,
        rol);
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, 'mfaprueba', 'mfa.prueba@factech.co', 'Mara', 'Factor', ?, false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        persona,
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
  // Iniciar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-807` — iniciar devuelve la URI con emisor y usuario, el secreto y su caducidad")
  void iniciarDevuelveLoQueLaAppNecesita() throws Exception {
    JsonNode respuesta = iniciar(login());

    String secreto = respuesta.get("secret").asText();
    assertThat(secreto).hasSize(32).matches("[A-Z2-7]+");
    assertThat(respuesta.get("otpauthUri").asText())
        .startsWith("otpauth://totp/NEXUS:mfaprueba?secret=" + secreto)
        .contains("issuer=NEXUS", "digits=6", "period=30");
    assertThat(Instant.parse(respuesta.get("expiresAt").asText()))
        .isAfter(Instant.now().plusSeconds(9 * 60));
    assertThat(estados()).containsExactly("PENDIENTE");
  }

  @Test
  @DisplayName("`CA-SP-808` — el secreto no se guarda en claro y no descifra como si fuera de otro")
  void elSecretoVaCifrado() throws Exception {
    String secreto = iniciar(login()).get("secret").asText();

    String guardado =
        jdbc.queryForObject(
            "SELECT secret_ciphertext FROM user_mfa_factors WHERE user_id = ?",
            String.class,
            persona);

    assertThat(guardado).startsWith("v1:").doesNotContain(secreto);
    assertThat(secretos.descifrar(guardado, persona)).isEqualTo(secreto);
    assertThatThrownBy(() -> secretos.descifrar(guardado, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName(
      "`CA-SP-809` — con el factor solo pendiente, entrar sigue pidiendo solo la contraseña")
  void unPendienteNoSeExigeAlEntrar() throws Exception {
    iniciar(login());

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credenciales()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  @Test
  @DisplayName(
      "`CA-SP-813` — iniciar dos veces deja un solo pendiente: el primer secreto ya no vale")
  void iniciarDosVecesSustituye() throws Exception {
    String token = login();
    String primero = iniciar(token).get("secret").asText();
    iniciar(token);

    assertThat(estados()).containsExactlyInAnyOrder("RETIRADO", "PENDIENTE");
    confirmar(token, codigoActual(primero)).andExpect(status().isUnprocessableEntity());
  }

  // ---------------------------------------------------------------------------
  // Confirmar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-SP-810` — confirmar activa el factor y devuelve diez códigos, guardados resumidos")
  void confirmarActivaYEntregaLosCodigos() throws Exception {
    String token = login();
    String secreto = iniciar(token).get("secret").asText();

    JsonNode respuesta =
        json.readTree(
            confirmar(token, codigoActual(secreto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replacedPrevious").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString());

    Set<String> codigos = new HashSet<>();
    respuesta.get("recoveryCodes").forEach(c -> codigos.add(c.asText()));
    assertThat(codigos).hasSize(10).allMatch(c -> c.matches("[0-9A-Z]{5}-[0-9A-Z]{5}"));
    assertThat(estados()).containsExactly("ACTIVO");

    List<String> resumenes =
        jdbc.queryForList(
            """
            SELECT c.code_hash FROM mfa_recovery_codes c
              JOIN user_mfa_factors f ON f.id = c.factor_id
             WHERE f.user_id = ?
            """,
            String.class,
            persona);
    assertThat(resumenes).hasSize(10).noneMatch(codigos::contains);
  }

  @Test
  @DisplayName("`CA-SP-811` — un código inválido se rechaza y el pendiente sigue vivo")
  void unCodigoMalSeReintenta() throws Exception {
    String token = login();
    String secreto = iniciar(token).get("secret").asText();
    String bueno = codigoActual(secreto);
    String malo = bueno.equals("000000") ? "111111" : "000000";

    confirmar(token, malo)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    assertThat(estados()).containsExactly("PENDIENTE");

    confirmar(token, bueno).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-SP-811` — un código sin seis dígitos es un error de formato")
  void formatoDelCodigo() throws Exception {
    String token = login();
    iniciar(token);

    confirmar(token, "12ab").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-SP-812` — un pendiente de más de diez minutos ya no se confirma")
  void elPendienteCaduca() throws Exception {
    String token = login();
    String secreto = iniciar(token).get("secret").asText();
    jdbc.update(
        """
        UPDATE user_mfa_factors
           SET pending_expires_at = now() - interval '1 second',
               created_at = now() - interval '11 minutes'
         WHERE user_id = ?
        """,
        persona);

    confirmar(token, codigoActual(secreto)).andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName(
      "`CA-SP-814` — con un factor activo, iniciar sin código reciente se rechaza; con él, no")
  void cambiarDeTelefonoExigeVerificacionReciente() throws Exception {
    String token = login();
    activar(token);

    mvc.perform(post(INICIAR).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(REVERIFICAR))
        .andExpect(jsonPath("$.verificationPath").value("/api/v1/auth/mfa/verification"));

    mvc.perform(post(INICIAR).header("Authorization", "Bearer " + conVerificacionReciente()))
        .andExpect(status().isCreated());
    assertThat(estados()).containsExactlyInAnyOrder("ACTIVO", "PENDIENTE");
  }

  @Test
  @DisplayName(
      "`CA-SP-815` — confirmar otro con uno activo retira el anterior, y sus códigos con él")
  void cambiarDeTelefonoRetiraElAnterior() throws Exception {
    String token = login();
    activar(token);
    UUID viejo =
        jdbc.queryForObject(
            "SELECT id FROM user_mfa_factors WHERE user_id = ? AND status = 'ACTIVO'",
            UUID.class,
            persona);

    String reciente = conVerificacionReciente();
    String nuevo = iniciar(reciente).get("secret").asText();
    confirmar(reciente, codigoActual(nuevo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replacedPrevious").value(true));

    Map<String, Object> anterior =
        jdbc.queryForMap("SELECT status, retired_reason FROM user_mfa_factors WHERE id = ?", viejo);
    assertThat(anterior).containsEntry("status", "RETIRADO");
    assertThat(anterior).containsEntry("retired_reason", "REEMPLAZADO");
    assertThat(estados()).containsExactlyInAnyOrder("RETIRADO", "ACTIVO");
  }

  @Test
  @DisplayName("`CA-SP-816` — el código de la confirmación queda usado: su periodo es el último")
  void elCodigoDeLaConfirmacionQuedaUsado() throws Exception {
    String token = login();
    String secreto = iniciar(token).get("secret").asText();
    long periodo = Totp.periodo(Instant.now());
    confirmar(token, Totp.codigo(secreto, periodo)).andExpect(status().isOk());

    Long ultimo =
        jdbc.queryForObject(
            "SELECT last_used_step FROM user_mfa_factors WHERE user_id = ? AND status = 'ACTIVO'",
            Long.class,
            persona);
    assertThat(ultimo).isEqualTo(periodo);
    // Que ese periodo ya no casa lo prueba `TotpTest.unSoloUso`; aquí, que se anotó.
    assertThat(Totp.periodoQueCasa(secreto, Totp.codigo(secreto, periodo), Instant.now(), ultimo))
        .isEmpty();
  }

  @Test
  @DisplayName("`CA-SP-817` — dos confirmaciones simultáneas dejan un solo factor activo")
  void dosConfirmacionesALaVez() throws Exception {
    String token = login();
    String secreto = iniciar(token).get("secret").asText();
    String codigo = codigoActual(secreto);

    var resultados =
        ConcurrencyHarness.runTogether(
            2, i -> confirmar(token, codigo).andReturn().getResponse().getStatus());

    assertThat(resultados).allMatch(ConcurrencyHarness.Outcome::succeeded);
    assertThat(resultados.stream().map(ConcurrencyHarness.Outcome::value))
        .containsExactlyInAnyOrder(200, 422);
    assertThat(estados()).containsExactly("ACTIVO");
  }

  @Test
  @DisplayName(
      "`CA-SP-818` — la activación se audita alta, sin secreto ni códigos en ningún registro")
  void seAuditaSinSecretos() throws Exception {
    String token = login();
    String secreto = iniciar(token).get("secret").asText();
    JsonNode codigos =
        json.readTree(
                confirmar(token, codigoActual(secreto))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("recoveryCodes");

    Map<String, Object> evento =
        jdbc.queryForMap(
            """
            SELECT severity, outcome, detail::text AS detalle FROM audit_security_log
             WHERE event_type = 'MFA_ENABLED' AND target_user_id = ?
            """,
            persona);
    assertThat(evento).containsEntry("severity", "ALTA").containsEntry("outcome", "SUCCESS");
    assertThat((String) evento.get("detalle")).contains("replacedPrevious");

    String todo =
        jdbc.queryForObject(
                """
            SELECT coalesce(string_agg(changes::text, ' '), '') FROM audit_change_log
             WHERE entity = 'user_mfa_factors'
            """,
                String.class)
            + jdbc.queryForObject(
                "SELECT coalesce(string_agg(detail::text, ' '), '') FROM audit_security_log",
                String.class);
    assertThat(todo).doesNotContain(secreto);
    codigos.forEach(c -> assertThat(todo).doesNotContain(c.asText()));
  }

  @Test
  @DisplayName("`CA-SP-819` — sin el permiso responde prohibido; sin autenticar, 401")
  void autorizacion() throws Exception {
    mvc.perform(post(INICIAR)).andExpect(status().isUnauthorized());
    mvc.perform(post(CONFIRMAR).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());

    jdbc.update(
        """
        DELETE FROM role_permissions
         WHERE role_id = (SELECT id FROM roles WHERE code = ?)
           AND permission_id IN (SELECT id FROM permissions
                                  WHERE code IN ('users:start-own-mfa', 'users:confirm-own-mfa'))
        """,
        ROL);
    String token = login();

    mvc.perform(post(INICIAR).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    confirmar(token, "123456").andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Ayudas
  // ---------------------------------------------------------------------------

  private String credenciales() {
    return "{\"identifier\":\"mfaprueba\",\"password\":\"" + CLAVE + "\"}";
  }

  private String login() throws Exception {
    String cuerpo =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credenciales()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(cuerpo).get("accessToken").asText();
  }

  /** Un token de la persona con la prueba del segundo factor en este instante. */
  private String conVerificacionReciente() {
    Instant ahora = Instant.now();
    return tokens.emitir(persona, List.of(ROL), false, false, ahora, ahora);
  }

  private JsonNode iniciar(String token) throws Exception {
    String cuerpo =
        mvc.perform(post(INICIAR).header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(cuerpo);
  }

  private org.springframework.test.web.servlet.ResultActions confirmar(String token, String codigo)
      throws Exception {
    return mvc.perform(
        post(CONFIRMAR)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + codigo + "\"}"));
  }

  private void activar(String token) throws Exception {
    String secreto = iniciar(token).get("secret").asText();
    confirmar(token, codigoActual(secreto)).andExpect(status().isOk());
  }

  private static String codigoActual(String secreto) {
    return Totp.codigo(secreto, Totp.periodo(Instant.now()));
  }

  private List<String> estados() {
    return jdbc.queryForList(
        "SELECT status FROM user_mfa_factors WHERE user_id = ? ORDER BY created_at",
        String.class,
        persona);
  }
}
