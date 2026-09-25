package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
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
 * El cambio de estado del curso (`RF-AC-012` · `T-05`): `CA-AC-064` a `CA-AC-069`. `CA-AC-064`
 * —activar con un módulo activo— quedó bloqueado hasta `RF-AC-024` y se habilitó con el bloque 3
 * (`CA-AC-109`).
 */
@AutoConfigureMockMvc
class CourseStatusIT extends IntegrationTestBase {

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
      "`CA-AC-064` y `CA-AC-109` — un curso con descripciones y un módulo activo se activa y devuelve"
          + " el detalle ACTIVO con updatedAt avanzado")
  void activarConUnModuloActivo() throws Exception {
    UUID listo = curso(jdbc, "Listo", instructor, 0, "PRINCIPIANTE", "C", "L", "INACTIVO");
    UUID modulo = CourseTestSupport.modulo(jdbc, listo, "Módulo", 0, "ACTIVO");
    CourseTestSupport.leccionActiva(jdbc, modulo, "Lección", 5);
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM courses WHERE id = ?", String.class, listo);
    mvc.perform(estado(listo, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El curso no tiene ninguna membresía ni ningún servicio que lo abra."));
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at::text FROM courses WHERE id = ?", String.class, listo))
        .isNotEqualTo(antes);
    assertThat(auditadas(listo)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-065` — activar sin descripción corta, sin larga y sin módulo activo responde 409 con"
          + " TODOS los motivos; con descripciones, solo el del módulo")
  void activarSinCondiciones() throws Exception {
    UUID vacio = curso(jdbc, "Vacío", instructor, 0);
    mvc.perform(estado(vacio, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors", hasSize(3)))
        .andExpect(jsonPath("$.errors[*].code").value(hasItems("EX-002", "EX-003", "EX-004")));

    UUID conDescripciones =
        curso(jdbc, "Con descripciones", instructor, 1, "PRINCIPIANTE", "C", "L", "INACTIVO");
    mvc.perform(estado(conDescripciones, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM courses WHERE id = ?", String.class, conDescripciones))
        .isEqualTo("INACTIVO");
  }

  @Test
  @DisplayName(
      "`CA-AC-066` y `CA-AC-067` — un curso ACTIVO sin membresías se devuelve «sin membresías»;"
          + " desactivar no exige nada, y vaciar una descripción después no cambia el estado")
  void activoSinMembresiasYDesactivar() throws Exception {
    // Activo por siembra: activarlo por la API exige un módulo, bloqueado hasta RF-AC-024.
    UUID activo = curso(jdbc, "Activo", instructor, 0, "PRINCIPIANTE", "C", "L", "ACTIVO");
    mvc.perform(estado(activo, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El curso no tiene ninguna membresía ni ningún servicio que lo abra."));

    jdbc.update("UPDATE courses SET short_description = NULL WHERE id = ?", activo);
    assertThat(jdbc.queryForObject("SELECT status FROM courses WHERE id = ?", String.class, activo))
        .isEqualTo("ACTIVO");

    mvc.perform(estado(activo, "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.offerableReason").value("El curso está inactivo."));
  }

  @Test
  @DisplayName(
      "`CA-AC-068` — pedir el estado que ya tiene responde 200 sin avanzar updatedAt ni auditar; un"
          + " cambio real deja la fila UPDATE con antes y después")
  void sinCambioYConCambio() throws Exception {
    UUID activo = curso(jdbc, "Activo", instructor, 0, "PRINCIPIANTE", "C", "L", "ACTIVO");
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM courses WHERE id = ?", String.class, activo);
    mvc.perform(estado(activo, "ACTIVO")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at::text FROM courses WHERE id = ?", String.class, activo))
        .isEqualTo(antes);
    assertThat(auditadas(activo)).isZero();

    mvc.perform(estado(activo, "INACTIVO")).andExpect(status().isOk());
    assertThat(auditadas(activo)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT changes::text FROM audit_change_log WHERE entity = 'courses' AND entity_id"
                    + " = ? AND action = 'UPDATE'",
                String.class,
                activo))
        .contains("\"before\": \"ACTIVO\"")
        .contains("\"after\": \"INACTIVO\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-069` — un curso retirado responde 404; un status fuera de dominio o ausente, 400")
  void retiradoYEstadoInvalido() throws Exception {
    UUID retirado = curso(jdbc, "Retirado", instructor, 0);
    CourseTestSupport.retirar(jdbc, retirado);
    mvc.perform(estado(retirado, "INACTIVO")).andExpect(status().isNotFound());

    UUID vivo = curso(jdbc, "Vivo", instructor, 1);
    mvc.perform(estado(vivo, "PUBLICADO")).andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/v1/courses/" + vivo + "/status")
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("sin courses:update responde 403 aunque el actor porte courses:read")
  void sinPermiso() throws Exception {
    UUID vivo = curso(jdbc, "Vivo", instructor, 1);
    mvc.perform(
            patch("/api/v1/courses/" + vivo + "/status")
                .with(con("courses:read", "course-categories:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVO\"}"))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder estado(UUID id, String estado) {
    return patch("/api/v1/courses/" + id + "/status")
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"" + estado + "\"}");
  }

  private int auditadas(UUID id) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity = 'courses' AND entity_id = ? AND"
                + " action = 'UPDATE'",
            Integer.class,
            id);
    return filas == null ? 0 : filas;
  }
}
