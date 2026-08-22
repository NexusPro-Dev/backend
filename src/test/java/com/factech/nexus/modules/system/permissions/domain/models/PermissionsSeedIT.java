package com.factech.nexus.modules.system.permissions.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de {@code V3__seed_permissions.sql} (`RF-SP-010` · `T-03`).
 *
 * <p>El catálogo sembrado es el contrato del que dependen {@code V7__seed_system_roles.sql} y las
 * pruebas de `RF-SP-001` y `RF-SP-005`, que referencian permisos por identificador. Que esos
 * identificadores sean estables entre entornos no es una comodidad: es lo que permite que una
 * migración posterior los asocie.
 */
class PermissionsSeedIT extends IntegrationTestBase {

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("el catálogo tiene exactamente veinticuatro permisos")
  void catalogoCompleto() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class))
        .isEqualTo(24);
  }

  @Test
  @DisplayName("ocho de los permisos son de recurso users, incluido assign-supervisor")
  void ochoPermisosDeUsuarios() {
    List<String> acciones =
        jdbc.queryForList(
            "SELECT action FROM permissions WHERE resource = 'users' ORDER BY action",
            String.class);

    assertThat(acciones)
        .hasSize(8)
        .containsExactly(
            "assign-membership",
            "assign-roles",
            "assign-supervisor",
            "create",
            "delete",
            "read",
            "reset-password",
            "update");
  }

  @Test
  @DisplayName("el catálogo sembrado coincide con requirements/sp.md §9")
  void coincideConElCatalogoAprobado() {
    List<String> codigos =
        jdbc.queryForList("SELECT code FROM permissions ORDER BY code", String.class);

    assertThat(codigos)
        .containsExactlyInAnyOrder(
            "audit:read-changes",
            "audit:read-deletions",
            "audit:read-errors",
            "audit:read-security",
            "countries:create",
            "countries:read",
            "countries:update",
            "currencies:read",
            "currencies:update",
            "memberships:create",
            "memberships:read",
            "permissions:read",
            "roles:create",
            "roles:delete",
            "roles:read",
            "roles:update",
            "users:assign-membership",
            "users:assign-roles",
            "users:assign-supervisor",
            "users:create",
            "users:delete",
            "users:read",
            "users:reset-password",
            "users:update");
  }

  @Test
  @DisplayName("los identificadores son UUID versión 7 y variante RFC 9562")
  void identificadoresUuidV7() {
    List<UUID> ids = jdbc.queryForList("SELECT id FROM permissions", UUID.class);

    assertThat(ids).hasSize(24).doesNotHaveDuplicates();
    assertThat(ids).allSatisfy(id -> assertThat(id.version()).isEqualTo(7));
    // variant() == 2 es la variante RFC 9562 (bits 10xx).
    assertThat(ids).allSatisfy(id -> assertThat(id.variant()).isEqualTo(2));
  }

  @Test
  @DisplayName("los identificadores son literales estables, no generados en base de datos")
  void identificadoresEstables() {
    // Si alguien sustituyera los literales por gen_random_uuid(), esta prueba
    // fallaría en el siguiente entorno: es lo que protege la asociación que
    // V7__seed_system_roles.sql hará por identificador (Art. V.11).
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'roles:create'", String.class))
        .isEqualTo("01a029fc-5d80-7002-9c4f-5e7ad0000002");

    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'users:assign-supervisor'",
                String.class))
        .isEqualTo("01a029fc-5d80-7018-9c4f-5e7ad0000018");
  }

  @Test
  @DisplayName("todos los permisos declaran nombre y descripción legibles")
  void todosConNombreYDescripcion() {
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM permissions
                 WHERE name IS NULL OR btrim(name) = ''
                    OR description IS NULL OR btrim(description) = ''
                """,
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("la siembra del catálogo no deja rastro en ninguna auditoría")
  void laSiembraNoSeAudita() {
    // RN-SP-004 hace el permiso inmutable por API: no tiene línea de tiempo
    // que reconstruir. Cuando V4 cree las tablas de auditoría, esta prueba
    // pasará a comprobar que siguen vacías tras la siembra.
    Integer tablasDeAuditoria =
        jdbc.queryForObject(
            """
            SELECT count(*)
              FROM information_schema.tables
             WHERE table_name LIKE 'audit\\_%'
            """,
            Integer.class);

    assertThat(tablasDeAuditoria)
        .as("V4 todavía no existe; cuando exista, esta prueba debe verificarlas vacías")
        .isZero();
  }
}
