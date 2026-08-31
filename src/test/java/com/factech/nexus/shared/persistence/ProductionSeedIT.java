package com.factech.nexus.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * En producción NO se siembra, y el interruptor no puede cambiarlo.
 *
 * <p>Es la mitad que da valor a {@code DevelopmentSeedIT}: sin ella, una semilla rota pasaría por
 * un guardia que funciona. Aquí se enciende {@code nexus.dev-seed.enabled} <b>a propósito</b>, para
 * que lo único que pueda estar deteniendo la siembra sea el entorno.
 *
 * <p>Lo que se protege es concreto: quince cuentas que comparten el hash del superadministrador y
 * que no están obligadas a cambiar la contraseña. El archivo del guion <b>sí viaja</b> dentro del
 * artefacto de producción —tiene que hacerlo, porque el classpath es el mismo—, de modo que esta
 * prueba es lo único que separa ese archivo de esas cuentas.
 */
@TestPropertySource(
    properties = {
      "nexus.environment=production",
      "nexus.dev-seed.enabled=true",
      // Dos conexiones y no las diez de serie; el porqué está en
      // `DevelopmentSeedIT`.
      "spring.datasource.hikari.maximum-pool-size=2"
    })
class ProductionSeedIT extends IntegrationTestBase {

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

  @Autowired private JdbcTemplate jdbc;
  @Autowired private DevelopmentDataSeeder semilla;

  @Test
  @DisplayName("el arranque en producción no sembró ninguna de las quince")
  void elArranqueNoSembroNada() {
    assertThat(cuantasDeLasQuince()).isZero();
  }

  @Test
  @DisplayName("invocarla a mano en producción tampoco siembra: el entorno se mira primero")
  void niInvocandolaDirectamente() {
    // El interruptor está en `true` en esta clase. Si el orden de las dos
    // comprobaciones estuviera al revés —o si alguien las juntara en un solo
    // `if` con un `||`—, esto sembraría y sería la única prueba que lo vería.
    semilla.run(null);

    assertThat(cuantasDeLasQuince()).isZero();
  }

  private int cuantasDeLasQuince() {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE username = ANY (?)",
            Integer.class,
            (Object) USUARIOS.toArray(String[]::new));
    return total == null ? 0 : total;
  }
}
