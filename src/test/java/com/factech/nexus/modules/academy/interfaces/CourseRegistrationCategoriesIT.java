package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * El alta del curso con sus categorías (`RF-AC-008` 0.4.0, `CA-AC-227` a `CA-AC-229`): todo o nada,
 * con la misma escritura que la clasificación suelta.
 */
@AutoConfigureMockMvc
class CourseRegistrationCategoriesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID trading;
  private UUID cripto;

  @BeforeEach
  void sembrar() {
    limpiarTodo();
    instructor = CourseTestSupport.instructor(jdbc);
    trading = CourseCategoryTestSupport.categoria(jdbc, "Trading", 1);
    cripto = CourseCategoryTestSupport.categoria(jdbc, "Cripto", 0);
  }

  @AfterEach
  void limpiar() {
    limpiarTodo();
  }

  private void limpiarTodo() {
    CourseTestSupport.limpiar(jdbc);
    CourseCategoryTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-227` — con categoryIds nace en ellas, en su orden, con una fila y un CREATE por cada"
          + " una; sin la lista, o vacía, nace sin categorías")
  void naceEnSusCategorias() throws Exception {
    String id =
        alta("Velas", "\"" + trading + "\", \"" + cripto + "\"")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categories[*].name").value(contains("Cripto", "Trading")))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1");
    UUID curso = UUID.fromString(id);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM course_category_items WHERE course_id = ?",
                Integer.class,
                curso))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'AC'"
                    + " AND entity = 'course_category_items' AND entity_id = ? AND action = 'CREATE'",
                Integer.class,
                curso))
        .isEqualTo(2);

    alta("Sin lista", null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.categories", hasSize(0)));
    alta("Lista vacía", "")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.categories", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-AC-228` — una inexistente y una retirada: 422 EX-004 nombrándolas TODAS, y no queda"
          + " nada — ni curso, ni clasificaciones, ni auditoría")
  void todoONada() throws Exception {
    UUID inexistente = UUID.randomUUID();
    CourseCategoryTestSupport.retirar(jdbc, cripto);

    alta("Velas", "\"" + trading + "\", \"" + inexistente + "\", \"" + cripto + "\"")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"))
        .andExpect(jsonPath("$.errors[0].field").value("categoryIds"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString(inexistente.toString())))
        .andExpect(jsonPath("$.errors[0].message").value(containsString(cripto.toString())));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM courses", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_category_items", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'AC' AND entity IN"
                    + " ('courses', 'course_category_items')",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-229` — repetidas o con un nulo responden 400 VAL-008; el nulo, junto con los demás"
          + " errores de forma")
  void repetidasYNulas() throws Exception {
    alta("Velas", "\"" + trading + "\", \"" + trading + "\"")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-008"));

    mvc.perform(
            post("/api/v1/courses")
                .with(con("courses:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"instructorId\":\"%s\",\"difficulty\":\"PRINCIPIANTE\",\"displayOrder\":0,"
                            .formatted(instructor)
                        + "\"categoryIds\":[null]}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(org.hamcrest.Matchers.hasItems("VAL-001", "VAL-008")));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM courses", Integer.class)).isZero();
  }

  /** El alta con la lista dada; {@code null} la omite y {@code ""} la manda vacía. */
  private ResultActions alta(String titulo, String categorias) throws Exception {
    String lista = categorias == null ? "" : ",\"categoryIds\":[" + categorias + "]";
    return mvc.perform(
        post("/api/v1/courses")
            .with(con("courses:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"title\":\"%s\",\"instructorId\":\"%s\",\"difficulty\":\"PRINCIPIANTE\",\"displayOrder\":0%s}"
                    .formatted(titulo, instructor, lista)));
  }
}
