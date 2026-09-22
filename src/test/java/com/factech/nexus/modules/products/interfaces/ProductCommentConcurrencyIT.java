package com.factech.nexus.modules.products.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Las carreras de las reseñas: dos altas, dos correcciones y dos retiros simultáneos del mismo
 * autor. Es lo que el índice parcial y el bloqueo de fila existen para cubrir, y solo se ve con dos
 * hilos.
 */
@AutoConfigureMockMvc
class ProductCommentConcurrencyIT extends ProductCommentTestSupport {

  @Autowired private MockMvc mvc;

  @BeforeEach
  void sembrar() {
    sembrarBase();
  }

  @AfterEach
  void vaciar() {
    limpiarResenas();
  }

  @Test
  @DisplayName(
      "`CA-PM-179` — dos altas simultáneas del mismo actor: una fila, un 201 y un 409 — no un 500")
  void dosAltasDelMismoActor() {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(alta(activo, "Reseña " + indice)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados).noneMatch(Outcome::failed);
    assertThat(resenasVivas(activo)).isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.value() == 201).count()).isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.value() == 409).count()).isEqualTo(1);
  }

  @Test
  @DisplayName("dos correcciones simultáneas del autor: las dos responden 200 y gana la última")
  void dosCorrecciones() {
    UUID resena = resena(activo, ana, 3, "Original", false);

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(correccion(resena, indice + 1)));

    assertThat(resultados).allMatch(r -> r.succeeded() && r.value() == 200);
    Integer puntuacion =
        jdbc.queryForObject(
            "SELECT rating FROM product_comments WHERE id = CAST(? AS uuid)",
            Integer.class,
            resena.toString());
    assertThat(puntuacion).isIn(1, 2);
    Long auditorias =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity_id = CAST(? AS uuid)",
            Long.class,
            resena.toString());
    assertThat(auditorias).isEqualTo(2);
  }

  @Test
  @DisplayName("dos retiros simultáneos del autor: un 204, un 404 y UNA fila de auditoría")
  void dosRetiros() {
    UUID resena = resena(activo, ana, 3, "Original", false);

    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(retiro(resena)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.value() == 204).count()).isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.value() == 404).count()).isEqualTo(1);
    Long auditorias =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_deletion_log WHERE entity_id = CAST(? AS uuid)",
            Long.class,
            resena.toString());
    assertThat(auditorias).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder alta(UUID producto, String texto) {
    return post("/api/v1/products/{id}/comments", producto)
        .with(comoResenador(ana))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(5, texto));
  }

  private MockHttpServletRequestBuilder correccion(UUID resena, int puntuacion) {
    return patch("/api/v1/products/{p}/comments/{c}", activo, resena)
        .with(comoResenador(ana))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"rating\": " + puntuacion + "}");
  }

  private MockHttpServletRequestBuilder retiro(UUID resena) {
    return delete("/api/v1/products/{p}/comments/{c}", activo, resena).with(comoResenador(ana));
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }
}
