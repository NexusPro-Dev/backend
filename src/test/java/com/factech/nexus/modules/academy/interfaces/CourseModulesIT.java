package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccionActiva;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.modulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * El alta de módulos (`RF-AC-022`): `CA-AC-076` a `CA-AC-082`. Las carreras (`CA-AC-084`) viven en
 * {@link CourseTreeConcurrencyIT} y el arrastre (`CA-AC-083`) en {@link CourseDeletionIT}.
 */
@AutoConfigureMockMvc
class CourseModulesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID instructor;
  private UUID curso;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0, "PRINCIPIANTE", "C", "L", "INACTIVO");
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-076` — registra el módulo en la forma de su detalle: INACTIVO, courseId de la ruta,"
          + " portada nula, lessons vacío, cero minutos y offerable false «inactivo»")
  void altaEnLaFormaDelModulo() throws Exception {
    mvc.perform(
            alta(
                curso,
                """
                {"title":"  Fundamentos  ","shortDescription":"Lo básico.","longDescription":"   ",
                 "presentationVideoUrl":"https://v.io/m","displayOrder":1}
                """))
        .andExpect(status().isCreated())
        .andExpect(
            header().string("Location", startsWith("/api/v1/courses/" + curso + "/modules/")))
        .andExpect(jsonPath("$.courseId").value(curso.toString()))
        .andExpect(jsonPath("$.title").value("Fundamentos"))
        .andExpect(jsonPath("$.shortDescription").value("Lo básico."))
        .andExpect(jsonPath("$.longDescription").value(nullValue()))
        .andExpect(jsonPath("$.presentationVideoUrl").value("https://v.io/m"))
        .andExpect(jsonPath("$.displayOrder").value(1))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.durationMinutes").value(0))
        .andExpect(jsonPath("$.lessons", hasSize(0)))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value("El módulo está inactivo."))
        .andExpect(jsonPath("$.instructor").doesNotExist());
  }

  @Test
  @DisplayName(
      "`CA-AC-077` — 409 con el título de un módulo vivo del mismo curso; se admite el de un"
          + " retirado y el mismo título en otro curso")
  void tituloUnicoDentroDelCurso() throws Exception {
    mvc.perform(alta(curso, cuerpo("Introducción", 0))).andExpect(status().isCreated());
    mvc.perform(alta(curso, cuerpo("  INTRODUCCION ", 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    UUID otro = curso(jdbc, "Otro curso", instructor, 1);
    mvc.perform(alta(otro, cuerpo("Introducción", 0))).andExpect(status().isCreated());

    jdbc.update("UPDATE course_modules SET deleted_at = now() WHERE course_id = ?", curso);
    mvc.perform(alta(curso, cuerpo("Introducción", 0))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "`CA-AC-078` — 404 a un curso inexistente y a uno retirado; un curso ACTIVO admite el módulo")
  void cursoQueNoSirve() throws Exception {
    mvc.perform(alta(UUID.randomUUID(), cuerpo("X", 0)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un curso vivo con ese identificador."));
    UUID retirado = curso(jdbc, "Retirado", instructor, 2);
    CourseTestSupport.retirar(jdbc, retirado);
    mvc.perform(alta(retirado, cuerpo("X", 0))).andExpect(status().isNotFound());

    jdbc.update("UPDATE courses SET status = 'ACTIVO' WHERE id = ?", curso);
    mvc.perform(alta(curso, cuerpo("En activo", 0))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "`CA-AC-079` — título, orden, descripciones y video inválidos: 400 juntos; y 400 con status,"
          + " lessons, coverImageUrl o courseId")
  void validaciones() throws Exception {
    mvc.perform(
            alta(
                curso,
                """
                {"title":"  ","displayOrder":-1,"shortDescription":"%s","presentationVideoUrl":"ftp://x"}
                """
                    .formatted("x".repeat(301))))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(
                    hasItems("title", "displayOrder", "shortDescription", "presentationVideoUrl")));
    for (String extra :
        List.of(
            "\"status\":\"ACTIVO\"",
            "\"lessons\":[]",
            "\"coverImageUrl\":\"/x\"",
            "\"courseId\":\"" + curso + "\"")) {
      mvc.perform(alta(curso, "{\"title\":\"X\",\"displayOrder\":0," + extra + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString("no admite")));
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_modules", Integer.class)).isZero();
  }

  @Test
  @DisplayName("`CA-AC-080` — deja una fila CREATE de course_modules con el actor y course_id")
  void audita() throws Exception {
    UUID actor = UUID.randomUUID();
    String id =
        com.jayway.jsonpath.JsonPath.read(
            mvc.perform(alta(curso, cuerpo("Auditado", 0), actor))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    var fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'AC' AND entity = 'course_modules' AND entity_id = CAST(? AS uuid)",
            id);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat((String) fila.get("changes")).contains("\"course_id\": \"" + curso + "\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-081` — moduleCount del listado cuenta los módulos vivos: uno inactivo cuenta, uno retirado no")
  void moduleCountDelListado() throws Exception {
    modulo(jdbc, curso, "Activo", 0, "ACTIVO");
    modulo(jdbc, curso, "Inactivo", 1, "INACTIVO");
    CourseTestSupport.retirarModulo(jdbc, modulo(jdbc, curso, "Retirado", 2, "INACTIVO"));
    mvc.perform(get("/api/v1/courses").with(con("courses:read")))
        .andExpect(jsonPath("$.content[0].moduleCount").value(2));
  }

  @Test
  @DisplayName(
      "`CA-AC-082` — el detalle del curso trae los módulos en orden con desempate por id, vivos y"
          + " retirados marcados, cada uno con offerable, y cuesta una sentencia más")
  void arbolDelDetalle() throws Exception {
    UUID segundo = modulo(jdbc, curso, "Segundo", 1, "ACTIVO");
    modulo(jdbc, curso, "Primero", 0, "INACTIVO");
    UUID retirado = modulo(jdbc, curso, "Retirado", 1, "ACTIVO");
    CourseTestSupport.retirarModulo(jdbc, retirado);
    leccionActiva(jdbc, segundo, "Lección", 10);

    estadisticas.clear();
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules[*].title").value(contains("Primero", "Segundo", "Retirado")))
        .andExpect(jsonPath("$.modules[0].offerable").value(false))
        .andExpect(jsonPath("$.modules[1].offerable").value(true))
        .andExpect(jsonPath("$.modules[1].durationMinutes").value(10))
        .andExpect(jsonPath("$.modules[2].deleted").value(true))
        .andExpect(jsonPath("$.modules[2].offerable").value(false))
        .andExpect(jsonPath("$.totalDurationMinutes").value(10))
        .andExpect(jsonPath("$.lessonCount").value(1));
    // Curso, categorías (`RF-AC-016`), servicios (`RF-AC-037`), módulos y lecciones.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(5);
  }

  private MockHttpServletRequestBuilder alta(UUID curso, String cuerpo) {
    return alta(curso, cuerpo, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder alta(UUID curso, String cuerpo, UUID actor) {
    return post("/api/v1/courses/" + curso + "/modules")
        .with(con(actor, "courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static String cuerpo(String titulo, int orden) {
    return "{\"title\":\"" + titulo + "\",\"displayOrder\":" + orden + "}";
  }
}
