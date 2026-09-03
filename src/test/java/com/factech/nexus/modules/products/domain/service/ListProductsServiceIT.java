package com.factech.nexus.modules.products.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.application.ListProductsRequest;
import com.factech.nexus.modules.products.application.ProductItem;
import com.factech.nexus.modules.products.application.ProductPageResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Coste y plan del listado del catálogo (`RF-PM-002` · `T-05`, `T-07`, `T-11` y `T-12`).
 *
 * <p>Lo que se comprueba aquí <b>no se ve en el resultado</b>: cuántas sentencias cuesta la
 * consulta, qué índice usa y si la paginación es estable. Las tres son invisibles desde el JSON
 * —una página que empezara a costar cien consultas, o que se saltara filas al paginar, devolvería
 * exactamente la misma forma— y por eso ninguna prueba de API las detecta.
 */
class ListProductsServiceIT extends IntegrationTestBase {

  /** La moneda sembrada por `V15`. */
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  /**
   * Volumen sembrado para las dos pruebas de plan.
   *
   * <p><b>Se siembra a propósito y no es un número decorativo</b>: con una decena de filas el
   * planificador elige recorrido secuencial —y hace bien—, de modo que la prueba daría verde sin
   * haber comprobado que el índice existe y sirve. Es el mismo hueco que `SP` lleva abierto desde
   * `RF-SP-021` · `T-11`.
   *
   * <p><b>`T-12` fija doscientos y doscientos NO bastan.</b> Con esa cifra la prueba pasa aislada y
   * falla dentro de la suite completa: el recorrido secuencial de doscientas filas cuesta {@code
   * 57.00} y el planificador lo prefiere al índice, con razón. No es que el índice no sirva — es
   * que a ese tamaño no hace falta, y una prueba que dependa de qué más corrió antes no comprueba
   * nada. Con dos mil, el recorrido cuesta diez veces más y el índice gana de forma estable.
   *
   * <p><b>Si alguna de las dos pruebas de plan empezara a fallar, la primera sospecha es este
   * número</b> —otra versión del motor puede mover el umbral— y no el índice: súbase antes de darlo
   * por perdido.
   */
  private static final int VOLUMEN = 2000;

  @Autowired private ListProductsService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;

  @BeforeEach
  void limpiar() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM memberships");
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @Test
  @DisplayName("`T-05` — la consulta cuesta DOS sentencias SIN filtros: la página y el conteo")
  void dosSentenciasSinFiltros() {
    sembrar(5);
    estadisticas.clear();

    ProductPageResponse pagina = service.list(peticion(null, null, null));

    assertThat(pagina.content()).hasSize(5);
    // Dos y no siete: el destino y la moneda viajan en el LEFT JOIN de la misma
    // consulta. Resolverlos fila a fila contra el puerto de `SP` daría una
    // consulta por producto, y el JSON sería idéntico.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("`T-05` — y sigue costando DOS con los seis filtros puestos a la vez")
  void dosSentenciasConTodosLosFiltros() {
    UUID destino = membresia("ORO", "Oro", 1);
    UUID origen = membresia("FREE", "Free", 2, destino);
    sembrar(5);
    jdbc.update(
        "UPDATE products SET type = 'UPGRADE_MEMBRESIA',"
            + " source_membership_id = CAST(? AS uuid),"
            + " target_membership_id = CAST(? AS uuid)"
            + " WHERE code = 'VOL_1'",
        origen.toString(),
        destino.toString());
    estadisticas.clear();

    ProductPageResponse pagina =
        service.list(
            new ListProductsRequest(
                0,
                10,
                "name,asc",
                "UPGRADE_MEMBRESIA",
                "ACTIVO",
                origen,
                destino,
                "Producto",
                true));

    assertThat(pagina.content()).hasSize(1);
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("`T-07` — la página vacía más allá de la última devuelve el total REAL")
  void elConteoNoSeDeduce() {
    sembrar(12);
    estadisticas.clear();

    ProductPageResponse pagina =
        service.list(new ListProductsRequest(99, 20, null, null, null, null, null, null, null));

    assertThat(pagina.content()).isEmpty();
    // Y no 1980, que es lo que daría deducir el total del desplazamiento: un
    // número inventado, con la colección vacía y sin error que lo delate.
    assertThat(pagina.totalElements()).isEqualTo(12);
    assertThat(pagina.totalIsExact()).isTrue();
    // El atajo de `RF-SP-002` —omitir el conteo cuando la página no se llena—
    // NO se aplica aquí: se cuenta siempre, también con la página vacía.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("`T-07` — el conteo aplica EL MISMO filtro que la página, o los dos divergen")
  void elConteoAplicaElMismoFiltro() {
    sembrar(10);
    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE code IN ('VOL_1', 'VOL_2')");

    ProductPageResponse activos = service.list(peticion(3, null, "ACTIVO"));

    // Ocho activos en páginas de tres: el total dice ocho aunque la página
    // traiga tres. Un conteo escrito aparte —sin el filtro— diría diez, y el
    // defecto solo se ve comparando ambos números.
    assertThat(activos.content()).hasSize(3);
    assertThat(activos.totalElements()).isEqualTo(8);
  }

  @Test
  @DisplayName(
      "`T-11` — se recorren todas las páginas con productos del MISMO instante de alta y no falta"
          + " ni se repite ninguno")
  void paginacionEstable() {
    // Todos con el mismo `created_at`: es el caso que hace inestable un orden
    // que no sea total. Sin el desempate por identificador, el motor devuelve
    // los empates en el orden que le convenga —y no tiene por qué ser el mismo
    // entre dos sentencias—, de modo que una fila puede salir en dos páginas y
    // otra en ninguna.
    sembrar(21);

    List<UUID> recorridos = recorrerTodo(null, 2, 11);

    assertThat(recorridos)
        .as("ninguna fila se repite entre páginas")
        .hasSameSizeAs(new HashSet<>(recorridos));
    assertThat(recorridos).as("ninguna fila se queda sin salir").hasSize(21);
  }

  @Test
  @DisplayName("`T-11` — y también al ordenar por precio, donde el empate se provoca a mano")
  void paginacionEstableConPreciosIguales() {
    sembrar(9);
    jdbc.update("UPDATE products SET price = 10.0000");

    List<UUID> recorridos = recorrerTodo("price,asc", 2, 5);

    assertThat(recorridos).hasSize(9);
    assertThat(new HashSet<>(recorridos)).hasSize(9);
  }

  @Test
  @DisplayName("`T-12` — con volumen sembrado, la búsqueda usa `ix_products_busqueda`")
  void laBusquedaUsaSuIndice() {
    sembrar(VOLUMEN);
    jdbc.update(
        "UPDATE products SET name = 'Ascenso a Zafiro imperial' WHERE code = ?", "VOL_" + VOLUMEN);
    // Sin estadísticas frescas el planificador decide sobre una tabla que cree
    // vacía, y elegiría el recorrido secuencial por el mismo motivo que con
    // pocas filas.
    jdbc.execute("ANALYZE products");

    String plan =
        String.join(
            "\n",
            jdbc.queryForList(
                """
                EXPLAIN SELECT p.id FROM products p
                 WHERE p.deleted_at IS NULL
                   AND f_unaccent(lower(p.name)) LIKE f_unaccent(lower(?)) ESCAPE '\\'
                 ORDER BY p.created_at DESC, p.id DESC
                 OFFSET 0 LIMIT 20
                """,
                String.class,
                "%zafiro%"));

    assertThat(plan)
        .as("plan de la búsqueda con %s productos sembrados:%n%s", VOLUMEN, plan)
        .contains("ix_products_busqueda");
  }

  @Test
  @DisplayName("`T-01` — el orden por omisión usa `ix_products_listado`")
  void elListadoUsaSuIndice() {
    sembrar(VOLUMEN);
    jdbc.execute("ANALYZE products");

    String plan =
        String.join(
            "\n",
            jdbc.queryForList(
                """
                EXPLAIN SELECT p.id FROM products p
                 WHERE p.deleted_at IS NULL
                 ORDER BY p.created_at DESC, p.id DESC
                 OFFSET 0 LIMIT 20
                """,
                String.class));

    assertThat(plan).as("plan del orden por omisión:%n%s", plan).contains("ix_products_listado");
  }

  // ---------------------------------------------------------------------------

  /** Recorre {@code paginas} páginas de {@code tamano} y devuelve los identificadores vistos. */
  private List<UUID> recorrerTodo(String orden, int tamano, int paginas) {
    List<UUID> vistos = new ArrayList<>();
    for (int pagina = 0; pagina < paginas; pagina++) {
      service
          .list(new ListProductsRequest(pagina, tamano, orden, null, null, null, null, null, null))
          .content()
          .stream()
          .map(ProductItem::id)
          .forEach(vistos::add);
    }
    return vistos;
  }

  private static ListProductsRequest peticion(Integer tamano, String orden, String estado) {
    return new ListProductsRequest(0, tamano, orden, null, estado, null, null, null, null);
  }

  private UUID membresia(String codigo, String nombre, int nivel) {
    return membresia(codigo, nombre, nivel, null);
  }

  private UUID membresia(String codigo, String nombre, int nivel, UUID superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (CAST(? AS uuid), ?, ?, CAST(? AS uuid), ?,"
            + " upper(lpad(to_hex(? * 4919), 6, '0')))",
        id.toString(),
        codigo,
        nombre,
        superior == null ? null : superior.toString(),
        nivel,
        nivel);
    return id;
  }

  /**
   * Siembra {@code cuantos} servicios activos, <b>todos con el mismo instante de alta</b>.
   *
   * <p>El mismo instante es deliberado: es lo que convierte el orden por fecha en un empate masivo
   * y deja que {@code paginacionEstable} compruebe el desempate.
   */
  private void sembrar(int cuantos) {
    List<Object[]> filas = new ArrayList<>(cuantos);
    for (int i = 1; i <= cuantos; i++) {
      filas.add(
          new Object[] {
            UUID.randomUUID().toString(),
            "VOL_" + i,
            "Producto " + i,
            new BigDecimal(i + ".00"),
            USD,
            BASE,
            BASE
          });
    }
    jdbc.batchUpdate(
        "INSERT INTO products (id, code, type, name, source_membership_id,"
            + " target_membership_id, price, currency_id,"
            + " status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, NULL, NULL, ?, CAST(? AS uuid), 'ACTIVO',"
            + " ?, ?)",
        filas);
  }

  /**
   * Deja `products` vacía al terminar CADA prueba.
   *
   * <p><b>No es higiene: es lo que impide romper a otras clases.</b> Un producto que sobreviva a
   * esta clase mantiene una clave foránea sobre `memberships`, y varias pruebas de `SP` empiezan
   * con `DELETE FROM memberships WHERE level > 0`. Ese borrado falla con violación de integridad, y
   * el fallo aparece <b>lejos de aquí</b> —en la clase que borra— y solo cuando el orden de
   * ejecución las pone en ese orden, que es la peor forma de romper una suite.
   */
  @AfterEach
  void vaciarCatalogo() {
    jdbc.update("DELETE FROM products");
  }
}
