package com.factech.nexus.modules.system.roles.domain.models;

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
 * Verificación de {@code V7__seed_system_roles.sql} (`RF-SP-001` · `T-04`).
 *
 * <p>Todas las consultas filtran por {@code is_system = true}: otras pruebas de la suite insertan
 * roles en la misma base, y contar sin ese filtro haría que el resultado dependiera del orden de
 * ejecución.
 */
class SystemRolesSeedIT extends IntegrationTestBase {

  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");
  private static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");
  private static final UUID CONTABILIDAD = UUID.fromString("01a02a33-4c00-7003-9c4f-5e7ad1000003");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("existe exactamente un rol raíz, y es SUPERADMIN")
  void raizUnica() {
    // El «como máximo uno» lo garantiza uq_roles_single_root; el «exactamente
    // uno» lo aporta esta migración.
    List<String> raices =
        jdbc.queryForList(
            "SELECT code FROM roles WHERE parent_role_id IS NULL AND deleted_at IS NULL",
            String.class);

    assertThat(raices).containsExactly("SUPERADMIN");
  }

  @Test
  @DisplayName("los siete roles de sistema están, con la jerarquía del catálogo aprobado")
  void jerarquiaSembrada() {
    Map<String, String> padrePorCodigo =
        jdbc
            .query(
                """
                SELECT h.code AS hijo, p.code AS padre
                  FROM roles h LEFT JOIN roles p ON p.id = h.parent_role_id
                 WHERE h.is_system = true
                """,
                (rs, fila) ->
                    Map.entry(rs.getString("hijo"), String.valueOf(rs.getString("padre"))))
            .stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertThat(padrePorCodigo)
        .hasSize(7)
        .containsEntry("SUPERADMIN", "null")
        .containsEntry("ADMIN", "SUPERADMIN")
        .containsEntry("CONTABILIDAD", "ADMIN")
        .containsEntry("LIDER_ACADEMICO", "ADMIN")
        .containsEntry("MANAGER", "ADMIN")
        .containsEntry("DIRECTOR", "MANAGER")
        .containsEntry("AGENTE", "DIRECTOR");
  }

  @Test
  @DisplayName("ESTUDIANTE y CLIENTE NO se siembran: son roles de negocio que crea la API")
  void rolesDeNegocioFueraDeLaSiembra() {
    assertThat(codigosDeSistema()).doesNotContain("ESTUDIANTE", "CLIENTE");
  }

  @Test
  @DisplayName("SUPERADMIN declara el catálogo completo de permisos (RN-SEG-007)")
  void superadminLoTieneTodo() {
    Integer delCatalogo = jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class);

    assertThat(permisosDe(SUPERADMIN)).hasSize(delCatalogo);
  }

  @Test
  @DisplayName("ADMIN recibe todo SALVO audit:read-security y currencies:update")
  void reservaDeSuperadmin() {
    // Sin esa reserva, ADMIN y SUPERADMIN serían indistinguibles salvo por ser
    // uno la raíz. La consecuencia se acepta: ADMIN no puede crear un rol que
    // declare un permiso que él no tiene, porque RN-SEG-003 lo rechazaría.
    List<String> deAdmin = permisosDe(ADMIN);
    Integer delCatalogo = jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class);

    assertThat(deAdmin).doesNotContain("audit:read-security", "currencies:update");
    assertThat(deAdmin).hasSize(delCatalogo - 2);
  }

  @Test
  @DisplayName("CONTABILIDAD recibe solo los dos permisos que la documentación le atribuye")
  void permisosDeContabilidad() {
    assertThat(permisosDe(CONTABILIDAD))
        .containsExactlyInAnyOrder("audit:read-changes", "audit:read-deletions");
  }

  @Test
  @DisplayName("los cuatro roles restantes se siembran sin permisos, a la espera de RF-SP-005")
  void rolesSinPermisos() {
    // Sembrarlos a ojo produciría un catálogo que nadie aprobó y que quedaría
    // como referencia.
    List<String> conPermisos =
        jdbc.queryForList(
            """
            SELECT DISTINCT r.code
              FROM roles r JOIN role_permissions rp ON rp.role_id = r.id
             WHERE r.is_system = true
            """,
            String.class);

    assertThat(conPermisos).containsExactlyInAnyOrder("SUPERADMIN", "ADMIN", "CONTABILIDAD");
  }

  @Test
  @DisplayName("hay siete filas de auditoría del poblado, con actor, correlación e IP en nulo")
  void auditoriaDelPoblado() {
    // Es la forma correcta de decir «lo creó el sistema, no una persona»
    // (Art. V.15), y evita que los únicos roles del sistema sean también los
    // únicos sin respuesta a «quién los creó».
    Integer filas =
        jdbc.queryForObject(
            """
            SELECT count(*)
              FROM audit_change_log a JOIN roles r ON r.id = a.entity_id
             WHERE r.is_system = true
               AND a.entity = 'roles' AND a.module = 'SP' AND a.action = 'CREATE'
               AND a.actor_id IS NULL AND a.correlation_id IS NULL AND a.ip_address IS NULL
            """,
            Integer.class);

    assertThat(filas).isEqualTo(7);
  }

  @Test
  @DisplayName("changes lleva el estado inicial completo, con los permisos por código")
  void estadoInicialEnLaAuditoria() {
    // En un CREATE, `changes` lleva el estado inicial y no un diff con
    // `before` en null (architecture.md §6.6.2).
    String changes =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity_id = ?",
            String.class,
            CONTABILIDAD);

    assertThat(changes)
        .contains("\"code\": \"CONTABILIDAD\"")
        .contains("\"is_system\": true")
        .contains("\"status\": \"ACTIVO\"")
        .contains("audit:read-changes")
        .contains("audit:read-deletions");
  }

  @Test
  @DisplayName("los identificadores son UUID v7 literales y estables entre entornos")
  void identificadoresEstables() {
    // El de SUPERADMIN debe ser el mismo en todos los entornos para que las
    // pruebas y las migraciones posteriores puedan referenciarlo por constante.
    assertThat(SUPERADMIN.version()).isEqualTo(7);
    assertThat(SUPERADMIN.variant()).isEqualTo(2);

    String codigo =
        jdbc.queryForObject("SELECT code FROM roles WHERE id = ?", String.class, SUPERADMIN);
    assertThat(codigo).isEqualTo("SUPERADMIN");
  }

  private List<String> codigosDeSistema() {
    return jdbc.queryForList("SELECT code FROM roles WHERE is_system = true", String.class);
  }

  private List<String> permisosDe(UUID rol) {
    return jdbc.queryForList(
        """
        SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
         WHERE rp.role_id = ?
        """,
        String.class,
        rol);
  }
}
