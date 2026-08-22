package com.factech.nexus.modules.system.permissions.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de {@code V2__create_permissions.sql} (`RF-SP-010` · `T-02`).
 *
 * <p>Cada prueba ejercita una restricción con un {@code INSERT} que debe fallar. Una restricción
 * que nadie intenta violar es una restricción que nadie sabe si funciona.
 */
class PermissionsSchemaIT extends IntegrationTestBase {

  @Autowired private JdbcTemplate jdbc;

  private void insertar(String code, String resource, String action, String description) {
    jdbc.update(
        """
        INSERT INTO permissions (id, code, resource, action, name, description)
        VALUES (gen_random_uuid(), ?, ?, ?, 'Permiso de prueba', ?)
        """,
        code,
        resource,
        action,
        description);
  }

  @Test
  @DisplayName("uq_permissions_code rechaza un código repetido")
  void rechazaCodigoRepetido() {
    assertThatThrownBy(() -> insertar("roles:read", "roles", "read", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_permissions_code");
  }

  @Test
  @DisplayName("ck_permissions_code_format rechaza los formatos que no son <recurso>:<acción>")
  void rechazaFormatoInvalido() {
    List<String[]> invalidos =
        List.of(
            new String[] {"Roles:read", "Roles", "read"}, // mayúsculas
            new String[] {"roles read", "roles", "read"}, // sin dos puntos
            new String[] {"1roles:read", "1roles", "read"}, // empieza por dígito
            new String[] {"roles:", "roles", ""}, // acción vacía
            new String[] {"roles:read:extra", "roles", "read:extra"}); // dos separadores

    for (String[] caso : invalidos) {
      assertThatThrownBy(() -> insertar(caso[0], caso[1], caso[2], null))
          .as("el código «%s» debe rechazarse", caso[0])
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  @DisplayName("ck_permissions_code_format admite el guion medio en la acción")
  void admiteGuionMedio() {
    insertar("audit:read-something", "audit", "read-something", null);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM permissions WHERE code = 'audit:read-something'",
                Integer.class))
        .isEqualTo(1);

    jdbc.update("DELETE FROM permissions WHERE code = 'audit:read-something'");
  }

  @Test
  @DisplayName("ck_permissions_code_matches rechaza que code diverja de resource y action")
  void rechazaCodigoIncoherente() {
    // Es la restricción que impide el catálogo incoherente silencioso: sin
    // ella, este INSERT pasaría y el filtro por recurso 'role' devolvería un
    // permiso cuyo código dice 'roles'.
    assertThatThrownBy(() -> insertar("roles:read2", "role", "read2", null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_permissions_code_matches");
  }

  @Test
  @DisplayName("ck_permissions_description_length acota la descripción a 500 caracteres")
  void acotaLaDescripcion() {
    insertar("test:limite", "test", "limite", "x".repeat(500));
    jdbc.update("DELETE FROM permissions WHERE code = 'test:limite'");

    assertThatThrownBy(() -> insertar("test:exceso", "test", "exceso", "x".repeat(501)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_permissions_description_length");
  }

  @Test
  @DisplayName("la tabla no declara deleted_at: un permiso no se elimina")
  void noHayBorradoLogico() {
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM information_schema.columns
                 WHERE table_name = 'permissions' AND column_name = 'deleted_at'
                """,
                Integer.class))
        .isZero();
  }
}
