package com.factech.nexus.modules.commissions.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La siembra de los ocho permisos de la liquidación (`V51`, `RF-CM-013` · `plan.md` §2).
 *
 * <p>Tres cosas: que estén con su literal, que `SUPERADMIN` y `ADMIN` porten los ocho, y que <b>lo
 * propio</b> —`list-own` y `read-own`— llegue a todo rol de tipo `VENDEDOR` y a ningún
 * `CONSUMIDOR`, porque es quien cobra el que tiene lotes que mirar.
 */
class CommissionSettlementPermissionsSeedIT extends IntegrationTestBase {

  private static final String SUPERADMIN = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  private static final List<String> OCHO =
      List.of(
          "commission-batches:settle",
          "commission-batches:read",
          "commission-batches:read-detail",
          "commission-batches:pay",
          "commission-batches:list-own",
          "commission-batches:read-own",
          "commission-closings:read",
          "commission-accruals:read",
          // `V59` (30-09-2026): retirar y devolver (`RF-CM-022`, `RF-CM-023`).
          "commission-batches:withdraw-commission",
          "commission-batches:return-commission");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("los ocho de V51 y los dos de V59 existen, con literal de la serie de CM")
  void losOchoConSuLiteral() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource IN"
                + " ('commission-batches', 'commission-closings', 'commission-accruals')",
            String.class);

    assertThat(codigos).containsExactlyInAnyOrderElementsOf(OCHO);
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'commission-batches:settle'",
                String.class))
        .isEqualTo("01a0f7a0-9000-7001-9c4f-5e7ad6000012");
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'commission-accruals:read'",
                String.class))
        .isEqualTo("01a0f7a0-9000-7008-9c4f-5e7ad6000019");
  }

  @Test
  @DisplayName("SUPERADMIN y ADMIN portan los diez")
  void losRolesDeSistema() {
    for (String rol : new String[] {SUPERADMIN, ADMIN}) {
      assertThat(codigosDelRol(rol)).as(rol).containsAll(OCHO);
    }
  }

  @Test
  @DisplayName(
      "lo propio llega a todo rol VENDEDOR y a ningún CONSUMIDOR; lo demás, a ninguno de los dos")
  void loPropioPorTipoDeRol() {
    List<String> vendedoresSinLoPropio =
        jdbc.queryForList(
            """
            SELECT r.code FROM roles r
             WHERE r.role_type = 'VENDEDOR'
               AND (SELECT count(*) FROM role_permissions rp
                      JOIN permissions p ON p.id = rp.permission_id
                     WHERE rp.role_id = r.id
                       AND p.code IN ('commission-batches:list-own',
                                      'commission-batches:read-own')) <> 2
            """,
            String.class);
    assertThat(vendedoresSinLoPropio).isEmpty();

    Integer ajenos =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM role_permissions rp
              JOIN roles r ON r.id = rp.role_id
              JOIN permissions p ON p.id = rp.permission_id
             WHERE p.resource IN ('commission-batches', 'commission-closings',
                                  'commission-accruals')
               AND (r.role_type = 'CONSUMIDOR'
                    OR (r.role_type = 'VENDEDOR'
                        AND p.code NOT IN ('commission-batches:list-own',
                                           'commission-batches:read-own')))
            """,
            Integer.class);
    assertThat(ajenos).isZero();
  }

  private List<String> codigosDelRol(String roleId) {
    return jdbc.queryForList(
        """
        SELECT p.code FROM role_permissions rp
          JOIN permissions p ON p.id = rp.permission_id
         WHERE rp.role_id = ?::uuid
        """,
        String.class,
        roleId);
  }
}
