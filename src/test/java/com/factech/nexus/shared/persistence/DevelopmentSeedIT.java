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
 * El resto de la suite corre como {@code testing} y con la semilla apagada, porque quince personas
 * apareciendo solas romperían decenas de pruebas que cuentan personas y roles.
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
          "admin2",
          "admin3",
          "agente1",
          "agente2",
          "agente3",
          "cliente1",
          "cliente2",
          "cliente3",
          "director1",
          "director2",
          "director3",
          "manager1",
          "manager2",
          "manager3");

  /** Cuántas de las quince había ANTES de que esta clase tocara nada. */
  private static int alArrancar = -1;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private DevelopmentDataSeeder semilla;

  @BeforeAll
  static void mirarAntesDeTocarNada(@Autowired JdbcTemplate jdbc) {
    // Se mira en `@BeforeAll` y no dentro de una prueba porque el orden de los
    // métodos no está garantizado: cualquiera de las otras limpia, y entonces
    // el recuento ya no diría nada sobre el arranque.
    alArrancar = cuantasDeLasQuince(jdbc);
  }

  @BeforeEach
  void reponerElCatalogoDeMembresias() {
    // Otras clases de la suite hacen `DELETE FROM memberships WHERE level > 0`,
    // y sin las membresías la parte de asignación de la semilla no tendría a
    // qué apuntar. Se reponen por identificador literal —los de `V46`— para que
    // esta clase no dependa del orden de ejecución. El orden de la cadena es el que
    // dejó `V47`: ORO arriba y FREE abajo.
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
          ('01a04ad0-e800-7001-9c4f-5e7ad7000001', 'FREE', 'Free', 'Nivel de entrada.',
           '01a04ad0-e800-7002-9c4f-5e7ad7000002', 4, '9E9E9E')
        ON CONFLICT (id) DO NOTHING
        """);
  }

  @AfterAll
  static void devolverLaBaseASuSitio(@Autowired JdbcTemplate jdbc) {
    borrarLasQuince(jdbc);
  }

  @Test
  @DisplayName("el arranque aplicó la semilla: es un ApplicationRunner y no un método que llamar")
  void seAplicaAlArrancar() {
    // Lo que se verifica es que corre SOLA. Invocarla desde la prueba diría
    // únicamente que el método funciona, y dejaría sin comprobar lo único que
    // hace útil a esta funcionalidad: que nadie tenga que acordarse de nada.
    assertThat(alArrancar)
        .as("las quince personas tienen que estar antes de que esta clase toque la base")
        .isEqualTo(15);

    assertThat(semilla).isInstanceOf(ApplicationRunner.class);
  }

  @Test
  @DisplayName("cada persona porta UN SOLO rol, y ninguno es SUPERADMIN")
  void unRolPorPersonaYNingunSuperadmin() {
    borrarLasQuince(jdbc);
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

    assertThat(rolesPorPersona).hasSize(15).containsOnly(1);

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
    borrarLasQuince(jdbc);
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
    assertThat(niveles).containsExactly("FREE", "VIP", "PLATINO");
  }

  @Test
  @DisplayName(
      "las quince nacen SIN cambio de contraseña obligatorio, y esa es la razón del guardia")
  void sinCambioObligatorio() {
    borrarLasQuince(jdbc);
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
  @DisplayName("es IDEMPOTENTE: corre en cada arranque y no duplica a nadie")
  void idempotente() {
    // Es lo que permite que sea un `ApplicationRunner` y no una operación que
    // alguien lance una vez. Sin esto, el segundo arranque moriría contra
    // `uq_users_username` y la aplicación no levantaría.
    semilla.run(null);
    semilla.run(null);

    assertThat(cuantasDeLasQuince(jdbc)).isEqualTo(15);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM user_roles ur
                  JOIN users u ON u.id = ur.user_id
                 WHERE u.username = ANY (?)
                """,
                Integer.class,
                (Object) USUARIOS.toArray(String[]::new)))
        .isEqualTo(15);
  }

  private static int cuantasDeLasQuince(JdbcTemplate jdbc) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE username = ANY (?)",
            Integer.class,
            (Object) USUARIOS.toArray(String[]::new));
    return total == null ? 0 : total;
  }

  private static void borrarLasQuince(JdbcTemplate jdbc) {
    String[] usuarios = USUARIOS.toArray(String[]::new);
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN (SELECT id FROM users WHERE username = ANY"
            + " (?))",
        (Object) usuarios);
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = ANY (?))",
        (Object) usuarios);
    jdbc.update("DELETE FROM users WHERE username = ANY (?)", (Object) usuarios);
  }
}
