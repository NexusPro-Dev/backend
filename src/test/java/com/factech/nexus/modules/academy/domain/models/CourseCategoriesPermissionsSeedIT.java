package com.factech.nexus.modules.academy.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de {@code V19__ac_semilla_permisos_categorias.sql} (`RF-AC-001` · `T-02`,
 * `CA-AC-008`).
 *
 * <p>Cuatro sembrados, identificadores estables de la serie propia de `AC`, ocho asociaciones,
 * ninguna a {@code CLIENTE}. Es la primera siembra de permisos posterior a la consolidación, y por
 * eso la mitad que más importa es la de {@code ADMIN}: `V8` lo asoció por exclusión sobre el
 * catálogo de aquel día, y un permiso nuevo no le llega solo.
 */
class CourseCategoriesPermissionsSeedIT extends IntegrationTestBase {

  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");
  private static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");
  private static final UUID CLIENTE = UUID.fromString("01a02a33-4c00-7008-9c4f-5e7ad1000008");

  private static final Map<String, String> LOS_CUATRO =
      Map.of(
          "course-categories:read", "01a0b3c7-1000-7001-9c4f-5e7adc000001",
          "course-categories:create", "01a0b3c7-1000-7002-9c4f-5e7adc000002",
          "course-categories:update", "01a0b3c7-1000-7003-9c4f-5e7adc000003",
          "course-categories:delete", "01a0b3c7-1000-7004-9c4f-5e7adc000004");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName(
      "los cuatro permisos `course-categories:` de ac.md §7 están sembrados, y el quinto es"
          + " course-categories:list, de V28 (RF-SP-060)")
  void losCuatroSembrados() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource = 'course-categories' ORDER BY code",
            String.class);
    // V28 (RF-SP-060, 19-09-2026) separa el listado del detalle: course-categories:list.
    // El controlador lo declara en el tramo 3 de ese requerimiento.
    assertThat(codigos)
        .containsAll(LOS_CUATRO.keySet())
        .hasSize(5)
        .contains("course-categories:list");
  }

  @Test
  @DisplayName("llevan identificador literal y estable: los cuatro primeros de la serie de AC")
  void identificadoresEstables() {
    LOS_CUATRO.forEach(
        (codigo, id) ->
            assertThat(
                    jdbc.queryForObject(
                        "SELECT id::text FROM permissions WHERE code = ?", String.class, codigo))
                .as(codigo)
                .isEqualTo(id));
  }

  @Test
  @DisplayName("los cuatro están asociados a SUPERADMIN y a ADMIN, y a CLIENTE ninguno")
  void asociaciones() {
    assertThat(permisosDe(SUPERADMIN)).containsAll(LOS_CUATRO.keySet());
    assertThat(permisosDe(ADMIN)).containsAll(LOS_CUATRO.keySet());
    assertThat(permisosDe(CLIENTE)).doesNotContainAnyElementsOf(LOS_CUATRO.keySet());
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permissions rp
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE p.resource = 'course-categories'
                """,
                Integer.class))
        // Cinco por dos roles: V28 dio course-categories:list a quien portaba :read.
        .isEqualTo(10);
  }

  private List<String> permisosDe(UUID rol) {
    return jdbc.queryForList(
        """
        SELECT p.code
          FROM role_permissions rp
          JOIN permissions p ON p.id = rp.permission_id
         WHERE rp.role_id = ?
        """,
        String.class,
        rol);
  }
}
