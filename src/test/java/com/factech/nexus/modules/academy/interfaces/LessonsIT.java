package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccion;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.modulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * El alta de lecciones (`RF-AC-028`): `CA-AC-085` a `CA-AC-091`. La carrera (`CA-AC-093`) vive en
 * {@link CourseTreeConcurrencyIT} y el arrastre (`CA-AC-092`) en {@link CourseDeletionIT}.
 */
@AutoConfigureMockMvc
class LessonsIT extends IntegrationTestBase {

  private static final String MARKDOWN =
      "# Velas\n\nUn párrafo con <script>alert(1)</script> dentro.";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private ObjectMapper json;

  private Statistics estadisticas;
  private UUID instructor;
  private UUID curso;
  private UUID modulo;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0);
    modulo = modulo(jdbc, curso, "Fundamentos", 0, "INACTIVO");
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-085` — registra la lección INACTIVA con moduleId y courseId de la ruta, open falsa si"
          + " no vino, y el Markdown con <script> se guarda y se devuelve sin tocar")
  void altaConElContenidoTalCual() throws Exception {
    String cuerpo =
        json.writeValueAsString(
            Map.of(
                "type",
                "TEXTO",
                "title",
                "  Qué es una vela ",
                "content",
                MARKDOWN,
                "durationSeconds",
                12,
                "displayOrder",
                0));
    mvc.perform(alta(cuerpo))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", containsString("/modules/" + modulo + "/lessons/")))
        .andExpect(jsonPath("$.moduleId").value(modulo.toString()))
        .andExpect(jsonPath("$.courseId").value(curso.toString()))
        .andExpect(jsonPath("$.type").value("TEXTO"))
        .andExpect(jsonPath("$.title").value("Qué es una vela"))
        .andExpect(jsonPath("$.content").value(MARKDOWN))
        .andExpect(jsonPath("$.durationSeconds").value(12))
        .andExpect(jsonPath("$.open").value(false))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.deletedAt").doesNotExist());
    assertThat(jdbc.queryForObject("SELECT content FROM lessons", String.class))
        .isEqualTo(MARKDOWN);

    mvc.perform(
            alta(
                "{\"type\":\"VIDEO\",\"title\":\"Video\",\"content\":\"https://vimeo.com/100000001\",\"durationSeconds\":3,\"displayOrder\":1,\"open\":true}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.open").value(true))
        .andExpect(jsonPath("$.content").value("https://vimeo.com/100000001"));
  }

  @Test
  @DisplayName(
      "`CA-AC-086` — 409 el título de una lección viva del mismo módulo; se admite el de una retirada y el mismo en otro módulo")
  void tituloUnicoDentroDelModulo() throws Exception {
    mvc.perform(alta(cuerpo("Intro", 0))).andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(" INTRO ", 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    UUID otroModulo = modulo(jdbc, curso, "Otro", 1, "INACTIVO");
    mvc.perform(alta(otroModulo, cuerpo("Intro", 0))).andExpect(status().isCreated());
    jdbc.update("UPDATE lessons SET deleted_at = now() WHERE module_id = ?", modulo);
    mvc.perform(alta(cuerpo("Intro", 0))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("`CA-AC-087` — 404 a un módulo inexistente, a uno retirado y a uno de otro curso")
  void moduloQueNoSirve() throws Exception {
    mvc.perform(alta(UUID.randomUUID(), cuerpo("X", 0))).andExpect(status().isNotFound());
    UUID retirado = modulo(jdbc, curso, "Retirado", 2, "INACTIVO");
    CourseTestSupport.retirarModulo(jdbc, retirado);
    mvc.perform(alta(retirado, cuerpo("X", 0))).andExpect(status().isNotFound());
    UUID otroCurso = curso(jdbc, "Otro curso", instructor, 1);
    mvc.perform(
            post("/api/v1/courses/" + otroCurso + "/modules/" + modulo + "/lessons")
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("X", 0)))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.detail")
                .value("No existe un módulo vivo con ese identificador en este curso."));
  }

  @Test
  @DisplayName(
      "`CA-AC-088` — tipo, título, duración, orden y el contenido de un VIDEO inválidos: 400 JUNTOS;"
          + " un TEXTO con cualquier texto se admite; y 400 con status, moduleId o courseId")
  void validacionesJuntas() throws Exception {
    mvc.perform(
            alta(
                "{\"type\":\"VIDEO\",\"title\":\" \",\"content\":\"no es url\",\"durationSeconds\":0,\"displayOrder\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(hasItems("title", "content", "durationSeconds", "displayOrder")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(hasItems("VAL-003", "VAL-004", "VAL-005", "VAL-006")));
    mvc.perform(alta("{\"title\":\"Sin tipo\",\"durationSeconds\":1,\"displayOrder\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].code").value(hasItems("VAL-002")));
    mvc.perform(
            alta("{\"type\":\"AUDIO\",\"title\":\"X\",\"durationSeconds\":1,\"displayOrder\":0}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            alta(
                "{\"type\":\"TEXTO\",\"title\":\"Texto\",\"content\":\"no es url y no importa\",\"durationSeconds\":1,\"displayOrder\":0}"))
        .andExpect(status().isCreated());
    for (String extra :
        List.of(
            "\"status\":\"ACTIVO\"",
            "\"moduleId\":\"" + modulo + "\"",
            "\"courseId\":\"" + curso + "\"")) {
      mvc.perform(
              alta(
                  "{\"type\":\"TEXTO\",\"title\":\"Y\",\"durationSeconds\":1,\"displayOrder\":0,"
                      + extra
                      + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString("no admite")));
    }
  }

  @Test
  @DisplayName(
      "`CA-AC-089` — deja una fila CREATE de lessons con el actor, con content_length y SIN el contenido")
  void auditaSinElTexto() throws Exception {
    UUID actor = UUID.randomUUID();
    String cuerpo =
        json.writeValueAsString(
            Map.of(
                "type",
                "TEXTO",
                "title",
                "Auditada",
                "content",
                MARKDOWN,
                "durationSeconds",
                12,
                "displayOrder",
                0));
    String id =
        com.jayway.jsonpath.JsonPath.read(
            mvc.perform(alta(modulo, cuerpo, actor))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    var fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, changes::text AS changes FROM audit_change_log"
                + " WHERE entity = 'lessons' AND entity_id = CAST(? AS uuid)",
            id);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat((String) fila.get("changes"))
        .contains("\"content_length\": " + MARKDOWN.length())
        .doesNotContain("<script>")
        .doesNotContain("\"content\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-090` y `CA-AC-091` — lessonCount del listado y lessons del módulo cuentan las vivas, la"
          + " duración suma las activas, y el detalle del curso trae las lecciones en orden sin"
          + " contenido en una sentencia más")
  void cuentasYArbol() throws Exception {
    leccion(jdbc, modulo, "Segunda", "TEXTO", "# b", 20, 1, "ACTIVO");
    leccion(
        jdbc,
        modulo,
        "Primera",
        "VIDEO",
        "https://www.youtube.com/watch?v=aaaaaaaaaaa",
        10,
        0,
        "INACTIVO");
    CourseTestSupport.retirarLeccion(
        jdbc, leccion(jdbc, modulo, "Retirada", "TEXTO", "# r", 99, 2, "ACTIVO"));

    mvc.perform(get("/api/v1/courses").with(con("courses:read")))
        .andExpect(jsonPath("$.content[0].lessonCount").value(2))
        .andExpect(jsonPath("$.content[0].moduleCount").value(1));

    estadisticas.clear();
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.modules[0].lessons[*].title")
                .value(contains("Primera", "Segunda", "Retirada")))
        .andExpect(jsonPath("$.modules[0].lessons[0].type").value("VIDEO"))
        .andExpect(jsonPath("$.modules[0].lessons[0].content").doesNotExist())
        .andExpect(jsonPath("$.modules[0].lessons[2].deleted").value(true))
        .andExpect(jsonPath("$.modules[0].durationSeconds").value(20))
        .andExpect(jsonPath("$.lessonCount").value(2))
        .andExpect(jsonPath("$.totalDurationSeconds").value(20));
    // Curso, categorías (`RF-AC-016`), membresías (`RF-AC-020`), servicios (`RF-AC-037`), módulos y
    // lecciones.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(6);
  }

  @Test
  @DisplayName("sin courses:update responde 403 aunque el actor porte courses:read")
  void sinPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons")
                .with(con("courses:read", "courses:learn"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("X", 0)))
        .andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM lessons", Integer.class)).isZero();
  }

  private MockHttpServletRequestBuilder alta(String cuerpo) {
    return alta(modulo, cuerpo, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder alta(UUID modulo, String cuerpo) {
    return alta(modulo, cuerpo, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder alta(UUID modulo, String cuerpo, UUID actor) {
    return post("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons")
        .with(con(actor, "courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static String cuerpo(String titulo, int orden) {
    return "{\"type\":\"TEXTO\",\"title\":\""
        + titulo
        + "\",\"durationSeconds\":5,\"displayOrder\":"
        + orden
        + "}";
  }
}
