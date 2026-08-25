package com.factech.nexus.modules.system.currencies.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
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
 * `RF-SP-023` · `T-12` — cambio de estado concurrente sobre la misma moneda.
 *
 * <p><b>Lo que se prueba no es que ninguna falle, sino que solo una deja evento.</b> Ambas
 * peticiones devuelven {@code 200} —el resultado observable es idéntico: la moneda queda en el
 * estado pedido— y ahí está la trampa: sin el bloqueo de fila, las dos leerían el mismo estado
 * inicial, las dos creerían haberlo cambiado y {@code audit_change_log} acabaría con <b>dos</b>
 * eventos para un solo cambio real. `CA-SP-190` prohíbe exactamente eso, y una prueba secuencial no
 * puede distinguirlo.
 *
 * <p>Con el bloqueo, la segunda espera, relee la fila ya modificada y cae en `FA-001` —el estado
 * pedido es el que ya tiene—, de modo que no emite nada.
 */
@AutoConfigureMockMvc
class CurrencyConcurrencyIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void dejarSoloLaSembrada() {
    jdbc.update("DELETE FROM currencies WHERE is_default = false");
  }

  @Test
  @DisplayName("dos desactivaciones simultáneas dejan UN solo evento de auditoría")
  void dosDesactivacionesSimultaneas() {
    String eur = insertar("EUR", "Euro");
    UUID correlacion = UUID.randomUUID();

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(cambio(eur, false, correlacion)));

    assertThat(resultados)
        .as("una de las dos peticiones falló")
        .allMatch(Outcome::succeeded)
        .extracting(Outcome::value)
        .containsExactly(200, 200);

    Integer eventos =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM audit_change_log
             WHERE correlation_id = ? AND entity = 'currencies' AND action = 'UPDATE'
            """,
            Integer.class,
            correlacion);
    assertThat(eventos).as("un solo cambio real produjo más de un evento").isEqualTo(1);

    Integer activas =
        jdbc.queryForObject(
            "SELECT count(*) FROM currencies WHERE id = ?::uuid AND is_active", Integer.class, eur);
    assertThat(activas).isZero();
  }

  @Test
  @DisplayName("activar y desactivar a la vez deja la moneda en un estado y un solo evento por él")
  void activacionYDesactivacionSimultaneas() {
    // Las dos son legítimas y el orden lo decide el reloj: lo que no puede
    // ocurrir es que la fila quede en un estado que ningún evento explique.
    String eur = insertar("EUR", "Euro");
    UUID correlacion = UUID.randomUUID();

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(cambio(eur, indice == 0, correlacion)));

    assertThat(resultados).allMatch(Outcome::succeeded);

    boolean activaAlFinal =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT is_active FROM currencies WHERE id = ?::uuid", Boolean.class, eur));

    Integer eventos =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE correlation_id = ?",
            Integer.class,
            correlacion);

    // Si acabó activa, la petición de activar no cambió nada —ya lo estaba— y
    // solo pudo haber evento si antes la desactivó otra: cero o dos eventos.
    // Si acabó inactiva, hubo al menos el evento de la desactivación.
    assertThat(eventos)
        .as("el estado final no coincide con los eventos registrados")
        .isEqualTo(eventosEsperados(activaAlFinal, eventos));
  }

  @Test
  @DisplayName("dos intentos simultáneos de desactivar la moneda por defecto fallan los dos")
  void laMonedaPorDefectoResisteLaCarrera() {
    // `ck_currencies_default_active` y la verificación del dominio protegen por
    // caminos distintos; ninguna carrera debe abrir una rendija entre los dos.
    String usd =
        jdbc.queryForObject("SELECT id::text FROM currencies WHERE is_default", String.class);

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(cambio(usd, false, UUID.randomUUID())));

    assertThat(resultados).extracting(Outcome::value).containsExactly(409, 409);

    Integer activa =
        jdbc.queryForObject(
            "SELECT count(*) FROM currencies WHERE is_default AND is_active", Integer.class);
    assertThat(activa).as("la moneda por defecto quedó inactiva").isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  /**
   * Cuántos eventos son coherentes con el estado final.
   *
   * <p>Dos desenlaces son correctos y el reloj decide cuál: si la desactivación llegó primero hay
   * un evento y puede haber un segundo por la reactivación; si llegó la activación primero, esa no
   * cambió nada y solo cuenta la desactivación. Lo que se afirma es que el número observado es uno
   * de los coherentes, no cuál.
   */
  private static int eventosEsperados(boolean activaAlFinal, int observados) {
    List<Integer> coherentes = activaAlFinal ? List.of(0, 2) : List.of(1);
    assertThat(coherentes)
        .as("estado final activo=%s con %s eventos", activaAlFinal, observados)
        .contains(observados);
    return observados;
  }

  private MockHttpServletRequestBuilder cambio(String id, boolean activa, UUID correlacion) {
    return patch("/api/v1/currencies/" + id + "/status")
        .with(administrador())
        .header("X-Correlation-Id", correlacion.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"isActive\":" + activa + "}");
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private String insertar(String code, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO currencies (id, code, name, decimal_places, is_default, is_active)
        VALUES (?, ?, ?, 2, false, true)
        """,
        id,
        code,
        name);
    return id.toString();
  }

  private RequestPostProcessor administrador() {
    return user(UUID.randomUUID().toString())
        .authorities(() -> "currencies:read", () -> "currencies:update");
  }
}
