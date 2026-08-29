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
 * <p>El catálogo sembrado se REDUJO el 29-08-2026: {@code CONTABILIDAD} y {@code LIDER_ACADEMICO}
 * se retiraron de `V7` por decisión del responsable del proyecto. Esta clase se reescribió entera
 * para describir el catálogo que quedó, y no se ajustó número a número: varias de sus afirmaciones
 * —los permisos acotados de un rol funcionario, el recuento de roles— hablaban de roles que ya no
 * existen, y repuntarlas a otro rol las habría convertido en aserciones que pasan sin verificar lo
 * que fueron escritas para verificar.
 *
 * <p>Todas las consultas filtran por {@code is_system = true}: otras pruebas de la suite insertan
 * roles en la misma base, y contar sin ese filtro haría que el resultado dependiera del orden de
 * ejecución.
 */
class SystemRolesSeedIT extends IntegrationTestBase {

  private static final UUID SUPERADMIN = UUID.fromString("01a02a33-4c00-7001-9c4f-5e7ad1000001");
  private static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");

  /** Los cinco de `V7` más `CLIENTE`, que añade `V30`. */
  private static final int ROLES_DE_SISTEMA = 6;

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
  @DisplayName("los seis roles de sistema están, con la jerarquía del catálogo vigente")
  void jerarquiaSembrada() {
    // Cinco los siembra `V7` y el sexto —`CLIENTE`— lo añade `V30`, el
    // 24-08-2026. Se cuentan juntos porque la pregunta es cuáles son los roles
    // de sistema, no qué migración puso cada uno.
    //
    // La fuerza comercial es una CADENA y no un abanico: MANAGER cuelga de
    // ADMIN, DIRECTOR de MANAGER y AGENTE de DIRECTOR. Eso es lo que hace que
    // la contención de privilegios (`RN-SEG-003`) se estreche hacia abajo en
    // lugar de repartirse en paralelo.
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
        .hasSize(ROLES_DE_SISTEMA)
        .containsEntry("SUPERADMIN", "null")
        .containsEntry("ADMIN", "SUPERADMIN")
        .containsEntry("MANAGER", "ADMIN")
        .containsEntry("DIRECTOR", "MANAGER")
        .containsEntry("AGENTE", "DIRECTOR")
        .containsEntry("CLIENTE", "SUPERADMIN");
  }

  @Test
  @DisplayName("los dos roles retirados el 29-08-2026 ya no se siembran")
  void rolesRetiradosDeLaSiembra() {
    // `CONTABILIDAD` y `LIDER_ACADEMICO` estuvieron en el catálogo sembrado
    // desde `V7` y se retiraron de él por decisión del responsable del
    // proyecto. La prueba no desaparece con ellos: deja constancia de que la
    // ausencia es deliberada y no un olvido de la migración.
    assertThat(codigosDeSistema()).doesNotContain("CONTABILIDAD", "LIDER_ACADEMICO");
  }

  @Test
  @DisplayName("ESTUDIANTE sigue fuera de la siembra; CLIENTE entró el 24-08-2026")
  void rolesDeNegocioFueraDeLaSiembra() {
    // `V7` declara en su encabezado que los dos quedan fuera «porque son roles
    // de negocio que se crean por la API», y esta prueba lo verificaba para
    // ambos. `V30` invierte esa decisión **para uno solo**: `CLIENTE` pasa a
    // sembrarse y `ESTUDIANTE` no.
    //
    // La prueba no se borra al cambiar la decisión, se reescribe: sigue
    // guardando lo que sigue siendo cierto, y deja constancia de qué dejó de
    // serlo y cuándo.
    assertThat(codigosDeSistema()).doesNotContain("ESTUDIANTE").contains("CLIENTE");
  }

  @Test
  @DisplayName("CLIENTE se siembra como CONSUMIDOR, que es lo que lo ata a una membresía")
  void clienteEsConsumidor() {
    // No es un detalle de catálogo: la clasificación es lo que hace que dar de
    // alta a alguien con este rol exija indicar su membresía en la misma
    // operación (`RN-SP-018`), y que retirárselo la arrastre (`RN-SP-015`).
    String clasificacion =
        jdbc.queryForObject("SELECT role_type FROM roles WHERE code = 'CLIENTE'", String.class);

    assertThat(clasificacion).isEqualTo("CONSUMIDOR");
  }

  @Test
  @DisplayName("la fuerza comercial se siembra como VENDEDOR, que es lo que RN-SP-025 acota")
  void fuerzaComercialEsVendedora() {
    // Importa más que como etiqueta de catálogo: `RN-SP-025` prohíbe que una
    // persona porte dos roles de este tipo, y `RF-CM-005` resuelve la comisión
    // efectiva a partir del rol vendedor de quien vende. Si alguno de los tres
    // dejara de ser VENDEDOR, esa resolución no encontraría tarifa y devolvería
    // «no comisiona» en lugar de fallar.
    List<String> vendedores =
        jdbc.queryForList(
            "SELECT code FROM roles WHERE is_system = true AND role_type = 'VENDEDOR'",
            String.class);

    assertThat(vendedores).containsExactlyInAnyOrder("MANAGER", "DIRECTOR", "AGENTE");
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
  @DisplayName("los cuatro roles restantes se siembran sin permisos, a la espera de RF-SP-005")
  void rolesSinPermisos() {
    // MANAGER, DIRECTOR, AGENTE y CLIENTE. Sembrarlos a ojo produciría un
    // catálogo que nadie aprobó y que quedaría como referencia.
    List<String> conPermisos =
        jdbc.queryForList(
            """
            SELECT DISTINCT r.code
              FROM roles r JOIN role_permissions rp ON rp.role_id = r.id
             WHERE r.is_system = true
            """,
            String.class);

    assertThat(conPermisos).containsExactlyInAnyOrder("SUPERADMIN", "ADMIN");
  }

  @Test
  @DisplayName("hay seis filas de auditoría del poblado, con actor, correlación e IP en nulo")
  void auditoriaDelPoblado() {
    // Una por rol de sistema, `CLIENTE` incluido: `V30` emite la suya con la
    // misma forma que `V7`.
    //
    // Con actor, correlación e IP en nulo, que es la forma correcta de decir
    // «lo creó el sistema, no una persona» (Art. V.15) — y evita que los únicos
    // roles del sistema sean también los únicos sin respuesta a «quién los
    // creó». Un rol sembrado sin su fila sería exactamente ese caso.
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

    assertThat(filas).isEqualTo(ROLES_DE_SISTEMA);
  }

  @Test
  @DisplayName("changes lleva el estado inicial completo, con los permisos por código")
  void estadoInicialEnLaAuditoria() {
    // En un CREATE, `changes` lleva el estado inicial y no un diff con
    // `before` en null (architecture.md §6.6.2).
    //
    // Se mira ADMIN porque es el único rol sembrado con permisos ACOTADOS: en
    // SUPERADMIN, que los tiene todos, un `permissions` mal construido pasaría
    // tan desapercibido como uno correcto. La reserva de dos permisos es
    // justamente lo que hace observable la diferencia.
    String changes =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity_id = ?", String.class, ADMIN);

    assertThat(changes)
        .contains("\"code\": \"ADMIN\"")
        .contains("\"is_system\": true")
        .contains("\"status\": \"ACTIVO\"")
        .contains("roles:create")
        .doesNotContain("audit:read-security");
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
