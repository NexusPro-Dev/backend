package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.cuerpo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * El alta de categorías (`RF-AC-001` · `T-10`): `CA-AC-001` a `CA-AC-008`. La carrera (`CA-AC-009`)
 * vive en {@link CourseCategoryConcurrencyIT}.
 *
 * <p>Lo que más importa aquí no es el camino feliz sino <b>lo que la categoría no tiene</b> —ni
 * estado, ni cursos, ni portada en el cuerpo— y lo que sí tiene siempre: color en mayúsculas e
 * icono.
 */
@AutoConfigureMockMvc
class CourseCategoriesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void limpiar() {
    CourseCategoryTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-001` — registra la categoría en la forma del detalle: cero cursos, portada nula y el"
          + " color en mayúsculas aunque llegara en minúsculas")
  void altaEnLaFormaDelDetalle() throws Exception {
    mvc.perform(
            alta(
                """
                {"name":"  Trading  ","description":"Lo básico.","color":"1e88e5",
                 "icon":"chart-line","displayOrder":0}
                """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("/api/v1/course-categories/")))
        .andExpect(jsonPath("$.name").value("Trading"))
        .andExpect(jsonPath("$.description").value("Lo básico."))
        .andExpect(jsonPath("$.color").value("1E88E5"))
        .andExpect(jsonPath("$.icon").value("chart-line"))
        .andExpect(jsonPath("$.displayOrder").value(0))
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.courseCount").value(0))
        .andExpect(jsonPath("$.courses", hasSize(0)))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.deletedAt").doesNotExist())
        .andExpect(jsonPath("$.deletionReason").doesNotExist());

    // Y en la tabla no hay columna de estado que consultar: la categoría nace viva.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns"
                    + " WHERE table_name = 'course_categories' AND column_name = 'status'",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-002` — el nombre de una categoría viva responde 409 sin mayúsculas ni acentos, y el"
          + " de una retirada se admite")
  void nombreRepetido() throws Exception {
    mvc.perform(alta(cuerpo("Análisis técnico", "1E88E5", "chart-line", 0)))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo("  ANALISIS TECNICO ", "FF0000", "star", 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
        .andExpect(jsonPath("$.errors[0].field").value("name"));

    jdbc.update("UPDATE course_categories SET deleted_at = now() WHERE name = 'Análisis técnico'");
    mvc.perform(alta(cuerpo("Análisis técnico", "1E88E5", "chart-line", 0)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("`CA-AC-003` — un color y un orden que ya usa otra categoría se admiten")
  void colorYOrdenNoSonUnicos() throws Exception {
    mvc.perform(alta(cuerpo("Uno", "1E88E5", "chart-line", 3))).andExpect(status().isCreated());
    mvc.perform(alta(cuerpo("Dos", "1e88e5", "star", 3)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.color").value("1E88E5"))
        .andExpect(jsonPath("$.displayOrder").value(3));
    assertThat(cuantas()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-AC-004` — nombre ausente, color mal formado, icono mal formado y orden negativo: 400,"
          + " JUNTOS")
  void validacionesJuntas() throws Exception {
    mvc.perform(
            alta(
                """
                {"name":"   ","color":"#1E88E5","icon":"Chart Line","displayOrder":-1}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field").value(hasItems("name", "color", "icon", "displayOrder")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(hasItems("VAL-001", "VAL-002", "VAL-003", "VAL-004")));

    // El nombre largo y el orden ausente también.
    mvc.perform(
            alta(
                """
                {"name":"%s","color":"1E88E5","icon":"chart-line"}
                """
                    .formatted("x".repeat(151))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItems("name", "displayOrder")));
    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName("`CA-AC-005` — `status`, `courses` y `coverImageUrl` en el cuerpo responden 400")
  void camposQueLaCategoriaNoTiene() throws Exception {
    for (String extra :
        List.of("\"status\":\"ACTIVO\"", "\"courses\":[]", "\"coverImageUrl\":\"/x\"")) {
      mvc.perform(
              alta(
                  """
                  {"name":"Trading","color":"1E88E5","icon":"chart-line","displayOrder":0,%s}
                  """
                      .formatted(extra)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString("no admite")));
    }
    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName("`CA-AC-006` — una descripción de solo espacios se guarda nula y vuelve nula")
  void descripcionDeEspacios() throws Exception {
    mvc.perform(
            alta(
                """
                {"name":"Trading","description":"   ","color":"1E88E5","icon":"chart-line",
                 "displayOrder":0}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.description").value(nullValue()));
    assertThat(
            jdbc.queryForObject(
                "SELECT description IS NULL FROM course_categories WHERE name = 'Trading'",
                Boolean.class))
        .isTrue();
  }

  @Test
  @DisplayName("`CA-AC-007` — deja una fila CREATE en audit_change_log con el actor")
  void auditaLaCreacion() throws Exception {
    UUID actor = UUID.randomUUID();
    String id =
        com.jayway.jsonpath.JsonPath.read(
            mvc.perform(alta(cuerpo("Auditada", "1e88e5", "chart-line", 2), actor))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    var fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'AC' AND entity = 'course_categories' AND entity_id = CAST(? AS uuid)",
            id);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat((String) fila.get("changes"))
        .contains("\"name\": \"Auditada\"")
        .contains("\"color\": \"1E88E5\"")
        .contains("\"display_order\": 2")
        .doesNotContain("status");
  }

  @Test
  @DisplayName(
      "`CA-AC-008` — sin `course-categories:create` responde 403 aunque el actor porte los seis"
          + " `courses:` y los cuatro `products:`")
  void losCoursesNoHabilitan() throws Exception {
    mvc.perform(
            post("/api/v1/course-categories")
                .with(
                    con(
                        "courses:read",
                        "courses:create",
                        "courses:update",
                        "courses:delete",
                        "courses:teach",
                        "courses:learn",
                        "products:create",
                        "products:read",
                        "products:update",
                        "products:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Sin permiso", "1E88E5", "chart-line", 0)))
        .andExpect(status().isForbidden());
    assertThat(cuantas()).isZero();
  }

  private MockHttpServletRequestBuilder alta(String cuerpo) {
    return alta(cuerpo, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder alta(String cuerpo, UUID actor) {
    return post("/api/v1/course-categories")
        .with(con(actor, "course-categories:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private int cuantas() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM course_categories", Integer.class);
    return filas == null ? 0 : filas;
  }
}
