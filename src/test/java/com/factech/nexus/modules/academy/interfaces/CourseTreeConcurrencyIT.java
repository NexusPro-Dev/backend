package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccionActiva;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.modulo;
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
 * Las cuatro carreras del árbol: dos altas de módulo con el mismo título (`CA-AC-084`), dos altas
 * de lección (`CA-AC-093`), dos retiros del mismo módulo (`CA-AC-118`) y de la misma lección
 * (`CA-AC-123`). La que pierde recibe el mismo {@code 409} que la comprobación previa.
 */
@AutoConfigureMockMvc
class CourseTreeConcurrencyIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID curso;
  private UUID modulo;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    curso = curso(jdbc, "Velas", CourseTestSupport.instructor(jdbc), 0);
    modulo = modulo(jdbc, curso, "Fundamentos", 0, "INACTIVO");
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-084` — dos altas de módulo con el mismo título en el mismo curso: una fila y un 409")
  void dosModulos() throws Exception {
    List<Outcome<Integer>> r =
        runTogether(
            2,
            i ->
                estadoDe(
                    post("/api/v1/courses/" + curso + "/modules")
                        .with(con("courses:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Repetido\",\"displayOrder\":" + i + "}")));
    comprobar(r, 201);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM course_modules WHERE title = 'Repetido'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-093` — dos altas de lección con el mismo título en el mismo módulo: una fila y un 409")
  void dosLecciones() throws Exception {
    List<Outcome<Integer>> r =
        runTogether(
            2,
            i ->
                estadoDe(
                    post("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons")
                        .with(con("courses:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"type\":\"TEXTO\",\"title\":\"Repetida\",\"durationSeconds\":1,\"displayOrder\":"
                                + i
                                + "}")));
    comprobar(r, 201);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM lessons WHERE title = 'Repetida'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-118` — dos retiros del mismo módulo: un 204, un 409 y UNA fila de auditoría del módulo")
  void dosRetirosDeModulo() throws Exception {
    leccionActiva(jdbc, modulo, "Lección", 1);
    List<Outcome<Integer>> r =
        runTogether(2, i -> estadoDe(retiro("/modules/" + modulo + "/deletion")));
    comprobar(r, 204);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity_id = ?",
                Integer.class,
                modulo))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-AC-123` — dos retiros de la misma lección: un 204, un 409 y UNA fila")
  void dosRetirosDeLeccion() throws Exception {
    UUID leccion = leccionActiva(jdbc, modulo, "Lección", 1);
    List<Outcome<Integer>> r =
        runTogether(
            2, i -> estadoDe(retiro("/modules/" + modulo + "/lessons/" + leccion + "/deletion")));
    comprobar(r, 204);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity_id = ?",
                Integer.class,
                leccion))
        .isEqualTo(1);
  }

  private MockHttpServletRequestBuilder retiro(String ruta) {
    return post("/api/v1/courses/" + curso + ruta)
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"A la vez.\"}");
  }

  private static void comprobar(List<Outcome<Integer>> resultados, int exito) {
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == exito).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }
}
