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
 * Verificación de {@code V22__ac_semilla_permisos_cursos.sql} (`RF-AC-008` · `T-02`, `CA-AC-041`).
 *
 * <p>Seis sembrados con la serie de `AC` continuada, doce asociaciones —también {@code teach} y
 * {@code learn}, a propósito—, ninguna a {@code CLIENTE}.
 */
class CoursesPermissionsSeedIT extends IntegrationTestBase {

  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");
  private static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");
  private static final UUID CLIENTE = UUID.fromString("01a02a33-4c00-7008-9c4f-5e7ad1000008");

  private static final Map<String, String> LOS_SEIS =
      Map.of(
          "courses:read", "01a0b3c7-1000-7005-9c4f-5e7adc000005",
          "courses:create", "01a0b3c7-1000-7006-9c4f-5e7adc000006",
          "courses:update", "01a0b3c7-1000-7007-9c4f-5e7adc000007",
          "courses:delete", "01a0b3c7-1000-7008-9c4f-5e7adc000008",
          "courses:teach", "01a0b3c7-1000-7009-9c4f-5e7adc000009",
          "courses:learn", "01a0b3c7-1000-700a-9c4f-5e7adc000010");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName(
      "los seis permisos `courses:` de ac.md §7 están sembrados, y V28 (RF-SP-060) añade los"
          + " ocho del reparto de courses:read y courses:update")
  void losSeisSembrados() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource = 'courses' ORDER BY code", String.class);
    // V28 (RF-SP-060, 19-09-2026): el listado, el estado y las seis relaciones
    // ganan código propio bajo `courses`; módulos y lecciones son recurso aparte.
    // Los controladores los declaran en el tramo 3 de ese requerimiento.
    assertThat(codigos)
        .containsAll(LOS_SEIS.keySet())
        .hasSize(14)
        .contains(
            "courses:list",
            "courses:change-status",
            "courses:assign-category",
            "courses:revoke-category",
            "courses:assign-recommendation",
            "courses:revoke-recommendation",
            "courses:assign-membership",
            "courses:revoke-membership");
  }

  @Test
  @DisplayName("llevan identificador literal y estable: del quinto al décimo de la serie de AC")
  void identificadoresEstables() {
    LOS_SEIS.forEach(
        (codigo, id) ->
            assertThat(
                    jdbc.queryForObject(
                        "SELECT id::text FROM permissions WHERE code = ?", String.class, codigo))
                .as(codigo)
                .isEqualTo(id));
  }

  @Test
  @DisplayName(
      "los seis están asociados a SUPERADMIN y a ADMIN —teach y learn incluidos—, y a CLIENTE ninguno")
  void asociaciones() {
    assertThat(permisosDe(SUPERADMIN)).containsAll(LOS_SEIS.keySet());
    assertThat(permisosDe(ADMIN)).containsAll(LOS_SEIS.keySet());
    assertThat(permisosDe(CLIENTE)).doesNotContainAnyElementsOf(LOS_SEIS.keySet());
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permissions rp
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE p.resource = 'courses'
                """,
                Integer.class))
        // Catorce por dos roles: V28 dio cada hijo a quien portaba el padre.
        .isEqualTo(28);
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
