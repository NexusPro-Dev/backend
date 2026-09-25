package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccion;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.modulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** El cambio de estado de la lección (`RF-AC-030`): `CA-AC-110` a `CA-AC-113`. */
@AutoConfigureMockMvc
class LessonStatusIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID curso;
  private UUID modulo;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0, "PRINCIPIANTE", "C", "L", "ACTIVO");
    modulo = modulo(jdbc, curso, "Fundamentos", 0, "ACTIVO");
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-110` — activa una lección con contenido de los dos tipos; sin contenido, 409")
  void activar() throws Exception {
    UUID texto = leccion(jdbc, modulo, "Texto", "TEXTO", "# x", 5, 0, "INACTIVO");
    UUID video = leccion(jdbc, modulo, "Video", "VIDEO", "https://v.io/1", 5, 1, "INACTIVO");
    UUID vacia = leccion(jdbc, modulo, "Vacía", "TEXTO", null, 5, 2, "INACTIVO");
    mvc.perform(estado(texto, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"));
    mvc.perform(estado(video, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"));
    mvc.perform(estado(vacia, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName(
      "`CA-AC-111` — desactivar la última activa de un módulo activo deja el módulo ACTIVO y no"
          + " ofrecible, y la duración del módulo y del curso bajan")
  void desactivarLaUltima() throws Exception {
    UUID unica = leccion(jdbc, modulo, "Única", "TEXTO", "# x", 30, 0, "ACTIVO");
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.modules[0].offerable").value(true))
        .andExpect(jsonPath("$.totalDurationSeconds").value(30));
    mvc.perform(estado(unica, "INACTIVO")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.modules[0].status").value("ACTIVO"))
        .andExpect(jsonPath("$.modules[0].offerable").value(false))
        .andExpect(jsonPath("$.modules[0].durationSeconds").value(0))
        .andExpect(jsonPath("$.totalDurationSeconds").value(0));
  }

  @Test
  @DisplayName(
      "`CA-AC-112` — mismo estado: 200 sin auditar; cambio real: fila UPDATE con antes y después")
  void sinCambioYConCambio() throws Exception {
    UUID l = leccion(jdbc, modulo, "L", "TEXTO", "# x", 5, 0, "INACTIVO");
    mvc.perform(estado(l, "INACTIVO")).andExpect(status().isOk());
    assertThat(auditadas(l)).isZero();
    mvc.perform(estado(l, "ACTIVO")).andExpect(status().isOk());
    assertThat(auditadas(l)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT changes::text FROM audit_change_log WHERE entity = 'lessons' AND entity_id = ?",
                String.class,
                l))
        .contains("\"before\": \"INACTIVO\"")
        .contains("\"after\": \"ACTIVO\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-113` — retirada, inexistente o de otro módulo o curso: 404; status fuera de dominio: 400")
  void noExiste() throws Exception {
    UUID retirada = leccion(jdbc, modulo, "R", "TEXTO", "# x", 5, 0, "INACTIVO");
    CourseTestSupport.retirarLeccion(jdbc, retirada);
    mvc.perform(estado(retirada, "ACTIVO")).andExpect(status().isNotFound());
    mvc.perform(estado(UUID.randomUUID(), "ACTIVO")).andExpect(status().isNotFound());
    UUID viva = leccion(jdbc, modulo, "V", "TEXTO", "# x", 5, 1, "INACTIVO");
    UUID otroModulo = modulo(jdbc, curso, "Otro", 1, "INACTIVO");
    mvc.perform(
            patch(
                    "/api/v1/courses/"
                        + curso
                        + "/modules/"
                        + otroModulo
                        + "/lessons/"
                        + viva
                        + "/status")
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVO\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(estado(viva, "PUBLICADA")).andExpect(status().isBadRequest());
  }

  private MockHttpServletRequestBuilder estado(UUID leccion, String estado) {
    return patch(
            "/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons/" + leccion + "/status")
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"" + estado + "\"}");
  }

  private int auditadas(UUID id) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity = 'lessons' AND entity_id = ? AND action = 'UPDATE'",
            Integer.class,
            id);
    return filas == null ? 0 : filas;
  }
}
