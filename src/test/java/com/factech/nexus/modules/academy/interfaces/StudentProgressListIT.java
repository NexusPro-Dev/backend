package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ofrecido;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.ProgressTestSupport.progreso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.Ofrecido;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El listado del progreso por alumno y curso: `RF-AC-040` (`CA-AC-258` a `CA-AC-262`).
 *
 * <p>La red de {@link StudentCourseProgressIT}. Tres filas: el cliente en «Velas» (la más reciente)
 * y en «Otro», y el ajeno en «Velas». <b>La que define el requerimiento es `CA-AC-259`</b>, el
 * alcance sumado.
 */
@AutoConfigureMockMvc
class StudentProgressListIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private UUID admin;
  private UUID manager;
  private UUID cliente;
  private UUID ajeno;
  private UUID profe;
  private Ofrecido velas;
  private Ofrecido otro;

  @BeforeEach
  void sembrar() {
    ProgressTestSupport.limpiar(jdbc);
    admin = CourseTestSupport.persona(jdbc, "Ada", "Admin", ProgressTestSupport.ADMIN);
    manager = CourseTestSupport.persona(jdbc, "Mia", "Manager", ProgressTestSupport.MANAGER);
    UUID agente = CourseTestSupport.persona(jdbc, "Abel", "Agente", ProgressTestSupport.AGENTE);
    UUID agenteDeFuera =
        CourseTestSupport.persona(jdbc, "Fito", "Fuera", ProgressTestSupport.AGENTE);
    cliente = CourseTestSupport.persona(jdbc, "Cleo", "Cliente", ProgressTestSupport.CLIENTE);
    ajeno = CourseTestSupport.persona(jdbc, "Ajo", "Ajeno", ProgressTestSupport.CLIENTE);
    profe = CourseTestSupport.persona(jdbc, "Pablo", "Profe", null);
    ProgressTestSupport.reportaA(jdbc, agente, manager);
    ProgressTestSupport.esClienteDe(jdbc, cliente, agente);
    ProgressTestSupport.esClienteDe(jdbc, ajeno, agenteDeFuera);

    velas = ofrecido(jdbc, "Velas", profe, 0);
    otro = ofrecido(jdbc, "Otro", CourseTestSupport.instructor(jdbc), 1);
    progreso(jdbc, cliente, velas.leccion(), 0, true, 5);
    progreso(jdbc, cliente, otro.leccion(), 0, false, 30);
    progreso(jdbc, ajeno, velas.leccion(), 0, true, 60);
  }

  @AfterEach
  void limpiar() {
    ProgressTestSupport.limpiar(jdbc);
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/courses/progress");
  }

  private ResultActions pedir(UUID actor, MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion.with(con(actor, "courses:list-progress")));
  }

  @Test
  @DisplayName(
      "`CA-AC-258` — una fila por alumno y curso con sus cifras, por última actividad descendente")
  void unaFilaPorAlumnoYCurso() throws Exception {
    pedir(admin, listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].student.id").value(cliente.toString()))
        .andExpect(jsonPath("$.content[0].student.username").isNotEmpty())
        .andExpect(jsonPath("$.content[0].course.id").value(velas.curso().toString()))
        .andExpect(jsonPath("$.content[0].percent").value(100))
        .andExpect(jsonPath("$.content[0].completedLessons").value(1))
        .andExpect(jsonPath("$.content[0].lessonCount").value(1))
        .andExpect(jsonPath("$.content[0].completed").value(true))
        .andExpect(jsonPath("$.content[1].course.id").value(otro.curso().toString()))
        .andExpect(jsonPath("$.content[1].percent").value(0))
        .andExpect(jsonPath("$.content[1].completed").value(false))
        .andExpect(jsonPath("$.content[2].student.id").value(ajeno.toString()));
  }

  @Test
  @DisplayName(
      "`CA-AC-259` — administración todo; el manager su red sin el ajeno; el instructor su curso"
          + " y no el otro; el manager que dicta suma las dos cosas")
  void elAlcanceSumado() throws Exception {
    pedir(manager, listado())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(
            jsonPath("$.content[*].student.id")
                .value(containsInAnyOrder(cliente.toString(), cliente.toString())));

    pedir(profe, listado())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(
            jsonPath("$.content[*].course.id")
                .value(containsInAnyOrder(velas.curso().toString(), velas.curso().toString())));

    jdbc.update("UPDATE courses SET instructor_id = ? WHERE id = ?", manager, velas.curso());
    pedir(manager, listado())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content", hasSize(3)));
  }

  @Test
  @DisplayName(
      "`CA-AC-260` — userId fuera del alcance da página vacía; courseId y completed acotan")
  void losFiltros() throws Exception {
    pedir(manager, listado().param("userId", ajeno.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content", hasSize(0)));

    pedir(admin, listado().param("courseId", otro.curso().toString()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].student.id").value(cliente.toString()));
    pedir(admin, listado().param("courseId", UUID.randomUUID().toString()))
        .andExpect(jsonPath("$.totalElements").value(0));

    pedir(admin, listado().param("completed", "true"))
        .andExpect(jsonPath("$.totalElements").value(2));
    pedir(admin, listado().param("completed", "false"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].course.id").value(otro.curso().toString()));
    pedir(admin, listado().param("userId", ajeno.toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("`CA-AC-261` — un curso retirado con progreso sigue saliendo, marcado")
  void elRetiradoSigue() throws Exception {
    jdbc.update("UPDATE courses SET deleted_at = now() WHERE id = ?", otro.curso());
    pedir(admin, listado().param("courseId", otro.curso().toString()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].course.deleted").value(true));
  }

  @Test
  @DisplayName(
      "`CA-AC-262` — sin courses:list-progress es 403; parámetros mal formados, 400; la página"
          + " cuesta lo mismo con más filas")
  void permisoFormatoYSentencias() throws Exception {
    mvc.perform(listado().with(con(admin, "courses:read-progress")))
        .andExpect(status().isForbidden());
    pedir(admin, listado().param("userId", "no-es-uuid")).andExpect(status().isBadRequest());
    pedir(admin, listado().param("size", "0")).andExpect(status().isBadRequest());

    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.clear();
    pedir(admin, listado()).andExpect(status().isOk());
    long tres = estadisticas.getPrepareStatementCount();

    for (int i = 0; i < 4; i++) {
      UUID otroAlumno = CourseTestSupport.persona(jdbc, "Otro" + i, "Alumno", null);
      progreso(jdbc, otroAlumno, velas.leccion(), 0, false, 90 + i);
    }
    estadisticas.clear();
    pedir(admin, listado()).andExpect(jsonPath("$.totalElements").value(7));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(tres);
  }
}
