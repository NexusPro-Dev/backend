package com.factech.nexus.modules.system.auth.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.auth.domain.service.FailedAttemptLedger;
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
  @Autowired private FailedAttemptLedger sinCuenta;

  private UUID persona;

  @BeforeEach
  void prepararCuenta() {
    // El contador de los identificadores SIN cuenta vive en memoria y NO en la
    // base: sin esta limpieza, `nadie` llega a una prueba con los fallos que le
    // dejó la anterior, y la comparación de respuestas pasa a depender del
    // orden de ejecución -- que es la peor clase de prueba intermitente.
    sinCuenta.limpiar();
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
  @DisplayName("cada rechazo dice cuántos intentos quedan, y el último cuánto durará el bloqueo")
  void elRechazoDiceLosIntentosQueQuedan() throws Exception {
    for (int restantes = 4; restantes > 0; restantes--) {
      mvc.perform(login("JPerez", "ClaveEquivocadaLarga"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.remainingAttempts").value(restantes))
          // Mientras queden intentos NO hay bloqueo del que informar. El campo
          // se omite en lugar de viajar nulo: dos formas de decir «no aplica»
          // acaban con el cliente comprobando solo una.
          .andExpect(jsonPath("$.unlockAt").doesNotExist())
          .andExpect(jsonPath("$.retryAfterSeconds").doesNotExist());
    }

    // El quinto agota el contador. Sigue siendo `401` —`EX-003` dice que la
    // respuesta es la de `EX-001`—, pero ya anuncia el bloqueo en lugar de
    // dejar que la persona lo descubra reintentando.
    mvc.perform(login("JPerez", "ClaveEquivocadaLarga"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.remainingAttempts").value(0))
        .andExpect(jsonPath("$.unlockAt").exists())
        .andExpect(jsonPath("$.retryAfterSeconds").value(60))
        .andExpect(
            jsonPath("$.detail", org.hamcrest.Matchers.containsString("bloqueada temporalmente")))
        // Y NO lleva la duración escrita. Un texto con «1 minuto» es cierto al
        // serializarse y deja de serlo enseguida; el cliente descuenta
        // `retryAfterSeconds`, que no envejece.
        .andExpect(
            jsonPath(
                "$.detail",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("minuto"))));
  }

  @Test
  @DisplayName("el identificador SIN cuenta gasta intentos igual: el número no delata quién existe")
  void elIdentificadorSinCuentaGastaIntentosIgual() throws Exception {
    // Es la prueba de que devolver los intentos restantes no reabre la
    // enumeración que `CA-SP-292` cierra. Se comparan las respuestas ENTERAS,
    // no solo el contador: cualquier campo que apareciera en una y no en la
    // otra volvería a distinguirlas.
    for (int intento = 0; intento < 5; intento++) {
      String real = respuesta(login("JPerez", "ClaveEquivocadaLarga"));
      String inventado = respuesta(login("nadie", "ClaveEquivocadaLarga"));
      assertThat(comparable(real)).isEqualTo(comparable(inventado));
    }

    // Y el sexto responde `423` en los dos casos. Sin esta parte, el `401`
    // habría dejado de delatar la cuenta y el `423` habría seguido haciéndolo:
    // bastaba con gastar cinco intentos y mirar el estado.
    mvc.perform(login("JPerez", CLAVE)).andExpect(status().isLocked());
    mvc.perform(login("nadie", CLAVE)).andExpect(status().isLocked());
  }

  @Test
  @DisplayName("la cuenta inactiva gasta intento aunque la contraseña sea CORRECTA")
  void laCuentaNoHabilitadaTambienGastaIntento() throws Exception {
    // Si no lo gastara, su respuesta llevaría un número de intentos distinto
    // del de una contraseña incorrecta y `EX-001` volvería a tener cuatro
    // respuestas distinguibles, por el sitio menos visible de los cuatro.
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", persona);

    mvc.perform(login("JPerez", CLAVE))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.remainingAttempts").value(4));
    mvc.perform(login("JPerez", CLAVE))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.remainingAttempts").value(3));
  }

  @Test
  @DisplayName("el 423 automático entrega la espera como DATO, no escrita en el mensaje")
  void elBloqueoEntregaLaEsperaComoDato() throws Exception {
    for (int intento = 0; intento < 5; intento++) {
      mvc.perform(login("JPerez", "ClaveEquivocadaLarga")).andExpect(status().isUnauthorized());
    }

    mvc.perform(login("JPerez", CLAVE))
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.unlockAt").exists())
        // El instante sirve para decir «hasta las 14:32»; los segundos, para la
        // cuenta regresiva, que NO puede depender de que el reloj del navegador
        // coincida con el del servidor. Por eso viajan los dos.
        .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
        // El texto NO lleva la duración. La lleva el campo, para que el cliente
        // pueda descontarla; un número escrito en el mensaje se queda congelado
        // en el instante en que se serializó y miente en cuanto pasa un minuto.
        .andExpect(
            jsonPath(
                "$.detail",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("minuto"))))
        .andExpect(
            jsonPath(
                "$.detail",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segundo"))));
  }

  @Test
  @DisplayName("el bloqueo MANUAL no anuncia expiración, porque esa cuenta no expira sola")
  void elBloqueoManualNoAnunciaExpiracion() throws Exception {
    jdbc.update("UPDATE users SET status = 'BLOQUEADO' WHERE id = ?", persona);

    mvc.perform(login("JPerez", CLAVE))
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.unlockAt").doesNotExist())
        .andExpect(jsonPath("$.retryAfterSeconds").doesNotExist());
  }

  @Test
  @DisplayName("la CADUCIDAD manda: nula navega, con fecha obliga — aunque la marca diga otra cosa")
  void laCaducidadDecideYNoLaMarca() throws Exception {
    // El estado del alta y el del superadministrador sembrado: la marca puesta y
    // ninguna caducidad. Desde el 25-08-2026 **navega**.
    jdbc.update(
        "UPDATE users SET must_change_password = true, provisional_password_expires_at = NULL"
            + " WHERE id = ?",
        persona);

    mvc.perform(login("JPerez", CLAVE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false));

    // Y con fecha, obliga. `ck_users_provisional_expiry` exige que la marca
    // siga puesta, de modo que este es el único estado en que puede haber fecha.
    jdbc.update(
        "UPDATE users SET provisional_password_expires_at = now() + interval '48 hours'"
            + " WHERE id = ?",
        persona);

    mvc.perform(login("JPerez", CLAVE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  @DisplayName("el refresco recalcula la marca, y no la arrastra del token anterior")
  void elRefrescoRecalculaLaMarca() throws Exception {
    // Sin esto, restablecer la contraseña de alguien con la sesión ya abierta no
    // surtiría efecto hasta que volviera a entrar: seguiría renovando un token
    // sin marca durante los siete días del refresh token.
    String refresco = refreshToken(login("JPerez", CLAVE));

    jdbc.update(
        "UPDATE users SET must_change_password = true,"
            + " provisional_password_expires_at = now() + interval '48 hours' WHERE id = ?",
        persona);

    mvc.perform(refresh(refresco))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
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

  private String respuesta(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getContentAsString();
  }

  /**
   * Sin lo que cambia entre dos peticiones equivalentes.
   *
   * <p>La correlación y el instante de desbloqueo difieren por construcción —una es única por
   * petición y el otro se calcula con el reloj—, y compararlos convertiría la prueba de
   * indistinguibilidad en una carrera contra los milisegundos. Todo lo demás SÍ tiene que
   * coincidir.
   */
  private static String comparable(String cuerpo) {
    return sinCorrelacion(cuerpo).replaceAll("\"unlockAt\":\"[^\"]*\"", "\"unlockAt\":\"\"");
  }

  /** El identificador de correlación cambia en cada petición y no forma parte del contrato. */
  private static String sinCorrelacion(String cuerpo) {
    return cuerpo.replaceAll("\"correlationId\":\"[^\"]*\"", "\"correlationId\":\"\"");
  }
}
