package com.factech.nexus.modules.system.roles.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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

/**
 * Edición, estado, rol padre y eliminación de un rol (`RF-SP-004`, `RF-SP-007`, `RF-SP-008`,
 * `RF-SP-009`).
 *
 * <p>Van juntas porque comparten <b>las tres puertas</b> —el rol existe, no es de sistema y no lo
 * tiene asignado el actor— y porque el modo de fallo que importa es que una de las cuatro se olvide
 * de cruzar alguna. Probarlas por separado dejaría esa comprobación a la disciplina de cada clase.
 */
@AutoConfigureMockMvc
class RoleAdministrationIT extends IntegrationTestBase {

  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String CONTABILIDAD = "01a02a33-4c00-7003-9c4f-5e7ad1000003";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  /** Cuelga de CONTABILIDAD y hereda dos de sus permisos. */
  private UUID rol;

  private UUID hijo;

  @BeforeEach
  void preparar() {
    limpiar();
    rol = crearRol("AUXILIAR", "Auxiliar contable", CONTABILIDAD);
    hijo = crearRol("PRACTICANTE", "Practicante", rol.toString());
  }

  @AfterEach
  void devolverElEstadoCompartidoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // RF-SP-004 — editar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-023 y CA-SP-024 — cambia nombre y descripción, y no toca nada más")
  void edicion() throws Exception {
    mvc.perform(editar(rol, "{\"name\":\"Auxiliar de contabilidad\",\"description\":\"Apoyo.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Auxiliar de contabilidad"))
        .andExpect(jsonPath("$.description").value("Apoyo."))
        // Lo que NO cambia: el código es estable por diseño, la clasificación es
        // inmutable, y el estado y el rol padre tienen su propia operación.
        .andExpect(jsonPath("$.code").value("AUXILIAR"))
        .andExpect(jsonPath("$.roleType").value("FUNCIONARIO"))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.parentRole.code").value("CONTABILIDAD"))
        .andExpect(jsonPath("$.permissions.length()").value(2));
  }

  @Test
  @DisplayName("CA-SP-151 — la clasificación y el código no se admiten en el cuerpo")
  void camposNoAdmitidos() throws Exception {
    // Sin este rechazo se ignorarían en silencio y quien los enviara creería
    // haberlos cambiado.
    mvc.perform(editar(rol, "{\"roleType\":\"VENDEDOR\"}")).andExpect(status().isBadRequest());
    mvc.perform(editar(rol, "{\"code\":\"OTRO\"}")).andExpect(status().isBadRequest());
    mvc.perform(editar(rol, "{\"status\":\"INACTIVO\"}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("VAL-001 y VAL-002 — sin campos, o con el nombre vacío o nulo, es 400")
  void validacionesDeLaEdicion() throws Exception {
    mvc.perform(editar(rol, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    mvc.perform(editar(rol, "{\"name\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    // El nulo explícito NO es una orden en el nombre: la columna es NOT NULL.
    mvc.perform(editar(rol, "{\"name\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName("la descripción SÍ admite nulo: borrarla es una orden legítima")
  void borrarLaDescripcion() throws Exception {
    mvc.perform(editar(rol, "{\"description\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist());
  }

  @Test
  @DisplayName("CA-SP-027 y CA-SP-028 — el nombre en uso se rechaza; el de un eliminado se admite")
  void unicidadDelNombre() throws Exception {
    mvc.perform(editar(rol, "{\"name\":\"Contabilidad\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-001"));

    // El nombre de un rol eliminado queda libre: los índices únicos son
    // parciales sobre `deleted_at IS NULL`.
    jdbc.update("UPDATE roles SET deleted_at = now() WHERE id = ?", hijo);
    mvc.perform(editar(rol, "{\"name\":\"Practicante\"}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-SP-030 — reenviar los valores actuales no registra evento")
  void edicionSinCambio() throws Exception {
    long antes = eventosDeCambio(rol);

    mvc.perform(editar(rol, "{\"name\":\"Auxiliar contable\"}")).andExpect(status().isOk());

    assertThat(eventosDeCambio(rol)).isEqualTo(antes);
  }

  @Test
  @DisplayName("CA-SP-029 — la auditoría registra SOLO los campos que cambiaron")
  void auditoriaDeLaEdicion() throws Exception {
    mvc.perform(editar(rol, "{\"name\":\"Auxiliar mayor\"}")).andExpect(status().isOk());

    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity_id = ? ORDER BY occurred_at DESC"
                + " LIMIT 1",
            String.class,
            rol);

    assertThat(cambios).contains("name").contains("Auxiliar contable").contains("Auxiliar mayor");
    // La descripción no se envió, de modo que no aparece: un diff con campos que
    // nadie tocó haría ilegible la línea de tiempo del rol.
    assertThat(cambios).doesNotContain("description");
  }

  // ---------------------------------------------------------------------------
  // RF-SP-007 — estado
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-049 y CA-SP-051 — desactiva y reactiva, conservando las asignaciones")
  void cambioDeEstado() throws Exception {
    UUID persona = crearPersona("jperez");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", persona, rol);

    mvc.perform(cambiarEstado(rol, "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"));

    // Desactivar NO es retirar: la asignación sigue ahí.
    assertThat(asignacionesDe(rol)).isEqualTo(1);

    mvc.perform(cambiarEstado(rol, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"));
  }

  @Test
  @DisplayName("CA-SP-052 — pedir el estado que ya tiene es idempotente y no registra evento")
  void cambioDeEstadoIdempotente() throws Exception {
    long antes = eventosDeCambio(rol);

    mvc.perform(cambiarEstado(rol, "ACTIVO")).andExpect(status().isOk());

    assertThat(eventosDeCambio(rol)).isEqualTo(antes);
  }

  @Test
  @DisplayName("VAL-001 — un estado fuera del dominio se rechaza enumerando los admitidos")
  void estadoInvalido() throws Exception {
    mvc.perform(cambiarEstado(rol, "SUSPENDIDO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("INACTIVO")));
  }

  // ---------------------------------------------------------------------------
  // RF-SP-008 — rol padre
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-056 y CA-SP-061 — reubica el rol, y sus hijos lo acompañan")
  void reubicacion() throws Exception {
    // ADMIN posee todo lo que CONTABILIDAD posee, de modo que la contención se
    // cumple y el cambio procede.
    mvc.perform(cambiarPadre(rol, ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentRole.code").value("ADMIN"));

    // El hijo sigue colgando del rol movido, sin tocarlo: si el rol cabe en el
    // nuevo padre, sus hijos caben en él por transitividad.
    assertThat(padreDe(hijo)).isEqualTo(rol);
  }

  @Test
  @DisplayName(
      "CA-SP-057 y CA-SP-162 — si el rol excede al nuevo padre se rechaza SIN retirar nada")
  void reubicacionQueExcedeAlNuevoPadre() throws Exception {
    // MANAGER se siembra sin permisos, de modo que no puede acoger a un rol que
    // declara dos.
    mvc.perform(cambiarPadre(rol, MANAGER))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-013"));

    // Ni el padre ni los permisos se tocaron.
    assertThat(padreDe(rol)).isEqualTo(UUID.fromString(CONTABILIDAD));
    assertThat(permisosDe(rol)).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-SP-058 y CA-SP-059 — no se puede colgar de sí mismo ni de un descendiente")
  void reubicacionQueFormaCiclo() throws Exception {
    mvc.perform(cambiarPadre(rol, rol.toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-006"));

    mvc.perform(cambiarPadre(rol, hijo.toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-006"));
  }

  @Test
  @DisplayName("CA-SP-062 — el mismo padre que ya tenía no registra evento")
  void reubicacionSinCambio() throws Exception {
    long antes = eventosDeCambio(rol);

    mvc.perform(cambiarPadre(rol, CONTABILIDAD)).andExpect(status().isOk());

    assertThat(eventosDeCambio(rol)).isEqualTo(antes);
  }

  @Test
  @DisplayName("EX-004 — un padre inexistente o inactivo es 422, no 404")
  void nuevoPadreInvalido() throws Exception {
    // 422 y no 404: el recurso de la ruta es el rol que se mueve, que sí existe.
    // Lo que no resuelve es una referencia del cuerpo.
    mvc.perform(cambiarPadre(rol, UUID.randomUUID().toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));

    UUID inactivo = crearRol("ARCHIVO", "Archivo", CONTABILIDAD);
    jdbc.update("UPDATE roles SET status = 'INACTIVO' WHERE id = ?", inactivo);

    mvc.perform(cambiarPadre(rol, inactivo.toString())).andExpect(status().isUnprocessableEntity());
  }

  // ---------------------------------------------------------------------------
  // RF-SP-009 — eliminar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-064, CA-SP-069 y CA-SP-070 — elimina, libera el código y sale del listado")
  void eliminacion() throws Exception {
    mvc.perform(eliminar(hijo, "{\"reason\":\"Ya no se usa.\"}")).andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/roles/{id}", hijo).with(lector())).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/roles").with(lector()).param("search", "PRACTICANTE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    // El código queda libre: los índices únicos son parciales.
    crearRol("PRACTICANTE", "Practicante nuevo", rol.toString());
  }

  @Test
  @DisplayName("CA-SP-065 — sin motivo se rechaza ANTES de ejecutar nada")
  void eliminacionSinMotivo() throws Exception {
    mvc.perform(eliminar(hijo, "{\"reason\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    mvc.perform(eliminar(hijo, "{}")).andExpect(status().isBadRequest());

    // Y el rol sigue vivo: el rechazo llegó antes de tocarlo.
    mvc.perform(get("/api/v1/roles/{id}", hijo).with(lector())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-SP-066 — con roles hijos se rechaza, e indica CUÁLES")
  void eliminacionConHijos() throws Exception {
    mvc.perform(eliminar(rol, "{\"reason\":\"Reorganización.\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-008"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("PRACTICANTE")));
  }

  @Test
  @DisplayName("CA-SP-067 — con personas asignadas se rechaza, y sugiere desactivar")
  void eliminacionConPersonas() throws Exception {
    UUID persona = crearPersona("amartinez");
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", persona, hijo);

    mvc.perform(eliminar(hijo, "{\"reason\":\"Ya no se usa.\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-008"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("desactív")));
  }

  @Test
  @DisplayName("CA-SP-071 — la auditoría conserva el motivo y el rol completo")
  void auditoriaDeLaEliminacion() throws Exception {
    mvc.perform(eliminar(hijo, "{\"reason\":\"Cierre del programa.\"}"))
        .andExpect(status().isNoContent());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT reason, deletion_type, snapshot::text AS snapshot FROM audit_deletion_log"
                + " WHERE entity_id = ?",
            hijo);

    assertThat(fila.get("reason")).isEqualTo("Cierre del programa.");
    assertThat(fila.get("deletion_type")).isEqualTo("LOGICAL");
    assertThat((String) fila.get("snapshot")).contains("PRACTICANTE").contains("role_type");
  }

  // ---------------------------------------------------------------------------
  // Las tres puertas, comunes a las cuatro operaciones
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-025, CA-SP-053 y CA-SP-068 — ninguna operación toca un rol de sistema")
  void rolDeSistema() throws Exception {
    mvc.perform(editar(UUID.fromString(CONTABILIDAD), "{\"name\":\"Otra cosa\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-012"));

    mvc.perform(cambiarEstado(UUID.fromString(CONTABILIDAD), "INACTIVO"))
        .andExpect(status().isConflict());
    mvc.perform(cambiarPadre(UUID.fromString(CONTABILIDAD), ADMIN))
        .andExpect(status().isConflict());
    mvc.perform(eliminar(UUID.fromString(CONTABILIDAD), "{\"reason\":\"No.\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("CA-SP-060 — el rol raíz no se desactiva, no se elimina y no admite padre")
  void rolRaiz() throws Exception {
    // Se protege aparte de `isSystem` a propósito: un rol raíz inactivo no
    // concede nada y dejaría al sistema sin su última vía de administración.
    // Aquí lo detiene primero la prohibición de los roles de sistema.
    mvc.perform(cambiarEstado(UUID.fromString(SUPERADMIN_ROL), "INACTIVO"))
        .andExpect(status().isConflict());
    mvc.perform(cambiarPadre(UUID.fromString(SUPERADMIN_ROL), ADMIN))
        .andExpect(status().isConflict());
    mvc.perform(eliminar(UUID.fromString(SUPERADMIN_ROL), "{\"reason\":\"No.\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("CA-SP-026 y CA-SP-152 — el rol propio del actor es 403; un ancestro suyo, no")
  void rolDelActor() throws Exception {
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", SUPERADMIN, hijo);

    // 403 y no 409: no es un dato inválido, es una operación que este actor no
    // puede ejecutar sobre este recurso — y queda en la auditoría de seguridad.
    mvc.perform(editar(hijo, "{\"name\":\"Otro nombre\"}")).andExpect(status().isForbidden());
    mvc.perform(cambiarEstado(hijo, "INACTIVO")).andExpect(status().isForbidden());
    mvc.perform(eliminar(hijo, "{\"reason\":\"No.\"}")).andExpect(status().isForbidden());

    // `RN-SEG-011` alcanza solo a los roles asignados DIRECTAMENTE: el padre del
    // rol propio sí puede editarse.
    mvc.perform(editar(rol, "{\"name\":\"Auxiliar general\"}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("un rol inexistente o ya eliminado es 404 en las cuatro operaciones")
  void rolInexistente() throws Exception {
    UUID fantasma = UUID.randomUUID();

    mvc.perform(editar(fantasma, "{\"name\":\"X\"}")).andExpect(status().isNotFound());
    mvc.perform(cambiarEstado(fantasma, "ACTIVO")).andExpect(status().isNotFound());
    mvc.perform(cambiarPadre(fantasma, ADMIN)).andExpect(status().isNotFound());
    mvc.perform(eliminar(fantasma, "{\"reason\":\"X.\"}")).andExpect(status().isNotFound());

    jdbc.update("UPDATE roles SET deleted_at = now() WHERE id = ?", hijo);
    mvc.perform(editar(hijo, "{\"name\":\"X\"}")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("sin el permiso correspondiente, ninguna operación procede")
  void sinPermiso() throws Exception {
    mvc.perform(
            patch("/api/v1/roles/{id}", rol)
                .with(user(SUPERADMIN.toString()).authorities(() -> "roles:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}"))
        .andExpect(status().isForbidden());

    // Eliminar exige `roles:delete` y NO le basta `roles:update`.
    mvc.perform(
            post("/api/v1/roles/{id}/deletion", hijo)
                .with(user(SUPERADMIN.toString()).authorities(() -> "roles:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"X.\"}"))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder editar(UUID id, String cuerpo) {
    return patch("/api/v1/roles/{id}", id)
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private MockHttpServletRequestBuilder cambiarEstado(UUID id, String estado) {
    return patch("/api/v1/roles/{id}/status", id)
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"" + estado + "\"}");
  }

  private MockHttpServletRequestBuilder cambiarPadre(UUID id, String padre) {
    return patch("/api/v1/roles/{id}/parent", id)
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"parentRoleId\":\"" + padre + "\"}");
  }

  private MockHttpServletRequestBuilder eliminar(UUID id, String cuerpo) {
    return post("/api/v1/roles/{id}/deletion", id)
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor administrador() {
    return user(SUPERADMIN.toString())
        .authorities(() -> "roles:update", () -> "roles:delete", () -> "roles:read");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor lector() {
    return user(SUPERADMIN.toString()).authorities(() -> "roles:read");
  }

  private long eventosDeCambio(UUID roleId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity_id = ?", Long.class, roleId);
  }

  private long asignacionesDe(UUID roleId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_roles WHERE role_id = ?", Long.class, roleId);
  }

  private long permisosDe(UUID roleId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM role_permissions WHERE role_id = ?", Long.class, roleId);
  }

  private UUID padreDe(UUID roleId) {
    return jdbc.queryForObject("SELECT parent_role_id FROM roles WHERE id = ?", UUID.class, roleId);
  }

  /** Crea un rol con dos permisos que su padre —CONTABILIDAD— también declara. */
  private UUID crearRol(String codigo, String nombre, String padre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, description, role_type, parent_role_id,
                           status, is_system)
        VALUES (?, ?, ?, 'Rol de prueba.', 'FUNCIONARIO', ?::uuid, 'ACTIVO', false)
        """,
        id,
        codigo,
        nombre,
        padre);

    List<UUID> heredables =
        jdbc.queryForList(
            "SELECT permission_id FROM role_permissions WHERE role_id = ?::uuid"
                + " ORDER BY permission_id LIMIT 2",
            UUID.class,
            CONTABILIDAD);
    heredables.forEach(
        permiso ->
            jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)",
                id,
                permiso));
    return id;
  }

  private UUID crearPersona(String usuario) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO')
        """,
        id,
        usuario,
        usuario + "@factech.co");
    return id;
  }

  private void limpiar() {
    // Las tablas de auditoría NO se vacían. La migración de la semilla escribe
    // en ellas y otras pruebas verifican esas filas: borrarlas hacía fallar a
    // `RegisterUserIT` por algo que esta clase no estaba comprobando. Lo que se
    // acota es la CONSULTA, que cuenta por rol y no en global.
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN"
            + " (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update("DELETE FROM roles WHERE is_system = false");
    jdbc.update("UPDATE roles SET status = 'ACTIVO', deleted_at = NULL WHERE is_system = true");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?::uuid) ON CONFLICT DO NOTHING",
        SUPERADMIN,
        SUPERADMIN_ROL);
  }
}
