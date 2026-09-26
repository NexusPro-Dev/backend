package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccion;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccionActiva;
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

/**
 * El cambio de estado del módulo (`RF-AC-024`): `CA-AC-105` a `CA-AC-109`. `CA-AC-109` es la
 * habilitación de `CA-AC-064` de `RF-AC-012`, que vive en {@link CourseStatusIT}.
 */
@AutoConfigureMockMvc
class CourseModuleStatusIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID curso;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0, "PRINCIPIANTE", "C", "L", "ACTIVO");
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-105` — activa un módulo con una lección activa (offerable true); con lecciones solo"
          + " inactivas o solo retiradas, 409")
  void activar() throws Exception {
    UUID conActiva = modulo(jdbc, curso, "Con activa", 0, "INACTIVO");
    leccionActiva(jdbc, conActiva, "Lección", 10);
    mvc.perform(estado(conActiva, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(true))
        .andExpect(jsonPath("$.offerableReason").doesNotExist());

    UUID soloInactivas = modulo(jdbc, curso, "Solo inactivas", 1, "INACTIVO");
    leccion(jdbc, soloInactivas, "Inactiva", "TEXTO", "# x", 5, 0, "INACTIVO");
    mvc.perform(estado(soloInactivas, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    UUID soloRetiradas = modulo(jdbc, curso, "Solo retiradas", 2, "INACTIVO");
    CourseTestSupport.retirarLeccion(jdbc, leccionActiva(jdbc, soloRetiradas, "Retirada", 5));
    mvc.perform(estado(soloRetiradas, "ACTIVO")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "`CA-AC-106` — desactivar no exige nada ni toca las lecciones, y desactivar el último módulo"
          + " activo de un curso activo deja el curso ACTIVO con offerable false por el último motivo")
  void desactivarElUltimo() throws Exception {
    UUID unico = modulo(jdbc, curso, "Único", 0, "ACTIVO");
    UUID leccion = leccionActiva(jdbc, unico, "Lección", 10);
    mvc.perform(estado(unico, "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.lessons[0].status").value("ACTIVO"));
    assertThat(
            jdbc.queryForObject("SELECT status FROM lessons WHERE id = ?", String.class, leccion))
        .isEqualTo("ACTIVO");
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason")
                .value(
                    "El curso no tiene ningún módulo activo con al menos una lección activa con contenido."));
  }

  @Test
  @DisplayName(
      "`CA-AC-107` — mismo estado: 200 sin avanzar updatedAt ni auditar; cambio real: fila UPDATE")
  void sinCambioYConCambio() throws Exception {
    UUID inactivo = modulo(jdbc, curso, "Inactivo", 0, "INACTIVO");
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM course_modules WHERE id = ?", String.class, inactivo);
    mvc.perform(estado(inactivo, "INACTIVO")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at::text FROM course_modules WHERE id = ?", String.class, inactivo))
        .isEqualTo(antes);
    assertThat(auditadas(inactivo)).isZero();

    leccionActiva(jdbc, inactivo, "Lección", 3);
    mvc.perform(estado(inactivo, "ACTIVO")).andExpect(status().isOk());
    assertThat(auditadas(inactivo)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-108` — un módulo retirado, inexistente o de otro curso responde 404; un status fuera de dominio, 400")
  void noExisteOFueraDeDominio() throws Exception {
    UUID retirado = modulo(jdbc, curso, "Retirado", 0, "INACTIVO");
    CourseTestSupport.retirarModulo(jdbc, retirado);
    mvc.perform(estado(retirado, "INACTIVO")).andExpect(status().isNotFound());
    mvc.perform(estado(UUID.randomUUID(), "INACTIVO")).andExpect(status().isNotFound());

    UUID otroCurso = curso(jdbc, "Otro", instructor, 1);
    UUID ajeno = modulo(jdbc, otroCurso, "Ajeno", 0, "INACTIVO");
    mvc.perform(estado(ajeno, "INACTIVO")).andExpect(status().isNotFound());

    UUID vivo = modulo(jdbc, curso, "Vivo", 1, "INACTIVO");
    mvc.perform(estado(vivo, "PUBLICADO")).andExpect(status().isBadRequest());
  }

  private MockHttpServletRequestBuilder estado(UUID modulo, String estado) {
    return patch("/api/v1/courses/" + curso + "/modules/" + modulo + "/status")
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"" + estado + "\"}");
  }

  private int auditadas(UUID id) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity = 'course_modules' AND entity_id = ? AND action = 'UPDATE'",
            Integer.class,
            id);
    return filas == null ? 0 : filas;
  }
}
