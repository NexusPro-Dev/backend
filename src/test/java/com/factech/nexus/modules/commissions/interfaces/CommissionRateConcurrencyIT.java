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
 * `RN-CM-013`, que un {@code SELECT} previo burlaría igual de fácil.
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
  // `RN-CM-006` — el no solapamiento, YA NO EN EL MOTOR
  //
  // Hasta `V85` esta regla la garantizaba un `EXCLUDE`, y estas pruebas
  // comprobaban que la violación llegaba TRADUCIDA. Desde el 11-09-2026 el
  // índice no existe —la regla cruza dos tablas— y lo único que la sostiene es
  // el BLOQUEO CONSULTIVO que toma el caso de uso al asociar.
  //
  // De modo que estas pruebas cambian de sitio y de peso: antes verificaban una
  // traducción, ahora verifican LA GARANTÍA ENTERA. Si alguien quita el bloqueo,
  // es aquí y solo aquí donde se nota.
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("dos asociaciones simultáneas que se solapan: una queda, la otra recibe 409")
  void dosAsociacionesSimultaneasDelMismoPeriodo() throws Exception {
    // Las dos altas pasan: sin producto no hay solapamiento posible.
    UUID primera = altaPersonal("10.00", "2026-01-01", "2026-12-31");
    UUID segunda = altaPersonal("12.00", "2026-06-01", "2026-12-31");
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_CONC");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> asociarPersonal(indice == 0 ? primera : segunda, producto));

    // Ninguna puede salir como 500: sin el bloqueo las dos leerían «no hay
    // solape» y las dos escribirían, y no habría ni error que traducir.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    assertThat(asociacionesDe(producto))
        .as("las dos asociaciones quedaron, o no quedó ninguna")
        .isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .as("exactamente una debía asociarse")
        .isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .as("y la otra debía recibir el conflicto")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("dos asociaciones simultáneas de periodos que NO se tocan: las dos quedan")
  void dosAsociacionesSimultaneasConsecutivas() throws Exception {
    UUID primera = altaPersonal("10.00", "2026-01-01", "2026-06-30");
    UUID segunda = altaPersonal("12.00", "2026-07-01", "2026-12-31");
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_CONC2");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> asociarPersonal(indice == 0 ? primera : segunda, producto));

    assertThat(resultados).allMatch(r -> r.succeeded() && r.value() == 201);
    assertThat(asociacionesDe(producto)).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // `RN-CM-013` — un porcentaje por rol y producto
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("dos asociaciones simultáneas del mismo rol al mismo producto: solo una entra")
  void dosAsociacionesSimultaneas() throws Exception {
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");
    UUID primera = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
    UUID segunda = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "15.00");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> asociar(indice == 0 ? primera : segunda, producto));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // Si entraran las dos, la resolución tendría dos respuestas válidas para
    // «qué paga MANAGER por este producto» y elegiría el plan de ejecución. La
    // clave primaria es lo único que lo impide.
    assertThat(cuantasAsociaciones()).isEqualTo(1);

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
      "CA-CM-108 · dos asociaciones simultáneas al mismo producto, dentro del tope por separado"
          + " pero juntas fuera: solo una entra")
  void dosAsociacionesSimultaneasSePasanDeCienJuntas() throws Exception {
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_CAP");
    // Cada una, sola, cabe de sobra. Las dos juntas suman 110.
    UUID sesenta = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "60.00");
    UUID cincuenta = CommissionFixtures.sembrarTasaDeRol(jdbc, DIRECTOR, "50.00");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> asociar(indice == 0 ? sesenta : cincuenta, producto));

    // Ninguna puede salir como 500: si el bloqueo consultivo no cerrara la
    // ventana, las dos leerían la suma "antes" y las dos pasarían.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    assertThat(cuantasAsociaciones()).as("las dos entraron, o no entró ninguna").isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  /** El alta de una personalizada, devolviendo su identificador. */
  private UUID altaPersonal(String porcentaje, String desde, String hasta) throws Exception {
    String json =
        mvc.perform(
                post("/api/v1/user-commission-rates")
                    .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"userId\":\""
                            + vendedora
                            + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":"
                            + porcentaje
                            + ",\"validFrom\":\""
                            + desde
                            + "\",\"validTo\":\""
                            + hasta
                            + "\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(com.jayway.jsonpath.JsonPath.read(json, "$.id"));
  }

  private int asociarPersonal(UUID tasa, UUID producto) {
    return estadoDe(
        post("/api/v1/user-commission-rates/" + tasa + "/products")
            .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":\"" + producto + "\"}"));
  }

  private long asociacionesDe(UUID producto) {
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_commission_rate_products WHERE product_id = CAST(? AS uuid)",
            Long.class,
            producto.toString());
    return total == null ? 0 : total;
  }

  private int asociar(UUID tasa, UUID producto) {
    return estadoDe(
        post("/api/v1/commission-rates/" + tasa + "/products")
            .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":\"" + producto + "\"}"));
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

  private long cuantasAsociaciones() {
    return jdbc.queryForObject("SELECT count(*) FROM product_commission_rates", Long.class);
  }

  private void limpiar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'CM'");
  }
}
