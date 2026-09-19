package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccion;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccionActiva;
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
 * El retiro del módulo (`RF-AC-025`): `CA-AC-114` a `CA-AC-117`; la carrera en {@link
 * CourseTreeConcurrencyIT}.
 */
@AutoConfigureMockMvc
class CourseModuleDeletionIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID instructor;
  private UUID curso;
  private UUID modulo;
  private UUID viva;
  private UUID yaRetirada;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0, "PRINCIPIANTE", "C", "L", "ACTIVO");
    modulo = modulo(jdbc, curso, "Único", 0, "ACTIVO");
    viva = leccionActiva(jdbc, modulo, "Viva", 10);
    yaRetirada = leccion(jdbc, modulo, "Ya retirada", "TEXTO", "# y", 5, 1, "INACTIVO");
    jdbc.update(
        "UPDATE lessons SET deleted_at = now() - interval '1 day' WHERE id = ?", yaRetirada);
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-114` y `CA-AC-116` — retira el módulo conservando status; sus lecciones vivas quedan"
          + " retiradas con el mismo instante y una fila cada una; las ya retiradas no se tocan")
  void retiraConArrastre() throws Exception {
    UUID actor = UUID.randomUUID();
    mvc.perform(retiro(modulo, "{\"reason\":\"Se reestructura.\"}", actor))
        .andExpect(status().isNoContent());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT status, deleted_at::text AS retirado FROM course_modules WHERE id = ?", modulo);
    assertThat(fila.get("status")).isEqualTo("ACTIVO");
    String instante = (String) fila.get("retirado");
    assertThat(instante).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at::text FROM lessons WHERE id = ?", String.class, viva))
        .isEqualTo(instante);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at::text FROM lessons WHERE id = ?", String.class, yaRetirada))
        .isNotEqualTo(instante);

    Map<String, Object> baja =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, reason, snapshot::text AS snapshot FROM audit_deletion_log"
                + " WHERE entity = 'course_modules' AND entity_id = ?",
            modulo);
    assertThat(baja.get("actor")).isEqualTo(actor.toString());
    assertThat(baja.get("reason")).isEqualTo("Se reestructura.");
    assertThat((String) baja.get("snapshot"))
        .contains(viva.toString())
        .doesNotContain(yaRetirada.toString())
        .doesNotContain("deleted_at");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity = 'lessons' AND entity_id = ? AND reason = 'Se reestructura.'",
                Integer.class,
                viva))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity_id = ?",
                Integer.class,
                yaRetirada))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-115` — 400 un motivo inválido sin consultar nada; 404 inexistente y de otro curso; 409 ya retirado")
  void motivoYPertenencia() throws Exception {
    estadisticas.clear();
    mvc.perform(retiro(modulo, "{\"reason\":\"  \"}", UUID.randomUUID()))
        .andExpect(status().isBadRequest());
    assertThat(estadisticas.getPrepareStatementCount()).isZero();

    mvc.perform(retiro(UUID.randomUUID(), "{\"reason\":\"Nada.\"}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
    UUID otroCurso = curso(jdbc, "Otro", instructor, 1);
    mvc.perform(
            post("/api/v1/courses/" + otroCurso + "/modules/" + modulo + "/deletion")
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ajeno.\"}"))
        .andExpect(status().isNotFound());

    mvc.perform(retiro(modulo, "{\"reason\":\"Primera.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());
    mvc.perform(retiro(modulo, "{\"reason\":\"Segunda.\"}", UUID.randomUUID()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName(
      "`CA-AC-117` — retirar el último módulo activo deja el curso ACTIVO y no ofrecible, el detalle"
          + " enseña módulo y lecciones marcados, y el título queda libre")
  void elCursoNoCambia() throws Exception {
    mvc.perform(retiro(modulo, "{\"reason\":\"Se reestructura.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.modules[0].deleted").value(true))
        .andExpect(jsonPath("$.modules[0].lessons[0].deleted").value(true))
        .andExpect(jsonPath("$.totalDurationMinutes").value(0));
    mvc.perform(
            post("/api/v1/courses/" + curso + "/modules")
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Único\",\"displayOrder\":0}"))
        .andExpect(status().isCreated());
  }

  private MockHttpServletRequestBuilder retiro(UUID modulo, String cuerpo, UUID actor) {
    return post("/api/v1/courses/" + curso + "/modules/" + modulo + "/deletion")
        .with(con(actor, "courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }
}
