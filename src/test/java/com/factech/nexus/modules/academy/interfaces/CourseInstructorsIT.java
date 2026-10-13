package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
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
 * Varios instructores por curso (`RF-AC-062`, `RN-AC-033`): `CA-AC-324` a `CA-AC-326` y
 * `CA-AC-329`. El aula (`CA-AC-327`) y el progreso (`CA-AC-328`) viven en sus suites.
 */
@AutoConfigureMockMvc
class CourseInstructorsIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID juan;
  private UUID ana;
  private UUID beto;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    juan = CourseTestSupport.instructor(jdbc);
    ana = CourseTestSupport.persona(jdbc, "Ana", "Gómez", CourseTestSupport.rolInstructor(jdbc));
    beto = CourseTestSupport.persona(jdbc, "Beto", "Bravo", CourseTestSupport.rolInstructor(jdbc));
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-324` — registra con varios instructores, por nombre y sin `instructor`; vacía,"
          + " repetidos, inexistente y sin courses:teach se rechazan sin guardar nada")
  void registra() throws Exception {
    mvc.perform(alta("Velas", "\"" + juan + "\",\"" + ana + "\""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.instructors", hasSize(2)))
        .andExpect(jsonPath("$.instructors[*].fullName", contains("Ana Gómez", "Juan Pérez")))
        .andExpect(jsonPath("$.instructors[0].id").value(ana.toString()))
        .andExpect(jsonPath("$.instructors[0].username").exists())
        .andExpect(jsonPath("$.instructor").doesNotExist());
    assertThat(cuantos()).isEqualTo(1);

    mvc.perform(alta("Vacía", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("instructorIds"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(alta("Repetida", "\"" + juan + "\",\"" + juan + "\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"));
    mvc.perform(alta("Fantasma", "\"" + juan + "\",\"" + UUID.randomUUID() + "\""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("instructorIds"))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    UUID sinPermiso = CourseTestSupport.persona(jdbc, "Sin", "Teach", null);
    mvc.perform(alta("Sin permiso", "\"" + juan + "\",\"" + sinPermiso + "\""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(cuantos()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_instructors", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-AC-325` — corregir reemplaza la lista entera y la audita; la misma en otro orden no"
          + " cambia nada; vacía o nula, 400; un nuevo sin courses:teach, 422 y nada cambia")
  void corrige() throws Exception {
    UUID id = idDe(alta("Velas", "\"" + juan + "\",\"" + ana + "\""));

    mvc.perform(corregir(id, "\"" + ana + "\",\"" + juan + "\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructors", hasSize(2)));
    assertThat(actualizaciones(id)).isZero();

    mvc.perform(corregir(id, "\"" + beto + "\",\"" + ana + "\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructors[*].fullName", contains("Ana Gómez", "Beto Bravo")));
    assertThat(actualizaciones(id)).isEqualTo(1);
    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'courses' AND entity_id = ?"
                + " AND action = 'UPDATE'",
            String.class,
            id);
    assertThat(cambios)
        .contains("\"instructor_ids\"")
        .contains(juan.toString())
        .contains(beto.toString());

    mvc.perform(corregir(id, ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    mvc.perform(
            patch("/api/v1/courses/" + id)
                .with(con(UUID.randomUUID(), "courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instructorIds\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("instructorIds"));
    UUID sinPermiso = CourseTestSupport.persona(jdbc, "Sin", "Teach", null);
    mvc.perform(corregir(id, "\"" + ana + "\",\"" + sinPermiso + "\""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(
            jdbc.queryForList(
                "SELECT user_id FROM course_instructors WHERE course_id = ?", UUID.class, id))
        .containsExactlyInAnyOrder(ana, beto);
  }

  @Test
  @DisplayName(
      "`CA-AC-326` — el detalle y el listado traen instructors; el filtro instructorId deja los"
          + " cursos en cuya lista está, también cuando no es el primero")
  void leeYFiltra() throws Exception {
    UUID compartido = curso(jdbc, "Compartido", ana, 0);
    jdbc.update(
        "INSERT INTO course_instructors (course_id, user_id) VALUES (?, ?)", compartido, juan);
    curso(jdbc, "Solo de Ana", ana, 1);

    mvc.perform(get("/api/v1/courses/" + compartido).with(con("courses:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructors[*].fullName", contains("Ana Gómez", "Juan Pérez")));

    mvc.perform(get("/api/v1/courses?instructorId=" + juan).with(con("courses:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].title").value("Compartido"))
        .andExpect(jsonPath("$.content[0].instructors", hasSize(2)));
    mvc.perform(get("/api/v1/courses?instructorId=" + ana).with(con("courses:read")))
        .andExpect(jsonPath("$.content", hasSize(2)));
    mvc.perform(get("/api/v1/courses?instructorId=" + beto).with(con("courses:read")))
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  @DisplayName("`CA-AC-329` — V106: courses sin instructor_id y course_instructors con su clave")
  void elEsquema() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'courses'"
                    + " AND column_name = 'instructor_id'",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints WHERE table_name ="
                    + " 'course_instructors' AND constraint_name IN ('pk_course_instructors',"
                    + " 'fk_course_instructors_course', 'fk_course_instructors_user')",
                Integer.class))
        .isEqualTo(3);
  }

  private MockHttpServletRequestBuilder alta(String titulo, String instructores) {
    return post("/api/v1/courses")
        .with(con(UUID.randomUUID(), "courses:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"title\":\"%s\",\"instructorIds\":[%s],\"difficulty\":\"PRINCIPIANTE\",\"displayOrder\":0}"
                .formatted(titulo, instructores));
  }

  private MockHttpServletRequestBuilder corregir(UUID id, String instructores) {
    return patch("/api/v1/courses/" + id)
        .with(con(UUID.randomUUID(), "courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"instructorIds\":[" + instructores + "]}");
  }

  private UUID idDe(MockHttpServletRequestBuilder peticion) throws Exception {
    String respuesta =
        mvc.perform(peticion)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(respuesta, "$.id"));
  }

  private int cuantos() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM courses", Integer.class);
    return filas == null ? 0 : filas;
  }

  private int actualizaciones(UUID id) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity = 'courses' AND entity_id = ?"
                + " AND action = 'UPDATE'",
            Integer.class,
            id);
    return filas == null ? 0 : filas;
  }
}
