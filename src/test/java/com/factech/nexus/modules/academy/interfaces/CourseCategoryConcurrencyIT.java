package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.categoria;
import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.cuerpo;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Las dos carreras de las categorías: dos altas con el mismo nombre (`CA-AC-009`) y dos retiros de
 * la misma (`CA-AC-033`).
 *
 * <p>Lo que se comprueba no es que una gane —eso lo garantiza el motor— sino que la otra reciba
 * <b>el mismo {@code 409}</b> que habría recibido por la comprobación previa, y no un {@code 500}
 * de una restricción sin traducir.
 */
@AutoConfigureMockMvc
class CourseCategoryConcurrencyIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void limpiar() {
    CourseCategoryTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName("`CA-AC-009` — dos altas simultáneas con el mismo nombre: una fila y un 409")
  void dosAltasConElMismoNombre() throws Exception {
    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(alta("Trading", indice)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantas()).isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-AC-033` — dos retiros simultáneos: un 204, un 409 y UNA fila de auditoría")
  void dosRetiros() throws Exception {
    UUID id = categoria(jdbc, "Trading", 0);
    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(retiro(id)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 204).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity_id = ?", Integer.class, id))
        .isEqualTo(1);
  }

  private MockHttpServletRequestBuilder alta(String nombre, int orden) {
    return post("/api/v1/course-categories")
        .with(con("course-categories:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(nombre, "1E88E5", "chart-line", orden));
  }

  private MockHttpServletRequestBuilder retiro(UUID id) {
    return post("/api/v1/course-categories/" + id + "/deletion")
        .with(con("course-categories:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"A la vez.\"}");
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private int cuantas() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM course_categories", Integer.class);
    return filas == null ? 0 : filas;
  }
}
