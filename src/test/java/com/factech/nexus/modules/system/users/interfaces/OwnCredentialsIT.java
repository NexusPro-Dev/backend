package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.security.PasswordHasher;
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
 * Contraseñas y perfil propio (`RF-SP-037`, `RF-SP-038`, `RF-SP-039`).
 *
 * <p>Los tres van juntos porque forman <b>un solo recorrido</b>: alguien restablece la contraseña
 * de otra persona, esa persona entra con la credencial provisional, ve en su perfil que le toca
 * cambiarla, y la cambia. Repartidos, cada mitad pasaría sin comprobar que encajan — y lo que este
 * archivo verifica es justamente el encaje.
 */
@AutoConfigureMockMvc
class OwnCredentialsIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String CODIGO_ACOTADO = "AUDITORIA_ACOTADA";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";

  private static final String CLAVE = "ClaveLargaYSegura2026";
  private static final String NUEVA = "OtraClaveLargaDistinta2026";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordHasher hasher;

  private UUID juan;

  @BeforeEach
  void preparar() {
    limpiar();
    juan = crearPersona("jperez", "juan.perez@factech.co");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?",
        juan,
        crearRolAcotado(jdbc, CODIGO_ACOTADO, "Auditoría acotada"));
  }

  @AfterEach
  void devolverElEstadoCompartidoASuSitio() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update("DELETE FROM roles WHERE is_system = false");
    jdbc.update("DELETE FROM memberships WHERE level > 0");
    jdbc.update(
        """
        UPDATE users
           SET first_name = 'Super', last_name = 'Administrador', status = 'ACTIVO',
               deleted_at = NULL, locked_until = NULL, failed_attempts = 0,
               must_change_password = true, provisional_password_expires_at = NULL
         WHERE id = ?
        """,
        SUPERADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid ON CONFLICT DO NOTHING",
        SUPERADMIN,
        SUPERADMIN_ROL);
  }

  // ---------------------------------------------------------------------------
  // Cambiar la propia contraseña
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-327 — la cambia, limpia la marca y revoca TODAS las sesiones")
  void cambioValido() throws Exception {
    abrirSesion(juan);

    mvc.perform(cambiar(juan, CLAVE, NUEVA)).andExpect(status().isNoContent());

    var fila =
        jdbc.queryForMap(
            "SELECT password_hash, must_change_password, failed_attempts FROM users WHERE id = ?",
            juan);
    assertThat(hasher.matches(NUEVA, (String) fila.get("password_hash"))).isTrue();
    assertThat(fila.get("must_change_password")).isEqualTo(false);
    assertThat(fila.get("failed_attempts")).isEqualTo(0);

    // TODAS, incluida la que ejecutó el cambio: no hay forma de distinguirlas
    // sin conocer el token presentado, que esta operación no recibe.
    String motivo =
        jdbc.queryForObject(
            "SELECT revoked_reason FROM refresh_tokens WHERE user_id = ?", String.class, juan);
    assertThat(motivo).isEqualTo("CAMBIO_CONTRASENA");
  }

  @Test
  @DisplayName("CA-SP-328 — no hay forma de dirigir la operación a un tercero")
  void sinIdentificadorEnElCuerpo() throws Exception {
    UUID otra = crearPersona("otra", "otra@factech.co");

    // El cuerpo no declara `userId`, y el deserializador rechaza lo que no
    // declara: enviarlo no la redirige, la rechaza.
    mvc.perform(
            post("/api/v1/auth/password")
                .with(comoActor(juan))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\""
                        + CLAVE
                        + "\",\"newPassword\":\""
                        + NUEVA
                        + "\",\"userId\":\""
                        + otra
                        + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("EX-001 — la contraseña actual incorrecta es 422 y NO 401")
  void actualIncorrecta() throws Exception {
    // Un 401 le diría al cliente que su sesión ya no vale y lo mandaría a
    // iniciar sesión, cuando lo único que ocurrió es que escribió mal su
    // contraseña. La sesión sigue siendo válida.
    mvc.perform(cambiar(juan, "ClaveEquivocadaLarga", NUEVA))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));

    // Y consume intento: es el único paso que lo hace.
    Integer intentos =
        jdbc.queryForObject("SELECT failed_attempts FROM users WHERE id = ?", Integer.class, juan);
    assertThat(intentos).isEqualTo(1);
  }

  @Test
  @DisplayName("EX-003 — la nueva igual a la actual es 400: se decide mirando solo el cuerpo")
  void nuevaIgualALaActual() throws Exception {
    mvc.perform(cambiar(juan, CLAVE, CLAVE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));

    // Y NO consume intento: no hace falta leer nada para rechazarla.
    Integer intentos =
        jdbc.queryForObject("SELECT failed_attempts FROM users WHERE id = ?", Integer.class, juan);
    assertThat(intentos).isZero();
  }

  @Test
  @DisplayName("EL ORDEN: una nueva que no cumple la política NO gasta intento")
  void laPoliticaSeVerificaAntesQueLaActual() throws Exception {
    // Ponerlo al revés haría que cinco peticiones descuidadas de un cliente
    // propio bloquearan la cuenta de su titular.
    mvc.perform(cambiar(juan, "loQueSea", "corta")).andExpect(status().isBadRequest());

    Integer intentos =
        jdbc.queryForObject("SELECT failed_attempts FROM users WHERE id = ?", Integer.class, juan);
    assertThat(intentos).isZero();
  }

  @Test
  @DisplayName("a los cinco fallos la cuenta se bloquea y responde 423")
  void bloqueoPorIntentos() throws Exception {
    for (int i = 0; i < 4; i++) {
      mvc.perform(cambiar(juan, "ClaveEquivocadaLarga", NUEVA))
          .andExpect(status().isUnprocessableEntity());
    }
    mvc.perform(cambiar(juan, "ClaveEquivocadaLarga", NUEVA)).andExpect(status().isLocked());

    // Y con la cuenta ya bloqueada, ni siquiera la contraseña correcta pasa.
    mvc.perform(cambiar(juan, CLAVE, NUEVA)).andExpect(status().isLocked());
  }

  @Test
  @DisplayName("el fallo se audita como PASSWORD_CHANGED con outcome FAILURE, no con otro literal")
  void auditoriaDelFallo() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(
            cambiar(juan, "ClaveEquivocadaLarga", NUEVA)
                .header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isUnprocessableEntity());

    // Un literal aparte habría obligado a alterar el dominio cerrado del
    // esquema para separar lo que una columna ya separa.
    Integer filas =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_security_log
             WHERE correlation_id = ? AND event_type = 'PASSWORD_CHANGED'
               AND outcome = 'FAILURE' AND severity = 'ALTA'
            """,
            Integer.class,
            correlacion);
    assertThat(filas).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Restablecer la de otra persona
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-393 — fija la credencial, marca el cambio obligatorio y NO la devuelve")
  void restablecimientoValido() throws Exception {
    abrirSesion(juan);

    String cuerpo =
        mvc.perform(restablecer(juan, NUEVA))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // La conoce quien la escribió; repetirla la expondría a cualquier registro.
    assertThat(cuerpo).isEmpty();

    var fila =
        jdbc.queryForMap(
            """
            SELECT password_hash, must_change_password, provisional_password_expires_at AS caduca
              FROM users WHERE id = ?
            """,
            juan);
    assertThat(hasher.matches(NUEVA, (String) fila.get("password_hash"))).isTrue();
    assertThat(fila.get("must_change_password")).isEqualTo(true);
    assertThat(fila.get("caduca")).isNotNull();

    // Y las sesiones abiertas con la contraseña anterior caen.
    Integer vivas =
        jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
            Integer.class,
            juan);
    assertThat(vivas).isZero();
  }

  @Test
  @DisplayName("restablecer NO reactiva: la cuenta desactivada sigue desactivada")
  void noTocaElEstado() throws Exception {
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", juan);

    mvc.perform(restablecer(juan, NUEVA)).andExpect(status().isNoContent());

    // Confundirlos convertiría esta operación en una vía lateral para devolver
    // el acceso sin pasar por la que existe para eso, y sin su motivo.
    String estado =
        jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, juan);
    assertThat(estado).isEqualTo("INACTIVO");
  }

  @Test
  @DisplayName("EX-001 — sobre la propia cuenta es 409, y el mensaje dice cuál es la operación")
  void sobreSuPropiaCuenta() throws Exception {
    mvc.perform(
            post("/api/v1/users/{id}/password-reset", SUPERADMIN)
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:reset-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"" + NUEVA + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-017"))
        // Sin esa indicación, quien lo recibe concluye que no puede cambiar su
        // propia contraseña.
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("actual")));
  }

  @Test
  @DisplayName("EX-002 y EX-003 — contraseña débil, y persona inexistente")
  void rechazos() throws Exception {
    mvc.perform(restablecer(juan, "corta")).andExpect(status().isBadRequest());
    mvc.perform(restablecer(UUID.randomUUID(), NUEVA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("el evento lleva la caducidad y NADA derivado de la credencial")
  void auditoriaDelRestablecimiento() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(restablecer(juan, NUEVA).header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isNoContent());

    var fila =
        jdbc.queryForMap(
            """
            SELECT event_type, severity, detail::text AS detalle
              FROM audit_security_log WHERE correlation_id = ?
            """,
            correlacion);
    assertThat(fila.get("event_type")).isEqualTo("PASSWORD_RESET");
    assertThat(fila.get("severity")).isEqualTo("ALTA");
    assertThat((String) fila.get("detalle"))
        .contains("expires_at")
        .doesNotContain(NUEVA)
        .doesNotContain("argon2");
  }

  // ---------------------------------------------------------------------------
  // La caducidad, que es lo que ata las dos operaciones
  // ---------------------------------------------------------------------------
  @Test
  @DisplayName("la credencial provisional VENCIDA ya no corta el acceso: entra y le toca cambiarla")
  void laCredencialProvisionalVencidaAutenticaYObliga() throws Exception {
    mvc.perform(restablecer(juan, NUEVA)).andExpect(status().isNoContent());

    // Recién fijada, entra y le toca cambiarla.
    mvc.perform(login("jperez", NUEVA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));

    jdbc.update(
        "UPDATE users SET provisional_password_expires_at = now() - interval '1 hour' WHERE id = ?",
        juan);

    // Hasta el 25-08-2026 esto respondía `401`: la credencial provisional moría
    // pasado el plazo y había que restablecerla. La decisión de ese día es que
    // la fecha SOLO marca, de modo que vencida y por vencer valen igual.
    //
    // Su coste queda fijado aquí para que nadie lo descubra por sorpresa: la
    // contraseña que fijó otra persona **ya no expira**, y sigue abriendo la
    // puerta mientras nadie entre a cambiarla.
    mvc.perform(login("jperez", NUEVA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  @DisplayName("la contraseña que se pone el TITULAR no caduca")
  void laPropiaNoCaduca() throws Exception {
    mvc.perform(cambiar(juan, CLAVE, NUEVA)).andExpect(status().isNoContent());

    Object caduca =
        jdbc.queryForObject(
            "SELECT provisional_password_expires_at FROM users WHERE id = ?", Object.class, juan);
    assertThat(caduca).isNull();

    mvc.perform(login("jperez", NUEVA)).andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // El perfil propio
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-430 — devuelve el perfil con sus PERMISOS EFECTIVOS")
  void perfilPropio() throws Exception {
    mvc.perform(perfil(juan))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("jperez"))
        .andExpect(jsonPath("$.roles[0].code").value(CODIGO_ACOTADO))
        .andExpect(jsonPath("$.roles[0].status").value("ACTIVO"))
        // Es la razón de que este endpoint exista: sin él la interfaz tenía que
        // deducir del listado de roles qué puede hacer la persona.
        .andExpect(jsonPath("$.permissions[0]").value("audit:read-changes"))
        // FALSO, y esto es la regla del 25-08-2026 en su forma más visible:
        // `juan` se creó con `must_change_password = true` y **sin caducidad**,
        // como cualquier alta. Quien decide es la fecha, y no la hay.
        .andExpect(jsonPath("$.mustChangePassword").value(false));
  }

  @Test
  @DisplayName("CA-SP-473 — devuelve el identificador del actor, y es EL MISMO de su ficha")
  void elPerfilTraeElIdentificador() throws Exception {
    // No basta con que venga un `uuid`. Este campo existe para poner el
    // identificador en el cuerpo de una compra (`R-28` del frontend), de modo
    // que uno PLAUSIBLE Y EQUIVOCADO —el de la sesión, el de otra tabla—
    // dejaría comprar a nombre de otro sin que nada fallara. Se contrasta
    // contra el identificador real de la persona, no contra sí mismo.
    mvc.perform(perfil(juan))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(juan.toString()));
  }

  @Test
  @DisplayName("`me` es un literal: no hay parámetros y no acepta el propio identificador")
  void sinParametros() throws Exception {
    // Pedir el propio detalle por la ruta con identificador es otra operación y
    // exige permiso de lectura de usuarios.
    mvc.perform(get("/api/v1/users/{id}", juan).with(comoActor(juan)))
        .andExpect(status().isForbidden());

    // Y un parámetro de consulta no cambia nada: no hay ninguno declarado.
    mvc.perform(get("/api/v1/users/me").with(comoActor(juan)).param("id", SUPERADMIN.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("jperez"));
  }

  @Test
  @DisplayName("CA-SP-449 — devuelve el superior comercial y NUNCA el equipo")
  void soloElSuperior() throws Exception {
    UUID manager = crearPersona("elmanager", "mgr@factech.co");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        manager,
        MANAGER);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        juan,
        DIRECTOR);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at) VALUES (gen_random_uuid(), ?, ?, now())",
        juan,
        manager);
    UUID agente = crearPersona("elagente", "ag@factech.co");
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at) VALUES (gen_random_uuid(), ?, ?, now())",
        agente,
        juan);

    String cuerpo = mvc.perform(perfil(juan)).andReturn().getResponse().getContentAsString();

    // A quién reporta uno es un dato del actor; quiénes dependen de uno es un
    // conjunto de terceros — la distinción que sostiene la reserva de D-22.
    assertThat(cuerpo).contains("elmanager").doesNotContain("elagente").doesNotContain("team");
  }

  @Test
  @DisplayName("el perfil NO lleva identificador, ni fechas de la ficha, ni la credencial")
  void loQueElPerfilNoLleva() throws Exception {
    String cuerpo = mvc.perform(perfil(juan)).andReturn().getResponse().getContentAsString();

    assertThat(cuerpo)
        .doesNotContain("createdAt")
        .doesNotContain("updatedAt")
        .doesNotContain("lockedUntil")
        .doesNotContain("failedAttempts")
        .doesNotContain("passwordHash")
        .doesNotContain("argon2");
  }

  @Test
  @DisplayName("EX-002 — la cuenta eliminada tras emitirse el token devuelve 401, no 404")
  void cuentaEliminada() throws Exception {
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", juan);

    // Lo que ha dejado de valer es la sesión, no la ruta.
    mvc.perform(perfil(juan)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("sin membresía y sin superior, los dos campos van AUSENTES y no en nulo")
  void ausentesYNoNulos() throws Exception {
    String cuerpo = mvc.perform(perfil(juan)).andReturn().getResponse().getContentAsString();

    assertThat(cuerpo).doesNotContain("membership").doesNotContain("supervisor");
  }

  @Test
  @DisplayName("el recorrido completo: restablecer, entrar, ver que toca cambiarla, y cambiarla")
  void elRecorridoCompleto() throws Exception {
    // Es lo que ninguno de los tres verifica por su cuenta.
    mvc.perform(restablecer(juan, NUEVA)).andExpect(status().isNoContent());

    mvc.perform(login("jperez", NUEVA))
        .andExpect(status().isOk())
        // Autentica Y ADVIERTE: hace falta una sesión para poder cambiarla.
        .andExpect(jsonPath("$.mustChangePassword").value(true));

    mvc.perform(perfil(juan))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));

    String propia = "MiPropiaClaveLarga2026";
    mvc.perform(cambiar(juan, NUEVA, propia)).andExpect(status().isNoContent());

    mvc.perform(perfil(juan))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false));

    mvc.perform(login("jperez", propia)).andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder cambiar(UUID quien, String actual, String nueva) {
    return post("/api/v1/auth/password")
        .with(comoActor(quien))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"currentPassword\":\"" + actual + "\",\"newPassword\":\"" + nueva + "\"}");
  }

  private MockHttpServletRequestBuilder restablecer(UUID objetivo, String nueva) {
    return post("/api/v1/users/{id}/password-reset", objetivo)
        .with(user(SUPERADMIN.toString()).authorities(() -> "users:reset-password"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"newPassword\":\"" + nueva + "\"}");
  }

  private MockHttpServletRequestBuilder perfil(UUID quien) {
    return get("/api/v1/users/me").with(comoActor(quien));
  }

  private MockHttpServletRequestBuilder login(String identificador, String clave) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"" + identificador + "\",\"password\":\"" + clave + "\"}");
  }

  /**
   * Autenticado y <b>sin un solo permiso</b>.
   *
   * <p>Es deliberado: los dos endpoints que este actor usa —el perfil propio y el cambio de la
   * propia contraseña— <b>no exigen permiso alguno</b>, porque no hay recurso ajeno que proteger.
   * Un actor con permisos no distinguiría eso de tenerlos.
   */
  private RequestPostProcessor comoActor(UUID quien) {
    return user(quien.toString()).authorities();
  }

  private void abrirSesion(UUID quien) {
    jdbc.update(
        """
        INSERT INTO refresh_tokens (id, user_id, token_hash, family_id, family_started_at, expires_at)
        VALUES (gen_random_uuid(), ?, ?, gen_random_uuid(), now(), now() + interval '7 days')
        """,
        quien,
        "hash-" + quien);
  }

  private UUID crearPersona(String username, String correo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, ?, ?, 'Juan', 'Pérez', ?, true, 'ACTIVO')
        """,
        id,
        username,
        correo,
        hasher.hash(CLAVE));
    return id;
  }
}
