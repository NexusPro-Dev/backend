package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * El detalle del curso (`RF-AC-010` · `T-05`): `CA-AC-051` a `CA-AC-056`.
 *
 * <p><b>La que define el requerimiento es `CA-AC-052`</b>: el orden de los motivos con lo que hoy
 * existe. Las listas y el árbol viajan vacíos hasta sus requerimientos, y esta suite fija la forma.
 */
@AutoConfigureMockMvc
class CourseDetailIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private ObjectMapper json;

  private Statistics estadisticas;
  private UUID instructor;
  private UUID inactivo;
  private UUID activo;
  private UUID retirado;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    inactivo = curso(jdbc, "Inactivo", instructor, 0, "PRINCIPIANTE", "Corta", "Larga", "INACTIVO");
    activo = curso(jdbc, "Activo", instructor, 1, "INTERMEDIO", "Corta", "Larga", "ACTIVO");
    retirado = curso(jdbc, "Retirado", instructor, 2);
    CourseTestSupport.retirar(jdbc, retirado);
    jdbc.update(
        """
        INSERT INTO audit_deletion_log (id, occurred_at, actor_id, module, entity, entity_id,
                                        deletion_type, reason, snapshot)
        VALUES (gen_random_uuid(), now(), NULL, 'AC', 'courses', ?, 'LOGICAL',
                'Ya no se dicta.', '{}'::jsonb)
        """,
        retirado);
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-051` — el curso con sus campos, el instructor resuelto, coverImageUrl nula, las cuatro"
          + " listas y el árbol vacíos, y cero minutos y lecciones")
  void laForma() throws Exception {
    mvc.perform(detalle(inactivo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Inactivo"))
        .andExpect(jsonPath("$.instructor.username").exists())
        .andExpect(jsonPath("$.instructor.fullName").value("Juan Pérez"))
        .andExpect(jsonPath("$.instructor.email").doesNotExist())
        .andExpect(jsonPath("$.difficulty").value("PRINCIPIANTE"))
        .andExpect(jsonPath("$.longDescription").value("Larga"))
        .andExpect(jsonPath("$.introVideoUrl").value(nullValue()))
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.categories", hasSize(0)))
        .andExpect(jsonPath("$.recommendedCourses", hasSize(0)))
        .andExpect(jsonPath("$.memberships", hasSize(0)))
        .andExpect(jsonPath("$.modules", hasSize(0)))
        .andExpect(jsonPath("$.totalDurationMinutes").value(0))
        .andExpect(jsonPath("$.lessonCount").value(0))
        .andExpect(jsonPath("$.deletedAt").doesNotExist())
        .andExpect(jsonPath("$.deletionReason").doesNotExist());
  }

  @Test
  @DisplayName(
      "`CA-AC-052` — offerable y offerableReason viajan siempre: un INACTIVO dice «inactivo» y un"
          + " ACTIVO con descripciones y sin membresías dice «sin membresías»")
  void elOrdenDeLosMotivos() throws Exception {
    mvc.perform(detalle(inactivo))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value("El curso está inactivo."));
    mvc.perform(detalle(activo))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El curso no tiene ninguna membresía que lo abra."));

    // Y un ACTIVO al que se le vació una descripción dice «sin descripción» (18-09-2026).
    jdbc.update("UPDATE courses SET long_description = NULL WHERE id = ?", activo);
    mvc.perform(detalle(activo))
        .andExpect(
            jsonPath("$.offerableReason").value("El curso no tiene descripción corta o larga."));
  }

  @Test
  @DisplayName(
      "`CA-AC-053` — el retirado se devuelve con deletedAt, deletionReason y «retirado»; el"
          + " inexistente 404; el mal formado 400")
  void retiradoInexistenteYMalFormado() throws Exception {
    mvc.perform(detalle(retirado))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.deletionReason").value("Ya no se dicta."))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value("El curso está retirado."));

    mvc.perform(detalle(UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un curso con ese identificador."));

    mvc.perform(get("/api/v1/courses/no-es-uuid").with(con("courses:read")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`CA-AC-054` — la lectura cuesta TRES sentencias sin módulos —el curso, sus categorías y sus módulos—, UNA MÁS"
          + " con módulos (las lecciones) y UNA MÁS con el motivo de retiro")
  void sentencias() throws Exception {
    estadisticas.clear();
    mvc.perform(detalle(activo)).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);

    estadisticas.clear();
    mvc.perform(detalle(retirado)).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "`CA-AC-055` — sin courses:read responde 403 aunque el actor porte course-categories:read")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/courses/" + activo)
                .with(con("course-categories:read", "courses:learn", "courses:teach")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("`CA-AC-056` — el alta devuelve la misma forma que el detalle, campo a campo")
  void laMismaForma() throws Exception {
    String creado =
        mvc.perform(
                post("/api/v1/courses")
                    .with(con("courses:create"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CourseTestSupport.cuerpo("Nuevo", instructor, "AVANZADO", 5)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode alta = json.readTree(creado);
    JsonNode detalle =
        json.readTree(
            mvc.perform(detalle(UUID.fromString(alta.get("id").asText())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(detalle).isEqualTo(alta);
  }

  @Test
  @DisplayName(
      "caso límite — un retirado sin registro de eliminación responde 200 con el motivo nulo")
  void retiradoSinRegistro() throws Exception {
    jdbc.update("DELETE FROM audit_deletion_log WHERE entity_id = ?", retirado);
    mvc.perform(detalle(retirado))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.deletionReason").doesNotExist());
  }

  private MockHttpServletRequestBuilder detalle(UUID id) {
    return get("/api/v1/courses/" + id).with(con("courses:read"));
  }
}
