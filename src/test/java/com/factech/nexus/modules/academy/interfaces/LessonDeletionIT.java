package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccion;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.modulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * El retiro de la lección (`RF-AC-031`): `CA-AC-119` a `CA-AC-122`; la carrera en {@link
 * CourseTreeConcurrencyIT}.
 */
@AutoConfigureMockMvc
class LessonDeletionIT extends IntegrationTestBase {

  private static final String MARKDOWN = "# Largo\n\n" + "párrafo ".repeat(2000);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
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
    leccion = leccion(jdbc, modulo, "Única", "TEXTO", MARKDOWN, 30, 0, "ACTIVO");
    jdbc.update("UPDATE lessons SET open = true WHERE id = ?", leccion);
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-119` y `CA-AC-121` — retira con 204 conservando status, content y open, y la baja lleva el contenido ENTERO")
  void retiraYAuditaConElContenido() throws Exception {
    UUID actor = UUID.randomUUID();
    mvc.perform(retiro(leccion, "{\"reason\":\"Obsoleta.\"}", actor))
        .andExpect(status().isNoContent());
    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT status, content, open, deleted_at IS NOT NULL AS retirada FROM lessons WHERE id = ?",
            leccion);
    assertThat(fila.get("status")).isEqualTo("ACTIVO");
    assertThat(fila.get("content")).isEqualTo(MARKDOWN);
    assertThat(fila.get("open")).isEqualTo(true);
    assertThat(fila.get("retirada")).isEqualTo(true);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM course_modules WHERE id = ?", String.class, modulo))
        .isEqualTo("ACTIVO");

    Map<String, Object> baja =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, reason, snapshot::text AS snapshot FROM audit_deletion_log WHERE entity = 'lessons' AND entity_id = ?",
            leccion);
    assertThat(baja.get("actor")).isEqualTo(actor.toString());
    assertThat(baja.get("reason")).isEqualTo("Obsoleta.");
    assertThat((String) baja.get("snapshot"))
        .contains("párrafo párrafo")
        .contains("\"content_length\"")
        .doesNotContain("deleted_at");
  }

  @Test
  @DisplayName(
      "`CA-AC-120` — 400 un motivo inválido sin consultar; 404 inexistente y de otro módulo o curso; 409 ya retirada")
  void motivoYPertenencia() throws Exception {
    estadisticas.clear();
    mvc.perform(retiro(leccion, "{}", UUID.randomUUID())).andExpect(status().isBadRequest());
    assertThat(estadisticas.getPrepareStatementCount()).isZero();
    mvc.perform(retiro(UUID.randomUUID(), "{\"reason\":\"Nada.\"}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
    UUID otroModulo = modulo(jdbc, curso, "Otro", 1, "INACTIVO");
    mvc.perform(
            post("/api/v1/courses/"
                    + curso
                    + "/modules/"
                    + otroModulo
                    + "/lessons/"
                    + leccion
                    + "/deletion")
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ajena.\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(retiro(leccion, "{\"reason\":\"Primera.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());
    mvc.perform(retiro(leccion, "{\"reason\":\"Segunda.\"}", UUID.randomUUID()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName(
      "`CA-AC-122` — retirar la última activa deja el módulo ACTIVO y no ofrecible, las duraciones bajan, el detalle la enseña marcada y el título queda libre")
  void laUltimaActiva() throws Exception {
    mvc.perform(retiro(leccion, "{\"reason\":\"Obsoleta.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.modules[0].status").value("ACTIVO"))
        .andExpect(jsonPath("$.modules[0].offerable").value(false))
        .andExpect(jsonPath("$.modules[0].durationSeconds").value(0))
        .andExpect(jsonPath("$.modules[0].lessons[0].deleted").value(true))
        .andExpect(jsonPath("$.totalDurationSeconds").value(0));
    mvc.perform(
            post("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons")
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"TEXTO\",\"title\":\"Única\",\"durationSeconds\":1,\"displayOrder\":0}"))
        .andExpect(status().isCreated());
  }

  private MockHttpServletRequestBuilder retiro(UUID leccion, String cuerpo, UUID actor) {
    return post("/api/v1/courses/"
            + curso
            + "/modules/"
            + modulo
            + "/lessons/"
            + leccion
            + "/deletion")
        .with(con(actor, "courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }
}
