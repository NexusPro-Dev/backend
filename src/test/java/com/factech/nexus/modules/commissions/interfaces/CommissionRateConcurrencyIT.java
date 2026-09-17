package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Las dos reglas de `CM` que viven en el motor, bajo concurrencia.
 *
 * <p><b>Es la prueba que verifica DÓNDE vive cada regla.</b> Las otras suites comprueban que el
 * solapamiento y el rol duplicado se rechazan, y pasarían igual si la comprobación fuera un {@code
 * SELECT} previo en el caso de uso. Esta no: dos peticiones simultáneas leerían las dos que no hay
 * conflicto y las dos insertarían.
 *
 * <p>Es el defecto que `RN-SP-018` tuvo y que se corrigió el 26-08-2026, escrito aquí antes de
 * volver a tenerlo.
 *
 * <p><b>Y desde el 01-09-2026 son dos reglas y no una.</b> El no solapamiento se mudó a las tasas
 * personalizadas —las de rol perdieron la vigencia y con ella la posibilidad de solaparse—, y nació
 * `RN-CM-013`, que un {@code SELECT} previo burlaría igual de fácil. Desde el 15-09-2026 esa regla
 * la cierra {@code uq_commission_rates_product_role} en la propia tabla de tasas (`RN-CM-021`), y
 * la carrera es entre dos <b>altas</b>, no entre dos asociaciones.
 */
@AutoConfigureMockMvc
class CommissionRateConcurrencyIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedora;

  @BeforeEach
  void preparar() {
    limpiar();
    vendedora = CommissionFixtures.sembrarPersonaConRol(jdbc, "vendedora", MANAGER);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RN-CM-006` — el no solapamiento, OTRA VEZ EN EL MOTOR (16-09-2026)
  //
  // Hasta `V85` esta regla la garantizaba un `EXCLUDE` y estas pruebas
  // comprobaban que la violación llegaba TRADUCIDA. Del 11-09-2026 al
  // 16-09-2026 el índice no existía —la regla cruzaba dos tablas— y lo único que
  // la sostenía era el bloqueo consultivo del caso de uso al asociar. Desde
  // `V10` la personalizada nace con su producto y el `EXCLUDE` vuelve, sobre
  // (persona, producto, rango).
  //
  // De modo que estas pruebas vuelven a verificar UNA TRADUCCIÓN, y de dos
  // estados: `23P01` cuando la otra ya confirmó, y `40P01` cuando las dos
  // inserciones se esperan y PostgreSQL mata a una. La memoria del proyecto
  // dice que el segundo aparece una de cada pocas ejecuciones: esta prueba se
  // corre varias veces seguidas antes de darla por buena.
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-147 · dos altas simultáneas de la misma persona sobre el mismo producto y periodo:"
          + " una queda, la otra recibe 409")
  void dosAltasSimultaneasDelMismoPeriodo() throws Exception {
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_CONC");

    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                indice == 0
                    ? altaPersonal(producto, "10.00", "2026-01-01", "2026-12-31")
                    : altaPersonal(producto, "12.00", "2026-06-01", "2026-12-31"));

    // Ninguna puede salir como 500: la consulta previa del caso de uso no ve
    // la carrera, y lo que la cierra es el EXCLUDE — que hay que traducir, por
    // los DOS estados.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    assertThat(personalizadasSobre(producto))
        .as("las dos tasas quedaron, o no quedó ninguna")
        .isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .as("exactamente una debía registrarse")
        .isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .as("y la otra debía recibir el conflicto")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("dos altas simultáneas de periodos que NO se tocan, mismo producto: las dos quedan")
  void dosAltasSimultaneasConsecutivas() throws Exception {
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_CONC2");

    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                indice == 0
                    ? altaPersonal(producto, "10.00", "2026-01-01", "2026-06-30")
                    : altaPersonal(producto, "12.00", "2026-07-01", "2026-12-31"));

    assertThat(resultados).allMatch(r -> r.succeeded() && r.value() == 201);
    assertThat(personalizadasSobre(producto)).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // `RN-CM-013` — un rol por producto
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-138 · dos altas simultáneas del mismo rol sobre el mismo producto: solo una entra")
  void dosAltasSimultaneasDelMismoRol() throws Exception {
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> alta(producto, MANAGER, indice == 0 ? "10.00" : "15.00"));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // Si entraran las dos, la resolución tendría dos respuestas válidas para
    // «qué paga MANAGER por este producto» y elegiría el plan de ejecución. El
    // `existsAlive` previo da el mensaje en el camino normal; lo que cierra la
    // carrera es el índice único parcial —y el bloqueo del producto que toma
    // el tope, que pone las dos altas en fila.
    assertThat(cuantasTasas()).isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // `RN-CM-019` — el tope de cien (`cm.md` v0.8.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-108 · dos altas simultáneas sobre el mismo producto, dentro del tope por separado"
          + " pero juntas fuera: solo una entra")
  void dosAltasSimultaneasSePasanDeCienJuntas() throws Exception {
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_CAP");

    // Cada una, sola, cabe de sobra. Las dos juntas suman 110.
    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                indice == 0 ? alta(producto, MANAGER, "60.00") : alta(producto, DIRECTOR, "50.00"));

    // Ninguna puede salir como 500: si el bloqueo consultivo no cerrara la
    // ventana, las dos leerían la suma "antes" y las dos pasarían.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    assertThat(cuantasTasas()).as("las dos entraron, o no entró ninguna").isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  /** El alta de una personalizada de la vendedora sobre un producto, devolviendo el estado. */
  private int altaPersonal(UUID producto, String porcentaje, String desde, String hasta) {
    return estadoDe(
        post("/api/v1/user-commission-rates")
            .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"productId\":\""
                    + producto
                    + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":"
                    + porcentaje
                    + ",\"validFrom\":\""
                    + desde
                    + "\",\"validTo\":\""
                    + hasta
                    + "\"}"));
  }

  private long personalizadasSobre(UUID producto) {
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_commission_rates WHERE product_id = CAST(? AS uuid)",
            Long.class,
            producto.toString());
    return total == null ? 0 : total;
  }

  private int alta(UUID producto, String rol, String porcentaje) {
    return estadoDe(
        post("/api/v1/commission-rates")
            .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"productId\":\""
                    + producto
                    + "\",\"roleId\":\""
                    + rol
                    + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":"
                    + porcentaje
                    + "}"));
  }

  private int estadoDe(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion) {
    try {
      return mvc.perform(peticion).andReturn().getResponse().getStatus();
    } catch (Exception fallo) {
      throw new IllegalStateException(fallo);
    }
  }

  private long cuantasPersonales() {
    return jdbc.queryForObject("SELECT count(*) FROM user_commission_rates", Long.class);
  }

  private long cuantasTasas() {
    return jdbc.queryForObject("SELECT count(*) FROM commission_rates", Long.class);
  }

  private void limpiar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'CM'");
  }
}
