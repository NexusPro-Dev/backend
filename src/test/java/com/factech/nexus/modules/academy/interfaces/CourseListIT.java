package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El listado de cursos (`RF-AC-009` · `T-05`): `CA-AC-044` a `CA-AC-050`. El filtro por categoría
 * (`CA-AC-047`) acota de verdad desde `RF-AC-016`, cuyos casos viven en `CourseClassificationIT`, y
 * la cuenta de sentencias (`CA-AC-048`) es de tres desde entonces.
 */
@AutoConfigureMockMvc
class CourseListIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID juan;
  private UUID ana;
  private UUID retirado;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    juan = CourseTestSupport.instructor(jdbc);
    ana = CourseTestSupport.persona(jdbc, "Ana", "Gómez", CourseTestSupport.rolInstructor(jdbc));
    curso(jdbc, "Beta", juan, 1, "INTERMEDIO", "Corta", "Larga", "ACTIVO");
    curso(jdbc, "Alfa", juan, 0);
    curso(jdbc, "Gamma", ana, 1, "AVANZADO", null, null, "INACTIVO");
    retirado = curso(jdbc, "Retirado", ana, 5);
    CourseTestSupport.retirar(jdbc, retirado);
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-044` — cada fila trae el instructor resuelto, coverImageUrl, categories, offerable y"
          + " las cuentas, y cuadran con el detalle")
  void laFila() throws Exception {
    mvc.perform(listar(""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[1].title").value("Beta"))
        .andExpect(jsonPath("$.content[1].instructor.fullName").value("Juan Pérez"))
        .andExpect(jsonPath("$.content[1].coverImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.content[1].categories", hasSize(0)))
        .andExpect(jsonPath("$.content[1].offerable").value(false))
        .andExpect(jsonPath("$.content[1].moduleCount").value(0))
        .andExpect(jsonPath("$.content[1].lessonCount").value(0))
        .andExpect(jsonPath("$.content[1].status").value("ACTIVO"))
        .andExpect(jsonPath("$.content[1].longDescription").doesNotExist())
        .andExpect(jsonPath("$.content[1].deletedAt").doesNotExist());
  }

  @Test
  @DisplayName(
      "`CA-AC-045` — el orden por omisión es displayOrder ascendente con desempate por id; title y"
          + " createdAt se admiten; otro campo es 400")
  void orden() throws Exception {
    mvc.perform(listar(""))
        .andExpect(jsonPath("$.sort").value("displayOrder,asc"))
        .andExpect(jsonPath("$.content[*].title").value(contains("Alfa", "Beta", "Gamma")));
    mvc.perform(listar("?sort=title,desc"))
        .andExpect(jsonPath("$.content[*].title").value(contains("Gamma", "Beta", "Alfa")));
    mvc.perform(listar("?sort=createdAt"))
        .andExpect(jsonPath("$.sort").value("createdAt,desc"))
        .andExpect(jsonPath("$.content[0].title").value("Gamma"));
    mvc.perform(listar("?sort=difficulty"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("sort"));
  }

  @Test
  @DisplayName(
      "`CA-AC-046` — excluye los retirados salvo includeDeleted=true, y entonces con deletedAt y"
          + " offerable false")
  void retirados() throws Exception {
    mvc.perform(listar("")).andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(listar("?includeDeleted=true"))
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[3].title").value("Retirado"))
        .andExpect(jsonPath("$.content[3].deletedAt").exists())
        .andExpect(jsonPath("$.content[3].offerable").value(false));
  }

  @Test
  @DisplayName(
      "`CA-AC-047` — los filtros por título, instructor, dificultad y estado acotan y se combinan;"
          + " categoryId de una categoría sin cursos devuelve vacío")
  void filtros() throws Exception {
    mvc.perform(listar("?q=ALF")).andExpect(jsonPath("$.content[*].title").value(contains("Alfa")));
    mvc.perform(listar("?instructorId=" + ana))
        .andExpect(jsonPath("$.content[*].title").value(contains("Gamma")));
    mvc.perform(listar("?difficulty=INTERMEDIO"))
        .andExpect(jsonPath("$.content[*].title").value(contains("Beta")));
    mvc.perform(listar("?status=INACTIVO"))
        .andExpect(jsonPath("$.content[*].title").value(contains("Alfa", "Gamma")));
    mvc.perform(listar("?status=INACTIVO&instructorId=" + juan))
        .andExpect(jsonPath("$.content[*].title").value(contains("Alfa")));
    mvc.perform(listar("?categoryId=" + UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "`CA-AC-048` — tres sentencias fijas —página, categorías y total— con una y con tres filas")
  void sentencias() throws Exception {
    estadisticas.clear();
    mvc.perform(listar("?size=1")).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);

    estadisticas.clear();
    mvc.perform(listar("")).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "`CA-AC-049` — paginación, orden, dificultad y estado inválidos se devuelven JUNTOS; sin"
          + " courses:read responde 403 aunque el actor porte course-categories:read")
  void parametrosInvalidosYPermiso() throws Exception {
    mvc.perform(listar("?page=-1&sort=nada&difficulty=EXPERTO&status=BORRADOR"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field").value(hasItems("page", "sort", "difficulty", "status")));

    mvc.perform(get("/api/v1/courses").with(con("course-categories:read", "courses:learn")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-050` — un curso cuyo instructor fue retirado después sigue listado y nombrándolo")
  void instructorRetirado() throws Exception {
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", ana);
    mvc.perform(listar("?q=Gamma"))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].instructor.fullName").value("Ana Gómez"));
  }

  private MockHttpServletRequestBuilder listar(String consulta) {
    return get("/api/v1/courses" + consulta).with(con("courses:read"));
  }
}
