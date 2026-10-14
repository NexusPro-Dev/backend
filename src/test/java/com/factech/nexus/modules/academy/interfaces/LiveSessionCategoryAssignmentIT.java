package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.CLASES;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.SENALES;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.TRADING;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.cuerpo;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.manana;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.zoom.FakeZoomMeetings;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * La categoría de cada clase en vivo (`RN-AC-034`): programar (`CA-AC-335`), corregir (`CA-AC-336`)
 * y las lecturas con su filtro (`CA-AC-337`). Zoom es el doble de la suite.
 */
@AutoConfigureMockMvc
class LiveSessionCategoryAssignmentIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/live-sessions";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeZoomMeetings zoom;

  private UUID admin;
  private UUID profe;
  private UUID alumno;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
    admin = CourseTestSupport.persona(jdbc, "Ada", "Admin", ProgressTestSupport.ADMIN);
    profe = CourseTestSupport.instructor(jdbc);
    alumno = ClassroomTestSupport.alumno(jdbc);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
  }

  @Test
  @DisplayName(
      "`CA-AC-335` — programar sin categoryId, 400 VAL-005; inexistente o retirada, 422 EX-002 sin"
          + " Zoom; con una viva, responde category; también por /mine")
  void programa() throws Exception {
    String sinCategoria =
        cuerpo("Clase", null, manana(19), manana(20), "", "")
            .replace("\"categoryId\":\"" + CLASES + "\",", "");
    programar(sinCategoria)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field", contains("categoryId")))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
    programar(conCategoria(UUID.randomUUID()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("categoryId"))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    retirarSenales();
    programar(conCategoria(SENALES)).andExpect(status().isUnprocessableEntity());
    assertThat(zoom.llamadas()).isEmpty();

    programar(conCategoria(TRADING))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.category.id").value(TRADING.toString()))
        .andExpect(jsonPath("$.category.name").value("Trading"));

    mvc.perform(
            post(RUTA + "/mine")
                .with(con(profe, "live-sessions:create-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("Mía", null, manana(19), manana(20), "", "")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.category.name").value("Clases"));
  }

  @Test
  @DisplayName(
      "`CA-AC-336` — corregir con categoryId la cambia y lo audita; con una retirada, 422; sin"
          + " él, la conserva aunque se haya retirado")
  void corrige() throws Exception {
    UUID clase = idDe(programar(conCategoria(TRADING)));

    corregir(clase, "{\"categoryId\":\"" + CLASES + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category.name").value("Clases"));
    assertThat(
            jdbc.queryForObject(
                "SELECT changes::text FROM audit_change_log WHERE entity = 'live_sessions' AND"
                    + " entity_id = ? AND action = 'UPDATE'",
                String.class,
                clase))
        .contains("categoryId")
        .contains(TRADING.toString())
        .contains(CLASES.toString());

    retirarSenales();
    corregir(clase, "{\"categoryId\":\"" + SENALES + "\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    jdbc.update("UPDATE live_session_categories SET deleted_at = now() WHERE id = ?", CLASES);
    corregir(clase, "{\"title\":\"Otro título\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category.id").value(CLASES.toString()))
        .andExpect(jsonPath("$.category.name").value("Clases"));
  }

  @Test
  @DisplayName(
      "`CA-AC-337` — el detalle, los dos listados y la vitrina traen category, y el filtro"
          + " categoryId deja solo las de esa categoría")
  void leeYFiltra() throws Exception {
    UUID trading = idDe(programar(conCategoria(TRADING)));
    UUID senales = idDe(programar(conCategoria(SENALES)));
    jdbc.update(
        "INSERT INTO live_session_instructors (live_session_id, user_id) VALUES (?, ?), (?, ?)",
        trading,
        profe,
        senales,
        profe);

    mvc.perform(get(RUTA + "/" + senales).with(con("live-sessions:read")))
        .andExpect(jsonPath("$.category.name").value("Señales"));
    mvc.perform(get(RUTA).with(con("live-sessions:list")))
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[*].category.name").exists());
    mvc.perform(get(RUTA).param("categoryId", TRADING.toString()).with(con("live-sessions:list")))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(trading.toString()))
        .andExpect(jsonPath("$.content[0].category.name").value("Trading"));
    mvc.perform(
            get(RUTA + "/mine")
                .param("categoryId", SENALES.toString())
                .with(con(profe, "live-sessions:list-own")))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(senales.toString()));
    mvc.perform(
            get(RUTA + "/available")
                .param("categoryId", SENALES.toString())
                .with(con(alumno, "live-sessions:learn")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].category.name").value("Señales"));
    mvc.perform(get(RUTA + "/available").with(con(alumno, "live-sessions:learn")))
        .andExpect(jsonPath("$", hasSize(2)));
  }

  private String conCategoria(UUID categoria) {
    return cuerpo("Clase", null, manana(19), manana(20), "", "")
        .replace(CLASES.toString(), categoria.toString());
  }

  private void retirarSenales() throws Exception {
    mvc.perform(
            post("/api/v1/live-session-categories/" + SENALES + "/deletion")
                .with(con(admin, "live-session-categories:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ya no\"}"))
        .andExpect(status().isNoContent());
  }

  private ResultActions programar(String cuerpo) throws Exception {
    return mvc.perform(
        post(RUTA)
            .with(con(admin, "live-sessions:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions corregir(UUID id, String cuerpo) throws Exception {
    return mvc.perform(
        patch(RUTA + "/" + id)
            .with(con(admin, "live-sessions:update"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private UUID idDe(ResultActions respuesta) throws Exception {
    return UUID.fromString(
        JsonPath.read(
            respuesta
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id"));
  }
}
