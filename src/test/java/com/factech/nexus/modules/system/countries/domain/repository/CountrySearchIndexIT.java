package com.factech.nexus.modules.system.countries.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El índice de búsqueda del catálogo de países **sirve para la consulta que se hace** (`RF-SP-021`
 * · `T-11`).
 *
 * <h2>Qué se afirma aquí, y qué sería falso afirmar</h2>
 *
 * <p>{@code CountriesIT} ya comprueba que {@code ix_countries_busqueda} está <b>declarado</b> y que
 * es un GIN de trigramas. Lo que faltaba —y lo que `plan.md` §11 pedía— es que el planificador
 * pueda <b>resolver con él</b> el predicado de la búsqueda. Es la clase de cosa que ninguna prueba
 * funcional detecta: si el índice deja de ser aplicable, los resultados siguen siendo correctos y
 * solo cambia el coste.
 *
 * <p><b>Lo que NO se afirma es que el planificador lo prefiera</b>, y no es una rebaja: sería
 * afirmar algo falso. El catálogo de países tiene del orden de <b>250 filas</b> en producción, y
 * con ese tamaño recorrer la tabla entera es de verdad más barato que consultar un índice —
 * PostgreSQL elige el recorrido secuencial y <b>acierta</b>. Lo dice la propia migración que crea
 * el índice: existe «para el caso en que el catálogo se pueble de verdad». Una prueba que exigiera
 * la preferencia obligaría a sembrar un volumen que este catálogo no va a tener nunca, y estaría
 * verificando un escenario inventado.
 *
 * <p>De ahí que la comprobación se haga con {@code enable_seqscan = off}: eso no fuerza al
 * planificador a usar <b>este</b> índice, solo le quita la salida fácil. Si el predicado dejara de
 * poder resolverse con él —porque alguien cambió su forma, cambió la clase de operadores del índice
 * o lo recreó mal—, PostgreSQL seguiría prefiriendo un recorrido completo aun penalizado, y la
 * prueba lo vería.
 *
 * <h2>La otra limitación, dicha para que nadie la dé por cubierta</h2>
 *
 * <p>El {@code EXPLAIN} corre sobre el predicado escrito <b>en esta clase</b> y no sobre el que
 * Hibernate genera desde la API de criterios: no hay forma limpia de recuperar aquel SQL con sus
 * parámetros puestos. Si alguien cambia la forma del predicado en {@code
 * JpaCountryQueryRepository#findAll}, esta prueba seguirá en verde. Por eso la segunda prueba
 * ejercita el repositorio de verdad: no cubre el hueco entero, pero impide que los dos caminos
 * devuelvan cosas distintas sin que nadie se entere.
 */
class CountrySearchIndexIT extends IntegrationTestBase {

  /**
   * Cuántos países se siembran.
   *
   * <p>Suficiente para que el plan sea representativo y no el de una tabla vacía. El tope ya no lo
   * fija el formato: con tres letras mayúsculas caben 17.576 códigos, y ninguna cifra razonable de
   * siembra los agota. 675 es lo que se siembra; uno más se reserva para la aguja.
   */
  private static final int CUANTOS = 675;

  /** Un término que aparece en una sola fila: es el caso para el que un índice sirve. */
  private static final String TERMINO = "zyx";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private CountryQueryRepository paises;

  @BeforeEach
  void sembrar() {
    limpiar();

    List<Object[]> filas =
        IntStream.range(0, CUANTOS)
            .mapToObj(
                i ->
                    new Object[] {UUID.randomUUID(), codigo(i), "Pais de prueba numero " + i, true})
            .collect(Collectors.toList());

    jdbc.batchUpdate(
        "INSERT INTO countries (id, code, name, is_active) VALUES (?, ?, ?, ?)", filas);

    // La aguja en el pajar. Va con acento a propósito: la búsqueda es
    // insensible a ellos y el índice se construye sobre `f_unaccent`.
    jdbc.update(
        "INSERT INTO countries (id, code, name, is_active) VALUES (?, ?, ?, true)",
        UUID.randomUUID(),
        "ZZZ",
        "Zyxá del Norte");

    // Sin estadísticas, el planificador decide sobre las de una tabla vacía y
    // el plan que se lea no dice nada de la tabla que hay.
    jdbc.execute("ANALYZE countries");
  }

  @AfterEach
  void limpiarDespues() {
    limpiar();
  }

  @Test
  @DisplayName("el predicado de la búsqueda SE PUEDE resolver con ix_countries_busqueda")
  void elIndiceSirveParaLaConsulta() {
    String plan = String.join("\n", explicarBusqueda());

    assertThat(plan).as("el plan de ejecución fue:%n%s", plan).contains("ix_countries_busqueda");
  }

  @Test
  @DisplayName(
      "y con el catálogo pequeño el planificador prefiere recorrer la tabla, que es correcto")
  void conPocasFilasPrefiereElRecorrido() {
    /*
     * Se afirma a propósito, y no es una curiosidad: deja escrito en la suite
     * que el recorrido secuencial de este catálogo NO es un defecto del índice.
     * Sin esta prueba, quien lea la anterior podría «arreglar» algo que no está
     * roto — bajar `random_page_cost`, forzar el índice desde la consulta— y
     * empeorar el caso real por hacer cierto un plan que nadie necesita.
     */
    String plan =
        String.join(
            "\n", jdbc.queryForList("EXPLAIN " + BUSQUEDA, String.class, patron(), patron()));

    assertThat(plan).as("el plan de ejecución fue:%n%s", plan).contains("Seq Scan on countries");
  }

  @Test
  @DisplayName("la búsqueda real devuelve exactamente lo que el predicado encuentra")
  void laBusquedaRealCoincide() {
    var encontrados = paises.findAll(TERMINO, false);

    assertThat(encontrados).hasSize(1);
    assertThat(encontrados.get(0).name()).isEqualTo("Zyxá del Norte");
  }

  /**
   * El mismo predicado que construye {@code JpaCountryQueryRepository#findAll}: {@code
   * f_unaccent(lower(columna)) LIKE '%término%'} sobre {@code code} y sobre {@code name}.
   */
  private static final String BUSQUEDA =
      """
      SELECT c.id, c.code, c.name, c.is_active
        FROM countries c
       WHERE c.is_active = true
         AND (f_unaccent(lower(c.code)) LIKE ?
              OR f_unaccent(lower(c.name)) LIKE ?)
       ORDER BY c.name
      """;

  /**
   * El plan con el recorrido secuencial <b>penalizado</b>.
   *
   * <p>Las dos sentencias van en la <b>misma conexión</b>: {@code SET} vale para la sesión, y con
   * dos llamadas sueltas el pool podría dar conexiones distintas y el ajuste no alcanzaría al
   * {@code EXPLAIN}. Se restaura al salir para no contaminar la conexión que vuelve al pool.
   */
  private List<String> explicarBusqueda() {
    return jdbc.execute(
        (ConnectionCallback<List<String>>)
            conexion -> {
              try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute("SET enable_seqscan = off");
                try {
                  return leerPlan(conexion);
                } finally {
                  sentencia.execute("SET enable_seqscan = on");
                }
              }
            });
  }

  private List<String> leerPlan(java.sql.Connection conexion) throws java.sql.SQLException {
    List<String> lineas = new ArrayList<>();
    try (var consulta = conexion.prepareStatement("EXPLAIN " + BUSQUEDA)) {
      consulta.setString(1, patron());
      consulta.setString(2, patron());
      try (ResultSet filas = consulta.executeQuery()) {
        while (filas.next()) {
          lineas.add(filas.getString(1));
        }
      }
    }
    return lineas;
  }

  private static String patron() {
    return "%" + TERMINO + "%";
  }

  /** Códigos de tres letras distintos entre sí, que es lo único que la tabla exige. */
  private static String codigo(int i) {
    return String.valueOf((char) ('A' + i / 676))
        + (char) ('A' + (i / 26) % 26)
        + (char) ('A' + i % 26);
  }

  private void limpiar() {
    // `countries` no la siembra ninguna migración: arranca vacía y se devuelve
    // vacía. Las monedas no se tocan: la referencia va en el otro sentido.
    jdbc.update("DELETE FROM countries");
  }
}
