package com.factech.nexus.modules.system.countries.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-SP-020` · `T-12` y `RF-SP-022` · `T-08` — concurrencia sobre el catálogo de países.
 *
 * <p><b>El alta concurrente es el caso que la verificación previa NO puede resolver.</b> Dos
 * peticiones simultáneas con el mismo código pasan las dos el {@code SELECT} de unicidad —ninguna
 * ve a la otra— y llegan al {@code INSERT}. Es el índice único quien decide, y la única pregunta
 * que importa es si su violación llega al cliente como un {@code 409} legible o como un {@code
 * 500}: el adaptador la traduce por <b>nombre de restricción</b>, y esto lo comprueba.
 *
 * <p>Aquí importa más que en otros catálogos: `RN-SP-009` no admite edición, de modo que un
 * duplicado que se colara sería <b>permanente</b>.
 */
@AutoConfigureMockMvc
class CountryConcurrencyIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  @BeforeEach
  void vaciarElCatalogo() {
    jdbc.update("DELETE FROM countries");
  }

  @Test
  @DisplayName("dos altas simultáneas con el mismo código producen un 201 y un 409, nunca un 500")
  void mismoCodigo() {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(alta("PA", "Panamá " + indice)));

    assertThat(resultados).allMatch(Outcome::succeeded);
    assertThat(resultados).extracting(Outcome::value).containsExactlyInAnyOrder(201, 409);
    assertThat(cuantos("PA")).isEqualTo(1);
  }

  @Test
  @DisplayName("dos altas simultáneas con nombres equivalentes producen un 201 y un 409")
  void mismoNombreNormalizado() {
    // «Panamá» y «Panama» son el mismo nombre para `uq_countries_name`, que va
    // sobre la forma normalizada. Si la carrera dejara pasar las dos, el
    // catálogo tendría para siempre dos opciones indistinguibles.
    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice -> estadoDe(alta(indice == 0 ? "PA" : "PX", indice == 0 ? "Panamá" : "Panama")));

    assertThat(resultados).allMatch(Outcome::succeeded);
    assertThat(resultados).extracting(Outcome::value).containsExactlyInAnyOrder(201, 409);

    Integer filas = jdbc.queryForObject("SELECT count(*) FROM countries", Integer.class);
    assertThat(filas).isEqualTo(1);
  }

  @Test
  @DisplayName("seis altas simultáneas del mismo país dejan una sola fila y ningún 500")
  void muchasALaVez() {
    // Con dos, el desenlace en que la primera confirma antes de que la segunda
    // lea es frecuente y la restricción no llega a intervenir. Con seis, alguna
    // llega necesariamente al INSERT.
    List<Outcome<Integer>> resultados =
        runTogether(6, indice -> estadoDe(alta("CO", "Colombia " + indice)));

    assertThat(resultados)
        .as("un alta concurrente produjo un fallo del sistema")
        .noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados).filteredOn(r -> r.value() == 201).hasSize(1);
    assertThat(cuantos("CO")).isEqualTo(1);
  }

  @Test
  @DisplayName("dos desactivaciones simultáneas del mismo país dejan UN solo evento")
  void dosDesactivacionesSimultaneas() throws Exception {
    String pa = crear("PA", "Panamá");
    UUID correlacion = UUID.randomUUID();

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(cambio(pa, false, correlacion)));

    assertThat(resultados).extracting(Outcome::value).containsExactly(200, 200);

    // Sin el bloqueo de fila, las dos leerían el estado inicial y las dos
    // creerían haberlo cambiado: dos eventos para un solo cambio real, que es lo
    // que `CA-SP-182` prohíbe.
    Integer eventos =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_change_log
             WHERE correlation_id = ? AND entity = 'countries' AND action = 'UPDATE'
            """,
            Integer.class,
            correlacion);
    assertThat(eventos).as("un solo cambio real produjo más de un evento").isEqualTo(1);

    Integer activos =
        jdbc.queryForObject(
            "SELECT count(*) FROM countries WHERE id = ?::uuid AND is_active", Integer.class, pa);
    assertThat(activos).isZero();
  }

  @Test
  @DisplayName("un alta y una desactivación simultáneas no se estorban")
  void altaYCambioDeEstadoNoSeEstorban() throws Exception {
    // Bloquean cosas distintas —una inserta, la otra bloquea una fila ajena—, de
    // modo que ninguna debe esperar a la otra ni fallar por su causa.
    String pa = crear("PA", "Panamá");

    List<Outcome<Integer>> resultados =
        runTogether(
            List.of(
                () -> estadoDe(alta("CO", "Colombia")),
                () -> estadoDe(cambio(pa, false, UUID.randomUUID()))));

    assertThat(resultados).extracting(Outcome::value).containsExactly(201, 200);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder alta(String code, String name) {
    return post("/api/v1/countries")
        .with(administrador())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}");
  }

  private MockHttpServletRequestBuilder cambio(String id, boolean activo, UUID correlacion) {
    return patch("/api/v1/countries/" + id + "/status")
        .with(administrador())
        .header("X-Correlation-Id", correlacion.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"isActive\":" + activo + "}");
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private String crear(String code, String name) throws Exception {
    String cuerpo = mvc.perform(alta(code, name)).andReturn().getResponse().getContentAsString();
    return json.readTree(cuerpo).get("id").asText();
  }

  private Integer cuantos(String code) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM countries WHERE code = ?", Integer.class, code);
  }

  private RequestPostProcessor administrador() {
    return user(UUID.randomUUID().toString())
        .authorities(() -> "countries:read", () -> "countries:create", () -> "countries:update");
  }
}
