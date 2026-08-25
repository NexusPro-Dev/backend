package com.factech.nexus.modules.system.roles.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * Agregar y retirar permisos de un rol (`RF-SP-005`, `RF-SP-006`).
 *
 * <p>Van juntas porque son la misma operación en dos sentidos y <b>no son simétricas</b>: agregar
 * un permiso al padre nunca rompe nada —el conjunto solo crece—, mientras que retirárselo puede
 * dejar a un hijo declarando algo que su padre ya no tiene. Esa asimetría es lo que estas pruebas
 * vigilan.
 *
 * <p>La jerarquía de la prueba es {@code CONTABILIDAD → PADRE → HIJO}. El actor es el
 * superadministrador, que posee el catálogo entero, de modo que `RN-SEG-010` no lo bloquea nunca y
 * lo que se prueba aquí es `RN-SEG-003` y `RN-SEG-005`.
 */
@AutoConfigureMockMvc
class RolePermissionsIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String CONTABILIDAD = "01a02a33-4c00-7003-9c4f-5e7ad1000003";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID padre;
  private UUID hijo;

  /** Dos permisos que CONTABILIDAD declara, y uno que no. */
  private List<UUID> heredables;

  private UUID ajeno;

  @BeforeEach
  void preparar() {
    limpiar();

    heredables =
        jdbc.queryForList(
            "SELECT permission_id FROM role_permissions WHERE role_id = ?::uuid"
                + " ORDER BY permission_id LIMIT 2",
            UUID.class,
            CONTABILIDAD);

    ajeno =
        jdbc.queryForObject(
            "SELECT id FROM permissions WHERE id NOT IN"
                + " (SELECT permission_id FROM role_permissions WHERE role_id = ?::uuid)"
                + " ORDER BY code LIMIT 1",
            UUID.class,
            CONTABILIDAD);

    padre = crearRol("PADRE", "Rol padre", CONTABILIDAD);
    hijo = crearRol("HIJO", "Rol hijo", padre.toString());
  }

  @AfterEach
  void devolverElEstadoCompartidoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // RF-SP-005 — agregar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-031 — asocia los permisos contenidos en el rol padre")
  void agregar() throws Exception {
    mvc.perform(agregar(padre, heredables))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions.length()").value(2));

    assertThat(permisosDe(padre)).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-SP-032 — un solo permiso fuera del rol padre rechaza la operación ENTERA")
  void agregarFueraDelPadre() throws Exception {
    // Aplicar los válidos e ignorar el que falla dejaría el rol en un estado que
    // nadie pidió, y sin forma de saber cuál de los dos resultados se obtuvo.
    mvc.perform(agregar(padre, List.of(heredables.get(0), ajeno)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-003"));

    assertThat(permisosDe(padre)).isZero();
  }

  @Test
  @DisplayName("CA-SP-034 y CA-SP-153 — es idempotente y nunca retira lo ya declarado")
  void agregarEsIdempotente() throws Exception {
    mvc.perform(agregar(padre, heredables)).andExpect(status().isOk());
    long eventos = eventosDeCambio(padre);

    // Repetir la misma petición no duplica, no falla y no registra evento.
    mvc.perform(agregar(padre, heredables))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions.length()").value(2));

    assertThat(permisosDe(padre)).isEqualTo(2);
    assertThat(eventosDeCambio(padre)).isEqualTo(eventos);

    // Y agregar uno solo conserva el otro: agregar es agregar.
    mvc.perform(agregar(padre, List.of(heredables.get(0)))).andExpect(status().isOk());
    assertThat(permisosDe(padre)).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-SP-040 — la contención se valida contra el padre INMEDIATO, sin recorrer arriba")
  void contencionContraElPadreInmediato() throws Exception {
    // `ajeno` lo tiene SUPERADMIN —el abuelo del abuelo— y no CONTABILIDAD. Si
    // la validación recorriera la cadena de ancestros, esto pasaría.
    mvc.perform(agregar(padre, List.of(ajeno)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-003"));
  }

  @Test
  @DisplayName("EX-003 — un permiso que no existe en el catálogo es 422, y se enumeran todos")
  void agregarPermisoInexistente() throws Exception {
    mvc.perform(agregar(padre, List.of(UUID.randomUUID(), UUID.randomUUID())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
  }

  @Test
  @DisplayName("VAL-001 y VAL-006 — sin permisos, o con más de cien, es 400")
  void validacionesDeLaConcesion() throws Exception {
    mvc.perform(agregar(padre, List.of())).andExpect(status().isBadRequest());

    // Ciento uno: el tope se comprueba antes de resolver nada contra el
    // catálogo, de modo que da igual que los identificadores no existan.
    List<UUID> demasiados = java.util.stream.Stream.generate(UUID::randomUUID).limit(101).toList();
    mvc.perform(agregar(padre, demasiados)).andExpect(status().isBadRequest());
    mvc.perform(retirar(padre, demasiados)).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("CA-SP-039 — la auditoría registra solo los permisos realmente agregados")
  void auditoriaDeLaConcesion() throws Exception {
    mvc.perform(agregar(padre, List.of(heredables.get(0)))).andExpect(status().isOk());
    mvc.perform(agregar(padre, heredables)).andExpect(status().isOk());

    List<String> cambios =
        jdbc.queryForList(
            "SELECT changes::text FROM audit_change_log WHERE entity_id = ? ORDER BY occurred_at",
            String.class,
            padre);

    String codigoYaConcedido = codigoDe(heredables.get(0));
    String codigoNuevo = codigoDe(heredables.get(1));

    // Dos eventos. El segundo registra SOLO el permiso que faltaba: anotar los
    // solicitados diría que alguien concedió algo que el rol ya tenía, y eso es
    // indistinguible de una concesión real cuando se investiga meses después.
    assertThat(cambios).hasSize(2);
    assertThat(cambios.get(0)).contains(codigoYaConcedido);
    assertThat(cambios.get(1)).contains(codigoNuevo).doesNotContain(codigoYaConcedido);
  }

  // ---------------------------------------------------------------------------
  // RF-SP-006 — retirar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-041 y CA-SP-046 — retira los permisos y elimina la asociación físicamente")
  void retirar() throws Exception {
    mvc.perform(agregar(padre, heredables)).andExpect(status().isOk());

    mvc.perform(retirar(padre, heredables))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions").isEmpty());

    // Físicamente: la fila desaparece, no queda marcada.
    assertThat(permisosDe(padre)).isZero();
  }

  @Test
  @DisplayName("CA-SP-042, CA-SP-043 y CA-SP-155 — un hijo que lo declara lo impide, aun inactivo")
  void retirarLoQueUnHijoDeclara() throws Exception {
    mvc.perform(agregar(padre, heredables)).andExpect(status().isOk());
    mvc.perform(agregar(hijo, List.of(heredables.get(0)))).andExpect(status().isOk());

    mvc.perform(retirar(padre, List.of(heredables.get(0))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-005"))
        // Dice qué rol y qué permiso: sin ese detalle no hay nada que corregir.
        .andExpect(
            jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("HIJO")));

    // Sin cascada: el permiso sigue en los dos.
    assertThat(permisosDe(padre)).isEqualTo(2);
    assertThat(permisosDe(hijo)).isEqualTo(1);

    // Y un hijo INACTIVO lo impide igual: el invariante vale siempre, no solo
    // mientras el rol concede algo.
    jdbc.update("UPDATE roles SET status = 'INACTIVO' WHERE id = ?", hijo);
    mvc.perform(retirar(padre, List.of(heredables.get(0)))).andExpect(status().isConflict());

    // Un hijo ELIMINADO ya no está vigente y deja de impedirlo.
    jdbc.update("UPDATE roles SET deleted_at = now() WHERE id = ?", hijo);
    mvc.perform(retirar(padre, List.of(heredables.get(0)))).andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-SP-044 — retirar lo que el rol no declaraba es idempotente")
  void retirarEsIdempotente() throws Exception {
    long eventos = eventosDeEliminacion(padre);

    mvc.perform(retirar(padre, heredables))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions").isEmpty());

    assertThat(eventosDeEliminacion(padre)).isEqualTo(eventos);
  }

  @Test
  @DisplayName("CA-SP-045 y CA-SP-156 — la auditoría de eliminación va sin motivo y con códigos")
  void auditoriaDeLaRevocacion() throws Exception {
    mvc.perform(agregar(padre, heredables)).andExpect(status().isOk());
    mvc.perform(retirar(padre, heredables)).andExpect(status().isOk());

    java.util.Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT reason, deletion_type, snapshot::text AS snapshot FROM audit_deletion_log"
                + " WHERE entity_id = ?",
            padre);

    // Sin motivo: es una asociación y no una entidad de negocio, de modo que el
    // Art. V.13 no la alcanza.
    assertThat(fila.get("reason")).isNull();
    assertThat(fila.get("deletion_type")).isEqualTo("ASSOCIATION");
    // Con los códigos, legibles sin resolver referencias contra el catálogo.
    assertThat((String) fila.get("snapshot")).contains("PADRE").contains(":");
  }

  @Test
  @DisplayName("las dos operaciones cruzan las mismas puertas: sistema, actor y rol inexistente")
  void puertasComunes() throws Exception {
    mvc.perform(agregar(UUID.fromString(CONTABILIDAD), heredables))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-SEG-012"));
    mvc.perform(retirar(UUID.fromString(MANAGER), heredables)).andExpect(status().isConflict());

    mvc.perform(agregar(UUID.randomUUID(), heredables)).andExpect(status().isNotFound());
    mvc.perform(retirar(UUID.randomUUID(), heredables)).andExpect(status().isNotFound());

    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", SUPERADMIN, hijo);
    mvc.perform(agregar(hijo, heredables)).andExpect(status().isForbidden());
    mvc.perform(retirar(hijo, heredables)).andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder agregar(UUID roleId, List<UUID> permisos) {
    return post("/api/v1/roles/{id}/permissions", roleId)
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(permisos));
  }

  private MockHttpServletRequestBuilder retirar(UUID roleId, List<UUID> permisos) {
    return post("/api/v1/roles/{id}/permissions/revocations", roleId)
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(permisos));
  }

  private static String cuerpo(List<UUID> permisos) {
    return "{\"permissionIds\":["
        + permisos.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","))
        + "]}";
  }

  private RequestPostProcessor administrador() {
    return user(SUPERADMIN.toString()).authorities(() -> "roles:update", () -> "roles:read");
  }

  private String codigoDe(UUID permissionId) {
    return jdbc.queryForObject(
        "SELECT code FROM permissions WHERE id = ?", String.class, permissionId);
  }

  private long permisosDe(UUID roleId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM role_permissions WHERE role_id = ?", Long.class, roleId);
  }

  private long eventosDeCambio(UUID roleId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity_id = ?", Long.class, roleId);
  }

  private long eventosDeEliminacion(UUID roleId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_deletion_log WHERE entity_id = ?", Long.class, roleId);
  }

  /** Crea un rol SIN permisos: cada prueba decide cuáles concederle. */
  private UUID crearRol(String codigo, String nombre, String padreId) {
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
        padreId);
    return id;
  }

  private void limpiar() {
    // Las tablas de auditoría NO se vacían: ver el mismo comentario en
    // `RoleAdministrationIT`. Los conteos de esta clase van por rol.
    jdbc.update(
        "DELETE FROM user_roles WHERE role_id IN"
            + " (SELECT id FROM roles WHERE is_system = false)");
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
