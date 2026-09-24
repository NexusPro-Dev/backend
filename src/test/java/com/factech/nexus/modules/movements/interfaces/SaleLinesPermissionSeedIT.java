package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `CA-MV-177` — la siembra de `V37`: `movements:list-sale-lines`.
 *
 * <p><b>Lo que se comprueba no es que el permiso exista, sino QUIÉN lo porta.</b> El reparto
 * estrecho —solo `SUPERADMIN` y `ADMIN`— es la mitad de la decisión de `RF-MV-017`: con este
 * permiso se ve <b>todo</b> el libro sin que ninguna regla lo acote, de modo que dárselo a un rol
 * comercial le enseñaría las líneas de la empresa entera. La otra mitad, que `RN-MV-031` no se
 * aplica aquí, vive en {@code SaleLinesIT}.
 *
 * <p><b>Es la diferencia con `V32`</b>, que dio {@code movements:list-sales} a todo rol por su tipo
 * porque aquella sí era la vista de ventas de cualquiera. Si alguien ensanchara este reparto, esta
 * prueba es la única que se pondría roja.
 */
class SaleLinesPermissionSeedIT extends IntegrationTestBase {

  private static final String CODIGO = "movements:list-sale-lines";
  private static final String ID = "01a0d7f1-3800-700d-9c4f-5e7ad700000a";
  private static final String SUPERADMIN = "01a02a33-4c00-7001-9c4f-5e7ad1000001";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("el permiso está sembrado con su identificador literal y su recurso y acción")
  void sembradoConSuIdentificador() {
    var fila =
        jdbc.queryForMap(
            "SELECT id::text AS id, resource, action FROM permissions WHERE code = ?", CODIGO);

    assertThat(fila.get("id")).isEqualTo(ID);
    assertThat(fila.get("resource")).isEqualTo("movements");
    assertThat(fila.get("action")).isEqualTo("list-sale-lines");
  }

  @Test
  @DisplayName("lo portan SUPERADMIN y ADMIN, y NINGÚN otro rol")
  void soloLosDosDeAdministracion() {
    var roles =
        jdbc.queryForList(
            "SELECT r.code FROM role_permissions rp"
                + " JOIN roles r ON r.id = rp.role_id"
                + " JOIN permissions p ON p.id = rp.permission_id"
                + " WHERE p.code = ? ORDER BY r.code",
            String.class,
            CODIGO);

    assertThat(roles).containsExactly("ADMIN", "SUPERADMIN");
  }

  @Test
  @DisplayName("el catálogo queda en 134, SUPERADMIN en 134 y ADMIN en 132")
  void losRecuentosDelCatalogo() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class))
        .as("el catálogo entero")
        .isEqualTo(134);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_id = ?",
                Integer.class,
                java.util.UUID.fromString(SUPERADMIN)))
        .as("SUPERADMIN acota el catálogo completo")
        .isEqualTo(134);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_id = ?",
                Integer.class,
                java.util.UUID.fromString(ADMIN)))
        .as("ADMIN, con la reserva de DOS desde V40; el catalogo lo bajo V38")
        .isEqualTo(132);
  }
}
