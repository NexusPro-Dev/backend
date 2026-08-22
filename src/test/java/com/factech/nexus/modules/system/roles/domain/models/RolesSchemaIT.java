package com.factech.nexus.modules.system.roles.domain.models;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de {@code V5__create_roles.sql} y {@code V6__create_role_permissions.sql}
 * (`RF-SP-001` · `T-02`, `T-03`).
 *
 * <p>Las pruebas comparten un solo PostgreSQL y escriben sin revertir, de modo que cada una usa un
 * código y un nombre propios. Sin esa disciplina, el orden de ejecución decidiría el resultado.
 */
class RolesSchemaIT extends IntegrationTestBase {

  /** Raíz sembrada por {@code V7}, disponible como padre para cualquier inserción de prueba. */
  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("ck_roles_code_format rechaza minúsculas, guion medio, espacios e inicio por dígito")
  void formatoDelCodigo() {
    // La misma restricción vive en RoleCode. En el esquema la garantía vale
    // también para las migraciones de poblado y para cualquier punto de
    // entrada futuro; en Java solo cubre la API.
    for (String codigo : new String[] {"minus", "CON-GUION", "CON ESPACIO", "1DIGITO", "_GUION"}) {
      assertThatThrownBy(() -> insertar(codigo, "Nombre de " + codigo, null))
          .as("el código '%s' debe rechazarse", codigo)
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  @DisplayName("ck_roles_description_length acota la descripción en 500 caracteres")
  void longitudDeDescripcion() {
    assertThatCode(() -> insertar("DESC_OK", "Descripción justa", "x".repeat(500)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> insertar("DESC_LARGA", "Descripción excedida", "x".repeat(501)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("ck_roles_status y ck_roles_type son dominios cerrados")
  void dominiosCerrados() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO roles (id, code, name, role_type, parent_role_id, status)
                    VALUES (gen_random_uuid(), 'ESTADO_RARO', 'Estado raro', 'FUNCIONARIO', ?, 'SUSPENDIDO')
                    """,
                    SUPERADMIN))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO roles (id, code, name, role_type, parent_role_id)
                    VALUES (gen_random_uuid(), 'TIPO_RARO', 'Tipo raro', 'EXTERNO', ?)
                    """,
                    SUPERADMIN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("uq_roles_code y uq_roles_name rechazan el duplicado entre los no eliminados")
  void unicidadEntreNoEliminados() {
    insertar("UNICO_A", "Único A", null);

    assertThatThrownBy(() -> insertar("UNICO_A", "Otro nombre", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertar("OTRO_CODIGO", "Único A", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("CA-SP-006 — el índice es PARCIAL: el código de un rol eliminado se reutiliza")
  void codigoReutilizableTrasEliminar() {
    // Con una restricción única corriente, el código de un rol eliminado
    // quedaría bloqueado para siempre y este criterio sería imposible.
    insertar("REUTILIZABLE", "Reutilizable", null);
    jdbc.update("UPDATE roles SET deleted_at = now() WHERE code = 'REUTILIZABLE'");

    assertThatCode(() -> insertar("REUTILIZABLE", "Reutilizable de nuevo", null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("uq_roles_single_root impide una segunda raíz (RN-SEG-007)")
  void raizUnica() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO roles (id, code, name, role_type, parent_role_id)
                    VALUES (gen_random_uuid(), 'SEGUNDA_RAIZ', 'Segunda raíz', 'FUNCIONARIO', NULL)
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("ck_roles_parent_not_self impide el ciclo de longitud uno")
  void padreDistintoDeSiMismo() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO roles (id, code, name, role_type, parent_role_id)
                    VALUES (?, 'AUTOPADRE', 'Auto padre', 'FUNCIONARIO', ?)
                    """,
                    id,
                    id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("fk_roles_parent exige que el padre exista (RN-SEG-008)")
  void padreInexistente() {
    assertThatThrownBy(() -> insertar("HUERFANO", "Huérfano", null, UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------------------------------------------------------------------------
  // role_permissions — T-03
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("la clave primaria compuesta rechaza el par duplicado")
  void parDuplicado() {
    UUID rol = insertar("CON_PERMISO", "Con permiso", null);
    UUID permiso = unPermiso();

    jdbc.update(
        "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)", rol, permiso);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)",
                    rol,
                    permiso))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("ON DELETE RESTRICT: el borrado FÍSICO de un rol con permisos falla")
  void borradoFisicoRestringido() {
    // El borrado del rol es lógico (RF-SP-009); una eliminación física
    // accidental debe fallar y no llevarse las asociaciones por delante.
    UUID rol = insertar("NO_BORRABLE", "No borrable", null);
    jdbc.update(
        "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)", rol, unPermiso());

    assertThatThrownBy(() -> jdbc.update("DELETE FROM roles WHERE id = ?", rol))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("ON DELETE RESTRICT hacia permissions: el catálogo no se vacía por debajo")
  void permisoAsociadoNoSeBorra() {
    UUID rol = insertar("ATA_PERMISO", "Ata permiso", null);
    UUID permiso = unPermiso();
    jdbc.update(
        "INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)", rol, permiso);

    assertThatThrownBy(() -> jdbc.update("DELETE FROM permissions WHERE id = ?", permiso))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------------------------------------------------------------------------

  private UUID insertar(String code, String name, String description) {
    return insertar(code, name, description, SUPERADMIN);
  }

  private UUID insertar(String code, String name, String description, UUID padre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, description, role_type, parent_role_id)
        VALUES (?, ?, ?, ?, 'FUNCIONARIO', ?)
        """,
        id,
        code,
        name,
        description,
        padre);
    return id;
  }

  private UUID unPermiso() {
    return jdbc.queryForObject(
        "SELECT id FROM permissions WHERE code = 'permissions:read'", UUID.class);
  }
}
