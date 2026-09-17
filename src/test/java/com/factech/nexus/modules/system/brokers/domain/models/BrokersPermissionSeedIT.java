package com.factech.nexus.modules.system.brokers.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de la siembra de `brokers:read`: {@code V75} (`RF-SP-052` · `T-03`).
 *
 * <p><b>Por qué existe una prueba solo para una migración</b>, igual que sus hermanas de `PM`, `MV`
 * y las tasas de cambio: el resto de la suite concede los permisos al actor directamente, de modo
 * que <b>ninguna se entera</b> de si están sembrados ni de a qué roles se asociaron. Una asociación
 * que se cayera del guion no rompería nada hasta que alguien intentara crear un rol que la
 * necesitara.
 */
class BrokersPermissionSeedIT extends IntegrationTestBase {

  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");
  private static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("`brokers:read` está sembrado, y es el ÚNICO permiso del recurso")
  void unSoloPermiso() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource = 'brokers' ORDER BY code", String.class);

    // Uno y no cuatro: este catálogo NO se administra por API (`RN-SP-039`).
    // Sembrar `brokers:create` «por simetría» con las tasas dejaría un permiso
    // que nadie puede ejercer y que alguien acabaría concediendo.
    assertThat(codigos).containsExactly("brokers:read");
  }

  @Test
  @DisplayName("lleva identificador literal y estable, no generado")
  void identificadorEstable() {
    // Art. V.11: igual en todos los entornos. Con `gen_random_uuid()` fallaría
    // en el siguiente despliegue y no aquí, que es donde se puede ver.
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'brokers:read'", String.class))
        .isEqualTo("01a081f0-6000-7001-9c4f-5e7ada000001");
  }

  @Test
  @DisplayName("lo tienen SUPERADMIN y también ADMIN")
  void losDosRoles() {
    assertThat(permisosDe(SUPERADMIN)).contains("brokers:read");

    // La mitad que se olvida: sin ella `ADMIN` no puede conceder lo que no
    // tiene, y `RN-SEG-003` rechaza la operación sin decir que falta una
    // siembra. La guarda de `V75` aborta la migración si esta fila no entró.
    assertThat(permisosDe(ADMIN)).contains("brokers:read");
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
