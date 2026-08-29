package com.factech.nexus.modules.system.roles.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Detalle de un rol (`RF-SP-003` · `T-10` y `T-11`).
 *
 * <p>Lo que esta clase vigila de cerca es <b>el conteo de personas asignadas</b>: de él depende que
 * quien va a desactivar o eliminar un rol sepa a cuánta gente afecta. Un conteo que incluyera a las
 * eliminadas —o que excluyera a las bloqueadas— daría un número que parece correcto y no lo es.
 */
@AutoConfigureMockMvc
class RoleDetailIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID hijoSinPermisos;

  @BeforeEach
  void preparar() {
    limpiar();

    // Cuelga de ADMIN, que SÍ declara permisos, y no declara ninguno
    // propio: es lo que hace verificable `RN-SEG-004`.
    hijoSinPermisos = crearRol("AUXILIAR_CONTABLE", "Auxiliar contable", ADMIN);
  }

  @AfterEach
  void devolverElEstadoCompartidoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("CA-SP-016 — devuelve la lista completa de los permisos que el rol declara")
  void permisosDeclarados() throws Exception {
    long declarados =
        jdbc.queryForObject(
            "SELECT count(*) FROM role_permissions WHERE role_id = ?::uuid", Long.class, ADMIN);

    mvc.perform(detalle(ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("ADMIN"))
        .andExpect(jsonPath("$.permissions.length()").value((int) declarados))
        // Cada permiso llega completo: sin `resource` y `action`, el cliente
        // tendría que partir el código por los dos puntos.
        .andExpect(jsonPath("$.permissions[0].code").isNotEmpty())
        .andExpect(jsonPath("$.permissions[0].resource").isNotEmpty())
        .andExpect(jsonPath("$.permissions[0].action").isNotEmpty());
  }

  @Test
  @DisplayName("CA-SP-017 — devuelve el rol padre y el NÚMERO de hijos directos")
  void padreYNumeroDeHijos() throws Exception {
    // DOS hijos directos de ADMIN: MANAGER, que cuelga de él en la siembra, y
    // AUXILIAR_CONTABLE, que crea esta clase. DIRECTOR y AGENTE cuelgan más
    // abajo en la cadena comercial: son descendientes y NO cuentan, que es
    // exactamente lo que este campo tiene que distinguir.
    mvc.perform(detalle(ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentRole.code").value("SUPERADMIN"))
        .andExpect(jsonPath("$.childRoleCount").value(2));

    // Y se mira un SEGUNDO rol, no dos veces el mismo: uno que todavía no tiene
    // hijos y que gana uno aquí. Con un solo rol, un contador que devolviera
    // siempre el total de la tabla pasaría la prueba.
    mvc.perform(detalle(hijoSinPermisos.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.childRoleCount").value(0));

    crearRol("AUXILIAR_JUNIOR", "Auxiliar junior", hijoSinPermisos.toString());

    mvc.perform(detalle(hijoSinPermisos.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.childRoleCount").value(1));
  }

  @Test
  @DisplayName("CA-SP-018 y CA-SP-021 — sin permisos propios la lista va VACÍA, no la del padre")
  void sinHerenciaDePermisos() throws Exception {
    // El modelo no usa herencia: `AUXILIAR_CONTABLE` cuelga de un rol con
    // permisos y no declara ninguno. Si la lista viniera llena, sería porque
    // alguien recorrió la cadena de ancestros — que es justo lo que `RN-SEG-004`
    // prohíbe.
    mvc.perform(detalle(hijoSinPermisos.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions").isEmpty())
        .andExpect(jsonPath("$.parentRole.code").value("ADMIN"));

    // Y el sembrado sin permisos sigue igual.
    mvc.perform(detalle(AGENTE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions").isEmpty());
  }

  @Test
  @DisplayName("CA-SP-019 — el rol raíz devuelve el rol padre vacío")
  void rolRaiz() throws Exception {
    mvc.perform(detalle(SUPERADMIN_ROL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUPERADMIN"))
        .andExpect(jsonPath("$.parentRole").doesNotExist());
  }

  @Test
  @DisplayName("CA-SP-020 — un rol eliminado devuelve el MISMO 404 que uno inexistente")
  void rolEliminado() throws Exception {
    jdbc.update("UPDATE roles SET deleted_at = now() WHERE id = ?", hijoSinPermisos);

    // Ni una pista de que existió: el mismo estado, el mismo tipo y el mismo
    // mensaje que un identificador que nunca correspondió a nada. Reconstruir
    // qué era corresponde a la auditoría de eliminación, con su propio permiso.
    //
    // Se comparan los campos que describen el hecho y no el cuerpo entero:
    // `instance` lleva la ruta pedida y `correlationId` cambia en cada petición,
    // de modo que dos respuestas idénticas en lo que importa nunca serían
    // iguales carácter por carácter.
    String mensajeDelEliminado = detalleDe(hijoSinPermisos.toString());
    String mensajeDelInexistente = detalleDe(UUID.randomUUID().toString());

    org.assertj.core.api.Assertions.assertThat(mensajeDelEliminado)
        .isEqualTo(mensajeDelInexistente)
        .isEqualTo("No existe un rol con ese identificador.");
  }

  @Test
  @DisplayName("CA-SP-022 — sin `roles:read` no se obtiene el detalle")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/roles/{id}", ADMIN)
                .with(user(SUPERADMIN.toString()).authorities(() -> "users:read")))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/v1/roles/{id}", ADMIN)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-SP-149 — cuenta personas distintas: incluye a las bloqueadas, no a las borradas")
  void personasAsignadas() throws Exception {
    // Cero es un dato, no una ausencia: es exactamente lo que se necesita saber
    // antes de eliminar un rol.
    mvc.perform(detalle(hijoSinPermisos.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignedUserCount").value(0));

    UUID activa = crearPersona("jperez", "juan@factech.co", "ACTIVO", false);
    UUID bloqueada = crearPersona("amartinez", "ana@factech.co", "BLOQUEADO", false);
    UUID eliminada = crearPersona("lgomez", "luis@factech.co", "ACTIVO", true);
    asignar(activa, hijoSinPermisos);
    asignar(bloqueada, hijoSinPermisos);
    asignar(eliminada, hijoSinPermisos);

    // Dos y no tres: quien está bloqueado SIGUE portando el rol —y su existencia
    // es lo que impide borrarlo (`RN-SEG-008`)—, mientras que quien está
    // eliminado no existe.
    mvc.perform(detalle(hijoSinPermisos.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignedUserCount").value(2));
  }

  @Test
  @DisplayName("CA-SP-150 — el tamaño de la respuesta no depende de cuántos hijos tenga el rol")
  void sinListaDeHijos() throws Exception {
    // Los cinco cuelgan del rol que crea esta clase y no de uno sembrado: así
    // el número esperado lo fija la propia prueba y no cambia el día que el
    // catálogo del sistema gane o pierda un rol.
    for (int i = 0; i < 5; i++) {
      crearRol("HIJO_" + i, "Hijo " + i, hijoSinPermisos.toString());
    }

    String cuerpo =
        mvc.perform(detalle(hijoSinPermisos.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.childRoleCount").value(5))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // No hay `childRoles` ni página embebida: el listado se obtiene con
    // `GET /api/v1/roles?parentRoleId={id}`, que ya está paginado.
    org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("childRoles");
  }

  @Test
  @DisplayName("un identificador mal formado es 400, no 404")
  void identificadorNoCanonico() throws Exception {
    // `UUID.fromString` del JDK acepta `1-1-1-1-1` y lo convierte en un
    // identificador válido que no existe: sin el conversor canónico, esto
    // devolvería 404 y quien lo viera creería que el rol fue borrado.
    mvc.perform(detalle("1-1-1-1-1")).andExpect(status().isBadRequest());
    mvc.perform(detalle("no-es-un-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("el detalle no expone `deletedAt` ni el actor de los cambios")
  void loQueElDetalleNoLleva() throws Exception {
    String cuerpo =
        mvc.perform(detalle(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.isSystem").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // `deletedAt` sería siempre nulo —un rol eliminado da 404— y el actor vive
    // en la auditoría, no en la tabla de negocio (Art. V.7).
    org.assertj.core.api.Assertions.assertThat(cuerpo)
        .doesNotContain("deletedAt")
        .doesNotContain("createdBy");
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  /** El campo {@code detail} del error, que es lo que el cliente lee. */
  private String detalleDe(String id) throws Exception {
    return com.jayway.jsonpath.JsonPath.read(
        mvc.perform(detalle(id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("https://nexus.factech.co/errors/no-encontrado"))
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.detail");
  }

  private MockHttpServletRequestBuilder detalle(String id) {
    return get("/api/v1/roles/{id}", id)
        .with(user(SUPERADMIN.toString()).authorities(() -> "roles:read"));
  }

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
    return id;
  }

  private UUID crearPersona(String usuario, String correo, String estado, boolean eliminada) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, deleted_at)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, ?, ?)
        """,
        id,
        usuario,
        correo,
        estado,
        eliminada ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
    return id;
  }

  private void asignar(UUID persona, UUID rol) {
    jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", persona, rol);
  }

  private void limpiar() {
    jdbc.update("DELETE FROM refresh_tokens");
    jdbc.update("DELETE FROM user_supervisors");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM user_roles WHERE user_id <> ?", SUPERADMIN);
    jdbc.update("DELETE FROM users WHERE id <> ?", SUPERADMIN);
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN"
            + " (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update("DELETE FROM roles WHERE is_system = false");
    jdbc.update("UPDATE roles SET status = 'ACTIVO', deleted_at = NULL WHERE is_system = true");
  }
}
