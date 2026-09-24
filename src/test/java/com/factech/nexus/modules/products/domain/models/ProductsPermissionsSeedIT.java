package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de las tres siembras de permisos de `PM`: {@code V40}, {@code V48} y {@code V60}.
 *
 * <p><b>Por qué existe una prueba solo para unas migraciones</b>, por el mismo motivo que la
 * hermana de `MV`: todas las demás pruebas de la suite conceden los permisos al actor directamente,
 * de modo que <b>ninguna se entera</b> de si el permiso está sembrado ni de a qué roles se asoció.
 * Una asociación que se cayera del guion no rompería nada hasta que alguien intentara crear un rol
 * que la necesitara, y entonces `RN-SEG-003` rechazaría la operación sin decir en ningún sitio que
 * lo que falta es una siembra.
 *
 * <p><b>`products:hotlink` es el caso que más lo necesita</b> (07-09-2026): nace <b>sin endpoint
 * que lo exija</b> —el canal de hotlinks todavía no está construido—, de modo que no hay ni una
 * sola prueba de API que lo toque. Si su siembra o sus dos asociaciones desaparecieran, esta clase
 * es lo único que lo diría.
 */
class ProductsPermissionsSeedIT extends IntegrationTestBase {

  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");
  private static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");

  private static final List<String> LOS_SIETE =
      List.of(
          "products:read",
          "products:create",
          "products:update",
          "products:delete",
          "products:sale",
          "products:hotlink",
          "products:comment");

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName(
      "los catorce permisos `products:` de pm.md §4 están sembrados: los siete de V8 y los siete de V28")
  void losSeisSembrados() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource = 'products' ORDER BY code", String.class);

    assertThat(codigos)
        .containsAll(LOS_SIETE)
        .hasSize(15)
        // Los siete de V28 (RF-SP-060): products:update se queda con la edición y
        // products:comment con escribir la reseña.
        .contains(
            "products:list",
            "products:change-status",
            "products:set-cover",
            "products:remove-cover",
            "products:read-own-comments",
            "products:update-comment",
            "products:delete-comment");
  }

  @Test
  @DisplayName("`products:hotlink` lleva identificador literal y estable, no generado")
  void hotlinkConIdentificadorEstable() {
    // Art. V.11: debe ser igual en todos los entornos. Si alguien lo sustituyera
    // por `gen_random_uuid()`, esta prueba fallaría en el siguiente despliegue y
    // no en el sitio donde se rompiera la referencia.
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'products:hotlink'", String.class))
        .isEqualTo("01a0792a-2400-7001-9c4f-5e7ad5000006");
  }

  @Test
  @DisplayName(
      "`products:comment` lleva identificador literal y estable, y es el séptimo de la serie")
  void commentConIdentificadorEstable() {
    // `V88`: el primer permiso de escritura de PM que no es de administración.
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'products:comment'", String.class))
        .isEqualTo("01a09d97-2400-7001-9c4f-5e7ad5000007");
  }

  @Test
  @DisplayName("los siete están asociados a SUPERADMIN, que acota el catálogo completo")
  void losSeisEnSuperadmin() {
    assertThat(permisosDe(SUPERADMIN)).containsAll(LOS_SIETE);
  }

  @Test
  @DisplayName("los siete están asociados a ADMIN: el catálogo comercial NO es reserva de la raíz")
  void losSeisEnAdmin() {
    // Es la mitad que se olvida, y no falla al aplicar la migración: deja a
    // `ADMIN` incapaz de conceder lo que no tiene, y `RN-SEG-003` rechaza la
    // operación sin decir que lo que falta es una siembra.
    //
    // Y es lo que separa a `PM` de las tres reservas del superadministrador
    // (`security.md` §4.4): ver o gobernar un catálogo comercial es
    // administración ordinaria.
    assertThat(permisosDe(ADMIN)).containsAll(LOS_SIETE);
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
