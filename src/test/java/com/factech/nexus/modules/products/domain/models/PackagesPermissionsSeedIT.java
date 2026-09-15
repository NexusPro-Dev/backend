package com.factech.nexus.modules.products.domain.models;

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
 * Verificación de {@code V93__seed_packages_permissions.sql} (`RF-PM-017` · `T-02`, `CA-PM-268`).
 *
 * <p>Cuatro sembrados, identificadores estables, ocho asociaciones, ninguna a {@code CLIENTE}. La
 * mitad que se olvida es la de {@code ADMIN}: no falla al aplicar la migración, deja a {@code
 * ADMIN} incapaz de conceder lo que no tiene.
 */
class PackagesPermissionsSeedIT extends IntegrationTestBase {

  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");
  private static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");
  private static final UUID CLIENTE = UUID.fromString("01a02a33-4c00-7008-9c4f-5e7ad1000008");

  private static final Map<String, String> LOS_CUATRO =
      Map.of(
          "packages:create", "01a0a25d-0400-7001-9c4f-5e7ad5000008",
          "packages:read", "01a0a25d-0400-7002-9c4f-5e7ad5000009",
          "packages:update", "01a0a25d-0400-7003-9c4f-5e7ad5000010",
          "packages:delete", "01a0a25d-0400-7004-9c4f-5e7ad5000011");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("los cuatro permisos `packages:` de pm.md §4 están sembrados, y no hay un quinto")
  void losCuatroSembrados() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource = 'packages' ORDER BY code", String.class);
    assertThat(codigos).containsExactlyInAnyOrderElementsOf(LOS_CUATRO.keySet());
  }

  @Test
  @DisplayName("llevan identificador literal y estable: del octavo al undécimo de la serie de PM")
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
                 WHERE p.resource = 'packages'
                """,
                Integer.class))
        .isEqualTo(8);
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
