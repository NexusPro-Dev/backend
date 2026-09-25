package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import java.util.Map;
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
 * La clasificación del curso: `RF-AC-016` (`CA-AC-124` a `CA-AC-130`) y `RF-AC-017` (`CA-AC-131` a
 * `CA-AC-134`) en una suite, como plan de `RF-AC-017` §11 pide, y <b>las enmiendas</b> que la tabla
 * vuelve reales en seis lecturas ya construidas (`CA-AC-127` a `CA-AC-129`).
 *
 * <p>{@code offerable} de los cursos de una categoría es siempre falso todavía: la cuenta de
 * membresías es un literal cero hasta `RF-AC-020` (`tasks.md` §4, bloqueo 2). Aquí se prueba que
 * viaja, no que alguna vez sea verdadero.
 */
@AutoConfigureMockMvc
class CourseClassificationIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID instructor;
  private UUID velas;
  private UUID trading;
  private UUID cripto;

  @BeforeEach
  void sembrar() {
    limpiarTodo();
    instructor = CourseTestSupport.instructor(jdbc);
    velas = curso(jdbc, "Velas", instructor, 0);
    trading = CourseCategoryTestSupport.categoria(jdbc, "Trading", 1, "1E88E5", "chart-line", null);
    cripto = CourseCategoryTestSupport.categoria(jdbc, "Cripto", 0, "F4511E", "bitcoin", null);
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
  }

  @AfterEach
  void limpiar() {
    limpiarTodo();
  }

  private void limpiarTodo() {
    // Los cursos primero: su limpieza borra las filas de clasificación, que
    // referencian a las dos tablas sin ON DELETE.
    CourseTestSupport.limpiar(jdbc);
    CourseCategoryTestSupport.limpiar(jdbc);
  }

  // --- RF-AC-016 -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-124` — clasifica con 201 y Location, devuelve el detalle con la categoría —id, nombre,"
          + " color, icono—; un curso INACTIVO se clasifica igual y su estado no cambia; en varias"
          + " categorías salen en su orden")
  void clasifica() throws Exception {
    mvc.perform(clasificar(velas, trading))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/courses/" + velas))
        .andExpect(jsonPath("$.id").value(velas.toString()))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.categories", hasSize(1)))
        .andExpect(jsonPath("$.categories[0].id").value(trading.toString()))
        .andExpect(jsonPath("$.categories[0].name").value("Trading"))
        .andExpect(jsonPath("$.categories[0].color").value("1E88E5"))
        .andExpect(jsonPath("$.categories[0].icon").value("chart-line"));

    // Cripto tiene orden 0 y Trading 1: el orden es el de la categoría, no el
    // de la clasificación.
    mvc.perform(clasificar(velas, cripto))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.categories[*].name").value(contains("Cripto", "Trading")));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM course_category_items WHERE course_id = ?",
                Integer.class,
                velas))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT status FROM courses WHERE id = ?", String.class, velas))
        .isEqualTo("INACTIVO");
  }

  @Test
  @DisplayName(
      "`CA-AC-125` — 409 a la pareja repetida nombrando la categoría; 422 a la categoría inexistente"
          + " o retirada; 404 al curso inexistente o retirado; 400 a categoryId ausente, mal formado"
          + " o a un campo de más")
  void rechazos() throws Exception {
    mvc.perform(clasificar(velas, trading)).andExpect(status().isCreated());
    mvc.perform(clasificar(velas, trading))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("El curso ya está clasificado en la categoría Trading."));

    mvc.perform(clasificar(velas, UUID.randomUUID()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    CourseCategoryTestSupport.retirar(jdbc, cripto);
    mvc.perform(clasificar(velas, cripto))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    mvc.perform(clasificar(UUID.randomUUID(), trading))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un curso vivo con ese identificador."));
    UUID retirado = curso(jdbc, "Retirado", instructor, 1);
    CourseTestSupport.retirar(jdbc, retirado);
    mvc.perform(clasificar(retirado, trading)).andExpect(status().isNotFound());

    for (String cuerpo :
        new String[] {
          "{}",
          "{\"categoryId\":\"no-es-uuid\"}",
          "{\"categoryId\":\"" + trading + "\",\"displayOrder\":1}"
        }) {
      mvc.perform(
              post("/api/v1/courses/" + velas + "/categories")
                  .with(con("courses:update"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(cuerpo))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            post("/api/v1/courses/no-es-uuid/categories")
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + trading + "\"}"))
        .andExpect(status().isBadRequest());

    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_category_items", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-126` — una fila CREATE de course_category_items con el curso como entidad, el actor y"
          + " la pareja con el nombre en la instantánea")
  void auditaElAlta() throws Exception {
    UUID actor = UUID.randomUUID();
    mvc.perform(
            post("/api/v1/courses/" + velas + "/categories")
                .with(con(actor, "courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + trading + "\"}"))
        .andExpect(status().isCreated());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'AC' AND entity = 'course_category_items' AND entity_id = ?",
            velas);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat((String) fila.get("changes"))
        .contains(velas.toString())
        .contains(trading.toString())
        .contains("Trading");
  }

  @Test
  @DisplayName("sin courses:update responde 403 aunque el actor porte course-categories:update")
  void sinPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/courses/" + velas + "/categories")
                .with(con("course-categories:update", "courses:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + trading + "\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/v1/courses/" + velas + "/categories/" + trading)
                .with(con("course-categories:update", "courses:read")))
        .andExpect(status().isForbidden());
  }

  // --- Enmiendas -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-127` — courseCount cuenta los vivos —el inactivo sí, el retirado no—; el detalle de la"
          + " categoría trae sus cursos vivos en orden con offerable; la instantánea del retiro de la"
          + " categoría lleva course_ids")
  void enmiendasDeLaCategoria() throws Exception {
    UUID alfa = curso(jdbc, "Alfa", instructor, 5, "PRINCIPIANTE", "C", "L", "ACTIVO");
    UUID retirado = curso(jdbc, "Retirado", instructor, 1);
    for (UUID id : List.of(velas, alfa, retirado)) {
      mvc.perform(clasificar(id, trading)).andExpect(status().isCreated());
    }
    CourseTestSupport.retirar(jdbc, retirado);

    mvc.perform(get("/api/v1/course-categories").with(con("course-categories:read")))
        .andExpect(jsonPath("$.content[?(@.name == 'Trading')].courseCount").value(contains(2)))
        .andExpect(jsonPath("$.content[?(@.name == 'Cripto')].courseCount").value(contains(0)));

    estadisticas.clear();
    mvc.perform(get("/api/v1/course-categories/" + trading).with(con("course-categories:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCount").value(2))
        .andExpect(jsonPath("$.courses[*].title").value(contains("Velas", "Alfa")))
        .andExpect(jsonPath("$.courses[0].status").value("INACTIVO"))
        .andExpect(jsonPath("$.courses[1].status").value("ACTIVO"))
        .andExpect(jsonPath("$.courses[1].offerable").value(false));
    // Una sentencia más que sin cursos: la de los cursos.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);

    mvc.perform(
            post("/api/v1/course-categories/" + trading + "/deletion")
                .with(con("course-categories:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Se fusiona.\"}"))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT snapshot::text FROM audit_deletion_log WHERE entity = 'course_categories'"
                    + " AND entity_id = ?",
                String.class,
                trading))
        .contains(velas.toString())
        .contains(alfa.toString())
        .doesNotContain(retirado.toString());
  }

  @Test
  @DisplayName(
      "`CA-AC-128` — cada fila del listado trae sus categorías vivas —la retirada no— en una"
          + " sentencia por página; categoryId acota —la retirada no—; el detalle trae categories")
  void enmiendasDelCurso() throws Exception {
    UUID beta = curso(jdbc, "Beta", instructor, 1);
    UUID gamma = curso(jdbc, "Gamma", instructor, 2);
    mvc.perform(clasificar(velas, trading)).andExpect(status().isCreated());
    mvc.perform(clasificar(velas, cripto)).andExpect(status().isCreated());
    mvc.perform(clasificar(beta, cripto)).andExpect(status().isCreated());

    estadisticas.clear();
    mvc.perform(get("/api/v1/courses").with(con("courses:read")))
        .andExpect(jsonPath("$.content[*].title").value(contains("Velas", "Beta", "Gamma")))
        .andExpect(jsonPath("$.content[0].categories[*].name").value(contains("Cripto", "Trading")))
        .andExpect(jsonPath("$.content[1].categories[*].name").value(contains("Cripto")))
        .andExpect(jsonPath("$.content[2].categories", hasSize(0)));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);

    mvc.perform(get("/api/v1/courses?categoryId=" + cripto).with(con("courses:read")))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[*].title").value(contains("Velas", "Beta")));
    mvc.perform(get("/api/v1/courses?categoryId=" + trading).with(con("courses:read")))
        .andExpect(jsonPath("$.content[*].title").value(contains("Velas")));

    CourseCategoryTestSupport.retirar(jdbc, cripto);
    mvc.perform(get("/api/v1/courses").with(con("courses:read")))
        .andExpect(jsonPath("$.content[0].categories[*].name").value(contains("Trading")))
        .andExpect(jsonPath("$.content[1].categories", hasSize(0)));
    mvc.perform(get("/api/v1/courses?categoryId=" + cripto).with(con("courses:read")))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/v1/courses/" + velas).with(con("courses:read")))
        .andExpect(jsonPath("$.categories[*].name").value(contains("Trading")));
    assertThat(gamma).isNotNull();
  }

  @Test
  @DisplayName(
      "`CA-AC-129` — la instantánea del retiro del curso lleva category_ids, y las filas de"
          + " clasificación permanecen")
  void enmiendaDelRetiroDelCurso() throws Exception {
    mvc.perform(clasificar(velas, trading)).andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/courses/" + velas + "/deletion")
                .with(con("courses:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ya no se dicta.\"}"))
        .andExpect(status().isNoContent());

    assertThat(
            jdbc.queryForObject(
                "SELECT snapshot::text FROM audit_deletion_log WHERE entity = 'courses'"
                    + " AND entity_id = ?",
                String.class,
                velas))
        .contains("\"category_ids\": [\"" + trading + "\"]");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM course_category_items WHERE course_id = ?",
                Integer.class,
                velas))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-130` — dos clasificaciones simultáneas de la misma pareja: una fila y un 409")
  void dosClasificaciones() throws Exception {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(clasificar(velas, trading)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_category_items", Integer.class))
        .isEqualTo(1);
  }

  // --- RF-AC-017 -----------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-AC-131` y `CA-AC-132` — desclasifica con 200 y el detalle sin la categoría; la fila no"
          + " existe; el estado no cambia; una fila ASSOCIATION sin motivo con la pareja")
  void desclasifica() throws Exception {
    mvc.perform(clasificar(velas, trading)).andExpect(status().isCreated());
    mvc.perform(clasificar(velas, cripto)).andExpect(status().isCreated());
    UUID actor = UUID.randomUUID();

    mvc.perform(
            delete("/api/v1/courses/" + velas + "/categories/" + trading)
                .with(con(actor, "courses:update")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.categories[*].name").value(contains("Cripto")));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM course_category_items WHERE course_id = ? AND category_id = ?",
                Integer.class,
                velas,
                trading))
        .isZero();
    Map<String, Object> baja =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE module = 'AC' AND entity = 'course_category_items'"
                + " AND entity_id = ?",
            velas);
    assertThat(baja.get("actor")).isEqualTo(actor.toString());
    assertThat(baja.get("deletion_type")).isEqualTo("ASSOCIATION");
    assertThat(baja.get("reason")).isNull();
    assertThat((String) baja.get("snapshot"))
        .contains(trading.toString())
        .contains("\"category_name\": \"Trading\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-133` — 404 al curso inexistente o retirado y 404 a la pareja inexistente, con"
          + " mensajes distintos; una categoría retirada se desclasifica igual")
  void desclasificarRechazos() throws Exception {
    mvc.perform(desclasificar(UUID.randomUUID(), trading))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un curso vivo con ese identificador."));
    mvc.perform(desclasificar(velas, trading))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("El curso no está clasificado en esa categoría."));

    mvc.perform(clasificar(velas, cripto)).andExpect(status().isCreated());
    CourseCategoryTestSupport.retirar(jdbc, cripto);
    mvc.perform(desclasificar(velas, cripto))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories", hasSize(0)));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM course_category_items", Integer.class))
        .isZero();

    mvc.perform(clasificar(velas, trading)).andExpect(status().isCreated());
    CourseTestSupport.retirar(jdbc, velas);
    mvc.perform(desclasificar(velas, trading)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-134` — dos desclasificaciones simultáneas: un 200, un 404 y UNA fila de auditoría")
  void dosDesclasificaciones() throws Exception {
    mvc.perform(clasificar(velas, trading)).andExpect(status().isCreated());
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(desclasificar(velas, trading)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 200).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 404).count())
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity = 'course_category_items'"
                    + " AND entity_id = ?",
                Integer.class,
                velas))
        .isEqualTo(1);
  }

  private MockHttpServletRequestBuilder clasificar(UUID curso, UUID categoria) {
    return post("/api/v1/courses/" + curso + "/categories")
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"categoryId\":\"" + categoria + "\"}");
  }

  private MockHttpServletRequestBuilder desclasificar(UUID curso, UUID categoria) {
    return delete("/api/v1/courses/" + curso + "/categories/" + categoria)
        .with(con("courses:update"));
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }
}
