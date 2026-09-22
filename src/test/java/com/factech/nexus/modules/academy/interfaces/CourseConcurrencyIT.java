package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.cuerpo;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
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
 * Las dos carreras del curso: dos altas con el mismo título (`CA-AC-042`) y dos retiros del mismo
 * (`CA-AC-075`, su parte de la carrera). Lo que se comprueba es que la que pierde reciba <b>el
 * mismo {@code 409}</b> que la comprobación previa, y no un {@code 500}.
 */
@AutoConfigureMockMvc
class CourseConcurrencyIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName("`CA-AC-042` — dos altas simultáneas con el mismo título: una fila y un 409")
  void dosAltasConElMismoTitulo() throws Exception {
    List<Outcome<Integer>> resultados = runTogether(2, indice -> estadoDe(alta("Velas", indice)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM courses", Integer.class)).isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-075` — dos retiros simultáneos: un 204, un 409 y UNA fila de auditoría del curso")
  void dosRetiros() throws Exception {
    UUID id = curso(jdbc, "Velas", instructor, 0);
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

  private MockHttpServletRequestBuilder alta(String titulo, int orden) {
    return post("/api/v1/courses")
        .with(con("courses:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(titulo, instructor, "PRINCIPIANTE", orden));
  }

  private MockHttpServletRequestBuilder retiro(UUID id) {
    return post("/api/v1/courses/" + id + "/deletion")
        .with(con("courses:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"A la vez.\"}");
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }
}
