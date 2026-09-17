package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.categoria;
import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.cuerpo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * El retiro de una categoría (`RF-AC-005` · `T-07`): `CA-AC-028` a `CA-AC-033`, salvo la carrera,
 * que vive en {@link CourseCategoryConcurrencyIT}.
 *
 * <p>La que define el requerimiento es <b>`CA-AC-032`</b>, la del curso que se sigue ofreciendo, y
 * es trivial hasta `RF-AC-016` y `RF-AC-033`: hoy fija que el listado excluye la retirada y que el
 * detalle trae el motivo.
 */
@AutoConfigureMockMvc
class CourseCategoryDeletionIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID trading;

  @BeforeEach
  void sembrar() {
    CourseCategoryTestSupport.limpiar(jdbc);
    trading = categoria(jdbc, "Trading", 0, "1E88E5", "chart-line", "Lo básico.");
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void limpiar() {
    CourseCategoryTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-028` y `CA-AC-031` — 204; la fila conserva todo salvo deleted_at; LOGICAL con motivo,"
          + " actor e instantánea con course_ids")
  void retiraYAudita() throws Exception {
    UUID actor = UUID.randomUUID();
    mvc.perform(retirar(trading, "{\"reason\":\"Ya no se usa.\"}", actor))
        .andExpect(status().isNoContent());

    var fila =
        jdbc.queryForMap(
            "SELECT name, color, icon, display_order, deleted_at FROM course_categories WHERE id = ?",
            trading);
    assertThat(fila.get("name")).isEqualTo("Trading");
    assertThat(fila.get("color")).isEqualTo("1E88E5");
    assertThat(fila.get("display_order")).isEqualTo(0);
    assertThat(fila.get("deleted_at")).isNotNull();

    var registro =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE module = 'AC' AND entity = 'course_categories'"
                + " AND entity_id = ?",
            trading);
    assertThat(registro.get("actor")).isEqualTo(actor.toString());
    assertThat(registro.get("deletion_type")).isEqualTo("LOGICAL");
    assertThat(registro.get("reason")).isEqualTo("Ya no se usa.");
    assertThat((String) registro.get("snapshot"))
        .contains("\"name\": \"Trading\"")
        .contains("\"course_ids\": []")
        .doesNotContain("deleted_at");
  }

  @Test
  @DisplayName("`CA-AC-029` — motivo ausente, vacío, de espacios o largo: 400 sin consultar nada")
  void motivoInvalidoNoCuestaNada() throws Exception {
    estadisticas.clear();
    mvc.perform(retirar(trading, null)).andExpect(status().isBadRequest());
    mvc.perform(retirar(trading, "{\"reason\":\"\"}")).andExpect(status().isBadRequest());
    mvc.perform(retirar(trading, "{\"reason\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(retirar(trading, "{\"reason\":\"" + "x".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    assertThat(estadisticas.getPrepareStatementCount()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM course_categories WHERE id = ?",
                Boolean.class,
                trading))
        .isTrue();
  }

  @Test
  @DisplayName("`CA-AC-030` — 404 a la inexistente y 409 a la ya retirada, distinguiéndolas")
  void inexistenteYYaRetirada() throws Exception {
    mvc.perform(retirar(UUID.randomUUID(), "{\"reason\":\"Motivo.\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe una categoría con ese identificador."));

    mvc.perform(retirar(trading, "{\"reason\":\"Motivo.\"}")).andExpect(status().isNoContent());
    mvc.perform(retirar(trading, "{\"reason\":\"Otra vez.\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_deletion_log WHERE entity_id = ?",
                Integer.class,
                trading))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-032` — la retirada desaparece del listado salvo includeDeleted, y su detalle trae el"
          + " motivo")
  void desapareceDelListado() throws Exception {
    mvc.perform(retirar(trading, "{\"reason\":\"Ya no se usa.\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/course-categories").with(con("course-categories:read")))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            get("/api/v1/course-categories?includeDeleted=true")
                .with(con("course-categories:read")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].deletedAt").exists());
    mvc.perform(get("/api/v1/course-categories/" + trading).with(con("course-categories:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletionReason").value("Ya no se usa."));
  }

  @Test
  @DisplayName("`CA-AC-033` — el nombre de la retirada puede reutilizarse en un alta")
  void elNombreQuedaLibre() throws Exception {
    mvc.perform(retirar(trading, "{\"reason\":\"Ya no se usa.\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/v1/course-categories")
                .with(con("course-categories:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("trading", "1E88E5", "chart-line", 0)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("sin course-categories:delete responde 403 aunque porte course-categories:update")
  void permiso() throws Exception {
    mvc.perform(
            post("/api/v1/course-categories/" + trading + "/deletion")
                .with(con("course-categories:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Motivo.\"}"))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder retirar(UUID id, String cuerpo) {
    return retirar(id, cuerpo, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder retirar(UUID id, String cuerpo, UUID actor) {
    MockHttpServletRequestBuilder peticion =
        post("/api/v1/course-categories/" + id + "/deletion")
            .with(con(actor, "course-categories:delete"))
            .contentType(MediaType.APPLICATION_JSON);
    return cuerpo == null ? peticion : peticion.content(cuerpo);
  }
}
