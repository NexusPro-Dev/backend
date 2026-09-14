package com.factech.nexus.modules.system.exchangerates.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de la siembra de permisos de las tasas de cambio: {@code V66} (`RF-SP-047` ·
 * `T-02`).
 *
 * <p><b>Por qué existe una prueba solo para una migración</b>, igual que sus hermanas de `PM` y
 * `MV`: el resto de la suite concede los permisos al actor directamente, de modo que <b>ninguna se
 * entera</b> de si están sembrados ni de a qué roles se asociaron. Una asociación que se cayera del
 * guion no rompería nada hasta que alguien intentara crear un rol que la necesitara, y entonces
 * `RN-SEG-003` rechazaría la operación <b>sin decir en ningún sitio</b> que lo que falta es una
 * siembra.
 *
 * <p><b>Tres de los cuatro no tienen todavía endpoint que los exija</b> —solo el alta está
 * construida—, y por eso esta clase es hoy lo único que los toca.
 */
class ExchangeRatesPermissionsSeedIT extends IntegrationTestBase {

  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");
  private static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");

  private static final List<String> LOS_CUATRO =
      List.of(
          "exchange-rates:read",
          "exchange-rates:create",
          "exchange-rates:update",
          "exchange-rates:delete");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("los cuatro permisos de sp.md están sembrados, y no hay un quinto")
  void losCuatroSembrados() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource = 'exchange-rates' ORDER BY code",
            String.class);

    assertThat(codigos).containsExactlyInAnyOrderElementsOf(LOS_CUATRO);
  }

  @Test
  @DisplayName("los identificadores son literales y estables, no generados")
  void identificadoresEstables() {
    // Art. V.11: iguales en todos los entornos. Sustituirlos por
    // `gen_random_uuid()` rompería la referencia en el despliegue siguiente y
    // no aquí, que es donde se puede ver.
    assertThat(idDe("exchange-rates:read")).isEqualTo("01a07e50-8000-7001-9c4f-5e7ad9000001");
    assertThat(idDe("exchange-rates:create")).isEqualTo("01a07e50-8000-7002-9c4f-5e7ad9000002");
    assertThat(idDe("exchange-rates:update")).isEqualTo("01a07e50-8000-7003-9c4f-5e7ad9000003");
    assertThat(idDe("exchange-rates:delete")).isEqualTo("01a07e50-8000-7004-9c4f-5e7ad9000004");
  }

  @Test
  @DisplayName("los cuatro están en SUPERADMIN y también en ADMIN")
  void losCuatroEnLosDosRoles() {
    assertThat(permisosDe(SUPERADMIN)).containsAll(LOS_CUATRO);

    // La mitad que se olvida: sin ella `ADMIN` no puede conceder lo que no
    // tiene. Administrar tasas es administración ordinaria y no una de las tres
    // reservas del superadministrador (`security.md` §4.4).
    assertThat(permisosDe(ADMIN)).containsAll(LOS_CUATRO);
  }

  private String idDe(String codigo) {
    return jdbc.queryForObject(
        "SELECT id::text FROM permissions WHERE code = ?", String.class, codigo);
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
