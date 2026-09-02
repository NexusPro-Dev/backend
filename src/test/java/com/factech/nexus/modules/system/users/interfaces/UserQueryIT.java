package com.factech.nexus.modules.system.users.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Listado y detalle de personas (`RF-SP-025`, `RF-SP-026`).
 *
 * <p>Van juntos porque comparten la mitad de las decisiones —la semántica de la membresía vencida,
 * qué roles se incluyen, qué campos <b>no</b> se devuelven— y porque la asimetría entre ambos es
 * deliberada: el listado no dice el estado de cada rol y el detalle sí.
 */
@AutoConfigureMockMvc
class UserQueryIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  /** Código del rol acotado que esta clase se fabrica. */
  private static final String CODIGO_ACOTADO = "AUDITORIA_ACOTADA";

  /** Rol con DOS permisos y ninguno más: es lo que hace observable `effectivePermissions`. */
  private String rolAcotado;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID juan;
  private UUID ana;
  private String consumidor;
  private String oro;

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
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        SUPERADMIN,
        SUPERADMIN_ROL);

    restaurarRolesDelSistema();

    rolAcotado = crearRolAcotado(jdbc, CODIGO_ACOTADO, "Auditoría acotada").toString();
    consumidor = crearRol("ESTUDIANTE", "CONSUMIDOR");
    oro = crearMembresia();

    juan = crearPersona("jperez", "juan.perez@factech.co", "Juan", "Pérez", "ACTIVO");
    ana = crearPersona("amartinez", "ana@factech.co", "Ana", "Martínez", "INACTIVO");

    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        juan,
        rolAcotado);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        juan,
        consumidor);
    jdbc.update(
        "INSERT INTO user_memberships (user_id, membership_id, started_at) VALUES (?, ?::uuid, now())",
        juan,
        oro);
  }

  private void restaurarRolesDelSistema() {
    jdbc.update("UPDATE roles SET status = 'ACTIVO', deleted_at = NULL WHERE is_system = true");
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
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid ON CONFLICT DO NOTHING",
        SUPERADMIN,
        "01a02a33-4c00-7001-9c4f-5e7ad1000001");
  }

  // ---------------------------------------------------------------------------
  // Listado
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-343 — cada fila lleva sus roles, y VACÍA cuando no tiene ninguno")
  void rolesPorFila() throws Exception {
    // Nunca nulo ni ausente: una persona sin roles es un estado válido, y
    // distinguirlo con la ausencia del campo obligaría a tratar dos formas.
    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.username == 'jperez')].roles.length()").value(2))
        .andExpect(jsonPath("$.content[?(@.username == 'amartinez')].roles[0]").doesNotExist());
  }

  @Test
  @DisplayName("el orden por defecto es el APELLIDO, no el nombre de usuario")
  void ordenPorDefecto() throws Exception {
    // Es la lista desde la que se administra el acceso, y quien la mira busca a
    // alguien por su apellido.
    // Administrador —el superadministrador sembrado—, Martínez y Pérez, en ese
    // orden. Con el nombre de usuario mandando, la primera sería `amartinez` por
    // casualidad y la prueba no distinguiría un criterio del otro.
    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].lastName").value("Administrador"))
        .andExpect(jsonPath("$.content[1].lastName").value("Martínez"))
        .andExpect(jsonPath("$.content[2].lastName").value("Pérez"));
  }

  @Test
  @DisplayName("VAL-003 — no se admite ordenar por ningún campo de la credencial")
  void ordenamientoProhibido() throws Exception {
    // Ordenar por la marca de cambio obligatorio produce la lista de quien no ha
    // cambiado su contraseña inicial: una lista que nadie debería poder pedir.
    for (String campo : java.util.List.of("passwordHash", "mustChangePassword", "failedAttempts")) {
      mvc.perform(listado().param("sort", campo + ",asc"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    }
  }

  @Test
  @DisplayName("los campos de la lista blanca sí ordenan, en los dos sentidos")
  void ordenamientoAdmitido() throws Exception {
    mvc.perform(listado().param("sort", "username,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].username").value("superadmin"));

    mvc.perform(listado().param("sort", "email,asc")).andExpect(status().isOk());
    mvc.perform(listado().param("sort", "username,ascendente")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("VAL-004 — un estado fuera de su dominio se rechaza; PENDIENTE se admite")
  void filtroPorEstado() throws Exception {
    mvc.perform(listado().param("status", "INVENTADO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));

    // Está declarado en el esquema y sin usar: excluirlo del dominio obligaría a
    // ampliarlo el día que exista el flujo de activación.
    mvc.perform(listado().param("status", "PENDIENTE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    mvc.perform(listado().param("status", "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].username").value("amartinez"));
  }

  @Test
  @DisplayName("CA-SP-207 — un filtro por rol inexistente devuelve colección vacía, no un error")
  void filtroPorRolInexistente() throws Exception {
    // Validarlo añadiría una consulta por petición para producir un fallo que la
    // especificación no quiere.
    mvc.perform(listado().param("roleId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(listado().param("roleId", rolAcotado))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  @DisplayName("el filtro por rol NO multiplica filas: cuenta personas, no asignaciones")
  void elFiltroPorRolNoDuplica() throws Exception {
    // Con un JOIN, `totalElements` contaría asignaciones en cuanto alguien
    // añadiera un segundo valor al filtro. Con EXISTS no puede duplicar.
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
        juan,
        ADMIN);

    mvc.perform(listado().param("roleId", rolAcotado))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("CA-SP-344 — la búsqueda va por fragmento, sin acentos y sin distinguir mayúsculas")
  void busqueda() throws Exception {
    mvc.perform(listado().param("search", "PEREZ"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].username").value("jperez"));

    // Por fragmento de correo, que es deliberado: quien tiene este permiso ve la
    // lista entera de todos modos.
    mvc.perform(listado().param("search", "juan.perez@"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));

    // En blanco equivale a ausente: buscar por espacios es no buscar.
    mvc.perform(listado().param("search", "   "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));
  }

  @Test
  @DisplayName("el comodín de LIKE se escapa: `%` busca un porcentaje, no todo")
  void busquedaConComodines() throws Exception {
    // Sin escapar, el término del cliente dejaría de ser un texto para pasar a
    // ser un patrón, y quien busque una dirección con guion bajo no encontraría
    // la suya.
    mvc.perform(listado().param("search", "%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    mvc.perform(listado().param("search", "_"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("CA-SP-204 — los eliminados solo aparecen si se piden, y `deletedAt` siempre está")
  void eliminados() throws Exception {
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", ana);

    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        // Presente y en nulo en los vigentes: el campo no aparece y desaparece.
        .andExpect(jsonPath("$.content[0].deletedAt").value(org.hamcrest.Matchers.nullValue()));

    mvc.perform(listado().param("includeDeleted", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[?(@.username == 'amartinez')].deletedAt").isNotEmpty());
  }

  @Test
  @DisplayName("CA-SP-366 — una membresía VENCIDA no es lo mismo que no tener")
  void membresiaVencida() throws Exception {
    mvc.perform(listado().param("search", "perez"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].membership.code").value("ORO"))
        .andExpect(jsonPath("$.content[0].membership.current").value(true));

    jdbc.update(
        """
        UPDATE user_memberships
           SET started_at = now() - interval '3 days', ends_at = now() - interval '1 day'
         WHERE user_id = ?
        """,
        juan);

    // No es nula: `current` dice cuál de los dos casos es y `endsAt` hasta cuándo
    // fue.
    mvc.perform(listado().param("search", "perez"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].membership.code").value("ORO"))
        .andExpect(jsonPath("$.content[0].membership.current").value(false))
        .andExpect(jsonPath("$.content[0].membership.endsAt").isNotEmpty());

    // Y el filtro por membresía VIGENTE deja de encontrarla.
    mvc.perform(listado().param("membershipId", oro))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("CA-SP-208 — ninguna fila lleva nada derivado de la credencial")
  void sinRastroDeLaCredencial() throws Exception {
    String cuerpo = mvc.perform(listado()).andReturn().getResponse().getContentAsString();

    assertThat(cuerpo)
        .doesNotContain("password")
        .doesNotContain("Hash")
        .doesNotContain("argon2")
        .doesNotContain("mustChangePassword")
        // Ni permisos efectivos ni el bloqueo: eso es el detalle.
        .doesNotContain("effectivePermissions")
        .doesNotContain("lockedUntil");
  }

  @Test
  @DisplayName("VAL-002 — un tamaño de página fuera de rango se rechaza, no se recorta")
  void paginacion() throws Exception {
    mvc.perform(listado().param("size", "500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));

    // Una página más allá de la última no es un error.
    mvc.perform(listado().param("page", "99"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName("el total es exacto y no depende de la página")
  void total() throws Exception {
    mvc.perform(listado().param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.totalIsExact").value(true));

    mvc.perform(listado().param("size", "1").param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  // ---------------------------------------------------------------------------
  // Detalle
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-213 — el detalle lleva los permisos efectivos, ordenados y sin duplicados")
  void detalle() throws Exception {
    mvc.perform(detalle(juan))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("jperez"))
        .andExpect(jsonPath("$.roles.length()").value(2))
        // El estado de cada rol: el listado no lo lleva y el detalle sí.
        .andExpect(jsonPath("$.roles[0].status").isNotEmpty())
        .andExpect(jsonPath("$.effectivePermissions").isArray())
        .andExpect(jsonPath("$.effectivePermissions[0]").value("audit:read-changes"))
        .andExpect(jsonPath("$.effectivePermissions[1]").value("audit:read-deletions"))
        // El nivel, que el listado no devuelve.
        .andExpect(jsonPath("$.membership.level").value(1));
  }

  @Test
  @DisplayName("FA-002 — con TODOS los roles inactivos, la lista de permisos llega vacía")
  void rolesInactivos() throws Exception {
    // Las dos mitades juntas son lo único que explica por qué una persona CON
    // roles no puede hacer nada.
    jdbc.update("UPDATE roles SET status = 'INACTIVO' WHERE id = ?::uuid", rolAcotado);

    mvc.perform(detalle(juan))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles.length()").value(2))
        .andExpect(
            jsonPath("$.roles[?(@.code == '" + CODIGO_ACOTADO + "')].status").value("INACTIVO"))
        .andExpect(jsonPath("$.effectivePermissions").isEmpty());
  }

  @Test
  @DisplayName("un rol ELIMINADO no aparece ni concede")
  void rolEliminado() throws Exception {
    jdbc.update("UPDATE roles SET deleted_at = now() WHERE id = ?::uuid", consumidor);

    mvc.perform(detalle(juan))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles.length()").value(1))
        .andExpect(jsonPath("$.roles[0].code").value(CODIGO_ACOTADO));
  }

  @Test
  @DisplayName("CA-SP-218 y CA-SP-346 — sin intentos fallidos y sin dato alguno de la credencial")
  void elDetalleNoFiltraNada() throws Exception {
    String cuerpo = mvc.perform(detalle(juan)).andReturn().getResponse().getContentAsString();

    assertThat(cuerpo)
        // Diría a cualquiera con permiso de lectura cuántos intentos le quedan a
        // una cuenta antes de bloquearse.
        .doesNotContain("failedAttempts")
        .doesNotContain("password")
        .doesNotContain("mustChangePassword")
        .doesNotContain("argon2")
        // Ni el superior comercial: eso tiene su propio endpoint.
        .doesNotContain("supervisor")
        .doesNotContain("deletedAt");
  }

  @Test
  @DisplayName("CA-SP-219 — la persona eliminada devuelve el MISMO 404 que una inexistente")
  void eliminadaYInexistente() throws Exception {
    String inexistente =
        mvc.perform(detalle(UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", juan);
    String eliminada =
        mvc.perform(detalle(juan))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Sin ninguna pista de que existió: reconstruir qué era corresponde a la
    // auditoría de eliminación, que tiene su propio permiso.
    assertThat(sinVariables(eliminada)).isEqualTo(sinVariables(inexistente));
  }

  @Test
  @DisplayName("VAL-001 — el identificador no canónico es 400, no 404")
  void identificadorNoCanonico() throws Exception {
    mvc.perform(get("/api/v1/users/1-1-1-1-1").with(lector()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  @Test
  @DisplayName("los permisos del detalle salen del MISMO sitio que la autorización")
  void mismaFuenteQueLaAutorizacion() throws Exception {
    // Si el detalle dice que alguien tiene un permiso, el filtro que atienda su
    // próxima petición dirá lo mismo — porque ambos preguntan al mismo sitio.
    mvc.perform(detalle(SUPERADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectivePermissions", org.hamcrest.Matchers.hasItem("users:read")));
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/users").with(lector());
  }

  private MockHttpServletRequestBuilder detalle(UUID id) {
    return get("/api/v1/users/{id}", id).with(lector());
  }

  private RequestPostProcessor lector() {
    return user(SUPERADMIN.toString()).authorities(() -> "users:read");
  }

  private UUID crearPersona(
      String username, String correo, String nombre, String apellido, String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status)
        VALUES (?, ?, ?, ?, ?, 'x', false, ?)
        """,
        id,
        username,
        correo,
        nombre,
        apellido,
        estado);
    return id;
  }

  private String crearRol(String codigo, String tipo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO roles (id, code, name, role_type, parent_role_id) VALUES (?, ?, ?, ?, ?::uuid)",
        id,
        codigo,
        codigo,
        tipo,
        ADMIN);
    return id.toString();
  }

  private String crearMembresia() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (?, 'ORO', 'Oro', NULL, 1, 'D4AF37')",
        id);
    return id.toString();
  }

  /** El identificador de correlación y la ruta cambian por petición. */
  private static String sinVariables(String cuerpo) {
    return cuerpo
        .replaceAll("\"correlationId\":\"[^\"]*\"", "")
        .replaceAll("\"instance\":\"[^\"]*\"", "");
  }
}
