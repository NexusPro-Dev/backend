package com.factech.nexus.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * La semilla de desarrollo se aplica cuando el entorno NO es producción.
 *
 * <p><b>Levanta un contexto propio</b> con {@code development} y la semilla encendida: es la única
 * forma de observar lo que hace un {@link ApplicationRunner}, que corre al construir el contexto.
 * El resto de la suite corre como {@code testing} y con la semilla apagada, porque diecinueve
 * personas apareciendo solas romperían decenas de pruebas que cuentan personas y roles.
 *
 * <p><b>Limpia detrás</b>, y no por cortesía: la base es la misma para toda la suite.
 *
 * <p>La pareja de esta clase es {@code ProductionSeedIT}: aquella comprueba lo contrario —que en
 * producción no se siembra— y sin las dos juntas ninguna prueba distinguiría «el guardia funciona»
 * de «la semilla no funciona».
 */
@TestPropertySource(
    properties = {
      "nexus.environment=development",
      "nexus.dev-seed.enabled=true",
      // DOS CONEXIONES Y NO LAS DIEZ DE SERIE. Cada contexto propio de la suite
      // abre su propio pozo y el caché de contextos los mantiene todos vivos:
      // con el pozo por omisión, añadir esta clase y su pareja se llevó por
      // delante el `max_connections` del contenedor, y quien fallaba era una
      // clase que no tiene nada que ver —`RateLimitIT`, con un
      // `FATAL: sorry, too many clients already` durante Flyway—.
      //
      // Dos bastan: aquí no hay nada concurrente. Las clases que sí lo son
      // conservan el pozo entero.
      "spring.datasource.hikari.maximum-pool-size=2"
    })
class DevelopmentSeedIT extends IntegrationTestBase {

  private static final List<String> USUARIOS =
      List.of(
          "admin1",
          "agente1",
          "agente2",
          "agente3",
          "agente4",
          "agente5",
          "agente6",
          "agente7",
          "agente8",
          "agente9",
          "cliente1",
          "cliente2",
          "cliente3",
          "director1",
          "director2",
          "director3",
          "manager1",
          "manager2",
          "manager3");

  /** Cuántas de las diecinueve había ANTES de que esta clase tocara nada. */
  /** Los dieciséis códigos de `semilla-productos.sql`, en el mismo orden que el guion. */
  private static final List<String> PRODUCTOS =
      List.of(
          "UPGRADE_BECA_VIP",
          "UPGRADE_BECA_PLATINO",
          "UPGRADE_BECA_ORO",
          "UPGRADE_VIP_PLATINO",
          "UPGRADE_VIP_ORO",
          "UPGRADE_PLATINO_ORO",
          "MEMBRESIA_BECA",
          "RENOVAR_VIP",
          "RENOVAR_PLATINO",
          "RENOVAR_ORO",
          "UPGRADE_BECA_VIP_ANUAL",
          "BOT_SENALES",
          "BOT_COPY_TRADING",
          "BOT_ALERTAS",
          "BOT_PRO_ANUAL",
          "BOT_LEGADO");

  private static int alArrancar = -1;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private DevelopmentDataSeeder semilla;

  @BeforeAll
  static void mirarAntesDeTocarNada(@Autowired JdbcTemplate jdbc) {
    // Se mira en `@BeforeAll` y no dentro de una prueba porque el orden de los
    // métodos no está garantizado: cualquiera de las otras limpia, y entonces
    // el recuento ya no diría nada sobre el arranque.
    alArrancar = cuantasDeLasDiecinueve(jdbc);
  }

  @BeforeEach
  void reponerElCatalogoDeMembresias() {
    // Otras clases de la suite vacían `memberships`, y sin ellas la parte de
    // asignación de la semilla no tendría a qué apuntar. Se reponen por
    // identificador literal —los de `V46`— para que esta clase no dependa del
    // orden de ejecución. El orden de la cadena es el que dejó `V47`: ORO arriba
    // y BECA abajo.
    //
    // SE VACÍA PRIMERO, y desde el 05-09-2026 hace falta: quien haya corrido
    // antes pudo dejar a BECA SOLA Y SIN PADRE —así la repone `reponerElSuelo`,
    // porque `RN-SP-018` exige que exista—, y entonces el ORO de aquí abajo, que
    // también va sin padre, chocaría con `uq_memberships_parent`. Un `ON CONFLICT
    // (id)` no lo ve: el choque no es de identificador.
    // Y ANTES QUE LAS MEMBRESÍAS, los productos sembrados: los upgrades las
    // referencian por clave foránea, y sin esto el DELETE de abajo fallaría.
    // Se vuelven a sembrar en la prueba que los mira, ya con la cadena entera.
    borrarLosProductos(jdbc);
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM memberships");
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color)
        VALUES
          ('01a04ad0-e800-7004-9c4f-5e7ad7000004', 'ORO', 'Oro', 'Nivel más alto.',
           NULL, 1, 'FFB300'),
          ('01a04ad0-e800-7003-9c4f-5e7ad7000003', 'PLATINO', 'Platino', 'Nivel intermedio.',
           '01a04ad0-e800-7004-9c4f-5e7ad7000004', 2, 'B0BEC5'),
          ('01a04ad0-e800-7002-9c4f-5e7ad7000002', 'VIP', 'VIP', 'Primer nivel de pago.',
           '01a04ad0-e800-7003-9c4f-5e7ad7000003', 3, '7E57C2'),
          ('01a04ad0-e800-7001-9c4f-5e7ad7000001', 'BECA', 'Free', 'Nivel de entrada.',
           '01a04ad0-e800-7002-9c4f-5e7ad7000002', 4, '9E9E9E')
        ON CONFLICT (id) DO NOTHING
        """);
  }

  @AfterAll
  static void devolverLaBaseASuSitio(@Autowired JdbcTemplate jdbc) {
    borrarLosProductos(jdbc);
    borrarLasDiecinueve(jdbc);
  }

  @Test
  @DisplayName("el arranque aplicó la semilla: es un ApplicationRunner y no un método que llamar")
  void seAplicaAlArrancar() {
    // Lo que se verifica es que corre SOLA. Invocarla desde la prueba diría
    // únicamente que el método funciona, y dejaría sin comprobar lo único que
    // hace útil a esta funcionalidad: que nadie tenga que acordarse de nada.
    assertThat(alArrancar)
        .as("las diecinueve personas tienen que estar antes de que esta clase toque la base")
        .isEqualTo(19);

    assertThat(semilla).isInstanceOf(ApplicationRunner.class);
  }

  @Test
  @DisplayName("cada persona porta UN SOLO rol, y ninguno es SUPERADMIN")
  void unRolPorPersonaYNingunSuperadmin() {
    borrarLasDiecinueve(jdbc);
    semilla.run(null);

    // Dos roles VENDEDOR en la misma persona harían indeterminable la comisión
    // de `RF-CM-005`, y `RN-SP-025` —que lo prohíbe— todavía no está
    // implementada: aquí no hay nada que lo impida salvo el propio guion.
    List<Integer> rolesPorPersona =
        jdbc.queryForList(
            """
            SELECT count(*) FROM user_roles ur
              JOIN users u ON u.id = ur.user_id
             WHERE u.username = ANY (?)
             GROUP BY ur.user_id
            """,
            Integer.class,
            (Object) USUARIOS.toArray(String[]::new));

    assertThat(rolesPorPersona).hasSize(19).containsOnly(1);

    Integer conSuperadmin =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM user_roles ur
              JOIN users u ON u.id = ur.user_id
              JOIN roles r ON r.id = ur.role_id
             WHERE u.username = ANY (?) AND r.code = 'SUPERADMIN'
            """,
            Integer.class,
            (Object) USUARIOS.toArray(String[]::new));

    assertThat(conSuperadmin).as("`RN-SP-001` protege el privilegio máximo").isZero();
  }

  @Test
  @DisplayName("los tres clientes reciben membresías ESCALONADAS, no la misma")
  void membresiasEscalonadas() {
    borrarLasDiecinueve(jdbc);
    semilla.run(null);

    List<String> niveles =
        jdbc.queryForList(
            """
            SELECT m.code FROM user_memberships um
              JOIN users u ON u.id = um.user_id
              JOIN memberships m ON m.id = um.membership_id
             WHERE u.username IN ('cliente1', 'cliente2', 'cliente3')
             ORDER BY u.username
            """,
            String.class);

    // Con los tres en el mismo nivel, la mitad de `RF-PM-007` —qué upgrades se
    // pueden ofrecer por encima del nivel vigente— quedaría sin ejercitar.
    assertThat(niveles).containsExactly("BECA", "VIP", "PLATINO");
  }

  @Test
  @DisplayName(
      "las diecinueve nacen SIN cambio de contraseña obligatorio, y esa es la razón del guardia")
  void sinCambioObligatorio() {
    borrarLasDiecinueve(jdbc);
    semilla.run(null);

    Integer retenidas =
        jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE username = ANY (?) AND must_change_password",
            Integer.class,
            (Object) USUARIOS.toArray(String[]::new));

    // No es un detalle de comodidad: es exactamente lo que hace que este guion
    // no pueda llegar a producción. Un alta real por la API nace retenida.
    assertThat(retenidas).isZero();
  }

  @Test
  @DisplayName("el catálogo de prueba: once upgrades con sus dos membresías resueltas y cinco bots")
  void elCatalogoDeProductos() {
    // La cadena entera acaba de reponerse en `@BeforeEach`, de modo que los
    // once upgrades encuentran sus dos membresías por CÓDIGO.
    semilla.run(null);

    assertThat(cuantosProductos(jdbc)).isEqualTo(16);

    List<java.util.Map<String, Object>> upgrades =
        jdbc.queryForList(
            """
            SELECT p.code, o.code AS origen, d.code AS destino, p.status, p.price
              FROM products p
              JOIN memberships o ON o.id = p.source_membership_id
              JOIN memberships d ON d.id = p.target_membership_id
             WHERE p.type = 'UPGRADE_MEMBRESIA' AND p.code = ANY (?)
            """,
            (Object) PRODUCTOS.toArray(String[]::new));
    assertThat(upgrades).hasSize(11);

    // Un upgrade declarado DESDE cada membresía: es lo que hace que los tres
    // clientes escalonados vean ofertas distintas (`RN-PM-011`).
    assertThat(upgrades.stream().map(u -> u.get("origen")).distinct())
        .containsExactlyInAnyOrder("BECA", "VIP", "PLATINO", "ORO");

    // Las cuatro renovaciones, y la de BECA es el producto GRATUITO (desde el
    // 18-09-2026 se llama MEMBRESIA_BECA; si el guion cambia un código y esta
    // lista no, el producto sobrevive al DELETE y bloquea el de memberships).
    assertThat(upgrades.stream().filter(u -> u.get("origen").equals(u.get("destino"))).count())
        .isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT price FROM products WHERE code = 'MEMBRESIA_BECA'",
                java.math.BigDecimal.class))
        .isEqualByComparingTo("0");

    // Los bots no llevan membresía (`RN-PM-002`), y uno declara su precio de compra.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE type = 'BOT' AND code = ANY (?)"
                    + " AND source_membership_id IS NULL AND target_membership_id IS NULL",
                Integer.class,
                (Object) PRODUCTOS.toArray(String[]::new)))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT purchase_price FROM products WHERE code = 'BOT_PRO_ANUAL'",
                java.math.BigDecimal.class))
        .isEqualByComparingTo("250");

    // El inactivo comparte par con un activo —el índice único es parcial— y el
    // retirado tiene su marca: son lo que el catálogo administrativo lista y la
    // oferta no.
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM products WHERE code = 'UPGRADE_BECA_VIP_ANUAL'", String.class))
        .isEqualTo("INACTIVO");
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM products WHERE code = 'BOT_LEGADO'",
                Boolean.class))
        .isTrue();

    // Todos los activos llevan descripción, que es lo que `RN-PM-014` exige.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE status = 'ACTIVO' AND code = ANY (?)"
                    + " AND (description IS NULL OR btrim(description) = '')",
                Integer.class,
                (Object) PRODUCTOS.toArray(String[]::new)))
        .isZero();
  }

  @Test
  @DisplayName("el catálogo también es IDEMPOTENTE: dos arranques, dieciséis productos")
  void elCatalogoEsIdempotente() {
    semilla.run(null);
    semilla.run(null);
    assertThat(cuantosProductos(jdbc)).isEqualTo(16);
  }

  @Test
  @DisplayName("es IDEMPOTENTE: corre en cada arranque y no duplica a nadie")
  void idempotente() {
    // Es lo que permite que sea un `ApplicationRunner` y no una operación que
    // alguien lance una vez. Sin esto, el segundo arranque moriría contra
    // `uq_users_username` y la aplicación no levantaría.
    semilla.run(null);
    semilla.run(null);

    assertThat(cuantasDeLasDiecinueve(jdbc)).isEqualTo(19);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM user_roles ur
                  JOIN users u ON u.id = ur.user_id
                 WHERE u.username = ANY (?)
                """,
                Integer.class,
                (Object) USUARIOS.toArray(String[]::new)))
        .isEqualTo(19);
  }

  @Test
  @DisplayName("las diecinueve nacen con documento CC, y a quien ya existía sin él se lo completa")
  void documento() {
    borrarLasDiecinueve(jdbc);
    semilla.run(null);

    assertThat(sinDocumento()).isZero();

    // Una base sembrada ANTES de que la semilla declarara el documento tiene a
    // las diecinueve sin él, y el INSERT las salta porque ya existen: es lo que
    // el entorno de desarrollo compartido mostraba el 21-09-2026. Se reproduce
    // sobre dos y se vuelve a arrancar.
    jdbc.update(
        """
        UPDATE users
           SET document_type_id = NULL, document_number = NULL, phone = NULL
         WHERE username IN ('agente1', 'cliente3')
        """);
    assertThat(sinDocumento()).isEqualTo(2);

    semilla.run(null);

    assertThat(sinDocumento()).isZero();
    // Con LOS MISMOS valores que recibe quien nace hoy: `CC`, el número derivado
    // del nombre de usuario y el teléfono con la forma que admite el CHECK.
    assertThat(
            jdbc.queryForList(
                """
                SELECT dt.abbreviation || '|' || u.document_number || '|' || u.phone AS fila
                  FROM users u
                  JOIN document_types dt ON dt.id = u.document_type_id
                 WHERE u.username IN ('agente1', 'cliente3')
                 ORDER BY u.username
                """,
                String.class))
        .satisfiesExactly(
            fila -> assertThat(fila).startsWith("CC|AGENTE1|+57300"),
            fila -> assertThat(fila).startsWith("CC|CLIENTE3|+57300"));
    // Y cada una con el suyo: `uq_users_document` no admite dos iguales.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT document_number) FROM users WHERE username = ANY (?)",
                Integer.class,
                (Object) USUARIOS.toArray(String[]::new)))
        .isEqualTo(19);
    // El superadministrador NO es persona de prueba y la semilla no lo toca:
    // nace en V9 sin documento y así sigue.
    assertThat(
            jdbc.queryForObject(
                "SELECT document_type_id IS NULL FROM users WHERE username = 'superadmin'",
                Boolean.class))
        .isTrue();
  }

  @Test
  @DisplayName(
      "CA-SP-730 — las veinte personas ven su perfil: cada una porta users:read-own-profile por su"
          + " rol (RF-SP-062, RN-SEG-015)")
  void todasVenSuPerfil() {
    // Desde el 21-09-2026 GET /users/me exige permiso. V31 lo da a todo rol por su
    // tipo, y la semilla asigna un rol a cada persona: si alguna quedara sin el
    // permiso, entraría a un panel vacío sin poder saber por qué.
    Integer sinPerfil =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM users u
             WHERE u.username = ANY (?)
               AND NOT EXISTS (
                 SELECT 1 FROM user_roles ur
                   JOIN role_permissions rp ON rp.role_id = ur.role_id
                   JOIN permissions p ON p.id = rp.permission_id
                  WHERE ur.user_id = u.id AND p.code = 'users:read-own-profile')
            """,
            Integer.class,
            (Object) USUARIOS.toArray(String[]::new));
    assertThat(sinPerfil).isZero();
  }

  @Test
  @DisplayName("cada director tiene TRES a cargo, y el árbol llega hasta el superadministrador")
  void estructuraComercial() {
    borrarLasDiecinueve(jdbc);
    semilla.run(null);

    String[] usuarios = USUARIOS.toArray(String[]::new);

    // `RN-SP-019` dice que todo el que porte un rol VENDEDOR tiene superior.
    // Sin estas filas la semilla dejaba a directores y agentes en un estado que
    // la regla prohíbe, y `RF-SP-041` se probaría contra una base imposible.
    List<Integer> aCargoPorDirector =
        jdbc.queryForList(
            """
            SELECT count(*) FROM user_supervisors us
              JOIN users sup ON sup.id = us.supervisor_id
             WHERE us.ended_at IS NULL
               AND sup.username = ANY (?)
               AND sup.username LIKE 'director%'
             GROUP BY us.supervisor_id
            """,
            Integer.class, (Object) usuarios);

    // Tres directores con TRES a cargo cada uno: sus agentes. Entre el
    // 04-09-2026 y el 18-09-2026 `director1` tenía cuatro —`cliente2` colgaba
    // de él en esta tabla—; desde `RN-SP-028` revertida la cartera vive en
    // `client_sellers` y el equipo vuelve a ser solo fuerza comercial. Un
    // equipo de uno no distingue «el equipo de alguien» de «alguien».
    assertThat(aCargoPorDirector).containsExactlyInAnyOrder(3, 3, 3);

    // `RN-SP-020` TIENE UNA SOLA RAMA desde el 18-09-2026: entre vendedores el
    // superior porta el rol PADRE INMEDIATO —un agente colgado de un manager
    // pasaría el recuento de arriba y sería igualmente inválido—. Ningún
    // CONSUMIDOR aparece como subordinado: el cliente no cuelga de esta tabla.
    List<String> parejas =
        jdbc.queryForList(
            """
            SELECT rsub.code || ' -> ' || rsup.code
              FROM user_supervisors us
              JOIN user_roles ursub ON ursub.user_id = us.user_id
              JOIN roles rsub ON rsub.id = ursub.role_id
              JOIN user_roles ursup ON ursup.user_id = us.supervisor_id
              JOIN roles rsup ON rsup.id = ursup.role_id
             WHERE us.ended_at IS NULL
             GROUP BY 1
             ORDER BY 1
            """,
            String.class);

    // En orden alfabético, que es el que la consulta pide.
    assertThat(parejas)
        .containsExactly(
            // La rama de FUNCIONARIOS, desde el 10-09-2026. Ninguna regla la
            // exige y `RF-SP-041` no sabría producirla: ver más abajo.
            "ADMIN -> SUPERADMIN",
            // La estructura entre vendedores, que sigue siendo estricta.
            "AGENTE -> DIRECTOR",
            "DIRECTOR -> MANAGER",
            "MANAGER -> ADMIN");

    // Y LA CARTERA, en `client_sellers` y a TRES PROFUNDIDADES distintas: es lo
    // que hace observable en desarrollo el caso que obliga a decidir a qué
    // tarifa cobra quien tiene al cliente cuando no es un agente. Cada cliente
    // con UNA fila REGISTRO —su principal— y ninguna en `user_supervisors`.
    List<String> cartera =
        jdbc.queryForList(
            """
            SELECT c.username || ' -> ' || v.username || ' (' || cs.origin || ')'
              FROM client_sellers cs
              JOIN users c ON c.id = cs.client_id
              JOIN users v ON v.id = cs.seller_id
             WHERE c.username = ANY (?)
             ORDER BY 1
            """,
            String.class,
            (Object) usuarios);
    assertThat(cartera)
        .containsExactly(
            "cliente1 -> agente1 (REGISTRO)",
            "cliente2 -> director1 (REGISTRO)",
            "cliente3 -> manager1 (REGISTRO)");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM user_supervisors us
                  JOIN users c ON c.id = us.user_id
                 WHERE c.username LIKE 'cliente%'
                """,
                Integer.class))
        .isZero();

    // LOS MANAGER YA NO SON LA CÚSPIDE de los datos de prueba, y hasta el
    // 10-09-2026 esta prueba exigía justamente lo contrario: cero managers con
    // superior. Se invirtió por decisión del responsable del proyecto, para que
    // la semilla deje ver la estructura COMPLETA en local.
    //
    // LO QUE SE SIEMBRA AQUÍ `RF-SP-041` LO RECHAZARÍA: `409 VAL-004` para el
    // manager —`RN-SP-019` lo exceptúa por ser la cúspide de la fuerza
    // comercial, ya que su rol padre `ADMIN` no es `VENDEDOR`— y `409 VAL-003`
    // para el administrador, que no pertenece a la fuerza comercial y no tiene
    // superior que asignar. Es DEUDA DECLARADA en la cabecera del guion, y esta
    // prueba es el sitio donde se ve: si algún día `RN-SP-019` y `RN-SP-020` se
    // enmiendan para cubrir a los funcionarios, esto deja de ser deuda sin que
    // haga falta cambiar ni una línea de aquí.
    List<String> ramaDeFuncionarios =
        jdbc.queryForList(
            """
            SELECT sub.username || ' -> ' || sup.username
              FROM user_supervisors us
              JOIN users sub ON sub.id = us.user_id
              JOIN users sup ON sup.id = us.supervisor_id
             WHERE us.ended_at IS NULL
               AND (sub.username LIKE 'manager%' OR sub.username = 'admin1')
             ORDER BY 1
            """,
            String.class);

    assertThat(ramaDeFuncionarios)
        .containsExactly(
            "admin1 -> superadmin",
            "manager1 -> admin1",
            "manager2 -> admin1",
            "manager3 -> admin1");

    // Y LA CÚSPIDE PASA A SER UNA SOLA. Importa porque `RF-SP-042` publica ese
    // hecho OMITIENDO `supervisor` (`CA-SP-445`), y la ausencia significa «no
    // depende de nadie» y nada más: el caso sigue siendo observable en
    // desarrollo, pero ahora hay una raíz y no cuatro.
    Integer superadminConSuperior =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM user_supervisors us
              JOIN users u ON u.id = us.user_id
             WHERE us.ended_at IS NULL AND u.username = 'superadmin'
            """,
            Integer.class);

    assertThat(superadminConSuperior).isZero();
  }

  /** Cuántas de las diecinueve existen sin tipo de documento. */
  private int sinDocumento() {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE username = ANY (?) AND document_type_id IS NULL",
            Integer.class,
            (Object) USUARIOS.toArray(String[]::new));
    return total == null ? 0 : total;
  }

  private static int cuantasDeLasDiecinueve(JdbcTemplate jdbc) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE username = ANY (?)",
            Integer.class,
            (Object) USUARIOS.toArray(String[]::new));
    return total == null ? 0 : total;
  }

  private static int cuantosProductos(JdbcTemplate jdbc) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM products WHERE code = ANY (?)",
            Integer.class,
            (Object) PRODUCTOS.toArray(String[]::new));
    return filas == null ? 0 : filas;
  }

  private static void borrarLosProductos(JdbcTemplate jdbc) {
    // Los ENLACES primero: `fk_product_links_product` no lleva `ON DELETE`
    // —el producto no se borra físicamente nunca (`RN-PM-010`)— y la semilla
    // le pone su video a cada producto desde el 22-09-2026 (`RN-PM-048`).
    jdbc.update(
        "DELETE FROM product_links WHERE product_id IN"
            + " (SELECT id FROM products WHERE code = ANY (?))",
        (Object) PRODUCTOS.toArray(String[]::new));
    jdbc.update(
        "DELETE FROM products WHERE code = ANY (?)", (Object) PRODUCTOS.toArray(String[]::new));
  }

  private static void borrarLasDiecinueve(JdbcTemplate jdbc) {
    String[] usuarios = USUARIOS.toArray(String[]::new);
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN (SELECT id FROM users WHERE username = ANY"
            + " (?))",
        (Object) usuarios);
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN (SELECT id FROM users WHERE username ="
            + " ANY (?)) OR seller_id IN (SELECT id FROM users WHERE username = ANY (?))",
        usuarios,
        usuarios);
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username ="
            + " ANY (?)) OR supervisor_id IN (SELECT id FROM users WHERE username = ANY (?))",
        usuarios,
        usuarios);
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = ANY (?))",
        (Object) usuarios);
    jdbc.update("DELETE FROM users WHERE username = ANY (?)", (Object) usuarios);
  }
}
