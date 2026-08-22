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
  }
}
