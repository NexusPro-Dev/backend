package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
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
 * Editar, cambiar el estado y eliminar a una persona (`RF-SP-027`, `RF-SP-028`, `RF-SP-029`).
 *
 * <p>Las tres van juntas porque comparten las reglas que las rechazan —`RN-SP-001`, `RN-SP-017` y
 * `RN-SP-022`— y porque la <b>asimetría</b> entre ellas es lo que hay que fijar: el actor <b>sí</b>
 * puede editarse a sí mismo y <b>no</b> puede cambiarse el estado ni eliminarse. Repartidas en tres
 * archivos, esa diferencia no se ve.
 */
@AutoConfigureMockMvc
class UserLifecycleIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN_ROL = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000003";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID juan;

  @BeforeEach
  void preparar() {
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
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)",
        SUPERADMIN,
        SUPERADMIN_ROL);

    juan = crearPersona("jperez", "juan.perez@factech.co", "Juan", "Pérez");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", juan, ADMIN_ROL);
  }

  /**
   * Devuelve a su sitio <b>todo lo que esta clase toca y sobrevive a un borrado de filas</b>: los
   * roles del sistema, que los siembra una migración, y la fila del superadministrador, que las
   * demás pruebas usan como actor.
   *
   * <p>Sin esto, una prueba que renombra al superadministrador o que le retira el rol raíz hace
   * fallar a otra clase por algo que esa clase no estaba comprobando — y el fallo aparece o
   * desaparece según el orden en que la suite ejecute los archivos, que es la peor forma de
   * intermitencia.
   */
  @org.junit.jupiter.api.AfterEach
  void devolverElEstadoCompartidoASuSitio() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update("DELETE FROM roles WHERE is_system = false");
    jdbc.update("UPDATE roles SET status = 'ACTIVO', deleted_at = NULL WHERE is_system = true");
    jdbc.update(
        """
        UPDATE users
           SET first_name = 'Super', last_name = 'Administrador',
               status = 'ACTIVO', deleted_at = NULL,
               locked_until = NULL, failed_attempts = 0
         WHERE id = ?
        """,
        SUPERADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid) ON CONFLICT DO NOTHING",
        SUPERADMIN,
        "01a02a33-4c00-7001-9c4f-5e7ad1000001");
  }

  // ---------------------------------------------------------------------------
  // Editar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-222 — cambia solo lo enviado, y el nombre de usuario sigue idéntico")
  void edicionParcial() throws Exception {
    mvc.perform(editar(juan, "{\"firstName\":\"Juan Carlos\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Juan Carlos"))
        // Los apellidos y el correo NO se tocan.
        .andExpect(jsonPath("$.lastName").value("Pérez"))
        .andExpect(jsonPath("$.email").value("juan.perez@factech.co"))
        // Se devuelve aunque no pueda cambiar: un campo que no se devuelve no
        // puede verificarse en la misma respuesta.
        .andExpect(jsonPath("$.username").value("jperez"));
  }

  @Test
  @DisplayName("VAL-002 — el nulo explícito y el blanco se RECHAZAN, no vacían")
  void ningunCampoSeVacia() throws Exception {
    // Es la diferencia con la edición de un rol, donde el nulo sí era una orden:
    // aquí las columnas son NOT NULL, y aceptarlo produciría un 500.
    mvc.perform(editar(juan, "{\"firstName\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    mvc.perform(editar(juan, "{\"lastName\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName("VAL-001 — el cuerpo vacío no es una edición")
  void cuerpoVacio() throws Exception {
    mvc.perform(editar(juan, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  @Test
  @DisplayName("CA-SP-223 — el nombre de usuario no se ignora en silencio: devuelve 400")
  void elNombreDeUsuarioNoEsEditable() throws Exception {
    // Sin ese rechazo, `username` se ignoraría y quien lo enviara creería
    // haberlo cambiado. Lo mismo con el estado, los roles y la contraseña.
    for (String sobrante :
        java.util.List.of(
            "\"username\":\"otro\"",
            "\"status\":\"INACTIVO\"",
            "\"password\":\"x\"",
            "\"roles\":[]")) {
      mvc.perform(editar(juan, "{\"firstName\":\"Juan\"," + sobrante + "}"))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("el correo se normaliza ANTES de comparar: reenviar el propio no es un cambio")
  void correoNormalizadoAntesDeComparar() throws Exception {
    int antes = eventosDeCambio(juan);

    // Sin normalizar antes, esto parecería un cambio, dispararía la consulta de
    // unicidad y produciría un conflicto de la persona consigo misma.
    mvc.perform(editar(juan, "{\"email\":\"  Juan.Perez@FACTECH.CO  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("juan.perez@factech.co"));

    assertThat(eventosDeCambio(juan)).isEqualTo(antes);
  }

  @Test
  @DisplayName("FA-001 — reenviar lo mismo devuelve 200 y no deja evento")
  void edicionSinEfecto() throws Exception {
    int antes = eventosDeCambio(juan);
    mvc.perform(editar(juan, "{\"firstName\":\"Juan\",\"lastName\":\"Pérez\"}"))
        .andExpect(status().isOk());
    assertThat(eventosDeCambio(juan)).isEqualTo(antes);
  }

  @Test
  @DisplayName("CA-SP-224 — el correo duplicado es 409 y NO dice de quién es")
  void correoDuplicado() throws Exception {
    crearPersona("otra", "ocupado@factech.co", "Otra", "Persona");

    // `RN-SP-016` reserva el correo de los eliminados para siempre: decir de
    // quién es informaría de una cuenta que la respuesta no debe revelar.
    mvc.perform(editar(juan, "{\"email\":\"ocupado@factech.co\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-016"))
        .andExpect(
            jsonPath(
                "$.detail",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("otra"))));
  }

  @Test
  @DisplayName("cambiar el correo deja evento de seguridad; cambiar el apellido, no")
  void soloElCorreoEsUnHechoDeSeguridad() throws Exception {
    UUID correlacion = UUID.randomUUID();
    mvc.perform(
            editar(juan, "{\"lastName\":\"Pérez Gómez\"}")
                .header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isOk());
    assertThat(eventosDeSeguridad(correlacion)).isZero();

    UUID otra = UUID.randomUUID();
    mvc.perform(
            editar(juan, "{\"email\":\"nuevo@factech.co\"}")
                .header("X-Correlation-Id", otra.toString()))
        .andExpect(status().isOk());

    // El correo es la identidad con la que se entra y la llave de la
    // recuperación: cambiarlo es un hecho de seguridad, no una corrección.
    Integer seguridad =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ? AND event_type = 'EMAIL_CHANGED'",
            Integer.class,
            otra);
    assertThat(seguridad).isEqualTo(1);
  }

  @Test
  @DisplayName("el actor SÍ puede editarse a sí mismo: corregir un apellido no concede nada")
  void autoedicionPermitida() throws Exception {
    // Asimetría deliberada con el cambio de estado y la eliminación, donde
    // `RN-SP-017` sí lo prohíbe: allí lo que está en juego es el propio acceso.
    mvc.perform(
            patch("/api/v1/users/{id}", SUPERADMIN)
                .with(editor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastName\":\"Administrador General\"}"))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // Cambiar el estado
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("retirar el acceso exige motivo y revoca las sesiones")
  void desactivar() throws Exception {
    jdbc.update(
        """
        INSERT INTO refresh_tokens (id, user_id, token_hash, family_id, family_started_at, expires_at)
        VALUES (gen_random_uuid(), ?, 'hash-jperez', gen_random_uuid(), now(), now() + interval '7 days')
        """,
        juan);

    mvc.perform(estado(juan, "INACTIVO", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));

    mvc.perform(estado(juan, "INACTIVO", "Baja voluntaria"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"));

    Integer vivas =
        jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
            Integer.class,
            juan);
    assertThat(vivas).isZero();
  }

  @Test
  @DisplayName("VAL-006 — el motivo al REACTIVAR se rechaza, no se ignora")
  void motivoAlReactivar() throws Exception {
    mvc.perform(estado(juan, "INACTIVO", "Baja")).andExpect(status().isOk());

    // Aceptarlo en silencio dejaría un texto que nadie sabría si interpretar
    // como justificación de la reactivación o como resto de otra petición.
    mvc.perform(estado(juan, "ACTIVO", "Vuelve"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));

    mvc.perform(estado(juan, "ACTIVO", null)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("VAL-001 — PENDIENTE no se admite, aunque el esquema lo acepte")
  void pendienteNoSeAdmite() throws Exception {
    // Sería el único camino hacia un estado del que nadie sabe salir.
    mvc.perform(estado(juan, "PENDIENTE", "x"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    mvc.perform(estado(juan, "INVENTADO", "x")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("CA-SP-351 — el bloqueo MANUAL deja lockedUntil en nulo")
  void bloqueoManual() throws Exception {
    mvc.perform(estado(juan, "BLOQUEADO", "Sospecha de compromiso"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOQUEADO"))
        // Nulo con BLOQUEADO significa manual: ese bloqueo no se levanta solo.
        .andExpect(jsonPath("$.lockedUntil").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName(
      "FA-003 — de bloqueo AUTOMÁTICO a MANUAL sí hay cambio, aunque el estado sea el mismo")
  void deAutomaticoAManual() throws Exception {
    jdbc.update(
        "UPDATE users SET status = 'BLOQUEADO', locked_until = now() + interval '1 hour' WHERE id = ?",
        juan);
    int antes = eventosDeCambio(juan);

    // Es el único caso en que pedir el estado que ya se tiene NO es idempotente,
    // y lo decide `locked_until` y no un campo aparte.
    mvc.perform(estado(juan, "BLOQUEADO", "Se convierte en bloqueo definitivo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockedUntil").value(org.hamcrest.Matchers.nullValue()));

    assertThat(eventosDeCambio(juan)).isEqualTo(antes + 1);
  }

  @Test
  @DisplayName("FA-001 — pedir el estado que ya se tiene no cambia nada ni deja evento")
  void estadoIdempotente() throws Exception {
    int antes = eventosDeCambio(juan);
    mvc.perform(estado(juan, "ACTIVO", null)).andExpect(status().isOk());
    assertThat(eventosDeCambio(juan)).isEqualTo(antes);
  }

  @Test
  @DisplayName("FA-002 — reactivar limpia el contador y el bloqueo, y no exige motivo")
  void reactivar() throws Exception {
    jdbc.update(
        """
        UPDATE users SET status = 'BLOQUEADO', locked_until = now() + interval '1 hour',
                         failed_attempts = 4
         WHERE id = ?
        """,
        juan);

    mvc.perform(estado(juan, "ACTIVO", null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"));

    var fila =
        jdbc.queryForMap(
            "SELECT failed_attempts, locked_until, password_hash FROM users WHERE id = ?", juan);
    assertThat(fila.get("failed_attempts")).isEqualTo(0);
    assertThat(fila.get("locked_until")).isNull();
    // La credencial queda INTACTA: reactivar no es restablecer.
    assertThat(fila.get("password_hash")).isNotNull();
  }

  @Test
  @DisplayName("RN-SP-017 — el actor NO puede cambiar el estado de su propia cuenta: 403")
  void estadoSobreSiMismo() throws Exception {
    // 403 y no 409: es una prohibición sobre quién ejecuta, y el mismo cuerpo
    // enviado por otro actor sería válido.
    mvc.perform(
            patch("/api/v1/users/{id}/status", SUPERADMIN)
                .with(editor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\",\"reason\":\"me voy\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-017"));
  }

  @Test
  @DisplayName("RN-SP-001 — desactivar al último superadministrador se rechaza explicando por qué")
  void ultimoSuperadministrador() throws Exception {
    UUID otroAdmin = crearPersona("admin2", "admin2@factech.co", "Otro", "Admin");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", otroAdmin, SUPERADMIN_ROL);
    jdbc.update("DELETE FROM user_roles WHERE user_id = ?", SUPERADMIN);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)",
        SUPERADMIN,
        SUPERADMIN_ROL);
    jdbc.update("DELETE FROM user_roles WHERE user_id = ?", otroAdmin);

    mvc.perform(estado(SUPERADMIN, "INACTIVO", "x")).andExpect(status().isForbidden());

    // Con otro actor y sobre el único superadministrador, el rechazo es 409 y el
    // mensaje explica la CONSECUENCIA, no solo niega.
    UUID otroActor = crearPersona("operador", "op@factech.co", "Ope", "Rador");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", otroActor, SUPERADMIN_ROL);
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id = ? AND role_id = ?::uuid",
        otroActor,
        SUPERADMIN_ROL);

    mvc.perform(
            patch("/api/v1/users/{id}/status", SUPERADMIN)
                .with(user(otroActor.toString()).authorities(() -> "users:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\",\"reason\":\"x\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-001"))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("administración")));
  }

  @Test
  @DisplayName("RN-SP-022 — con equipo a cargo se rechaza diciendo cuántas, nunca quiénes")
  void conEquipoACargo() throws Exception {
    UUID jefe = crearPersona("eljefe", "jefe@factech.co", "El", "Jefe");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", jefe, DIRECTOR);
    UUID manager = crearPersona("elmanager", "mgr@factech.co", "El", "Manager");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", manager, MANAGER);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at) VALUES (gen_random_uuid(), ?, ?, now())",
        jefe,
        manager);

    mvc.perform(estado(manager, "INACTIVO", "Baja"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-022"))
        .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("1")))
        .andExpect(
            jsonPath(
                "$.detail",
                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("eljefe"))));
  }

  @Test
  @DisplayName("CA-SP-411 — REACTIVAR nunca falla por regla, aunque tenga equipo a cargo")
  void reactivarNoFallaPorRegla() throws Exception {
    UUID jefe = crearPersona("eljefe", "jefe@factech.co", "El", "Jefe");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", jefe, DIRECTOR);
    UUID manager = crearPersona("elmanager", "mgr@factech.co", "El", "Manager");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", manager, MANAGER);
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = ?", manager);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at) VALUES (gen_random_uuid(), ?, ?, now())",
        jefe,
        manager);

    // Devolver el acceso no puede dejar a nadie sin administración ni a ningún
    // equipo huérfano: los pasos 4 y 5 ni se evalúan.
    mvc.perform(estado(manager, "ACTIVO", null)).andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------
  // Eliminar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-242 — el motivo se exige ANTES de nada, incluso sobre alguien inexistente")
  void motivoObligatorio() throws Exception {
    mvc.perform(eliminar(juan, "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"));

    // Antes incluso de saber si la persona existe: el Art. V.13 exige rechazar
    // «antes de ejecutarla», y comprobar la existencia primero daría 404.
    mvc.perform(eliminar(UUID.randomUUID(), null)).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("CA-SP-360 — la eliminación captura el estado completo ANTES de borrar nada")
  void laCapturaVaAntes() throws Exception {
    UUID manager = crearPersona("elmanager", "mgr@factech.co", "El", "Manager");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", manager, MANAGER);
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", juan, DIRECTOR);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at) VALUES (gen_random_uuid(), ?, ?, now())",
        juan,
        manager);

    UUID correlacion = UUID.randomUUID();
    mvc.perform(
            eliminar(juan, "Registro duplicado").header("X-Correlation-Id", correlacion.toString()))
        .andExpect(status().isNoContent());

    var fila =
        jdbc.queryForMap(
            """
            SELECT deletion_type, reason, snapshot::text AS snapshot
              FROM audit_deletion_log WHERE correlation_id = ?
            """,
            correlacion);
    assertThat(fila.get("deletion_type")).isEqualTo("LOGICAL");
    assertThat(fila.get("reason")).isEqualTo("Registro duplicado");
    // Después de borrar ya no habría nada que capturar, y NADA fallaría.
    assertThat((String) fila.get("snapshot"))
        .contains("ADMIN")
        .contains("DIRECTOR")
        .contains("elmanager")
        .contains("ACTIVO")
        // Sin ningún campo derivado de la credencial (Art. IV.8).
        .doesNotContain("password")
        .doesNotContain("argon2");
  }

  @Test
  @DisplayName("la eliminación retira roles y membresía, y CIERRA el superior sin borrarlo")
  void loQueLaEliminacionHaceConCadaTabla() throws Exception {
    UUID manager = crearPersona("elmanager", "mgr@factech.co", "El", "Manager");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", manager, MANAGER);
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid)", juan, DIRECTOR);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at) VALUES (gen_random_uuid(), ?, ?, now())",
        juan,
        manager);

    mvc.perform(eliminar(juan, "Duplicado")).andExpect(status().isNoContent());

    Integer roles =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_roles WHERE user_id = ?", Integer.class, juan);
    assertThat(roles).isZero();

    // La fila del superior PERMANECE con su fecha de cierre: es historial de
    // mando, no una versión vieja de un dato.
    var superior =
        jdbc.queryForMap("SELECT ended_at FROM user_supervisors WHERE user_id = ?", juan);
    assertThat(superior.get("ended_at")).isNotNull();

    // Y el estado NO se toca: el registro debe decir en qué situación estaba.
    var persona = jdbc.queryForMap("SELECT status, deleted_at FROM users WHERE id = ?", juan);
    assertThat(persona.get("status")).isEqualTo("ACTIVO");
    assertThat(persona.get("deleted_at")).isNotNull();
  }

  @Test
  @DisplayName("EX-004 — el 404 no distingue «nunca existió» de «ya estaba eliminada»")
  void yaEliminada() throws Exception {
    mvc.perform(eliminar(juan, "Duplicado")).andExpect(status().isNoContent());

    String yaEliminada =
        mvc.perform(eliminar(juan, "Otra vez"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String inexistente =
        mvc.perform(eliminar(UUID.randomUUID(), "Otra vez"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(sinVariables(yaEliminada)).isEqualTo(sinVariables(inexistente));
  }

  @Test
  @DisplayName("RN-SP-017 — el actor no puede eliminarse a sí mismo: 403")
  void eliminarseASiMismo() throws Exception {
    mvc.perform(
            post("/api/v1/users/{id}/deletion", SUPERADMIN)
                .with(borrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"me voy\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SP-017"));
  }

  @Test
  @DisplayName("la eliminación revoca las sesiones dentro de la misma transacción")
  void eliminarRevocaSesiones() throws Exception {
    jdbc.update(
        """
        INSERT INTO refresh_tokens (id, user_id, token_hash, family_id, family_started_at, expires_at)
        VALUES (gen_random_uuid(), ?, 'hash-jperez', gen_random_uuid(), now(), now() + interval '7 days')
        """,
        juan);

    mvc.perform(eliminar(juan, "Duplicado")).andExpect(status().isNoContent());

    String motivo =
        jdbc.queryForObject(
            "SELECT revoked_reason FROM refresh_tokens WHERE user_id = ?", String.class, juan);
    assertThat(motivo).isEqualTo("ACCESO_RETIRADO");
  }

  @Test
  @DisplayName("no hay 409 por tener roles: las asignaciones se retiran CON la persona")
  void tenerRolesNoImpideEliminar() throws Exception {
    // Es la diferencia con la eliminación de un rol, que sí se rechaza si tiene
    // portadores: allí quedarían colgando y aquí no hay nada aguas abajo.
    mvc.perform(eliminar(juan, "Duplicado")).andExpect(status().isNoContent());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder editar(UUID id, String cuerpo) {
    return patch("/api/v1/users/{id}", id)
        .with(editor())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private MockHttpServletRequestBuilder estado(UUID id, String destino, String motivo) {
    String cuerpo =
        "{\"status\":\""
            + destino
            + "\",\"reason\":"
            + (motivo == null ? "null" : "\"" + motivo + "\"")
            + "}";
    return patch("/api/v1/users/{id}/status", id)
        .with(editor())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private MockHttpServletRequestBuilder eliminar(UUID id, String motivo) {
    return post("/api/v1/users/{id}/deletion", id)
        .with(borrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":" + (motivo == null ? "null" : "\"" + motivo + "\"") + "}");
  }

  private RequestPostProcessor editor() {
    return user(SUPERADMIN.toString()).authorities(() -> "users:update");
  }

  private RequestPostProcessor borrador() {
    return user(SUPERADMIN.toString()).authorities(() -> "users:delete");
  }

  private UUID crearPersona(String username, String correo, String nombre, String apellido) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, ?, ?, ?, ?, 'x', false, 'ACTIVO')
        """,
        id,
        username,
        correo,
        nombre,
        apellido);
    return id;
  }

  private int eventosDeCambio(UUID usuario) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity_id = ? AND entity = 'users'",
            Integer.class,
            usuario);
    return total == null ? 0 : total;
  }

  private int eventosDeSeguridad(UUID correlacion) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);
    return total == null ? 0 : total;
  }

  private static String sinVariables(String cuerpo) {
    return cuerpo
        .replaceAll("\"correlationId\":\"[^\"]*\"", "")
        .replaceAll("\"instance\":\"[^\"]*\"", "");
  }
}
