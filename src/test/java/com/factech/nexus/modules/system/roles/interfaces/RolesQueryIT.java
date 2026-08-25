package com.factech.nexus.modules.system.roles.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Listado de roles (`RF-SP-002` · `T-11` y `T-12`).
 *
 * <p>La suite se apoya en los <b>ocho roles de sistema</b> que siembran `V7` y `V30` y añade cuatro
 * propios: uno con acentos, uno con un comodín en el nombre, uno inactivo y uno eliminado. Esos
 * cuatro son los que hacen verificables la búsqueda, los filtros y la marca de eliminación sin
 * depender de que alguien haya creado datos antes.
 */
@AutoConfigureMockMvc
class RolesQueryIT extends IntegrationTestBase {

  private static final String SUPERADMIN_ROL = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String CONTABILIDAD = "01a02a33-4c00-7003-9c4f-5e7ad1000003";
  private static final String MANAGER = "01a02a33-4c00-7005-9c4f-5e7ad1000005";

  /** Ocho de sistema más tres propios vigentes; el cuarto está eliminado. */
  private static final int VIGENTES = 11;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  @BeforeEach
  void preparar() {
    limpiar();

    crearRol("ADMINISTRACION", "Administración", "FUNCIONARIO", CONTABILIDAD, "ACTIVO", false);
    crearRol("SOPORTE_100", "Soporte 100% remoto", "FUNCIONARIO", CONTABILIDAD, "ACTIVO", false);
    crearRol("ROL_INACTIVO", "Rol inactivo", "FUNCIONARIO", CONTABILIDAD, "INACTIVO", false);
    crearRol("ARCHIVADO", "Rol archivado", "FUNCIONARIO", CONTABILIDAD, "ACTIVO", true);
  }

  @AfterEach
  void devolverElEstadoCompartidoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // Criterios de aceptación
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-SP-009 — página, totales, y ninguna fila repetida ni omitida entre páginas")
  void paginacion() throws Exception {
    mvc.perform(listado().param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(5))
        .andExpect(jsonPath("$.totalElements").value(VIGENTES))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(5))
        .andExpect(jsonPath("$.totalIsExact").value(true));

    // Recorrer las tres páginas debe devolver cada rol exactamente una vez: es
    // lo único que distingue una paginación correcta de una que reparte mal.
    List<String> recorrido = recorrerPaginas("code,asc", 5);
    assertThat(recorrido).hasSize(VIGENTES).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("CA-SP-010 — los eliminados quedan fuera por defecto, y tampoco se cuentan")
  void eliminadosFueraPorDefecto() throws Exception {
    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(VIGENTES))
        .andExpect(jsonPath("$.content[?(@.code == 'ARCHIVADO')]").isEmpty());
  }

  @Test
  @DisplayName("CA-SP-011 — con includeDeleted aparecen, y `deletedAt` es lo que los distingue")
  void eliminadosBajoPeticion() throws Exception {
    // Sin `deletedAt` en la respuesta, este criterio quedaría satisfecho con una
    // mezcla que el cliente no puede separar.
    mvc.perform(listado().param("includeDeleted", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(VIGENTES + 1))
        .andExpect(jsonPath("$.content[?(@.code == 'ARCHIVADO')].deletedAt").isNotEmpty())
        .andExpect(
            jsonPath("$.content[?(@.code == 'ADMINISTRACION')].deletedAt[0]").doesNotExist());
  }

  @Test
  @DisplayName("CA-SP-012 — estado, clasificación y rol padre filtran; el padre es el DIRECTO")
  void filtros() throws Exception {
    mvc.perform(listado().param("status", "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("ROL_INACTIVO"));

    // MANAGER, DIRECTOR y AGENTE son los tres roles de la fuerza comercial.
    mvc.perform(listado().param("roleType", "VENDEDOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));

    // Hijos DIRECTOS de ADMIN: CONTABILIDAD, LIDER_ACADEMICO y MANAGER. DIRECTOR
    // es nieto —cuelga de MANAGER— y no debe aparecer: el filtro es por rol
    // padre, no por subárbol.
    mvc.perform(listado().param("parentRoleId", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[?(@.code == 'DIRECTOR')]").isEmpty());

    // Los tres filtros combinados.
    mvc.perform(
            listado()
                .param("status", "ACTIVO")
                .param("roleType", "VENDEDOR")
                .param("parentRoleId", MANAGER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("DIRECTOR"));
  }

  @Test
  @DisplayName("CA-SP-013 — sin coincidencias es 200 con la colección vacía, nunca 404")
  void sinCoincidencias() throws Exception {
    mvc.perform(listado().param("search", "no-existe-ningun-rol-asi"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0));
  }

  @Test
  @DisplayName("CA-SP-014 — un tamaño por encima del máximo se RECHAZA, no se recorta")
  void tamanoExcesivo() throws Exception {
    // El recorte silencioso es la forma en que este fallo se manifestaría: quien
    // pide 500 recibe 100 y cree que solo hay 100.
    mvc.perform(listado().param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'size')]").isNotEmpty());

    mvc.perform(listado().param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'page')]").isNotEmpty());
  }

  @Test
  @DisplayName("CA-SP-015 — sin `roles:read` no se obtiene dato alguno del catálogo")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/roles").with(user(SUPERADMIN.toString()).authorities(() -> "users:read")))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-SP-147 — la búsqueda ignora acentos y mayúsculas, y casa por fragmento")
  void busquedaSinAcentosNiCaja() throws Exception {
    // Exige PostgreSQL real: `unaccent` no es simulable, y una base embebida
    // daría un falso positivo o un falso fallo.
    mvc.perform(listado().param("search", "administracion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.code == 'ADMINISTRACION')]").isNotEmpty());

    mvc.perform(listado().param("search", "ADMINISTRACIÓN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.code == 'ADMINISTRACION')]").isNotEmpty());

    // Por fragmento y no por prefijo: ningún prefijo encuentra `academico`
    // dentro de `LIDER_ACADEMICO`, que es el motivo del índice de trigramas.
    mvc.perform(listado().param("search", "academico"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("LIDER_ACADEMICO"));
  }

  @Test
  @DisplayName("CA-SP-148 — la fila no lleva permisos ni número de usuarios asignados")
  void loQueLaFilaNoLleva() throws Exception {
    String cuerpo =
        mvc.perform(listado())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // No es una comprobación de redacción: si apareciera cualquiera de los dos,
    // sería porque la consulta tocó `role_permissions` o `user_roles`.
    assertThat(cuerpo).doesNotContain("permissions").doesNotContain("userCount");
  }

  // ---------------------------------------------------------------------------
  // Casos límite de `spec.md` §13 y decisiones de `plan.md`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("una página más allá de la última devuelve vacío y el total INTACTO")
  void paginaMasAllaDeLaUltima() throws Exception {
    // El total no puede deducirse de una página vacía: hacerlo daría el
    // desplazamiento —1980— como total, con la colección vacía y sin ningún
    // error que lo delate.
    mvc.perform(listado().param("page", "99"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(VIGENTES));
  }

  @Test
  @DisplayName("el rol raíz aparece con `parentRole` nulo — detecta un JOIN donde va un LEFT JOIN")
  void rolRaiz() throws Exception {
    mvc.perform(listado().param("search", "SUPERADMIN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].parentRole").doesNotExist());

    mvc.perform(listado().param("search", "CONTABILIDAD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].parentRole.code").value("ADMIN"));
  }

  @Test
  @DisplayName("los comodines del término se tratan como texto literal")
  void busquedaConComodines() throws Exception {
    // Sin escape, `100%` devuelve el catálogo entero: el término dejaría de ser
    // un texto para pasar a ser un patrón.
    mvc.perform(listado().param("search", "100%"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].code").value("SOPORTE_100"));

    // El guion bajo casa consigo mismo y no con cualquier carácter: solo los
    // tres códigos que lo llevan.
    mvc.perform(listado().param("search", "_"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));
  }

  @Test
  @DisplayName("una búsqueda en blanco equivale a no filtrar")
  void busquedaEnBlanco() throws Exception {
    mvc.perform(listado().param("search", "   "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(VIGENTES));
  }

  @Test
  @DisplayName("un rol padre inexistente devuelve colección vacía, no un error")
  void padreInexistente() throws Exception {
    mvc.perform(listado().param("parentRoleId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));

    // Lo que sí se exige es la forma canónica del identificador.
    mvc.perform(listado().param("parentRoleId", "no-es-un-uuid"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("VAL-003 — un campo de ordenamiento arbitrario no llega a la base de datos")
  void ordenamientoArbitrario() throws Exception {
    for (String campo : List.of("deleted_at", "(select 1)", "permissions.code", "description")) {
      mvc.perform(listado().param("sort", campo + ",asc"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    }
  }

  @Test
  @DisplayName("el orden por defecto es el código, y la lista blanca ordena en los dos sentidos")
  void ordenamiento() throws Exception {
    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].code").value("ADMIN"));

    mvc.perform(listado().param("sort", "code,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].code").value("SUPERADMIN"));

    mvc.perform(listado().param("sort", "name,asc")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("ordenar por un campo con valores repetidos no repite ni omite filas")
  void desempatePorIdentificador() throws Exception {
    // Diez de los once roles comparten `status`: sin el desempate por clave
    // primaria, el orden de las filas empatadas queda a criterio del plan de
    // ejecución y puede cambiar entre la página 1 y la 2.
    List<String> recorrido = recorrerPaginas("status,asc", 3);

    assertThat(recorrido).hasSize(VIGENTES).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("los 400 se evalúan JUNTOS y se devuelven juntos, cada uno con su campo")
  void losRechazosSeDevuelvenJuntos() throws Exception {
    // Devolverlos de a uno obliga a corregir la URL parámetro por parámetro:
    // cuatro vueltas para quien escribió mal cuatro cosas.
    mvc.perform(
            listado()
                .param("page", "-1")
                .param("size", "500")
                .param("sort", "inventado,asc")
                .param("status", "INVENTADO")
                .param("roleType", "INVENTADO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'page')]").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'size')]").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'sort')].code").value("VAL-003"))
        .andExpect(jsonPath("$.errors[?(@.field == 'status')].code").value("VAL-004"))
        .andExpect(jsonPath("$.errors[?(@.field == 'roleType')].code").value("VAL-004"));
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  /** Recorre todas las páginas y devuelve los códigos en el orden en que salieron. */
  private List<String> recorrerPaginas(String orden, int tamano) throws Exception {
    List<String> codigos = new ArrayList<>();
    for (int pagina = 0; pagina * tamano < VIGENTES; pagina++) {
      String cuerpo =
          mvc.perform(
                  listado()
                      .param("sort", orden)
                      .param("size", String.valueOf(tamano))
                      .param("page", String.valueOf(pagina)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      for (JsonNode fila : json.readTree(cuerpo).get("content")) {
        codigos.add(fila.get("code").asText());
      }
    }
    return codigos;
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/roles").with(lector());
  }

  private RequestPostProcessor lector() {
    return user(SUPERADMIN.toString()).authorities(() -> "roles:read");
  }

  private void crearRol(
      String codigo,
      String nombre,
      String clasificacion,
      String padre,
      String estado,
      boolean eliminado) {

    jdbc.update(
        """
        INSERT INTO roles (id, code, name, description, role_type, parent_role_id,
                           status, is_system, deleted_at)
        VALUES (?, ?, ?, 'Rol de prueba.', ?, ?::uuid, ?, false, ?)
        """,
        UUID.randomUUID(),
        codigo,
        nombre,
        clasificacion,
        padre,
        estado,
        eliminado ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
  }

  /**
   * Devuelve a su sitio <b>todo lo que esta clase toca y sobrevive a un borrado de filas</b>: los
   * roles del sistema, que los siembra una migración. Sin esto, una prueba posterior fallaría por
   * algo que no estaba comprobando, y el fallo aparecería o desaparecería según el orden de la
   * suite — la peor forma de intermitencia.
   */
  private void limpiar() {
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN"
            + " (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update(
        "DELETE FROM user_roles WHERE role_id IN"
            + " (SELECT id FROM roles WHERE is_system = false)");
    jdbc.update("DELETE FROM roles WHERE is_system = false");
    jdbc.update("UPDATE roles SET status = 'ACTIVO', deleted_at = NULL WHERE is_system = true");
  }
}
