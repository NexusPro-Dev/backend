package com.factech.nexus;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de toda prueba de integración: levanta un PostgreSQL real y deja que Flyway aplique las
 * migraciones sobre él.
 *
 * <p>Se usa PostgreSQL real y no una base de datos en memoria porque casi todo lo que estas pruebas
 * verifican —restricciones {@code CHECK}, índices parciales, funciones de la extensión {@code
 * unaccent}, comportamiento de los bloqueos— no existe fuera de PostgreSQL. Una base en memoria
 * daría verde sin haber probado nada (Art. VII.4).
 *
 * <p><b>Un solo contenedor para toda la suite.</b> Se arranca en el bloque estático y no se declara
 * con {@code @Container}, que lo reiniciaría por clase de prueba. Arrancar PostgreSQL cuesta
 * segundos y las migraciones se aplican una vez; con una anotación por clase, la suite pagaría ese
 * coste tantas veces como clases tenga. El contenedor muere con la JVM de la prueba.
 *
 * <p>La versión de la imagen es la misma que la de {@code docker-compose.yml}. Si una difiere de la
 * otra, las pruebas dejan de decir algo sobre lo que se despliega.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

  static {
    // Docker Desktop 29 declara MinAPIVersion 1.40 y rechaza con 400 —sin
    // mensaje de error— cualquier petición que negocie por debajo. El cliente
    // que usa Testcontainers negocia una versión anterior, de modo que el
    // arranque falla con «Could not find a valid Docker environment», que no
    // dice nada de lo que ocurre en realidad.
    //
    // Fijar la versión aquí y no en la máquina de cada quien es lo que hace
    // que la suite arranque igual en cualquier estación y en CI. 1.44 la
    // soporta Docker 25 en adelante; si alguna vez hubiera que correr contra
    // un motor más antiguo, este es el único punto que tocar.
    System.setProperty("api.version", "1.44");

    POSTGRES.start();
  }

  /**
   * Publica hacia {@code application.yml} las variables que este declara sin valor por defecto. No
   * se duplica la configuración en un archivo de prueba: lo que se prueba es la configuración real,
   * y solo se le da el origen de los datos.
   */
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("DATABASE_URL", POSTGRES::getJdbcUrl);
    registry.add("DATABASE_USER", POSTGRES::getUsername);
    registry.add("DATABASE_PASSWORD", POSTGRES::getPassword);
    // Secreto de prueba. No es el de ningún entorno y no concede nada:
    // ninguna prueba lo comparte con otro proceso.
    registry.add("JWT_SECRET", () -> "secreto-de-prueba-solo-para-la-suite-automatizada");

    // El entorno de la suite es `testing`, que es lo que es: no es local de
    // nadie y no es el sistema real. Se declara porque desde el 31-08-2026 la
    // variable es OBLIGATORIA y su ausencia tumba el arranque (Art. IX.5).
    registry.add("ENVIRONMENT", () -> "testing");

    // La SEMILLA DE DESARROLLO queda APAGADA, por lo mismo que las dos de
    // abajo. En `testing` se aplicaría, y son quince personas con sus roles y
    // tres membresías apareciendo solas en la base que toda la suite comparte:
    // decenas de pruebas cuentan personas, cuentan roles o listan páginas, y
    // fallarían por algo que no tiene nada que ver con lo que comprueban.
    //
    // Quien prueba la semilla la enciende para su clase (`DevelopmentSeedIT`),
    // que es donde tiene sentido — y limpia detrás.
    registry.add("DEV_SEED_ENABLED", () -> "false");

    // El límite de tasa queda APAGADO para la suite general, y es deliberado:
    // varias clases provocan ráfagas contra el inicio de sesión a propósito
    // —`RF-SP-034` comprueba el bloqueo a los cinco intentos—, y con el límite
    // activo recibirían `429` antes de llegar a lo que están comprobando. El
    // fallo sería además intermitente, porque depende de cuántas pruebas de esa
    // clase hayan corrido antes dentro de la misma ventana.
    //
    // Quien prueba el límite lo enciende para su clase (`RateLimitIT`), que es
    // donde tiene sentido.
    registry.add("RATE_LIMIT_ENABLED", () -> "false");

    // La purga de sesiones caducadas queda APAGADA por lo mismo: corre por su
    // cuenta cada madrugada, y una prueba que crea sesiones no debe competir
    // con una tarea que las borra. El fallo sería intermitente y no se
    // parecería en nada a su causa. `RefreshTokenPurgeIT` invoca el servicio
    // directamente, que es como se prueba lo que hace y no cuándo lo hace.
    registry.add("TOKEN_PURGE_ENABLED", () -> "false");

    // Credencial inicial del superadministrador, que `V9__semilla_catalogos_y_superadmin.sql`
    // exige como marcador de posición. Se declara aquí y no en un archivo de
    // propiedades de prueba por lo mismo que las anteriores: lo que se prueba es
    // la configuración real, y solo se le da el origen de los datos.
    //
    // El hash es de una contraseña que ninguna prueba conoce ni necesita: las
    // que autentican como superadministrador lo hacen por su identificador, que
    // la migración fija. Ponerlo aquí en claro sería una credencial en el
    // repositorio aunque fuera de mentira, y el hábito es lo que se rompe.
    registry.add("SUPERADMIN_EMAIL", () -> "superadmin@factech.co");
    registry.add(
        "SUPERADMIN_PASSWORD_HASH",
        () -> "$argon2id$v=19$m=16384,t=2,p=1$c3VpdGVkZXBydWViYQ$8mQ0kM1e3xLQz1sT0cVQ0aQm0Q9nQpVQ");
  }

  /**
   * Identificador del superadministrador sembrado por {@code
   * V9__semilla_catalogos_y_superadmin.sql}.
   *
   * <p>Es fijo a propósito: toda prueba de integración que necesite un actor con permisos reales lo
   * refiere por esta constante en lugar de consultarlo, que es la razón por la que aquella
   * migración escribe el identificador en lugar de generarlo.
   */
  protected static final java.util.UUID SUPERADMIN =
      java.util.UUID.fromString("01a033a4-4a00-7001-9c4f-5e7ad4000001");

  /** Identificador de {@code ADMIN}, sembrado por {@code V8__semilla_permisos_y_roles.sql}. */
  protected static final String ADMIN_SEMBRADO = "01a02a33-4c00-7002-9c4f-5e7ad1000002";

  /**
   * Colombia, el único país sembrado, por {@code V4__sp_seguridad.sql} (`RN-SP-034`).
   *
   * <p>Es fijo por el mismo motivo que {@link #SUPERADMIN}: toda alta de persona lo exige, y una
   * prueba que tuviera que consultarlo antes estaría probando el catálogo de países en lugar de lo
   * suyo.
   *
   * <p><b>Y el catálogo de países deja de nacer vacío por su culpa</b>, que es la consecuencia de
   * `RN-SP-034` que más se nota aquí: {@code users.country_id} es {@code NOT NULL} y {@code V22}
   * siembra un superadministrador, de modo que ninguna base —tampoco la de estas pruebas— puede
   * arrancar sin al menos un país.
   */
  protected static final java.util.UUID COLOMBIA =
      java.util.UUID.fromString("01a07bbd-5200-7001-9c4f-5e7ad3000101");

  /**
   * Cédula de ciudadanía, sembrada por {@code V3__sp_catalogos.sql} (`RN-SP-035`).
   *
   * <p>Es fijo por lo mismo que {@link #COLOMBIA}: toda alta de persona exige un tipo de documento,
   * y una prueba que tuviera que consultarlo antes estaría probando el catálogo en lugar de lo
   * suyo.
   *
   * <p><b>El catálogo solo contiene documentos de mayor de edad</b>, y esa ausencia es la
   * validación entera: no existe constante equivalente para una tarjeta de identidad porque <b>esa
   * fila no está</b>.
   */
  protected static final java.util.UUID CEDULA =
      java.util.UUID.fromString("01a080e3-ae00-7001-9c4f-5e7ad6000001");

  private static final java.util.concurrent.atomic.AtomicInteger SECUENCIA_DE_DOCUMENTO =
      new java.util.concurrent.atomic.AtomicInteger();

  /**
   * Un número de documento distinto en cada llamada.
   *
   * <p>{@code uq_users_document} es <b>total</b> —no libera al eliminar (`RN-SP-035`)— y la base es
   * compartida por toda la suite, de modo que un literal repetido haría fallar la segunda prueba
   * que lo usara, y el fallo dependería del orden de ejecución.
   */
  protected static String documentoNuevo() {
    return "DOC" + String.format("%07d", SECUENCIA_DE_DOCUMENTO.incrementAndGet());
  }

  /**
   * Los dos permisos con los que nace un {@link #crearRolAcotado rol acotado}: los de lectura de
   * auditoría, que es el par más pequeño que el catálogo ofrece y que ningún endpoint de negocio
   * abre.
   */
  protected static final java.util.List<String> PERMISOS_ACOTADOS =
      java.util.List.of("audit:read-changes", "audit:read-deletions");

  /**
   * Crea un rol de negocio colgado de {@code ADMIN} con DOS permisos y ninguno más, y devuelve su
   * identificador.
   *
   * <p><b>Por qué existe.</b> Hasta el 29-08-2026 este papel lo hacía {@code CONTABILIDAD}, un rol
   * sembrado por {@code V7} con exactamente esa forma. Al retirarse del catálogo —junto con {@code
   * LIDER_ACADEMICO}, por decisión del responsable del proyecto— el sistema dejó de sembrar
   * cualquier rol con permisos acotados: los dos que quedan con permisos son {@code SUPERADMIN},
   * que los tiene todos, y {@code ADMIN}, que solo se reserva dos.
   *
   * <p>Eso importa más de lo que parece. Media suite usaba aquel rol para representar a «alguien
   * que existe pero no puede casi nada», y es esa acotación la que hace observables la contención
   * de privilegios, que un token autentique sin conceder, y que la marca de cambio de contraseña
   * deniegue por un motivo distinto del permiso. Apuntar esas pruebas a {@code ADMIN} las habría
   * dejado en verde sin verificar nada de eso, que es peor que dejarlas en rojo.
   *
   * <p>Se inserta por SQL a propósito: es un fixture, y construirlo por la API haría que un fallo
   * del endpoint de creación se confundiera con el caso bajo prueba. Quien lo llame es responsable
   * de borrarlo en su limpieza, como con cualquier rol que la prueba cree.
   */
  /**
   * Vuelve a dar a los roles de sistema lo que `V31` reparte por tipo (`RF-SP-062`): los once de
   * alcance propio a `FUNCIONARIO` y `VENDEDOR`, ocho a `CONSUMIDOR`; y lo que `V32` da a los tres
   * tipos (`movements:list-sales`, `RF-MV-015`). Para las suites que vacían `MANAGER`, `DIRECTOR`,
   * `AGENTE` y `CLIENTE` —que hasta el 21-09-2026 nacían vacíos— y tienen que dejarlos como los
   * deja la migración, no como los dejaba `V8`.
   */
  protected static void reponerAlcancePropio(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT r.id, p.id
          FROM roles r
          CROSS JOIN permissions p
         WHERE r.is_system = true
           AND (p.code IN ('users:read-own-profile', 'users:update-own-profile',
                           'users:change-own-password', 'users:read-own-sellers',
                           'movements:list-own', 'movements:read-own',
                           'movements:read-own-products', 'packages:buy',
                           'movements:list-sales')
                OR (r.role_type IN ('FUNCIONARIO', 'VENDEDOR')
                    AND p.code IN ('users:read-own-clients', 'broker-accounts:read-own-team',
                                   'broker-accounts:read-team-member')))
        ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING
        """);
  }

  /**
   * Los códigos que todo rol recibe por su tipo —los once de alcance propio de `V31` y {@code
   * movements:list-sales} de `V32`—, para descontarlos donde se cuente «lo demás».
   */
  protected static final java.util.List<String> ALCANCE_PROPIO =
      java.util.List.of(
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
          "packages:buy",
          "movements:list-sales");

  protected static java.util.UUID crearRolAcotado(
      org.springframework.jdbc.core.JdbcTemplate jdbc, String codigo, String nombre) {
    java.util.UUID id = java.util.UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, description, role_type, parent_role_id,
                           status, is_system)
        VALUES (?, ?, ?, 'Rol acotado de prueba.', 'FUNCIONARIO', ?::uuid, 'ACTIVO', false)
        """,
        id,
        codigo,
        nombre,
        ADMIN_SEMBRADO);
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions WHERE code IN ('audit:read-changes', 'audit:read-deletions')
        """,
        id);
    // Y los once de alcance propio que V31 da a todo rol de tipo FUNCIONARIO
    // (RF-SP-062, RN-SEG-015) más el de las ventas de mi alcance de V32
    // (RF-MV-015): sin ellos, una persona con este rol no vería su perfil ni
    // podría cambiar la contraseña obligatoria. Es lo que un rol creado a mano
    // recibe por RF-SP-005 el día que nace, y lo que `MustChangePasswordIT`
    // necesita para que la cuenta marcada tenga salida.
    jdbc.update(
        """
        INSERT INTO role_permissions (role_id, permission_id)
        SELECT ?, id FROM permissions
         WHERE code IN ('users:read-own-profile', 'users:update-own-profile',
                        'users:change-own-password', 'users:read-own-sellers',
                        'users:read-own-clients', 'broker-accounts:read-own-team',
                        'broker-accounts:read-team-member', 'movements:list-own',
                        'movements:read-own', 'movements:read-own-products', 'packages:buy',
                        'movements:list-sales')
        """,
        id);
    return id;
  }

  /**
   * Repone la membresía de arranque, <b>la de código {@code BECA}</b>, con el identificador literal
   * que `V46` siembra.
   *
   * <p><b>Hace falta porque la suite comparte una sola base y dos docenas de clases hacen {@code
   * DELETE FROM memberships}</b> para montar su propia cadena. Desde el 05-09-2026 eso rompe a
   * quien venga después: `RN-SP-018` da nivel a toda persona y el alta lo resuelve por código, de
   * modo que sin esta fila <b>registrar un usuario devuelve {@code 500}</b> — y el fallo aparece o
   * no según el orden de ejecución, que es la peor clase de prueba intermitente.
   *
   * <p>Se llama <b>después</b> del barrido de la clase, cuando ya no queda ninguna otra membresía
   * sin padre con la que chocar en {@code uq_memberships_parent}.
   */
  protected static void reponerElSuelo(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color)
        VALUES ('01a04ad0-e800-7001-9c4f-5e7ad7000001', 'BECA', 'Free', 'Nivel de entrada.',
                NULL, 1, '9E9E9E')
        ON CONFLICT (id) DO NOTHING
        """);
  }

  /**
   * Concede la membresía de arranque a una persona creada <b>por SQL</b>.
   *
   * <p>Los fixtures que insertan en {@code users} directamente se saltan el caso de uso, y con él
   * la concesión que `RN-SP-018` exige. Sin esto, esas personas quedan en un estado que el sistema
   * <b>ya no produce</b> —viva y sin nivel—, y una prueba que lo dé por bueno estaría fijando algo
   * que no puede ocurrir.
   */
  protected static void darElSuelo(
      org.springframework.jdbc.core.JdbcTemplate jdbc, java.util.UUID userId) {
    jdbc.update(
        """
        INSERT INTO user_memberships (id, user_id, membership_id)
        SELECT gen_random_uuid(), ?, id FROM memberships WHERE code = 'BECA'
        """,
        userId);
  }
}
