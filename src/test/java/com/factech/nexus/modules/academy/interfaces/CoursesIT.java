package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.cuerpo;
import static org.assertj.core.api.Assertions.assertThat;
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
 * El alta de cursos (`RF-AC-008` · `T-12`): `CA-AC-034` a `CA-AC-041` y `CA-AC-043`. La carrera
 * (`CA-AC-042`) vive en {@link CourseConcurrencyIT}.
 *
 * <p>Lo que más importa aquí es <b>el instructor</b>: es la primera vez que un módulo condiciona un
 * dato suyo a un permiso de `SP`, y los tres rechazos —no existe, retirado, sin permiso— y el caso
 * del rol inactivo son lo que fija que `PermissionHolderLookup` dice lo mismo que la autorización.
 */
@AutoConfigureMockMvc
class CoursesIT extends IntegrationTestBase {

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
  @DisplayName(
      "`CA-AC-034` — registra el curso en la forma del detalle: INACTIVO, instructor resuelto, listas"
          + " vacías, portada nula, cero minutos y offerable false «inactivo»")
  void altaEnLaFormaDelDetalle() throws Exception {
    mvc.perform(
            alta(
                """
                {"title":"  Velas japonesas  ","instructorId":"%s","difficulty":"PRINCIPIANTE",
                 "shortDescription":"Lo básico.","longDescription":"Todo lo básico.",
                 "introVideoUrl":"https://v.io/intro","displayOrder":2}
                """
                    .formatted(instructor)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("/api/v1/courses/")))
        .andExpect(jsonPath("$.title").value("Velas japonesas"))
        .andExpect(jsonPath("$.instructor.id").value(instructor.toString()))
        .andExpect(jsonPath("$.instructor.username").value(startsWith("ac-")))
        .andExpect(jsonPath("$.instructor.fullName").value("Juan Pérez"))
        .andExpect(jsonPath("$.difficulty").value("PRINCIPIANTE"))
        .andExpect(jsonPath("$.shortDescription").value("Lo básico."))
        .andExpect(jsonPath("$.introVideoUrl").value("https://v.io/intro"))
        .andExpect(jsonPath("$.displayOrder").value(2))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.categories", hasSize(0)))
        .andExpect(jsonPath("$.recommendedCourses", hasSize(0)))
        .andExpect(jsonPath("$.memberships", hasSize(0)))
        .andExpect(jsonPath("$.modules", hasSize(0)))
        .andExpect(jsonPath("$.totalDurationMinutes").value(0))
        .andExpect(jsonPath("$.lessonCount").value(0))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value("El curso está inactivo."))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.deletedAt").doesNotExist())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  @DisplayName(
      "`CA-AC-035` — el título de un curso vivo responde 409 sin mayúsculas ni acentos, y el de un"
          + " retirado se admite")
  void tituloRepetido() throws Exception {
    mvc.perform(alta(cuerpo("Análisis técnico", instructor, "INTERMEDIO", 0)))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo("  ANALISIS TECNICO ", instructor, "AVANZADO", 1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"))
        .andExpect(jsonPath("$.errors[0].field").value("title"));

    jdbc.update("UPDATE courses SET deleted_at = now() WHERE title = 'Análisis técnico'");
    mvc.perform(alta(cuerpo("Análisis técnico", instructor, "INTERMEDIO", 0)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "`CA-AC-036` — instructor inexistente y retirado: 422 EX-002; vivo sin courses:teach, y con"
          + " el permiso por un rol INACTIVO: 422 EX-003")
  void instructorQueNoSirve() throws Exception {
    mvc.perform(alta(cuerpo("Uno", UUID.randomUUID(), "PRINCIPIANTE", 0)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[0].field").value("instructorId"));

    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", instructor);
    mvc.perform(alta(cuerpo("Dos", instructor, "PRINCIPIANTE", 0)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    UUID sinPermiso = CourseTestSupport.persona(jdbc, "Ana", "Sin", null);
    mvc.perform(alta(cuerpo("Tres", sinPermiso, "PRINCIPIANTE", 0)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(jsonPath("$.detail").value(containsString("courses:teach")));

    UUID rolInactivo = CourseTestSupport.rolInstructor(jdbc);
    jdbc.update("UPDATE roles SET status = 'INACTIVO' WHERE id = ?", rolInactivo);
    UUID conRolInactivo = CourseTestSupport.persona(jdbc, "Luis", "Inactivo", rolInactivo);
    mvc.perform(alta(cuerpo("Cuatro", conRolInactivo, "PRINCIPIANTE", 0)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(cuantos()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-037` — título ausente, instructor ausente, dificultad ausente, orden negativo,"
          + " descripciones largas y video mal formado: 400, JUNTOS")
  void validacionesJuntas() throws Exception {
    mvc.perform(
            alta(
                """
                {"title":"   ","displayOrder":-1,"shortDescription":"%s","longDescription":"%s",
                 "introVideoUrl":"ftp://nada"}
                """
                    .formatted("x".repeat(301), "y".repeat(10_001))))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(
                    hasItems(
                        "title",
                        "instructorId",
                        "difficulty",
                        "displayOrder",
                        "shortDescription",
                        "longDescription",
                        "introVideoUrl")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(hasItems("VAL-001", "VAL-002", "VAL-003", "VAL-004", "VAL-005", "VAL-006")));

    // Una dificultad fuera del dominio también es 400.
    mvc.perform(alta(cuerpo("Uno", instructor, "EXPERTO", 0))).andExpect(status().isBadRequest());
    assertThat(cuantos()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-038` — status, categories, memberships, modules, coverImageUrl y code en el cuerpo"
          + " responden 400")
  void camposQueElCursoNoAdmite() throws Exception {
    for (String extra :
        List.of(
            "\"status\":\"ACTIVO\"",
            "\"categories\":[]",
            "\"memberships\":[]",
            "\"modules\":[]",
            "\"coverImageUrl\":\"/x\"",
            "\"code\":\"CUR-1\"")) {
      mvc.perform(
              alta(
                  """
                  {"title":"Velas","instructorId":"%s","difficulty":"PRINCIPIANTE","displayOrder":0,%s}
                  """
                      .formatted(instructor, extra)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString("no admite")));
    }
    assertThat(cuantos()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-039` — las descripciones de espacios se guardan nulas y el video tal cual, sin seguirlo")
  void descripcionesDeEspacios() throws Exception {
    mvc.perform(
            alta(
                """
                {"title":"Velas","instructorId":"%s","difficulty":"PRINCIPIANTE","displayOrder":0,
                 "shortDescription":"   ","longDescription":" ","introVideoUrl":"https://no-existe.invalid/v"}
                """
                    .formatted(instructor)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shortDescription").value(nullValue()))
        .andExpect(jsonPath("$.longDescription").value(nullValue()))
        .andExpect(jsonPath("$.introVideoUrl").value("https://no-existe.invalid/v"));
    assertThat(
            jdbc.queryForObject(
                "SELECT short_description IS NULL AND long_description IS NULL FROM courses WHERE"
                    + " title = 'Velas'",
                Boolean.class))
        .isTrue();
  }

  @Test
  @DisplayName(
      "`CA-AC-040` — deja una fila CREATE en audit_change_log con el actor e instructor_id")
  void auditaLaCreacion() throws Exception {
    UUID actor = UUID.randomUUID();
    String id =
        com.jayway.jsonpath.JsonPath.read(
            mvc.perform(alta(cuerpo("Auditado", instructor, "AVANZADO", 4), actor))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    var fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'AC' AND entity = 'courses' AND entity_id = CAST(? AS uuid)",
            id);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat((String) fila.get("changes"))
        .contains("\"title\": \"Auditado\"")
        .contains("\"instructor_id\": \"" + instructor + "\"")
        .contains("\"status\": \"INACTIVO\"")
        .contains("\"display_order\": 4");
  }

  @Test
  @DisplayName(
      "`CA-AC-041` — sin courses:create responde 403 aunque el actor porte los cuatro"
          + " course-categories:")
  void lasCategoriasNoHabilitan() throws Exception {
    mvc.perform(
            post("/api/v1/courses")
                .with(
                    con(
                        "course-categories:read",
                        "course-categories:create",
                        "course-categories:update",
                        "course-categories:delete",
                        "courses:read",
                        "courses:teach"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Sin permiso", instructor, "PRINCIPIANTE", 0)))
        .andExpect(status().isForbidden());
    assertThat(cuantos()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-043` — revocar después el rol que daba courses:teach no cambia el curso: el detalle"
          + " lo sigue nombrando")
  void perderElPermisoDespuesNoTocaElCurso() throws Exception {
    String id =
        com.jayway.jsonpath.JsonPath.read(
            mvc.perform(alta(cuerpo("Perdura", instructor, "PRINCIPIANTE", 0)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    jdbc.update("DELETE FROM user_roles WHERE user_id = ?", instructor);

    mvc.perform(get("/api/v1/courses/" + id).with(con("courses:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructor.id").value(instructor.toString()))
        .andExpect(jsonPath("$.instructor.fullName").value("Juan Pérez"));
  }

  private MockHttpServletRequestBuilder alta(String cuerpo) {
    return alta(cuerpo, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder alta(String cuerpo, UUID actor) {
    return post("/api/v1/courses")
        .with(con(actor, "courses:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private int cuantos() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM courses", Integer.class);
    return filas == null ? 0 : filas;
  }
}
