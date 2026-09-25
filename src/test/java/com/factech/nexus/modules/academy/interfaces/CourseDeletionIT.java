package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
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
 * El retiro del curso (`RF-AC-013` · `T-07`): `CA-AC-070` a `CA-AC-073` y la parte de `CA-AC-075`
 * que no es del aula, y <b>el arrastre</b> (`CA-AC-074`, `CA-AC-083`, `CA-AC-092`) desde el bloque
 * 3: módulos y lecciones vivos retirados con el mismo instante y una fila cada uno.
 */
@AutoConfigureMockMvc
class CourseDeletionIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID instructor;
  private UUID curso;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0, "PRINCIPIANTE", "C", "L", "ACTIVO");
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-070` y `CA-AC-073` — retira con 204 conservando status e instructor_id, y deja la fila"
          + " LOGICAL con motivo, actor e instantánea con las cuatro listas de identificadores")
  void retiraYAudita() throws Exception {
    UUID actor = UUID.randomUUID();
    mvc.perform(retiro(curso, "{\"reason\":\"Ya no se dicta.\"}", actor))
        .andExpect(status().isNoContent());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT status, instructor_id::text AS instructor, deleted_at IS NOT NULL AS retirado"
                + " FROM courses WHERE id = ?",
            curso);
    assertThat(fila.get("status")).isEqualTo("ACTIVO");
    assertThat(fila.get("instructor")).isEqualTo(instructor.toString());
    assertThat(fila.get("retirado")).isEqualTo(true);

    Map<String, Object> baja =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE module = 'AC' AND entity = 'courses' AND"
                + " entity_id = ?",
            curso);
    assertThat(baja.get("actor")).isEqualTo(actor.toString());
    assertThat(baja.get("deletion_type")).isEqualTo("LOGICAL");
    assertThat(baja.get("reason")).isEqualTo("Ya no se dicta.");
    assertThat((String) baja.get("snapshot"))
        .contains("\"title\": \"Velas\"")
        .contains("\"category_ids\": []")
        .contains("\"membership_ids\": []")
        .contains("\"product_ids\": []")
        .contains("\"recommended_course_ids\": []")
        .contains("\"module_ids\": []")
        .doesNotContain("deleted_at");
  }

  @Test
  @DisplayName("`CA-AC-071` — motivo ausente, vacío, de espacios o largo: 400 sin consultar nada")
  void motivoInvalido() throws Exception {
    for (String cuerpo :
        new String[] {
          "{}",
          "{\"reason\":\"\"}",
          "{\"reason\":\"   \"}",
          "{\"reason\":\"" + "x".repeat(501) + "\"}"
        }) {
      estadisticas.clear();
      mvc.perform(retiro(curso, cuerpo, UUID.randomUUID())).andExpect(status().isBadRequest());
      assertThat(estadisticas.getPrepareStatementCount()).isZero();
    }
    mvc.perform(
            post("/api/v1/courses/" + curso + "/deletion")
                .with(con("courses:delete"))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM courses WHERE id = ?", Boolean.class, curso))
        .isTrue();
  }

  @Test
  @DisplayName("`CA-AC-072` — 404 al inexistente y 409 al ya retirado, distinguiéndolos")
  void inexistenteYRetirado() throws Exception {
    mvc.perform(retiro(UUID.randomUUID(), "{\"reason\":\"Nada.\"}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un curso con ese identificador."));
    mvc.perform(retiro(curso, "{\"reason\":\"Primera.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());
    mvc.perform(retiro(curso, "{\"reason\":\"Segunda.\"}", UUID.randomUUID()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName(
      "`CA-AC-075` — el retirado sale del listado salvo includeDeleted, su detalle trae el motivo, y"
          + " su título puede reutilizarse")
  void despuesDelRetiro() throws Exception {
    mvc.perform(retiro(curso, "{\"reason\":\"Ya no se dicta.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/courses").with(con("courses:read")))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/v1/courses?includeDeleted=true").with(con("courses:read")))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletionReason").value("Ya no se dicta."));

    mvc.perform(
            post("/api/v1/courses")
                .with(con("courses:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CourseTestSupport.cuerpo("Velas", instructor, "PRINCIPIANTE", 0)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "el retiro sin árbol cuesta hasta ocho sentencias: el curso bloqueado, sus categorías, sus"
          + " membresías, sus servicios, los módulos vivos, el UPDATE, la baja y su secuencia")
  void sentencias() throws Exception {
    estadisticas.clear();
    mvc.perform(retiro(curso, "{\"reason\":\"Ya no se dicta.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());
    assertThat(estadisticas.getPrepareStatementCount()).isLessThanOrEqualTo(8);
  }

  @Test
  @DisplayName(
      "`CA-AC-074`, `CA-AC-083` y `CA-AC-092` — retirar el curso retira sus módulos y lecciones vivos"
          + " con el mismo instante y una fila de auditoría cada uno, con el mismo motivo; los ya"
          + " retirados no se tocan; la instantánea del curso lleva los module_ids")
  void arrastre() throws Exception {
    UUID vivo = CourseTestSupport.modulo(jdbc, curso, "Vivo", 0, "ACTIVO");
    UUID leccionViva = CourseTestSupport.leccionActiva(jdbc, vivo, "Viva", 5);
    UUID leccionYaRetirada = CourseTestSupport.leccionActiva(jdbc, vivo, "Ya retirada", 5);
    jdbc.update(
        "UPDATE lessons SET deleted_at = now() - interval '1 day' WHERE id = ?", leccionYaRetirada);
    UUID moduloYaRetirado = CourseTestSupport.modulo(jdbc, curso, "Ya retirado", 1, "INACTIVO");
    jdbc.update(
        "UPDATE course_modules SET deleted_at = now() - interval '1 day' WHERE id = ?",
        moduloYaRetirado);

    mvc.perform(retiro(curso, "{\"reason\":\"Se cierra.\"}", UUID.randomUUID()))
        .andExpect(status().isNoContent());

    String instante =
        jdbc.queryForObject(
            "SELECT deleted_at::text FROM courses WHERE id = ?", String.class, curso);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at::text FROM course_modules WHERE id = ?", String.class, vivo))
        .isEqualTo(instante);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at::text FROM lessons WHERE id = ?", String.class, leccionViva))
        .isEqualTo(instante);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at::text FROM course_modules WHERE id = ?",
                String.class,
                moduloYaRetirado))
        .isNotEqualTo(instante);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at::text FROM lessons WHERE id = ?",
                String.class,
                leccionYaRetirada))
        .isNotEqualTo(instante);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE reason = 'Se cierra.' AND entity_id IN (?, ?, ?)",
                Integer.class,
                curso,
                vivo,
                leccionViva))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity_id IN (?, ?)",
                Integer.class,
                moduloYaRetirado,
                leccionYaRetirada))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT snapshot::text FROM audit_deletion_log WHERE entity = 'courses' AND entity_id = ?",
                String.class,
                curso))
        .contains(vivo.toString())
        .doesNotContain(moduloYaRetirado.toString());
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.modules[0].deleted").value(true))
        .andExpect(jsonPath("$.modules[0].lessons[0].deleted").value(true));
  }

  @Test
  @DisplayName("sin courses:delete responde 403 aunque el actor porte courses:update")
  void sinPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/courses/" + curso + "/deletion")
                .with(con("courses:update", "course-categories:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Nada.\"}"))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder retiro(UUID id, String cuerpo, UUID actor) {
    return post("/api/v1/courses/" + id + "/deletion")
        .with(con(actor, "courses:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }
}
