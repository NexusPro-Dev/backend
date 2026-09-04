package com.factech.nexus.modules.movements.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * El catálogo de métodos de pago (`RF-MV-009` · `T-07`).
 *
 * <p>Cubre `CA-MV-027` a `CA-MV-033`. <b>`CA-MV-034` no está aquí</b>, y no por descuido: afirma
 * que <b>registrar una venta con un método excluido se registra igual</b>, de modo que vive donde
 * está la siembra de una venta — {@code RegisterSaleIT}. Es el criterio que sostiene la decisión
 * entera de este requerimiento.
 *
 * <h2>Esta prueba NO da por sabido qué métodos siembra la migración</h2>
 *
 * <p>Y es deliberado. El sembrado de `V54` <b>cambió dos veces en el mismo día</b> —de
 * `EFECTIVO`/`TRANSFERENCIA` a `CREDIT_CARD`/`PSE`/`TRANSFERENCIA`, y de ahí a
 * `CREDIT_CARD`/`PSE`/`POINTS`—, y una prueba que afirme «el segundo es `PSE`» se rompe en cada
 * cambio <b>sin que nada esté mal</b>: el catálogo es del negocio y no del contrato.
 *
 * <p>Lo que sí es del contrato es la <b>forma</b>: que salgan los activos, que vayan ordenados por
 * código y que las exclusiones viajen. Eso se comprueba <b>contra la base</b> —el orden se
 * contrasta con lo que la tabla tiene— y con métodos que esta clase crea y borra.
 */
@AutoConfigureMockMvc
class PaymentMethodCatalogIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID mexico;
  private UUID peru;

  @BeforeEach
  void sembrar() {
    limpiar();
    mexico = pais("PMX", "México de prueba");
    peru = pais("PPE", "Perú de prueba");
  }

  @AfterEach
  void borrar() {
    limpiar();
  }

  @Test
  @DisplayName("CA-MV-027 y CA-MV-031: los activos, envueltos en `content` y ordenados por código")
  void elCatalogo() throws Exception {
    // El orden se contrasta con lo que la tabla tiene, no con una lista escrita
    // a mano: así la prueba sigue comprobando el ORDEN cuando cambie el
    // sembrado, que es lo que de verdad promete el contrato.
    List<String> esperados =
        jdbc.queryForList(
            "SELECT code FROM payment_methods WHERE is_active = true ORDER BY code ASC",
            String.class);

    mvc.perform(get("/api/v1/payment-methods").with(actor()))
        .andExpect(status().isOk())
        // Envuelto y no un arreglo en la raíz: es lo que permite añadir
        // metadatos después sin romper a todos los clientes.
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(esperados.size()))
        .andExpect(jsonPath("$.content[*].code").value(Matchers.contains(esperados.toArray())))
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].name").exists());
  }

  @Test
  @DisplayName("CA-MV-030: un método sin exclusiones trae la lista VACÍA y PRESENTE")
  void sinExclusiones() throws Exception {
    metodo("PM_LIBRE", "Método sin exclusiones", true);

    // Es la prueba que detecta el `JOIN` interno (riesgo 2 del plan): con él,
    // un método sin exclusiones no saldría, y como hoy NINGUNO las tiene, el
    // catálogo entero vendría vacío sin error.
    mvc.perform(get("/api/v1/payment-methods").with(actor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath(deCodigo("PM_LIBRE") + ".excludedCountries").isArray())
        .andExpect(jsonPath(deCodigo("PM_LIBRE") + ".excludedCountries.length()").value(0));
  }

  @Test
  @DisplayName("CA-MV-029: las exclusiones viajan, con identificador y código de cada país")
  void conExclusiones() throws Exception {
    UUID excluido = metodo("PM_EXCLUIDO", "Método con exclusiones", true);
    metodo("PM_LIBRE", "Método sin exclusiones", true);
    excluir(excluido, mexico);
    excluir(excluido, peru);

    mvc.perform(get("/api/v1/payment-methods").with(actor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath(deCodigo("PM_EXCLUIDO") + ".excludedCountries.length()").value(2))
        .andExpect(
            jsonPath(deCodigo("PM_EXCLUIDO") + ".excludedCountries[*].code")
                .value(Matchers.containsInAnyOrder("PMX", "PPE")))
        .andExpect(jsonPath(deCodigo("PM_EXCLUIDO") + ".excludedCountries[0].id").exists())
        // El nombre del país NO viaja: quien pinta países ya tiene su catálogo,
        // y repetirlo aquí lo dejaría desincronizado.
        .andExpect(jsonPath(deCodigo("PM_EXCLUIDO") + ".excludedCountries[0].name").doesNotExist())
        // Excluir uno no toca a nadie más.
        .andExpect(jsonPath(deCodigo("PM_LIBRE") + ".excludedCountries.length()").value(0));
  }

  @Test
  @DisplayName("CA-MV-028: un método desactivado no aparece")
  void elDesactivadoNoAparece() throws Exception {
    UUID retirado = metodo("PM_RETIRADO", "Método retirado", false);
    // Se le declara una exclusión a propósito: si el filtro por activo se
    // cayera, este método aparecería con ella, que es más fácil de ver en el
    // fallo que una fila de más.
    excluir(retirado, mexico);

    mvc.perform(get("/api/v1/payment-methods").with(actor()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[*].code").value(Matchers.not(Matchers.hasItem("PM_RETIRADO"))));
  }

  @Test
  @DisplayName("CA-MV-032: responde a un actor SIN NINGUNA autoridad")
  void noExigePermiso() throws Exception {
    // Sin `.authorities(...)`: quien compra lo suyo (`RF-MV-002`) no porta
    // ningún permiso y tiene que poder ver con qué pagar.
    mvc.perform(get("/api/v1/payment-methods").with(user(SUPERADMIN.toString())))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("CA-MV-033: sin token responde 401")
  void sinToken() throws Exception {
    mvc.perform(get("/api/v1/payment-methods")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Un país desactivado NO retira la exclusión: son cosas distintas")
  void elPaisDesactivadoSigueExcluyendo() throws Exception {
    UUID excluido = metodo("PM_EXCLUIDO", "Método con exclusiones", true);
    excluir(excluido, mexico);
    jdbc.update(
        "UPDATE countries SET is_active = false WHERE id = CAST(? AS uuid)", mexico.toString());

    // Retirar un país de la circulación no dice nada sobre dónde vale un medio
    // de pago, y filtrarlo aquí haría que reactivarlo cambiara en silencio lo
    // que se ofrece (`spec.md` §13).
    mvc.perform(get("/api/v1/payment-methods").with(actor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath(deCodigo("PM_EXCLUIDO") + ".excludedCountries.length()").value(1));
  }

  // ---------------------------------------------------------------------------
  // Ayudas
  // ---------------------------------------------------------------------------

  /**
   * El método por su código y no por su posición.
   *
   * <p>Es lo que hace que estas pruebas sobrevivan al sembrado: la posición de un elemento depende
   * de cuántos y cuáles haya, y ninguna de las dos cosas es del contrato.
   */
  private static String deCodigo(String codigo) {
    return "$.content[?(@.code=='%s')]".formatted(codigo);
  }

  private RequestPostProcessor actor() {
    return user(SUPERADMIN.toString()).authorities(() -> "movements:read");
  }

  private UUID pais(String codigo, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO countries (id, code, name, is_active) VALUES (CAST(? AS uuid), ?, ?, true)",
        id.toString(),
        codigo,
        nombre);
    return id;
  }

  private UUID metodo(String codigo, String nombre, boolean activo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO payment_methods (id, code, name, is_active) VALUES (CAST(? AS uuid), ?, ?, ?)",
        id.toString(),
        codigo,
        nombre,
        activo);
    return id;
  }

  private void excluir(UUID metodo, UUID pais) {
    jdbc.update(
        "INSERT INTO payment_method_exclusions (payment_method_id, country_id)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid))",
        metodo.toString(),
        pais.toString());
  }

  private void limpiar() {
    jdbc.update("DELETE FROM payment_method_exclusions");
    jdbc.update("DELETE FROM payment_methods WHERE code LIKE 'PM\\_%'");
    jdbc.update("DELETE FROM countries WHERE name LIKE '% de prueba'");
  }
}
