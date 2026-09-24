package com.factech.nexus.modules.movements.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verificación de {@code V8__semilla_permisos_y_roles.sql} (`RF-MV-001` · `T-04`).
 *
 * <p>Es la primera clase del módulo `MV` y por ahora la única: la siembra de los permisos se
 * adelantó al resto del módulo porque `T-04` no depende de nada. Las cuatro tablas de la venta
 * todavía no existen, y esta prueba no las menciona.
 *
 * <p><b>Por qué existe una prueba solo para una migración.</b> Todas las demás pruebas de la suite
 * usan un actor al que se le conceden permisos directamente: ninguna se entera de si el permiso
 * está sembrado ni de a qué roles se asoció. Una asociación que se cayera del guion no rompería
 * nada hasta que alguien intentara crear un rol que la necesitara, y entonces `RN-SEG-003`
 * rechazaría la operación sin decir en ningún sitio que lo que falta es una siembra.
 */
class MovementsPermissionsSeedIT extends IntegrationTestBase {

  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");
  private static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");

  private static final List<String> LOS_CUATRO =
      List.of("movements:read", "movements:create", "movements:confirm", "movements:void");

  /**
   * Los tres de alcance propio que `V31` añadió al recurso el 21-09-2026 (`RF-SP-062`) y el de las
   * ventas de mi alcance de `V32` (`RF-MV-015`): no son de la reserva —van a todo rol por su tipo—
   * y se descuentan donde esta clase habla de «los cuatro».
   */
  private static final List<String> LOS_PROPIOS =
      List.of(
          "movements:list-own",
          "movements:read-own",
          "movements:read-own-products",
          "movements:list-sales");

  /**
   * El de `V36` (`RF-MV-016`): tampoco es de la reserva —va a SUPERADMIN y ADMIN, explícito— y
   * tampoco a todo rol: decidir a quién se le paga una venta es de administración.
   */
  private static final String ASIGNAR = "movements:assign-sellers";

  /**
   * El de `V37` (`RF-MV-017`): las líneas de venta para administración. Como el de asignar, va a
   * SUPERADMIN y ADMIN explícito y a nadie más, y tampoco es de la reserva. Y a diferencia de
   * `movements:list-sales`, NO lleva alcance por estructura: `RN-MV-031` gobierna aquel y solo
   * aquel, de modo que este no va a todo rol por su tipo.
   */
  private static final String LINEAS = "movements:list-sale-lines";

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName(
      "los cuatro permisos de mv.md §6 están sembrados, y no hay un quinto de la reserva —los"
          + " tres de alcance propio de V31 y el de V32 van aparte—")
  void losCuatroSembrados() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource = 'movements' ORDER BY code",
            String.class);

    assertThat(codigos)
        .containsAll(LOS_CUATRO)
        .containsAll(LOS_PROPIOS)
        .contains(ASIGNAR, LINEAS)
        .hasSize(10);
  }

  @Test
  @DisplayName("no existe movements:update, y eso es una decisión y no un olvido")
  void sinPermisoDeEdicion() {
    // Una venta no se actualiza nunca (RN-MV-001). Un permiso llamado
    // `movements:update` prometería una operación que no existe, y ese es el
    // motivo por el que `confirm` y `void` no reutilizan `update`.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM permissions WHERE code = 'movements:update'", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("los cuatro están asociados a SUPERADMIN, que debe acotar el catálogo completo")
  void asociadosASuperadmin() {
    // RN-SEG-007: la raíz de la contención está acotada por el catálogo
    // completo. Un permiso sembrado y no asociado la dejaría por detrás de sus
    // propios hijos.
    assertThat(permisosDeMovimientosDe(SUPERADMIN)).containsAll(LOS_CUATRO).hasSize(10);
  }

  @Test
  @DisplayName("LOS CUATRO están asociados a ADMIN: la reserva se levantó el 24-09-2026")
  void losCuatroAsociadosAAdmin() {
    // INVERTIDA EL 24-09-2026 POR `V40`, y esta prueba pedía ser la primera en
    // cambiar: su versión anterior exigía que ADMIN no portara NINGUNO, y dejó
    // escrito lo que costaba —«mientras esto siga en verde, RN-SEG-003 impide
    // que MANAGER, DIRECTOR o AGENTE declaren movements:create»—. Ese era el
    // motivo del cambio: no había a quién delegar la fuerza comercial.
    //
    // LO QUE LA PRUEBA SIGUE VIGILANDO ES LO MISMO: que la decisión sea
    // COMPLETA. Media reserva —dos concedidos y dos no— seguiría siendo el
    // estado que nadie decidió, y por eso se afirma el conjunto entero y no
    // que «tenga alguno».
    assertThat(permisosDeMovimientosDe(ADMIN))
        .containsAll(LOS_CUATRO)
        // Y nada más que eso: los cuatro, los propios de todo rol (`RF-SP-062`)
        // y los dos de administración explícitos —asignar vendedores (`V36`) y
        // las líneas de venta (`V37`)—.
        .containsExactlyInAnyOrderElementsOf(
            java.util.stream.Stream.concat(
                    java.util.stream.Stream.concat(LOS_CUATRO.stream(), LOS_PROPIOS.stream()),
                    java.util.stream.Stream.of(ASIGNAR, LINEAS))
                .toList());
  }

  @Test
  @DisplayName("los identificadores son UUID v7 literales y estables entre entornos")
  void identificadoresEstables() {
    // Si alguien sustituyera los literales por gen_random_uuid(), esta prueba
    // fallaría en el siguiente entorno (Art. V.11).
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'movements:create'", String.class))
        .isEqualTo("01a05f6a-5800-7002-9c4f-5e7ad7000002");

    List<UUID> ids =
        jdbc.queryForList("SELECT id FROM permissions WHERE resource = 'movements'", UUID.class);

    assertThat(ids).hasSize(10).doesNotHaveDuplicates();
    assertThat(ids).allSatisfy(id -> assertThat(id.version()).isEqualTo(7));
    // variant() == 2 es la variante RFC 9562 (bits 10xx).
    assertThat(ids).allSatisfy(id -> assertThat(id.variant()).isEqualTo(2));
  }

  @Test
  @DisplayName("los cuatro declaran nombre y descripción legibles")
  void conNombreYDescripcion() {
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM permissions
                 WHERE resource = 'movements'
                   AND (name IS NULL OR btrim(name) = ''
                     OR description IS NULL OR btrim(description) = '')
                """,
                Integer.class))
        .isZero();
  }

  /** Los permisos de recurso {@code movements} que declara un rol. */
  private List<String> permisosDeMovimientosDe(UUID rol) {
    return jdbc.queryForList(
        """
        SELECT p.code
          FROM role_permissions rp
          JOIN permissions p ON p.id = rp.permission_id
         WHERE rp.role_id = ?
           AND p.resource = 'movements'
        """,
        String.class,
        rol);
  }
}
