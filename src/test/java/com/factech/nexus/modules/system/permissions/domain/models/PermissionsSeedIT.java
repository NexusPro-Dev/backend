package com.factech.nexus.modules.system.permissions.domain.models;

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
 * Verificación de {@code V8__semilla_permisos_y_roles.sql} (`RF-SP-010` · `T-03`).
 *
 * <p>El catálogo sembrado es el contrato del que dependen {@code V8__semilla_permisos_y_roles.sql}
 * y las pruebas de `RF-SP-001` y `RF-SP-005`, que referencian permisos por identificador. Que esos
 * identificadores sean estables entre entornos no es una comodidad: es lo que permite que una
 * migración posterior los asocie.
 */
class PermissionsSeedIT extends IntegrationTestBase {

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName(
      "el catálogo tiene exactamente CIENTO TREINTA Y CUATRO: sesenta y uno de SP, veintiséis de"
          + " PM, diez de CM, nueve de MV y veintiocho de AC (V28: un permiso por operación,"
          + " CA-SP-688; V29 y V30: los de RF-SP-059 y 061; V31: los once de alcance propio de"
          + " RF-SP-062, CA-SP-725; V32: movements:list-sales de RF-MV-015; V34: los ocho teams: de RF-SP-063 a RF-SP-070;"
          + " V36: movements:assign-sellers de RF-MV-016)")
  void catalogoCompleto() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class))
        .isEqualTo(134);
  }

  @Test
  @DisplayName(
      "V32 siembra movements:list-sales con literal de la serie de MV y lo da a TODO rol por su"
          + " tipo, los tres tipos: el permiso abre y RN-MV-031 decide qué se ve (CA-MV-130)")
  void lasVentasDeMiAlcanceLleganATodos() {
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'movements:list-sales'",
                String.class))
        .isEqualTo("01a0c143-2c00-700c-9c4f-5e7ad7000008");
    for (String rol :
        new String[] {"SUPERADMIN", "ADMIN", "MANAGER", "DIRECTOR", "AGENTE", "CLIENTE"}) {
      assertThat(codigosDe(rol))
          .as("%s porta movements:list-sales", rol)
          .contains("movements:list-sales");
    }
  }

  @Test
  @DisplayName(
      "veinte de los permisos son de recurso users: los ocho de V8, los cinco que V28 separa,"
          + " users:read-sellers de V29, users:read-clients de V30 y los cinco de alcance propio"
          + " de V31")
  void veintePermisosDeUsuarios() {
    List<String> acciones =
        jdbc.queryForList(
            "SELECT action FROM permissions WHERE resource = 'users' ORDER BY action",
            String.class);

    assertThat(acciones)
        .hasSize(20)
        .containsExactly(
            "assign-membership",
            "assign-roles",
            "assign-supervisor",
            "change-own-password",
            "change-status",
            "create",
            "delete",
            "list",
            "read",
            "read-clients",
            "read-own-clients",
            "read-own-profile",
            "read-own-sellers",
            "read-sellers",
            "read-team",
            "reset-password",
            "revoke-membership",
            "revoke-roles",
            "update",
            "update-own-profile");
  }

  @Test
  @DisplayName(
      "el catálogo sembrado coincide con sp.md §9, pm.md §4, cm.md §6, mv.md §6 y ac.md §7")
  void coincideConElCatalogoAprobado() {
    List<String> codigos =
        jdbc.queryForList("SELECT code FROM permissions ORDER BY code", String.class);

    assertThat(codigos)
        .containsExactlyInAnyOrder(
            "audit:read-changes",
            "audit:read-deletions",
            "audit:read-errors",
            "audit:read-security",
            "commissions:create",
            "commissions:delete",
            "commissions:read",
            "commissions:update",
            // Los seis de V28 (RF-SP-060): commissions: se queda con las tasas de rol.
            "commissions:read-effective",
            "user-commission-rates:create",
            "user-commission-rates:delete",
            "user-commission-rates:read",
            "user-commission-rates:update",
            "product-commission-rates:read",
            "course-categories:create",
            "course-categories:delete",
            "course-categories:read",
            "course-categories:update",
            "course-categories:list",
            "courses:create",
            "courses:delete",
            "courses:learn",
            "courses:read",
            "courses:teach",
            "courses:update",
            // Los diecisiete de V28 para cursos, módulos y lecciones: sembrados el
            // 19-09-2026 y declarados en sus controladores en el tramo 3 de RF-SP-060.
            "courses:list",
            "courses:change-status",
            "courses:assign-category",
            "courses:revoke-category",
            "courses:assign-recommendation",
            "courses:revoke-recommendation",
            "courses:assign-membership",
            "courses:revoke-membership",
            "course-modules:create",
            "course-modules:update",
            "course-modules:change-status",
            "course-modules:delete",
            "lessons:create",
            "lessons:read",
            "lessons:update",
            "lessons:change-status",
            "lessons:delete",
            // El SEGUNDO recurso sin ninguna acción de escritura, por el mismo
            // motivo estructural y no por el mismo motivo de negocio: `RN-SP-039`
            // deja el catálogo de brokers fuera de la API porque son pocos y
            // cambian poco, no porque su contenido sea una regla.
            "brokers:read",
            // El CUARTO recurso sin ninguna acción de escritura, y el PRIMERO
            // cuyo recurso no es un catálogo: gobierna una LECTURA de datos
            // ajenos (`RF-SP-055`). No hay `create` porque la cuenta la declara
            // su titular al registrarse, sin sesión; no hay `update` porque
            // quien la completa es el webhook del broker, que no porta roles.
            "broker-accounts:read",
            "broker-accounts:read-indicators",
            "broker-accounts:read-own-team",
            "broker-accounts:read-team-member",
            "countries:create",
            "countries:read",
            "countries:update",
            // El ÚNICO recurso del catálogo sin ninguna acción de escritura, y no
            // es que falten: `RN-SP-036` las prohíbe, porque el contenido de
            // `document_types` ES la validación de mayoría de edad. Un
            // `document-types:create` la desactivaría sin cambiar ninguna regla.
            "document-types:read",
            "exchange-rates:create",
            "exchange-rates:delete",
            "exchange-rates:read",
            "exchange-rates:update",
            "currencies:read",
            "currencies:update",
            "memberships:create",
            "memberships:read",
            "memberships:list",
            "movements:confirm",
            "movements:create",
            "movements:list-own",
            "movements:read",
            "movements:read-own",
            "movements:read-own-products",
            "movements:list-sales",
            "movements:assign-sellers",
            "movements:void",
            // El SEGUNDO recurso de `PM` (`V93`, 15-09-2026), por decisión del
            // responsable del proyecto: armar paquetes y tocar el catálogo son
            // dos capacidades, y los `products:` no habilitan ni una operación
            // de paquetes.
            "packages:create",
            "packages:delete",
            "packages:read",
            "packages:update",
            // Los siete de V28: packages:update se queda con la edición.
            "packages:list",
            "packages:change-status",
            "packages:set-cover",
            "packages:remove-cover",
            "packages:add-product",
            "packages:update-product",
            "packages:buy",
            "packages:remove-product",
            "permissions:read",
            "permissions:list",
            "products:comment",
            "products:create",
            "products:delete",
            "products:hotlink",
            "products:read",
            "products:sale",
            "products:update",
            // Los siete de V28: products:update se queda con la edición y
            // products:comment con escribir la reseña.
            "products:list",
            "products:change-status",
            "products:set-cover",
            "products:remove-cover",
            "products:read-own-comments",
            "products:update-comment",
            "products:delete-comment",
            "roles:create",
            "roles:delete",
            "roles:read",
            "roles:update",
            // Los cinco de V28: roles:update deja de ser la llave del reparto.
            "roles:list",
            "roles:change-status",
            "roles:assign-parent",
            "roles:assign-permissions",
            "roles:revoke-permissions",
            "users:assign-membership",
            "users:assign-roles",
            "users:assign-supervisor",
            "users:create",
            "users:delete",
            "users:read",
            "users:reset-password",
            "users:update",
            "users:list",
            "users:change-status",
            "users:change-own-password",
            "users:read-clients",
            "users:read-own-clients",
            "users:read-own-profile",
            "users:read-own-sellers",
            "users:read-sellers",
            "users:read-team",
            "users:update-own-profile",
            "users:revoke-roles",
            "users:revoke-membership",
            // Los ocho de V34 (RF-SP-063 a RF-SP-070): el submodulo Equipos,
            // uno por operacion. A SUPERADMIN y ADMIN, y a ningun otro rol.
            "teams:list",
            "teams:read",
            "teams:create",
            "teams:update",
            "teams:change-status",
            "teams:delete",
            "teams:assign-members",
            "teams:remove-members");
  }

  @Test
  @DisplayName("los identificadores son UUID versión 7 y variante RFC 9562")
  void identificadoresUuidV7() {
    List<UUID> ids = jdbc.queryForList("SELECT id FROM permissions", UUID.class);

    assertThat(ids).hasSize(134).doesNotHaveDuplicates();
    assertThat(ids).allSatisfy(id -> assertThat(id.version()).isEqualTo(7));
    // variant() == 2 es la variante RFC 9562 (bits 10xx).
    assertThat(ids).allSatisfy(id -> assertThat(id.variant()).isEqualTo(2));
  }

  @Test
  @DisplayName("los identificadores son literales estables, no generados en base de datos")
  void identificadoresEstables() {
    // Si alguien sustituyera los literales por gen_random_uuid(), esta prueba
    // fallaría en el siguiente entorno: es lo que protege la asociación que
    // V8__semilla_permisos_y_roles.sql hará por identificador (Art. V.11).
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'roles:create'", String.class))
        .isEqualTo("01a029fc-5d80-7002-9c4f-5e7ad0000002");

    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'users:assign-supervisor'",
                String.class))
        .isEqualTo("01a029fc-5d80-7018-9c4f-5e7ad0000018");

    // V28 continúa cada serie donde quedó, sin renumerar ninguno (RF-SP-060 · plan §2.1).
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'roles:list'", String.class))
        .isEqualTo("01a0b6f6-7400-7001-9c4f-5e7ad0000019");
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'lessons:delete'", String.class))
        .isEqualTo("01a0b6f6-7400-7033-9c4f-5e7adc000028");

    // V29 sigue la serie de SP donde V28 la dejó (users:read-team fue …000024).
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'users:read-sellers'", String.class))
        .isEqualTo("01a0c143-2c00-7001-9c4f-5e7ad0000025");
    // V30, la misma marca del 21-09-2026, secuencia 7002, la serie de SP en …000026.
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'users:read-clients'", String.class))
        .isEqualTo("01a0c143-2c00-7002-9c4f-5e7ad0000026");
    // V31, serie propia 7001..700b de la misma marca; cada módulo sigue donde
    // quedó: users …000027, broker-accounts …000004, movements …000005, PM …000026.
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'users:read-own-profile'",
                String.class))
        .isEqualTo("01a0c143-2c00-7001-9c4f-5e7ad0000027");
    assertThat(
            jdbc.queryForObject(
                "SELECT id::text FROM permissions WHERE code = 'packages:buy'", String.class))
        .isEqualTo("01a0c143-2c00-700b-9c4f-5e7ad5000026");
  }

  @Test
  @DisplayName(
      "V28 a V36 reparten: SUPERADMIN porta los ciento treinta y cuatro y ADMIN ciento veintiocho,"
          + " y los seis que le faltan son la reserva (CA-SP-691, CA-SP-725)")
  void elRepartoLlegaALosRolesDeSistema() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_id ="
                    + " '01a02a33-4c00-7001-9c4f-5e7ad1000001'",
                Integer.class))
        .isEqualTo(134);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions WHERE role_id ="
                    + " '01a02a33-4c00-7002-9c4f-5e7ad1000002'",
                Integer.class))
        .isEqualTo(128);
    assertThat(
            jdbc.queryForList(
                """
                SELECT p.code FROM permissions p
                 WHERE NOT EXISTS (SELECT 1 FROM role_permissions rp
                                    WHERE rp.role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002'
                                      AND rp.permission_id = p.id)
                 ORDER BY p.code
                """,
                String.class))
        .containsExactly(
            "audit:read-security",
            "currencies:update",
            "movements:confirm",
            "movements:create",
            "movements:read",
            "movements:void");
  }

  @Test
  @DisplayName(
      "V31 reparte por TIPO de rol: la fuerza comercial porta los once de alcance propio y"
          + " CLIENTE ocho —no los tres de vendedor— (CA-SP-725)")
  void elAlcancePropioLlegaPorTipoDeRol() {
    String[] once = {
      "users:read-own-profile",
      "users:update-own-profile",
      "users:change-own-password",
      "users:read-own-sellers",
      "users:read-own-clients",
      "broker-accounts:read-own-team",
      "broker-accounts:read-team-member",
      "movements:list-own",
      "movements:read-own",
      "movements:read-own-products",
      "packages:buy"
    };
    for (String rol : new String[] {"MANAGER", "DIRECTOR", "AGENTE"}) {
      assertThat(codigosDe(rol)).as("%s porta los once de alcance propio", rol).contains(once);
    }
    List<String> cliente = codigosDe("CLIENTE");
    assertThat(cliente)
        .contains(
            "users:read-own-profile",
            "users:update-own-profile",
            "users:change-own-password",
            "users:read-own-sellers",
            "movements:list-own",
            "movements:read-own",
            "movements:read-own-products",
            "packages:buy")
        .doesNotContain(
            "users:read-own-clients",
            "broker-accounts:read-own-team",
            "broker-accounts:read-team-member");
    // Y solo eso más el de V32: CLIENTE sigue sin ningún otro permiso (V8).
    assertThat(cliente).hasSize(9).contains("movements:list-sales");
  }

  private List<String> codigosDe(String rol) {
    return jdbc.queryForList(
        """
        SELECT p.code FROM role_permissions rp
          JOIN roles r ON r.id = rp.role_id
          JOIN permissions p ON p.id = rp.permission_id
         WHERE r.code = ? ORDER BY p.code
        """,
        String.class,
        rol);
  }

  @Test
  @DisplayName(
      "los veintiún códigos que V28 estrecha ya no describen las operaciones que perdieron"
          + " (CA-SP-695)")
  void losEstrechadosNoNombranLoQuePerdieron() {
    // Cada pareja es (código, descripción ORIGINAL de V8, V19 o V22), la que nombraba
    // operaciones que hoy tienen código propio. V28 la reescribe; si alguien la
    // restaurara, la prueba lo diría. No se afirma un texto nuevo concreto: lo que
    // importa es que ninguno de los veintiuno siga describiendo lo que perdió.
    Map<String, String> original =
        Map.ofEntries(
            Map.entry(
                "roles:read",
                "Ver el listado de roles, el detalle de cada uno y los permisos que declara."),
            Map.entry(
                "roles:update",
                "Editar nombre y descripción, cambiar el estado, reubicar el rol padre y asignar o retirar permisos."),
            Map.entry(
                "permissions:read",
                "Ver el catálogo de permisos del sistema y el detalle de cada uno."),
            Map.entry("memberships:read", "Ver el listado de membresías y el detalle de cada una."),
            Map.entry(
                "users:read",
                "Ver el listado de usuarios, el detalle de cada uno y el equipo comercial a su cargo."),
            Map.entry("users:update", "Editar los datos de un usuario y cambiar su estado."),
            Map.entry(
                "users:assign-roles",
                "Asignar y retirar roles de un usuario, dentro de la cota de privilegios del propio actor."),
            Map.entry("users:assign-membership", "Asignar y retirar la membresía de un usuario."),
            Map.entry(
                "broker-accounts:read",
                "Consultar las cuentas de broker de cualquier persona (RF-SP-055). Sin él, cada quien ve solo las de su equipo directo (RN-SP-046)."),
            Map.entry(
                "products:read",
                "Ver el catalogo completo, incluido lo inactivo y lo retirado, y el detalle de cada producto."),
            Map.entry(
                "products:update",
                "Corregir nombre, descripcion, precio, moneda y vigencia, y publicar o retirar de la venta."),
            Map.entry(
                "products:comment",
                "Escribir, corregir y retirar la reseña propia sobre un producto (RN-PM-025 a RN-PM-029)."),
            Map.entry(
                "packages:read",
                "Ver todos los paquetes, incluidos los inactivos y los retirados, con su precio calculado y por que no se ofrecen."),
            Map.entry(
                "packages:update",
                "Corregir nombre, descripcion y alcance, publicar o despublicar, y asociar, corregir el descuento o desasociar sus productos."),
            Map.entry(
                "commissions:read",
                "Ver las tarifas declaradas, incluido el historial, y resolver la comision efectiva."),
            Map.entry(
                "commissions:create",
                "Declarar cuanto gana un rol vendedor, por producto y por persona, y desde cuando rige."),
            Map.entry(
                "commissions:update",
                "Corregir el porcentaje de una tarifa y cerrar o reabrir su fin de vigencia."),
            Map.entry(
                "commissions:delete",
                "Retirar una tarifa con eliminacion logica y motivo obligatorio."),
            Map.entry(
                "course-categories:read",
                "Ver el listado de categorías del catálogo de cursos y el detalle de cada una, incluidas las retiradas."),
            Map.entry(
                "courses:read",
                "Ver el listado de cursos y el detalle completo de cada uno —con lo inactivo, lo retirado y lo que no se ofrece— y el contenido de sus lecciones."),
            Map.entry(
                "courses:update",
                "Corregir un curso, cambiar su estado y su portada, clasificarlo, recomendarle cursos previos, darle visibilidad a membresías, y registrar, corregir, cambiar de estado, retirar y poner portada a sus módulos y lecciones."));
    original.forEach(
        (code, descripcionDeAntes) ->
            assertThat(
                    jdbc.queryForObject(
                        "SELECT description FROM permissions WHERE code = ?", String.class, code))
                .as(code)
                .isNotEqualTo(descripcionDeAntes));
  }

  @Test
  @DisplayName("todos los permisos declaran nombre y descripción legibles")
  void todosConNombreYDescripcion() {
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM permissions
                 WHERE name IS NULL OR btrim(name) = ''
                    OR description IS NULL OR btrim(description) = ''
                """,
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("la siembra del catálogo no deja rastro en ninguna auditoría")
  void laSiembraNoSeAudita() {
    // RN-SP-004 hace el permiso inmutable por API: no tiene línea de tiempo
    // que reconstruir. Desde que V4 crea las cuatro tablas, la comprobación ya
    // no es que no existan —existen— sino que ninguna fila se refiere al
    // catálogo.
    //
    // La comprobación va ACOTADA a `permissions` y no exige las tablas vacías:
    // V7 audita los siete roles que siembra, y eso es exactamente lo que debe
    // hacer. Una prueba que exigiera cero filas en total estaría afirmando lo
    // contrario de lo que dice V4.
    assertThat(
            jdbc.queryForObject(
                """
                SELECT (SELECT count(*) FROM audit_change_log   WHERE entity   = 'permissions')
                     + (SELECT count(*) FROM audit_deletion_log WHERE entity   = 'permissions')
                     + (SELECT count(*) FROM audit_error_log    WHERE resource = 'permissions')
                """,
                Integer.class))
        .as("la siembra del catálogo dejó rastro en la auditoría")
        .isZero();

    // Que NINGUNA migración emita un evento de control de acceso se comprueba
    // sobre los propios guiones y no contando filas.
    //
    // La comprobación anterior era `SELECT count(*) FROM audit_security_log`
    // sobre el total, y solo pasaba por el orden en que Failsafe ejecutaba las
    // clases: en cuanto otra prueba de integración ejercita un `403` —y varias
    // lo hacen, porque `CA-SP-008` y `CA-SP-119` lo exigen— la tabla deja de
    // estar vacía y esta prueba falla sin que nada esté mal. Se detectó al
    // incorporar el submódulo de membresías, cuyo paquete ordena antes.
    //
    // Leer las migraciones es además más fuerte: comprueba las de hoy y las que
    // se añadan, no el estado de la base en un instante concreto.
    assertThat(migracionesQueEscribenEventosDeSeguridad())
        .as("una migración emite eventos de control de acceso")
        .isEmpty();
  }

  /**
   * Migraciones cuyo texto inserta en {@code audit_security_log}.
   *
   * <p>Se buscan por el nombre de la tabla precedido de {@code INTO}: mencionarla en un comentario
   * —como hace {@code V4}, que la crea— no debe contar como escribirla.
   */
  private static java.util.List<String> migracionesQueEscribenEventosDeSeguridad() {
    try {
      var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
      java.util.List<String> culpables = new java.util.ArrayList<>();
      for (var recurso : resolver.getResources("classpath:db/migration/*.sql")) {
        String guion =
            new String(
                recurso.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String sinComentarios =
            guion.replaceAll("--[^\r\n]*", " ").toLowerCase(java.util.Locale.ROOT);
        if (sinComentarios.contains("into audit_security_log")) {
          culpables.add(recurso.getFilename());
        }
      }
      return culpables;
    } catch (java.io.IOException fallo) {
      throw new IllegalStateException("No se pudieron leer las migraciones", fallo);
    }
  }
}
