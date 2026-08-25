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

    // Credencial inicial del superadministrador, que `V22__seed_superadmin.sql`
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
   * Identificador del superadministrador sembrado por {@code V22__seed_superadmin.sql}.
   *
   * <p>Es fijo a propósito: toda prueba de integración que necesite un actor con permisos reales lo
   * refiere por esta constante en lugar de consultarlo, que es la razón por la que aquella
   * migración escribe el identificador en lugar de generarlo.
   */
  protected static final java.util.UUID SUPERADMIN =
      java.util.UUID.fromString("01a033a4-4a00-7001-9c4f-5e7ad4000001");
}
