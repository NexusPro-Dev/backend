package com.factech.nexus.modules.system.teams.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La siembra de los ocho `teams:` (`V34`, `CA-SP-738`).
 *
 * <p>Tres cosas, y la tercera es la que no se prueba sola en ningún otro sitio: que los ocho estén
 * con su literal, que `SUPERADMIN` y `ADMIN` los porten, y que <b>ningún otro rol</b> los porte —ni
 * `MANAGER`—, porque administrar cómo se organiza la cúspide es tarea de administración y ningún
 * manager organiza su propio equipo.
 */
class TeamsPermissionsSeedIT extends IntegrationTestBase {

  private static final String SUPERADMIN = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName(
      "`CA-SP-738` — los ocho teams: existen, uno por operación, con literal de la serie de SP")
  void losOchoConSuLiteral() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource = 'teams' ORDER BY code", String.class);

    assertThat(codigos)
        .containsExactlyInAnyOrder(
            "teams:list",
            "teams:read",
            "teams:create",
            "teams:update",
            "teams:change-status",
            "teams:delete",
            "teams:assign-members",
            "teams:remove-members");

    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'teams:list'", String.class))
        .isEqualTo("01a0c143-2c00-700d-9c4f-5e7ad000002c");
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'teams:remove-members'",
                String.class))
        .isEqualTo("01a0c143-2c00-7014-9c4f-5e7ad0000033");
  }

  @Test
  @DisplayName(
      "`CA-SP-738` — los porta SUPERADMIN y ADMIN, y NINGÚN otro rol: ni MANAGER organiza su"
          + " propio equipo")
  void soloLosDosRolesDeSistema() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id ="
                    + " rp.permission_id WHERE p.resource = 'teams' AND rp.role_id = CAST(? AS"
                    + " uuid)",
                Integer.class,
                SUPERADMIN))
        .isEqualTo(8);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id ="
                    + " rp.permission_id WHERE p.resource = 'teams' AND rp.role_id = CAST(? AS"
                    + " uuid)",
                Integer.class,
                ADMIN))
        .isEqualTo(8);

    assertThat(
            jdbc.queryForList(
                "SELECT r.code FROM role_permissions rp JOIN permissions p ON p.id ="
                    + " rp.permission_id JOIN roles r ON r.id = rp.role_id WHERE p.resource ="
                    + " 'teams' AND rp.role_id NOT IN (CAST(? AS uuid), CAST(? AS uuid))",
                String.class,
                SUPERADMIN,
                ADMIN))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "`CA-SP-738` — el catálogo queda en 133, SUPERADMIN en 133 y ADMIN en 127 tras V34; V36"
          + " (RF-MV-016) suma uno a los tres")
  void elCatalogoQuedaEnCientoTreintaYTres() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class))
        .isEqualTo(135);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_id = CAST(? AS uuid)",
                Integer.class,
                SUPERADMIN))
        .isEqualTo(135);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_id = CAST(? AS uuid)",
                Integer.class,
                ADMIN))
        .isEqualTo(129);
  }
}
