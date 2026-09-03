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
 * Verificación de {@code V51__seed_movements_permissions.sql} (`RF-MV-001` · `T-04`).
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

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("los cuatro permisos de mv.md §6 están sembrados, y no hay un quinto")
  void losCuatroSembrados() {
    List<String> codigos =
        jdbc.queryForList(
            "SELECT code FROM permissions WHERE resource = 'movements' ORDER BY code",
            String.class);

    assertThat(codigos).containsExactlyInAnyOrderElementsOf(LOS_CUATRO);
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
    assertThat(permisosDeMovimientosDe(SUPERADMIN)).containsExactlyInAnyOrderElementsOf(LOS_CUATRO);
  }

  @Test
  @DisplayName("NINGUNO está asociado a ADMIN: es la reserva decidida el 02-09-2026")
  void ningunoAsociadoAAdmin() {
    // Se aparta de la obligación de security.md §4.4 —sembrar y asociar a los
    // dos roles— por decisión del responsable del proyecto, y §4.4 recoge la
    // excepción. Lo que esta prueba fija es que la reserva sea deliberada y
    // completa: media reserva —dos permisos concedidos y dos no— sería el
    // estado que nadie decidió.
    //
    // Y deja escrito lo que cuesta: la fuerza comercial cuelga de ADMIN, de
    // modo que mientras esto siga en verde, RN-SEG-003 impide que MANAGER,
    // DIRECTOR o AGENTE declaren `movements:create`. El día que se revierta,
    // esta prueba es la que hay que cambiar primero.
    assertThat(permisosDeMovimientosDe(ADMIN)).isEmpty();
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

    assertThat(ids).hasSize(4).doesNotHaveDuplicates();
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
