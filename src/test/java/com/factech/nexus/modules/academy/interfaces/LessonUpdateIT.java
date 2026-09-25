package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccion;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.modulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** La corrección de la lección (`RF-AC-029`): `CA-AC-099` a `CA-AC-104` y `CA-AC-215`. */
@AutoConfigureMockMvc
class LessonUpdateIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID curso;
  private UUID modulo;
  private UUID leccion;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0, "PRINCIPIANTE", "C", "L", "ACTIVO");
    modulo = modulo(jdbc, curso, "Fundamentos", 0, "ACTIVO");
    leccion = leccion(jdbc, modulo, "Intro", "TEXTO", "# Intro", 10, 0, "INACTIVO");
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-099` — corrige los siete campos juntos y devuelve la lección con su contenido y updatedAt avanzado")
  void corrigeLosSiete() throws Exception {
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM lessons WHERE id = ?", String.class, leccion);
    mvc.perform(
            corregir(
                leccion,
                """
                {"type":"VIDEO","title":"Vela","description":"Desc","content":"https://v.io/1",
                 "durationSeconds":7,"displayOrder":3,"open":true}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("VIDEO"))
        .andExpect(jsonPath("$.title").value("Vela"))
        .andExpect(jsonPath("$.description").value("Desc"))
        .andExpect(jsonPath("$.content").value("https://v.io/1"))
        .andExpect(jsonPath("$.durationSeconds").value(7))
        .andExpect(jsonPath("$.displayOrder").value(3))
        .andExpect(jsonPath("$.open").value(true));
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at::text FROM lessons WHERE id = ?", String.class, leccion))
        .isNotEqualTo(antes);
  }

  @Test
  @DisplayName(
      "`CA-AC-100` — pasar a VIDEO con un texto guardado y sin URL responde 400 sin aplicar nada;"
          + " con la URL se admite; pasar a TEXTO con una URL guardada se admite")
  void laParejaResultante() throws Exception {
    mvc.perform(corregir(leccion, "{\"type\":\"VIDEO\",\"title\":\"Otro\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("content"))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    assertThat(jdbc.queryForMap("SELECT type, title FROM lessons WHERE id = ?", leccion))
        .containsEntry("type", "TEXTO")
        .containsEntry("title", "Intro");

    mvc.perform(corregir(leccion, "{\"type\":\"VIDEO\",\"content\":\"https://v.io/1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("VIDEO"));
    mvc.perform(corregir(leccion, "{\"type\":\"TEXTO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("TEXTO"))
        .andExpect(jsonPath("$.content").value("https://v.io/1"));
  }

  @Test
  @DisplayName(
      "`CA-AC-101` — el nulo vacía descripción y contenido —también en una ACTIVA— y se rechaza en tipo, título, duración, orden y open, juntos")
  void elNulo() throws Exception {
    jdbc.update("UPDATE lessons SET status = 'ACTIVO', description = 'D' WHERE id = ?", leccion);
    mvc.perform(corregir(leccion, "{\"description\":null,\"content\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value(nullValue()))
        .andExpect(jsonPath("$.content").value(nullValue()))
        .andExpect(jsonPath("$.status").value("ACTIVO"));
    mvc.perform(
            corregir(
                leccion,
                "{\"type\":null,\"title\":null,\"durationSeconds\":null,\"displayOrder\":null,\"open\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(hasItems("type", "title", "durationSeconds", "displayOrder", "open")));
  }

  @Test
  @DisplayName(
      "`CA-AC-102` — 400 cuerpo vacío y campos no admitidos; 409 el título de OTRA viva; 404 retirada, inexistente o de otro módulo o curso")
  void cuerpoTituloYPertenencia() throws Exception {
    mvc.perform(corregir(leccion, "{}")).andExpect(status().isBadRequest());
    for (String extra :
        List.of(
            "\"moduleId\":\"" + modulo + "\"",
            "\"courseId\":\"" + curso + "\"",
            "\"status\":\"ACTIVO\"")) {
      mvc.perform(corregir(leccion, "{\"title\":\"X\"," + extra + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString("no admite")));
    }
    leccion(jdbc, modulo, "Otra", "TEXTO", "# o", 1, 1, "INACTIVO");
    mvc.perform(corregir(leccion, "{\"title\":\" OTRA \"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));

    UUID retirada = leccion(jdbc, modulo, "Retirada", "TEXTO", "# r", 1, 2, "INACTIVO");
    CourseTestSupport.retirarLeccion(jdbc, retirada);
    mvc.perform(corregir(retirada, "{\"title\":\"Vuelve\"}")).andExpect(status().isNotFound());
    mvc.perform(corregir(UUID.randomUUID(), "{\"title\":\"Nada\"}"))
        .andExpect(status().isNotFound());
    UUID otroModulo = modulo(jdbc, curso, "Otro", 1, "INACTIVO");
    mvc.perform(
            patch("/api/v1/courses/" + curso + "/modules/" + otroModulo + "/lessons/" + leccion)
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Ajena\"}"))
        .andExpect(status().isNotFound());
    UUID otroCurso = curso(jdbc, "Otro curso", instructor, 1);
    mvc.perform(
            patch("/api/v1/courses/" + otroCurso + "/modules/" + modulo + "/lessons/" + leccion)
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Ajena\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-103` — sin cambios no audita; con cambios la fila UPDATE lleva solo lo que cambió y el contenido como longitud, no como texto")
  void auditaLaLongitud() throws Exception {
    mvc.perform(corregir(leccion, "{\"title\":\" Intro \",\"content\":\"# Intro\"}"))
        .andExpect(status().isOk());
    assertThat(auditadas()).isZero();
    mvc.perform(
            corregir(
                leccion,
                "{\"content\":\"# Intro\\n\\nMucho más texto <script>x</script>\",\"open\":true}"))
        .andExpect(status().isOk());
    assertThat(auditadas()).isEqualTo(1);
    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'lessons' AND entity_id = ? AND action = 'UPDATE'",
            String.class,
            leccion);
    assertThat(cambios)
        .contains("content_length")
        .contains("\"open\"")
        .doesNotContain("script")
        .doesNotContain("\"title\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-104` — cambiar open en los dos sentidos se audita; cambiar la duración de una activa cambia la del módulo y la del curso")
  void openYDuracion() throws Exception {
    jdbc.update("UPDATE lessons SET status = 'ACTIVO' WHERE id = ?", leccion);
    mvc.perform(corregir(leccion, "{\"open\":true}")).andExpect(jsonPath("$.open").value(true));
    mvc.perform(corregir(leccion, "{\"open\":false}")).andExpect(jsonPath("$.open").value(false));
    assertThat(auditadas()).isEqualTo(2);
    mvc.perform(corregir(leccion, "{\"durationSeconds\":45}")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.modules[0].durationSeconds").value(45))
        .andExpect(jsonPath("$.totalDurationSeconds").value(45));
  }

  @Test
  @DisplayName(
      "`CA-AC-215` — vaciar el contenido de la única lección activa de un módulo ofrecido deja lección"
          + " y módulo ACTIVOS y no ofrecibles, y el curso por su último motivo; reponerlo los devuelve")
  void vaciarSacaDeLaOferta() throws Exception {
    jdbc.update("UPDATE lessons SET status = 'ACTIVO' WHERE id = ?", leccion);
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.modules[0].offerable").value(true))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El curso no tiene ninguna membresía ni ningún servicio que lo abra."));

    mvc.perform(corregir(leccion, "{\"content\":null}"))
        .andExpect(jsonPath("$.status").value("ACTIVO"));
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.modules[0].status").value("ACTIVO"))
        .andExpect(jsonPath("$.modules[0].offerable").value(false))
        // El quinto motivo solo se ve con una membresía delante (RF-AC-020): hasta
        // entonces el curso dice el cuarto, y el módulo es el que enseña el hueco.
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El curso no tiene ninguna membresía ni ningún servicio que lo abra."));

    mvc.perform(corregir(leccion, "{\"content\":\"# De vuelta\"}")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.modules[0].offerable").value(true))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El curso no tiene ninguna membresía ni ningún servicio que lo abra."));
  }

  private MockHttpServletRequestBuilder corregir(UUID leccion, String cuerpo) {
    return patch("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons/" + leccion)
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private int auditadas() {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity = 'lessons' AND entity_id = ? AND action = 'UPDATE'",
            Integer.class,
            leccion);
    return filas == null ? 0 : filas;
  }
}
