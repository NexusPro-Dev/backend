package com.factech.nexus.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `RF-SP-060` · `T-08` — <b>el reparto de `V28` sobre roles creados ANTES de la migración</b>
 * (`CA-SP-692`, `CA-SP-693`).
 *
 * <p>Las pruebas de integración migran al arrancar, y cuando corren ya no hay pasado: los únicos
 * roles que existían antes de `V28` son los dos de sistema, y sobre ellos `PermissionsSeedIT` ya
 * afirma el resultado. Lo que ninguna otra prueba puede afirmar es lo que el plan decide para
 * <b>cualquier rol creado a mano</b>: que reciba los hijos de cada código dividido que portaba, y
 * nada más.
 *
 * <p>Esta clase fabrica el pasado: con la API de Flyway sobre un <b>esquema propio</b> del mismo
 * contenedor —las migraciones no califican tablas, de modo que se crean donde Flyway ponga el
 * esquema por omisión—, migra hasta antes de `V28`, inserta dos roles bajo `ADMIN` con lo que un
 * administrador de roles pudo haberles dado, aplica `V28` y mira. Es la única forma de probar la
 * sentencia de `plan.md` §2.2 sobre datos que `V8` no siembra.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionSplitMigrationIT extends IntegrationTestBase {

  private static final String ESQUEMA = "reparto_v28";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final UUID CON_PADRES = UUID.fromString("01a0b6f6-7400-7fff-9c4f-0000000000a1");
  private static final UUID SIN_PADRES = UUID.fromString("01a0b6f6-7400-7fff-9c4f-0000000000a2");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @Value("${spring.flyway.placeholders.superadmin_email}")
  private String superadminEmail;

  @Value("${spring.flyway.placeholders.superadmin_password_hash}")
  private String superadminPasswordHash;

  @BeforeAll
  void fabricarElPasadoYMigrar() {
    // 1. El esquema propio hasta ANTES de V28: el catálogo de sesenta y los roles
    //    de V8. Se apunta a V24 y no a V27 porque V25 a V27 pertenecen al bloque 4
    //    de AC y pueden no existir todavía; si existen, corren en el paso 3 junto
    //    con V28 y no tocan roles ni permisos, que es lo único que aquí se mira.
    flyway("24").migrate();

    // 2. Dos roles creados «a mano» bajo ADMIN, como los crearía RF-SP-001 y
    //    como RF-SP-005 les daría permisos: uno porta dos códigos que V28
    //    divide y otro solo uno que no se divide.
    jdbc.update(
        """
        INSERT INTO reparto_v28.roles (id, code, name, role_type, parent_role_id, status, is_system)
        VALUES (?, 'EDITOR_DE_ROLES', 'Editor de roles', 'FUNCIONARIO', ?::uuid, 'ACTIVO', false),
               (?, 'CREADOR_DE_ROLES', 'Creador de roles', 'FUNCIONARIO', ?::uuid, 'ACTIVO', false)
        """,
        CON_PADRES,
        ADMIN,
        SIN_PADRES,
        ADMIN);
    conceder(CON_PADRES, "roles:update", "users:read");
    conceder(SIN_PADRES, "roles:create");

    // 3. V28.
    flyway("28").migrate();
  }

  @AfterAll
  void limpiar() {
    jdbc.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
  }

  @Test
  @DisplayName(
      "CA-SP-692: un rol creado antes con roles:update y users:read porta, después, los cuatro"
          + " hijos de uno y los dos del otro")
  void elRolQuePortabaLosPadresRecibeTodosSusHijos() {
    assertThat(permisosDe(CON_PADRES))
        .containsExactlyInAnyOrder(
            "roles:update",
            "roles:change-status",
            "roles:assign-parent",
            "roles:assign-permissions",
            "roles:revoke-permissions",
            "users:read",
            "users:list",
            "users:read-team");
  }

  @Test
  @DisplayName("CA-SP-693: un rol creado antes con un código que no se divide no recibe nada")
  void elRolSinPadresNoRecibeNada() {
    assertThat(permisosDe(SIN_PADRES)).containsExactly("roles:create");
  }

  @Test
  @DisplayName("el reparto no toca lo que el rol no tenía: ni roles:list sin roles:read")
  void noRepartePorRecursoSinoPorPadre() {
    // Portaba roles:update y no roles:read: recibe lo de update y no
    // roles:list, aunque sea del mismo recurso. El reparto es por pareja
    // (rol, padre), no por prefijo.
    assertThat(permisosDe(CON_PADRES)).doesNotContain("roles:list", "roles:read");
  }

  @Test
  @DisplayName(
      "y en ese esquema, como en el real, ADMIN queda con ciento cinco y la raíz con todos")
  void losDeSistemaTambien() {
    Map<String, Object> cuentas =
        jdbc.queryForMap(
            """
            SELECT (SELECT count(*) FROM reparto_v28.permissions) AS catalogo,
                   (SELECT count(*) FROM reparto_v28.role_permissions
                     WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001') AS raiz,
                   (SELECT count(*) FROM reparto_v28.role_permissions
                     WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002') AS admin
            """);
    assertThat(cuentas)
        .containsEntry("catalogo", 111L)
        .containsEntry("raiz", 111L)
        .containsEntry("admin", 105L);
  }

  private Flyway flyway(String hasta) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        // `public` en el camino de búsqueda: ahí viven `gin_trgm_ops` y `unaccent`.
        .schemas(ESQUEMA, "public")
        .defaultSchema(ESQUEMA)
        .placeholders(
            Map.of(
                "superadmin_email", superadminEmail,
                "superadmin_password_hash", superadminPasswordHash))
        .target(hasta)
        .load();
  }

  private void conceder(UUID rol, String... codigos) {
    for (String codigo : codigos) {
      jdbc.update(
          """
          INSERT INTO reparto_v28.role_permissions (role_id, permission_id)
          SELECT ?, id FROM reparto_v28.permissions WHERE code = ?
          """,
          rol,
          codigo);
    }
  }

  private List<String> permisosDe(UUID rol) {
    return jdbc.queryForList(
        """
        SELECT p.code
          FROM reparto_v28.role_permissions rp
          JOIN reparto_v28.permissions p ON p.id = rp.permission_id
         WHERE rp.role_id = ?
         ORDER BY p.code
        """,
        String.class,
        rol);
  }
}
