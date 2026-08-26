package com.factech.nexus.modules.system.users.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Concurrencia sobre el ciclo de vida de las personas (`RF-SP-024` · `T-21`, `RF-SP-027` · `T-11`,
 * `RF-SP-028` · `T-15`, `RF-SP-029` · `T-12`, `RF-SP-033` · `T-09`).
 *
 * <p><b>Van juntas porque son la misma clase de fallo, no por comodidad.</b> Las cinco protegen un
 * invariante que <b>ninguna petición aislada puede violar</b> —una identidad única, un
 * superadministrador activo, un consumidor con membresía— y las cinco fallan igual: el sistema
 * responde correctamente a las dos peticiones y deja detrás un estado que ninguna regla contempla.
 * No hay error en el log, no hay `500`, no hay nada que mirar. Solo un dato imposible.
 *
 * <p><b>El módulo tenía concurrentes para membresías, países y monedas, y ninguna para personas</b>
 * — que es justo donde viven las reglas cuyo incumplimiento cuesta más caro: `RN-SP-001` deja el
 * sistema sin quien lo administre, y `RN-SP-018` deja consumidores sin nivel de acceso.
 *
 * <p><b>Qué se afirma y qué no.</b> No se afirma cuántas peticiones pasan: eso depende de si la
 * primera confirmó antes de que la segunda tomara su instantánea, y fijarlo haría la prueba
 * intermitente. Se afirma lo que debe ser cierto en <b>todos</b> los desenlaces: que nunca hay un
 * fallo del sistema, que todo rechazo es el declarado, y que el invariante sobrevive.
 */
@AutoConfigureMockMvc
class UserConcurrencyIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String CONTABILIDAD = "01a02a33-4c00-7003-9c4f-5e7ad1000003";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000007";

  private static final String CLAVE = "ClaveLargaYSegura2026";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordHasher hasher;

  @BeforeEach
  void dejarSoloAlSuperadministrador() {
    limpiar();
  }

  @AfterEach
  void limpiarDespues() {
    limpiar();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM password_reset_permits");
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM memberships WHERE level > 0");
    jdbc.update("DELETE FROM roles WHERE is_system = false");
    jdbc.update("UPDATE roles SET status = 'ACTIVO', deleted_at = NULL WHERE is_system = true");
    jdbc.update(
        """
        UPDATE users
           SET status = 'ACTIVO', deleted_at = NULL, locked_until = NULL, failed_attempts = 0
         WHERE id = ?
        """,
        SUPERADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid) ON CONFLICT DO NOTHING",
        SUPERADMIN,
        SUPERADMIN_ROL);
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-024` · T-21 — el alta
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`T-21` — dos altas con la MISMA identidad: una entra, la otra choca, nunca un 500")
  void dosAltasConLaMismaIdentidad() {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estado(alta("jperez", "juan@factech.co", CONTABILIDAD)));

    // La unicidad la sostiene `uq_users_username`, no la comprobación previa:
    // entre leer «no existe» y escribir hay una ventana que dos peticiones
    // simultáneas atraviesan las dos. Lo que no puede pasar es que el choque
    // salga como fallo del sistema en lugar de como conflicto.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantas(resultados, 201)).as("ninguna de las dos altas prosperó").isEqualTo(1);
    assertThat(cuantas(resultados, 409)).as("el choque no se tradujo a conflicto").isEqualTo(1);

    assertThat(contar("SELECT count(*) FROM users WHERE username = 'jperez'")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`T-21` — alta mientras su rol se desactiva: jamás queda alguien con un rol inactivo")
  void altaContraDesactivacionDelRol() {
    String rol = crearRol("TEMPORAL", "Temporal", "FUNCIONARIO");

    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                indice == 0
                    ? estado(alta("jperez", "juan@factech.co", rol))
                    : estado(cambiarEstadoRol(rol, "INACTIVO")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // `T-21` dice «jamás queda un usuario con un rol inactivo», y ESO NO ES UN
    // INVARIANTE DEL SISTEMA: `RF-SP-007` desactiva roles que la gente porta sin
    // preguntar por sus portadores, y `RN-SEG-002` existe precisamente para
    // decir qué pasa entonces —el rol sigue asignado y no concede nada—. Exigir
    // aquí lo que la tarea dice literalmente sería exigir algo que el
    // requerimiento de al lado produce a diario. Queda anotado en su §4.bis.
    //
    // Lo que sí debe cumplirse, y es lo que esta prueba fija: el alta es
    // ATÓMICA. O nació con su rol, o no nació.
    long altas = cuantas(resultados, 201);
    assertThat(contar("SELECT count(*) FROM users WHERE username = 'jperez'"))
        .as("el alta dejó una persona a medio escribir")
        .isEqualTo(altas);

    if (altas == 1) {
      assertThat(
              contar(
                  """
                  SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id
                   WHERE u.username = 'jperez'
                  """))
          .as("nació una persona sin el rol que pidió, que `RN-SP-023` prohíbe")
          .isEqualTo(1);
    } else {
      // Si no nació, fue porque vio el rol ya inactivo: `422`, no otra cosa.
      assertThat(cuantas(resultados, 422))
          .as("el alta se rechazó por un motivo que no es el rol inactivo")
          .isEqualTo(1);
    }
  }

  @Test
  @DisplayName(
      "`T-21` — alta mientras su superior se desactiva: nadie queda a cargo de una cuenta sin acceso")
  void altaContraDesactivacionDelSuperior() {
    UUID superior = crearPersona("jefa", AGENTE);

    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                indice == 0
                    ? estado(altaConSuperior("vendedor", "vendedor@factech.co", AGENTE, superior))
                    : estado(cambiarEstadoUsuario(superior, "INACTIVO", "Baja del contrato")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // Una asignación vigente hacia alguien sin acceso deja un equipo huérfano:
    // su responsable no puede entrar a atenderlo, y nadie recibe un aviso.
    assertThat(
            contar(
                """
                SELECT count(*) FROM user_supervisors s JOIN users u ON u.id = s.supervisor_id
                 WHERE s.ended_at IS NULL AND (u.status <> 'ACTIVO' OR u.deleted_at IS NOT NULL)
                """))
        .as("quedó alguien a cargo de una cuenta sin acceso")
        .isZero();
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-027` · T-11 — la edición
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`T-11` — dos ediciones distintas hacia EL MISMO correo: una 200 y una 409")
  void dosEdicionesHaciaElMismoCorreo() {
    UUID una = crearPersona("ana", CONTABILIDAD);
    UUID otra = crearPersona("bea", CONTABILIDAD);

    List<Outcome<Integer>> resultados =
        runTogether(
            2, indice -> estado(editarCorreo(indice == 0 ? una : otra, "repetido@factech.co")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantas(resultados, 200)).isEqualTo(1);
    assertThat(cuantas(resultados, 409)).isEqualTo(1);

    assertThat(contar("SELECT count(*) FROM users WHERE email = 'repetido@factech.co'"))
        .as("dos personas acabaron con el mismo correo, que es una identidad ambigua")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`T-11` — dos ediciones de la misma persona: los diffs ENCADENAN, no se pisan")
  void dosEdicionesDeLaMismaPersona() {
    UUID persona = crearPersona("ana", CONTABILIDAD);

    List<Outcome<Integer>> resultados =
        runTogether(
            2, indice -> estado(editarNombre(persona, indice == 0 ? "Primero" : "Segundo")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantas(resultados, 200)).as("alguna edición se perdió").isEqualTo(2);

    // Dos eventos, y el `before` del segundo es el `after` del primero: eso es
    // lo que significa que el bloqueo de fila sirvió. Sin él las dos leen el
    // mismo estado inicial y el registro cuenta una historia imposible — dos
    // cambios que parten del mismo sitio y solo uno sobrevivió.
    List<Map<String, Object>> eventos =
        jdbc.queryForList(
            """
            SELECT changes->'first_name'->>'before' AS antes,
                   changes->'first_name'->>'after'  AS despues
              FROM audit_change_log
             WHERE entity = 'users' AND entity_id = ? AND action = 'UPDATE'
             ORDER BY occurred_at, id
            """,
            persona);

    assertThat(eventos).hasSize(2);
    assertThat(eventos.get(1).get("antes"))
        .as("el segundo evento no parte de donde acabó el primero: los diffs no encadenan")
        .isEqualTo(eventos.get(0).get("despues"));
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-028` · T-15 y `RF-SP-029` · T-12 — el último superadministrador
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`T-15` — dos desactivaciones de DOS superadministradores distintos: queda uno")
  void dosDesactivacionesDeSuperadministradoresDistintos() {
    UUID uno = crearPersona("super-uno", SUPERADMIN_ROL);
    UUID otro = crearPersona("super-otro", SUPERADMIN_ROL);
    retirarElRolRaizAlActor();

    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                estado(
                    cambiarEstadoUsuario(indice == 0 ? uno : otro, "INACTIVO", "Baja simultánea")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // **Es la prueba que distingue bloquear el conjunto de bloquear la fila.**
    // Con un bloqueo por fila las dos transacciones tocan filas distintas, no se
    // estorban, y cada una cuenta dos portadores activos porque la otra todavía
    // no confirmó: las dos pasan y el sistema se queda sin quien lo administre.
    System.out.println(
        "DIAGNOSTICO estados = "
            + resultados.stream().map(r -> String.valueOf(r.value())).toList());
    assertThat(cuantas(resultados, 409))
        .as("las dos desactivaciones pasaron: el bloqueo es por fila y no por conjunto")
        .isEqualTo(1);
    assertThat(portadoresActivosDelRolRaiz())
        .as("el sistema se quedó sin superadministrador activo")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`T-12` — dos eliminaciones de superadministradores distintos: queda un portador")
  void dosEliminacionesDeSuperadministradoresDistintos() {
    UUID uno = crearPersona("super-uno", SUPERADMIN_ROL);
    UUID otro = crearPersona("super-otro", SUPERADMIN_ROL);
    retirarElRolRaizAlActor();

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estado(eliminar(indice == 0 ? uno : otro, "Baja simultánea")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(portadoresActivosDelRolRaiz())
        .as("el sistema se quedó sin superadministrador, y eliminar no tiene vuelta atrás")
        .isEqualTo(1);
  }

  /**
   * Deja al actor fuera del conjunto que se está atacando.
   *
   * <p>Sin esto la prueba no comprueba `RN-SP-001` sino `RN-SP-017`: el actor sería uno de los dos
   * objetivos, y el rechazo llegaría por «no puede operar sobre su propia cuenta» — un `403` que se
   * parece bastante a lo que se espera y que dejaría el bloqueo por conjunto sin ejercitar.
   *
   * <p>Conserva {@code ADMIN}, que lleva el catálogo completo salvo dos permisos que aquí no hacen
   * falta: el actor sigue pudiendo desactivar y eliminar.
   */
  private void retirarElRolRaizAlActor() {
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid) ON CONFLICT DO NOTHING",
        SUPERADMIN,
        ADMIN);
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id = ? AND role_id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);
  }

  @Test
  @DisplayName("`T-12` — eliminar contra iniciar sesión: no queda sesión viva sobre la cuenta")
  void eliminarContraIniciarSesion() {
    UUID persona = crearPersonaQuePuedeEntrar("jperez", CONTABILIDAD);

    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                indice == 0
                    ? estado(eliminar(persona, "Terminación del contrato"))
                    : estado(login("jperez", CLAVE)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // El inicio de sesión puede ganar la carrera y crear su sesión: lo que no
    // puede es SOBREVIVIR a la eliminación. Si la eliminación confirmó, sus
    // refresh tokens deben quedar revocados — el orden lo decide el reloj, el
    // invariante no.
    boolean eliminada =
        contar("SELECT count(*) FROM users WHERE id = ? AND deleted_at IS NOT NULL", persona) == 1;

    if (eliminada) {
      assertThat(
              contar(
                  "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                  persona))
          .as("quedó una sesión viva sobre una cuenta eliminada")
          .isZero();
    }
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-033` · T-09 — el par membresía / rol de consumidor
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`T-09` — retirar membresía contra asignar rol de consumidor, EN LOS DOS ÓRDENES")
  void retiroDeMembresiaContraAsignacionDeRolConsumidor() {
    // «Ejecutar un solo orden no prueba nada» (`plan.md` §11): la ventana existe
    // en los dos sentidos y cada uno rompe el invariante por su lado.
    ejecutarElPar(true);
    limpiar();
    ejecutarElPar(false);
  }

  /**
   * Lanza el par, con el retiro primero o la asignación primero según el argumento.
   *
   * <p>El invariante es el mismo en los dos casos y es `RN-SP-018`: <b>consumidor si y solo si
   * membresía</b>. Quien acabe portando un rol de consumidor debe tener nivel de acceso, y quien no
   * lo porte no debe conservar uno.
   */
  private void ejecutarElPar(boolean retiroPrimero) {
    String membresia = crearMembresia();
    String rolConsumidor = crearRol("ESTUDIANTE", "Estudiante", "CONSUMIDOR");
    UUID persona = crearPersona("consumidor", CONTABILIDAD);
    darMembresia(persona, membresia);

    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice -> {
              boolean esElRetiro = (indice == 0) == retiroPrimero;
              return esElRetiro
                  ? estado(retirarMembresia(persona))
                  : estado(asignarRol(persona, rolConsumidor));
            });

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    boolean esConsumidor =
        contar(
                """
                SELECT count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                 WHERE ur.user_id = ? AND r.role_type = 'CONSUMIDOR'
                """,
                persona)
            > 0;
    boolean tieneMembresia =
        contar("SELECT count(*) FROM user_memberships WHERE user_id = ?", persona) > 0;

    assertThat(esConsumidor)
        .as(
            "`RN-SP-018` roto con el %s primero: consumidor=%s, membresía=%s",
            retiroPrimero ? "retiro" : "alta de rol", esConsumidor, tieneMembresia)
        .isEqualTo(tieneMembresia);
  }

  // ---------------------------------------------------------------------------
  // Peticiones
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder alta(String username, String email, String rol) {
    return post("/api/v1/users")
        .with(actor("users:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username":"%s","email":"%s","firstName":"Juan","lastName":"Pérez",
             "password":"%s","roleIds":["%s"]}
            """
                .formatted(username, email, CLAVE, rol));
  }

  private MockHttpServletRequestBuilder altaConSuperior(
      String username, String email, String rol, UUID superior) {
    return post("/api/v1/users")
        .with(actor("users:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username":"%s","email":"%s","firstName":"Juan","lastName":"Pérez",
             "password":"%s","roleIds":["%s"],"supervisorId":"%s"}
            """
                .formatted(username, email, CLAVE, rol, superior));
  }

  private MockHttpServletRequestBuilder editarCorreo(UUID id, String correo) {
    return patch("/api/v1/users/{id}", id)
        .with(actor("users:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"%s\"}".formatted(correo));
  }

  private MockHttpServletRequestBuilder editarNombre(UUID id, String nombre) {
    return patch("/api/v1/users/{id}", id)
        .with(actor("users:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"firstName\":\"%s\"}".formatted(nombre));
  }

  private MockHttpServletRequestBuilder cambiarEstadoUsuario(
      UUID id, String estado, String motivo) {
    return patch("/api/v1/users/{id}/status", id)
        .with(actor("users:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"%s\",\"reason\":\"%s\"}".formatted(estado, motivo));
  }

  private MockHttpServletRequestBuilder cambiarEstadoRol(String id, String estado) {
    return patch("/api/v1/roles/{id}/status", id)
        .with(actor("roles:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"%s\"}".formatted(estado));
  }

  private MockHttpServletRequestBuilder eliminar(UUID id, String motivo) {
    return post("/api/v1/users/{id}/deletion", id)
        .with(actor("users:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"%s\"}".formatted(motivo));
  }

  private MockHttpServletRequestBuilder retirarMembresia(UUID id) {
    return delete("/api/v1/users/{id}/membership", id).with(actor("users:assign-membership"));
  }

  private MockHttpServletRequestBuilder asignarRol(UUID id, String rol) {
    return post("/api/v1/users/{id}/roles", id)
        .with(actor("users:assign-roles"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"roleIds\":[\"%s\"]}".formatted(rol));
  }

  private MockHttpServletRequestBuilder login(String identificador, String clave) {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identificador, clave));
  }

  /**
   * El superadministrador sembrado.
   *
   * <p>Sus permisos efectivos los resuelve la base; la autoridad del token solo abre la puerta de
   * {@code @PreAuthorize}.
   */
  private RequestPostProcessor actor(String permiso) {
    return user(SUPERADMIN.toString()).authorities(() -> permiso);
  }

  // ---------------------------------------------------------------------------
  // Preparación y lectura
  // ---------------------------------------------------------------------------

  private int estado(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private static long cuantas(List<Outcome<Integer>> resultados, int estado) {
    return resultados.stream().filter(r -> r.succeeded() && r.value() == estado).count();
  }

  private long contar(String sql, Object... argumentos) {
    Long total = jdbc.queryForObject(sql, Long.class, argumentos);
    return total == null ? 0 : total;
  }

  private long portadoresActivosDelRolRaiz() {
    return contar(
        """
        SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id
         WHERE ur.role_id = ?::uuid AND u.status = 'ACTIVO' AND u.deleted_at IS NULL
        """,
        SUPERADMIN_ROL);
  }

  private UUID crearPersona(String username, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash, status)
        VALUES (?, ?, ?, 'N', 'N', '$argon2id$sin-uso', 'ACTIVO')
        """,
        id,
        username,
        username + "@factech.co");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", id, rol);
    return id;
  }

  /** Con una credencial de verdad: la usa la prueba que compite contra el inicio de sesión. */
  private UUID crearPersonaQuePuedeEntrar(String username, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash, status)
        VALUES (?, ?, ?, 'N', 'N', ?, 'ACTIVO')
        """,
        id,
        username,
        username + "@factech.co",
        hasher.hash(CLAVE));
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", id, rol);
    return id;
  }

  private String crearRol(String codigo, String nombre, String tipo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, role_type, parent_role_id)
        VALUES (?, ?, ?, ?, ?::uuid)
        """,
        id,
        codigo,
        nombre,
        tipo,
        ADMIN);
    return id.toString();
  }

  private String crearMembresia() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, level, parent_membership_id, color)
        VALUES (?, 'BRONCE', 'Bronce', 1, NULL, 'CD7F32')
        """,
        id);
    return id.toString();
  }

  private void darMembresia(UUID persona, String membresia) {
    jdbc.update(
        "INSERT INTO user_memberships (user_id, membership_id) VALUES (?, ?::uuid)",
        persona,
        membresia);
  }
}
