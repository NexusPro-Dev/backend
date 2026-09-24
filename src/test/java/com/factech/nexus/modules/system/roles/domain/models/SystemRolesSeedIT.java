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
 * Verificación de {@code V8__semilla_permisos_y_roles.sql} (`RF-SP-001` · `T-04`).
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

  /**
   * Lo que porta la cúspide comercial —MANAGER y DIRECTOR portan LO MISMO— tras `V31` (alcance
   * propio por tipo de rol) y `V40` (el reparto del 24-09-2026).
   */
  private static final List<String> LA_CUSPIDE =
      List.of(
          "broker-accounts:read-own-team",
          "broker-accounts:read-team-member",
          "movements:create",
          "movements:list-own",
          "movements:list-sales",
          "movements:read",
          "movements:read-own",
          "movements:read-own-products",
          "packages:buy",
          "products:hotlink",
          "products:sale",
          "users:change-own-password",
          "users:read-clients",
          "users:read-own-clients",
          "users:read-own-profile",
          "users:read-own-sellers",
          "users:read-sellers",
          "users:read-team",
          "users:update-own-profile");

  /**
   * El agente: vende y reparte enlaces, y NO consulta vendedores — `users:read-own-sellers` se le
   * retiró el 24-09-2026 a propósito, porque un vendedor no tiene vendedores por encima que
   * consultar.
   */
  private static final List<String> EL_AGENTE =
      List.of(
          "broker-accounts:read-own-team",
          "broker-accounts:read-team-member",
          "movements:list-own",
          "movements:list-sales",
          "movements:read-own",
          "movements:read-own-products",
          "packages:buy",
          "products:hotlink",
          "products:sale",
          "users:change-own-password",
          "users:read-own-clients",
          "users:read-own-profile",
          "users:update-own-profile");

  /**
   * El cliente: compra, reseña lo comprado y consulta vendedores POR IDENTIFICADOR
   * (`users:read-sellers`) y no por `/me` — se preguntó y se confirmó el 24-09-2026. No porta
   * `movements:list-sales`: no vende.
   */
  private static final List<String> EL_CLIENTE =
      List.of(
          "movements:create",
          "movements:list-own",
          "movements:read-own",
          "movements:read-own-products",
          "packages:buy",
          "products:comment",
          "products:sale",
          "products:update-comment",
          "users:change-own-password",
          "users:read-own-profile",
          "users:read-sellers",
          "users:update-own-profile");

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
  @DisplayName("ADMIN recibe todo SALVO las dos reservas de V7 y los cuatro movements: de V51")
  void reservaDeSuperadmin() {
    // Sin esa reserva, ADMIN y SUPERADMIN serían indistinguibles salvo por ser
    // uno la raíz. La consecuencia se acepta: ADMIN no puede crear un rol que
    // declare un permiso que él no tiene, porque RN-SEG-003 lo rechazaría.
    //
    // LA RESERVA BAJÓ DE SEIS PERMISOS A DOS el 24-09-2026 (`V40`), y el motivo
    // es justo lo que esta prueba dejaba escrito: `V51` reservaba el trabajo
    // diario de la fuerza comercial, que cuelga entera de ADMIN, de modo que
    // con ADMIN fuera `RN-SEG-003` impedía que MANAGER, DIRECTOR o AGENTE
    // declararan `movements:create`. No había a quién delegarlo.
    //
    // Quedan los dos de `V7`, que sí son operaciones que solo hace el
    // superadministrador. La afirmación de fondo no se mueve: **ADMIN y
    // SUPERADMIN no pueden ser indistinguibles**, y esta cuenta es lo único
    // que lo delata.
    List<String> deAdmin = permisosDe(ADMIN);
    Integer delCatalogo = jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class);

    assertThat(deAdmin).doesNotContain("audit:read-security", "currencies:update");
    // Y LOS CUATRO DE `movements:` SÍ, que es la otra mitad del cambio: sin
    // esta línea, revertir `V40` dejaría la prueba en verde.
    assertThat(deAdmin)
        .contains("movements:read", "movements:create", "movements:confirm", "movements:void");
    assertThat(deAdmin).hasSize(delCatalogo - 2);
  }

  @Test
  @DisplayName("los cuatro roles restantes portan EXACTAMENTE lo que V31 y V40 les reparten")
  void rolesSinPermisos() {
    // INVERTIDA EL 24-09-2026. Decía que MANAGER, DIRECTOR, AGENTE y CLIENTE se
    // sembraban SIN permisos —«sembrarlos a ojo produciría un catálogo que nadie
    // aprobó»—, y `V40` los siembra: la fuerza comercial recibe lo que la reserva
    // de `V51` le impedía portar.
    //
    // LO QUE LA PRUEBA DEFIENDE NO CAMBIA, y por eso no se borra: que nadie
    // reparta a ojo. Antes se comprobaba con una lista vacía; ahora, afirmando
    // el conjunto EXACTO de cada rol. Una concesión de más —la clase de error
    // que no falla y solo abre— rompe esta prueba, que es todo su sentido.
    assertThat(permisosDeRol("MANAGER")).containsExactlyInAnyOrderElementsOf(LA_CUSPIDE);
    assertThat(permisosDeRol("DIRECTOR")).containsExactlyInAnyOrderElementsOf(LA_CUSPIDE);
    assertThat(permisosDeRol("AGENTE")).containsExactlyInAnyOrderElementsOf(EL_AGENTE);
    assertThat(permisosDeRol("CLIENTE")).containsExactlyInAnyOrderElementsOf(EL_CLIENTE);

    // MANAGER y DIRECTOR portan LO MISMO, y conviene que se lea: son dos escalones
    // de mando y no dos conjuntos de permisos distintos.
    assertThat(permisosDeRol("MANAGER"))
        .containsExactlyInAnyOrderElementsOf(permisosDeRol("DIRECTOR"));
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

  /** Por código, que es lo estable: los identificadores de rol no se escriben aquí. */
  private List<String> permisosDeRol(String codigo) {
    return jdbc.queryForList(
        """
        SELECT p.code FROM role_permissions rp
          JOIN roles r ON r.id = rp.role_id
          JOIN permissions p ON p.id = rp.permission_id
         WHERE r.code = ?
        """,
        String.class,
        codigo);
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
