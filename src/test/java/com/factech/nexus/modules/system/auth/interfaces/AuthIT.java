package com.factech.nexus.modules.system.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.security.PasswordHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Sesión de extremo a extremo (`RF-SP-034`, `RF-SP-035`, `RF-SP-036`).
 *
 * <p><b>Es la primera prueba del módulo que autentica de verdad</b>: obtiene un token del inicio de
 * sesión y lo presenta en una cabecera {@code Authorization}, en lugar de simular al actor. Eso es
 * lo que hace verificable que el token abra puertas y que retirar un rol las cierre.
 */
@AutoConfigureMockMvc
class AuthIT extends IntegrationTestBase {

  private static final String CONTABILIDAD = "01a02a33-4c00-7003-9c4f-5e7ad1000003";
  private static final String CLAVE = "ClaveLargaYSegura2026";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private PasswordHasher hasher;

  private UUID persona;

  @BeforeEach
  void prepararCuenta() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);

    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, 'JPerez', 'juan@factech.co', 'Juan', 'Pérez', ?, false, 'ACTIVO')
        """,
        persona,
        hasher.hash(CLAVE));
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", persona, CONTABILIDAD);
  }

  /**
   * Esta clase es la primera que deja <b>sesiones</b> detrás, y {@code refresh_tokens} cuelga de
   * {@code users} por clave foránea: sin este barrido, cualquier prueba posterior que vacíe {@code
   * users} muere con una violación de integridad que no tiene nada que ver con lo que estaba
   * comprobando. Se limpia al terminar, y no solo al empezar, para no cargar ese conocimiento sobre
   * las demás.
   */
  @AfterEach
  void noDejarSesionesDetras() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
  }

  // ---------------------------------------------------------------------------
  // Inicio de sesión
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("se entra con el nombre de usuario y también con el correo, con el mismo campo")
  void lasDosIdentidadesSirven() throws Exception {
    mvc.perform(login("JPerez", CLAVE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(jsonPath("$.mustChangePassword").value(false));

    mvc.perform(login("juan@factech.co", CLAVE)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("el nombre de usuario se compara SIN distinguir mayúsculas")
  void nombreDeUsuarioInsensibleALaCaja() throws Exception {
    // Es la obligación que `uq_users_username` impone: quien se registró como
    // `JPerez` tiene que poder entrar escribiendo `jperez`.
    mvc.perform(login("jperez", CLAVE)).andExpect(status().isOk());
    mvc.perform(login("JPEREZ", CLAVE)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("la respuesta no lleva permisos ni datos personales")
  void sinDatosPersonales() throws Exception {
    String cuerpo =
        mvc.perform(login("JPerez", CLAVE)).andReturn().getResponse().getContentAsString();

    assertThat(cuerpo)
        .doesNotContain("juan@factech.co")
        .doesNotContain("Juan")
        .doesNotContain("audit:read");
  }

  @Test
  @DisplayName("credenciales inválidas y cuenta inexistente dan EXACTAMENTE la misma respuesta")
  void mismaRespuestaParaLosCuatroCasos() throws Exception {
    String malaClave =
        mvc.perform(login("JPerez", "OtraClaveLargaDistinta"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String noExiste =
        mvc.perform(login("nadie", CLAVE))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Se comparan sin el identificador de correlación, que cambia por petición.
    assertThat(sinCorrelacion(malaClave)).isEqualTo(sinCorrelacion(noExiste));
    // Y no hay detalle por campo: señalar el identificador frente a la
    // contraseña reintroduciría lo que el mensaje único evita.
    assertThat(malaClave).doesNotContain("identifier").doesNotContain("password");
  }

  @Test
  @DisplayName("la cuenta inactiva o eliminada responde igual que una contraseña incorrecta")
  void cuentaNoHabilitada() throws Exception {
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", persona);
    mvc.perform(login("JPerez", CLAVE)).andExpect(status().isUnauthorized());

    jdbc.update("UPDATE users SET status = 'ACTIVO', deleted_at = now() WHERE id = ?", persona);
    mvc.perform(login("JPerez", CLAVE)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("a los cinco intentos la cuenta se bloquea y responde 423, distinguible")
  void bloqueoPorIntentos() throws Exception {
    for (int i = 0; i < 5; i++) {
      mvc.perform(login("JPerez", "ClaveEquivocadaLarga")).andExpect(status().isUnauthorized());
    }

    // Distinta y distinguible: `423` y no `401`. Es la excepción consciente al
    // mensaje genérico.
    mvc.perform(login("JPerez", CLAVE))
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/cuenta-bloqueada"))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("temporalmente")));

    Integer bloqueada =
        jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE id = ? AND locked_until IS NOT NULL",
            Integer.class,
            persona);
    assertThat(bloqueada).isEqualTo(1);
  }

  @Test
  @DisplayName("el bloqueo MANUAL da otro mensaje: esa cuenta no se desbloquea sola")
  void bloqueoManual() throws Exception {
    jdbc.update("UPDATE users SET status = 'BLOQUEADO' WHERE id = ?", persona);

    mvc.perform(login("JPerez", CLAVE))
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("administra")));
  }

  @Test
  @DisplayName("una entrada correcta limpia el contador de intentos")
  void laEntradaLimpiaElContador() throws Exception {
    mvc.perform(login("JPerez", "ClaveEquivocadaLarga")).andExpect(status().isUnauthorized());
    mvc.perform(login("JPerez", CLAVE)).andExpect(status().isOk());

    var fila =
        jdbc.queryForMap("SELECT failed_attempts, last_login_at FROM users WHERE id = ?", persona);
    assertThat(fila.get("failed_attempts")).isEqualTo(0);
    assertThat(fila.get("last_login_at")).isNotNull();
  }

  @Test
  @DisplayName("todo intento se audita, y el fallido va sin actor porque no hay identidad probada")
  void auditoriaDeLosIntentos() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(login("nadie", CLAVE).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isUnauthorized());

    Integer fallidos =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE correlation_id = ? AND event_type = 'LOGIN_FAILURE'
               AND outcome = 'FAILURE' AND actor_id IS NULL AND ip_address IS NOT NULL
            """,
            Integer.class,
            correlacion);
    assertThat(fallidos).isEqualTo(1);

    UUID exitosa = UUID.randomUUID();
    mvc.perform(login("JPerez", CLAVE).header("X-Correlation-Id", exitosa.toString()))
        .andExpect(status().isOk());

    Integer entradas =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE correlation_id = ? AND event_type = 'LOGIN_SUCCESS' AND severity = 'INFORMATIVA'
            """,
            Integer.class,
            exitosa);
    assertThat(entradas).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // El token abre puertas de verdad
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("el token de acceso autentica, y sus permisos salen de la BASE")
  void elTokenAutentica() throws Exception {
    String token = accessToken(login("JPerez", CLAVE));

    // CONTABILIDAD concede `audit:read-changes` y `audit:read-deletions`, y
    // ninguno más: el token autentica, pero no abre esta puerta.
    mvc.perform(get("/api/v1/memberships").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());

    // Y al concederle el rol, la puerta se abre SIN emitir un token nuevo:
    // los permisos se resuelven por petición contra la base.
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)",
        persona,
        "01a02a33-4c00-7002-9c4f-5e7ad1000002");
    mvc.perform(get("/api/v1/memberships").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("retirar el rol cierra la puerta de INMEDIATO, con el mismo token")
  void retirarElRolEsInmediato() throws Exception {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)",
        persona,
        "01a02a33-4c00-7002-9c4f-5e7ad1000002");
    String token = accessToken(login("JPerez", CLAVE));

    mvc.perform(get("/api/v1/memberships").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // Es la razón de resolver los permisos por petición: con ellos dentro del
    // token, esto seguiría abierto hasta quince minutos.
    jdbc.update("DELETE FROM user_roles WHERE user_id = ?", persona);

    mvc.perform(get("/api/v1/memberships").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("un token inventado o manipulado no autentica")
  void tokenInvalido() throws Exception {
    mvc.perform(get("/api/v1/memberships").header("Authorization", "Bearer no.es.un.token"))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // Refresco
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("el refresco ROTA: el token entregado deja de servir y el nuevo sirve")
  void laRotacion() throws Exception {
    String primero = refreshToken(login("JPerez", CLAVE));

    String segundo =
        refreshToken(
            mvc.perform(refresh(primero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()));

    assertThat(segundo).isNotEqualTo(primero);
    mvc.perform(refresh(segundo)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("reutilizar un token rotado es ROBO: cae la familia entera y suena la alarma")
  void reutilizacionDeTokenRotado() throws Exception {
    String primero = refreshToken(login("JPerez", CLAVE));
    String segundo = refreshToken(mvc.perform(refresh(primero)).andExpect(status().isOk()));

    UUID correlacion = UUID.randomUUID();
    mvc.perform(refresh(primero).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isUnauthorized());

    // La familia entera queda revocada: el token vigente del titular legítimo
    // también deja de servir, que es lo correcto cuando hay una copia suelta.
    mvc.perform(refresh(segundo)).andExpect(status().isUnauthorized());

    Integer alarma =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE correlation_id = ? AND event_type = 'REFRESH_TOKEN_REUSE'
               AND severity = 'ALTA' AND outcome = 'FAILURE'
            """,
            Integer.class,
            correlacion);
    assertThat(alarma).isEqualTo(1);

    Integer vivas =
        jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class);
    assertThat(vivas).isZero();
  }

  @Test
  @DisplayName("las cinco causas de rechazo del refresco devuelven la MISMA respuesta")
  void mismaRespuestaParaLasCincoCausas() throws Exception {
    String inventado =
        mvc.perform(refresh("token-que-no-existe"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String vigente = refreshToken(login("JPerez", CLAVE));
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", persona);

    String cuentaCaida =
        mvc.perform(refresh(vigente))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // El cliente no debe poder deducir si el token fue robado, si expiró o si la
    // cuenta fue desactivada.
    assertThat(sinCorrelacion(inventado)).isEqualTo(sinCorrelacion(cuentaCaida));
  }

  @Test
  @DisplayName("un token que no existe NO revoca nada")
  void elTokenInventadoNoRevocaNada() throws Exception {
    String vigente = refreshToken(login("JPerez", CLAVE));

    mvc.perform(refresh("token-que-no-existe")).andExpect(status().isUnauthorized());

    // Si revocara «lo que sea», cualquiera cerraría sesiones ajenas a ciegas.
    mvc.perform(refresh(vigente)).andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // Cierre
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("el cierre revoca la sesión y devuelve 204")
  void cierreSimple() throws Exception {
    String token = refreshToken(login("JPerez", CLAVE));

    mvc.perform(logout(token, false)).andExpect(status().isNoContent());
    mvc.perform(refresh(token)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("un token no reconocido también devuelve 204: no hay oráculo que consultar")
  void sinOraculo() throws Exception {
    // Distinguirlo permitiría comprobar con dos peticiones si una cadena de
    // texto es un refresh token del sistema.
    mvc.perform(logout("token-que-no-existe", false)).andExpect(status().isNoContent());

    String token = refreshToken(login("JPerez", CLAVE));
    mvc.perform(logout(token, false)).andExpect(status().isNoContent());
    // Y repetirlo sobre uno ya revocado, también.
    mvc.perform(logout(token, false)).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("el cierre total revoca todas las sesiones de la persona")
  void cierreDeTodasLasSesiones() throws Exception {
    String unaSesion = refreshToken(login("JPerez", CLAVE));
    String otraSesion = refreshToken(login("JPerez", CLAVE));

    mvc.perform(logout(unaSesion, true)).andExpect(status().isNoContent());

    mvc.perform(refresh(unaSesion)).andExpect(status().isUnauthorized());
    mvc.perform(refresh(otraSesion)).andExpect(status().isUnauthorized());

    Integer vivas =
        jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class);
    assertThat(vivas).isZero();
  }

  @Test
  @DisplayName("el token revocado por CIERRE no dispara la alarma de robo")
  void elCierreNoEsUnRobo() throws Exception {
    String token = refreshToken(login("JPerez", CLAVE));
    mvc.perform(logout(token, false)).andExpect(status().isNoContent());

    UUID correlacion = UUID.randomUUID();
    mvc.perform(refresh(token).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isUnauthorized());

    // Solo la ROTACIÓN significa robo: las demás revocaciones son deliberadas y
    // volver a presentar ese token es torpeza del cliente, no un incidente.
    Integer alarma =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ? AND event_type = 'REFRESH_TOKEN_REUSE'",
            Integer.class,
            correlacion);
    assertThat(alarma).isZero();
  }

  @Test
  @DisplayName("el motivo de revocación es obligatorio: el esquema no admite una sin él")
  void todaRevocacionLlevaMotivo() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO refresh_tokens (id, user_id, token_hash, family_id,
                                                family_started_at, expires_at, revoked_at)
                    VALUES (gen_random_uuid(), ?, 'hash', gen_random_uuid(),
                            now(), now() + interval '1 day', now())
                    """,
                    persona))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder login(String identificador, String clave) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identificador, clave));
  }

  private MockHttpServletRequestBuilder refresh(String token) {
    return post("/api/v1/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"refreshToken\":\"%s\"}".formatted(token));
  }

  private MockHttpServletRequestBuilder logout(String token, boolean todas) {
    return post("/api/v1/auth/logout")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"refreshToken\":\"%s\",\"allSessions\":%s}".formatted(token, todas));
  }

  private String accessToken(MockHttpServletRequestBuilder peticion) throws Exception {
    return campo(
        mvc.perform(peticion).andReturn().getResponse().getContentAsString(), "accessToken");
  }

  private String refreshToken(MockHttpServletRequestBuilder peticion) throws Exception {
    return campo(
        mvc.perform(peticion).andReturn().getResponse().getContentAsString(), "refreshToken");
  }

  private String refreshToken(org.springframework.test.web.servlet.ResultActions resultado)
      throws Exception {
    return campo(resultado.andReturn().getResponse().getContentAsString(), "refreshToken");
  }

  private String campo(String cuerpo, String nombre) throws Exception {
    JsonNode arbol = json.readTree(cuerpo);
    return arbol.get(nombre).asText();
  }

  /** El identificador de correlación cambia en cada petición y no forma parte del contrato. */
  private static String sinCorrelacion(String cuerpo) {
    return cuerpo.replaceAll("\"correlationId\":\"[^\"]*\"", "\"correlationId\":\"\"");
  }
}
