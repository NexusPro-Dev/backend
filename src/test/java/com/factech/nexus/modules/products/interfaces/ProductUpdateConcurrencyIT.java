package com.factech.nexus.modules.products.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * `RF-PM-004` · `T-12` — dos correcciones simultáneas del mismo producto.
 *
 * <p><b>Lo que no puede quedar es una mezcla.</b> Sin bloqueo, las dos transacciones leen el mismo
 * estado, cada una aplica lo suyo y la última en escribir pisa lo de la otra <b>campo a campo</b>:
 * el resultado puede ser el nombre de una con el precio de la otra, que es un producto que nadie
 * pidió. Con el bloqueo pesimista, la segunda espera y ve lo que la primera confirmó.
 *
 * <p>La afirmación no es sobre quién gana —cualquiera de las dos vale— sino sobre que el resultado
 * sea <b>una de las dos, entera</b>.
 */
@AutoConfigureMockMvc
class ProductUpdateConcurrencyIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID producto;

  @BeforeEach
  void sembrar() {
    jdbc.update("DELETE FROM products");
    producto = bot("SOPORTE", "Soporte prioritario");
  }

  @AfterEach
  void limpiar() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName("`T-12` — la última queda ENTERA, no una mezcla de las dos")
  void laUltimaQuedaEntera() {
    List<Callable<Integer>> carrera =
        List.of(
            () -> estadoDe(corregir(producto, "{\"name\":\"Soporte A\",\"price\":11.11}")),
            () -> estadoDe(corregir(producto, "{\"name\":\"Soporte B\",\"price\":22.22}")));

    List<Outcome<Integer>> resultados = runTogether(carrera);

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados).allMatch(r -> r.succeeded() && r.value() == 200);

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT name, price FROM products WHERE id = CAST(? AS uuid)", producto.toString());
    String nombre = (String) fila.get("name");
    String precio = fila.get("price").toString();

    // El par tiene que ser coherente: `Soporte A` con `11.11` o `Soporte B` con
    // `22.22`. `Soporte A` con `22.22` sería la mezcla que el bloqueo impide.
    assertThat(nombre)
        .as("el nombre debe ser el de una de las dos correcciones")
        .isIn("Soporte A", "Soporte B");
    assertThat(precio)
        .as("y el precio, el que venía CON ese nombre — no el de la otra petición")
        .startsWith("Soporte A".equals(nombre) ? "11.11" : "22.22");
  }

  @Test
  @DisplayName("dos correcciones simultáneas a NOMBRES distintos no dejan dos productos iguales")
  void dosCorreccionesHaciaElMismoNombre() {
    UUID otro = bot("ASESORIA", "Asesoría");

    // Los dos productos intentan llamarse igual a la vez. La unicidad del
    // nombre es del índice parcial, no de la comprobación previa.
    List<Callable<Integer>> carrera =
        List.of(
            () -> estadoDe(corregir(producto, "{\"name\":\"Nombre disputado\"}")),
            () -> estadoDe(corregir(otro, "{\"name\":\"Nombre disputado\"}")));

    List<Outcome<Integer>> resultados = runTogether(carrera);

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantosVivosLlamados("Nombre disputado"))
        .as("uno, nunca dos: la unicidad la decide el índice")
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder corregir(UUID id, String cuerpo) {
    return patch("/api/v1/products/{id}", id)
        .with(user(UUID.randomUUID().toString()).authorities(() -> "products:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private int cuantosVivosLlamados(String nombre) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM products WHERE deleted_at IS NULL"
                + " AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(? AS text)))",
            Integer.class,
            nombre);
    return filas == null ? 0 : filas;
  }

  private UUID bot(String codigo, String nombre) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, source_membership_id,"
            + " target_membership_id, price,"
            + " currency_id, validity_days, status, created_at, updated_at)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, 'Atención prioritaria.', NULL, NULL, 49.99,"
            + " CAST(? AS uuid), NULL, 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        nombre,
        USD,
        BASE,
        BASE);
    return id;
  }
}
