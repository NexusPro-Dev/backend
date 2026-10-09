package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.cuerpo;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.manana;
import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Las clases en vivo del instructor, bajo {@code /mine}: `RF-AC-048` a `RF-AC-052` (`CA-AC-297` a
 * `CA-AC-305`). <b>La que define el requerimiento es la propiedad</b> (`RN-AC-028`): las clases de
 * los cursos que dicta, y nada más, con {@code 404} sobre lo ajeno.
 */
@AutoConfigureMockMvc
class OwnLiveSessionsIT extends IntegrationTestBase {

  private static final String MIAS = "/api/v1/live-sessions/mine";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeZoomMeetings zoom;

  private UUID profe;
  private UUID otroProfe;
  private UUID suyo;
  private UUID ajeno;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
    profe = CourseTestSupport.persona(jdbc, "Pablo", "Profe", null);
    otroProfe = CourseTestSupport.persona(jdbc, "Olga", "Otra", null);
    suyo = CourseTestSupport.curso(jdbc, "Suyo", profe, 0);
    ajeno = CourseTestSupport.curso(jdbc, "Ajeno", otroProfe, 1);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
  }

  private UUID programar(UUID quien, UUID curso) throws Exception {
    String respuesta =
        mvc.perform(
                post(MIAS)
                    .with(con(quien, "live-sessions:create-own"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo("Clase", curso, manana(19), manana(20), "", "")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(respuesta, "$.id"));
  }

  @Test
  @DisplayName(
      "`CA-AC-297` a `CA-AC-299` — programa en un curso suyo; sin curso o en uno ajeno, 422 sin"
          + " Zoom; sin create-own, 403")
  void programa() throws Exception {
    programar(profe, suyo);
    assertThat(zoom.llamadas()).containsExactly("create");

    mvc.perform(
            post(MIAS)
                .with(con(profe, "live-sessions:create-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("X", ajeno, manana(19), manana(20), "", "")))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            post(MIAS)
                .with(con(profe, "live-sessions:create-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("X", null, manana(19), manana(20), "", "")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("courseId"));
    assertThat(zoom.llamadas()).containsExactly("create");

    mvc.perform(
            post(MIAS)
                .with(con(profe, "live-sessions:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("X", suyo, manana(19), manana(20), "", "")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-300`, `CA-AC-301` — lista solo las de sus cursos; al cambiar el instructor, pasan"
          + " al nuevo")
  void lista() throws Exception {
    UUID mia = programar(profe, suyo);
    programar(otroProfe, ajeno);
    LiveSessionTestSupport.clase(jdbc, null, profe, 60, 120, 77L);

    mvc.perform(get(MIAS).with(con(profe, "live-sessions:list-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(mia.toString()));

    jdbc.update("UPDATE courses SET instructor_id = ? WHERE id = ?", otroProfe, suyo);
    mvc.perform(get(MIAS).with(con(profe, "live-sessions:list-own")))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get(MIAS).with(con(otroProfe, "live-sessions:list-own")))
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get(MIAS).with(con(profe, "live-sessions:list"))).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-302` a `CA-AC-305` — corrige, cancela e inicia las suyas; las ajenas y las sueltas,"
          + " 404; no la mueve a un curso ajeno ni la suelta")
  void gobierna() throws Exception {
    UUID mia = programar(profe, suyo);
    UUID deOtro = programar(otroProfe, ajeno);
    UUID suelta = LiveSessionTestSupport.clase(jdbc, null, profe, 60, 120, 78L);

    mvc.perform(
            patch(MIAS + "/" + mia)
                .with(con(profe, "live-sessions:update-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Nuevo título\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Nuevo título"));
    for (UUID otra : new UUID[] {deOtro, suelta}) {
      mvc.perform(
              patch(MIAS + "/" + otra)
                  .with(con(profe, "live-sessions:update-own"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"title\":\"No\"}"))
          .andExpect(status().isNotFound());
      mvc.perform(
              post(MIAS + "/" + otra + "/cancellation")
                  .with(con(profe, "live-sessions:cancel-own"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"reason\":\"No\"}"))
          .andExpect(status().isNotFound());
      mvc.perform(post(MIAS + "/" + otra + "/host-link").with(con(profe, "live-sessions:host-own")))
          .andExpect(status().isNotFound());
    }
    mvc.perform(
            patch(MIAS + "/" + mia)
                .with(con(profe, "live-sessions:update-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":\"" + ajeno + "\"}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            patch(MIAS + "/" + mia)
                .with(con(profe, "live-sessions:update-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"removeCourse\":true}"))
        .andExpect(status().isUnprocessableEntity());

    mvc.perform(post(MIAS + "/" + mia + "/host-link").with(con(profe, "live-sessions:host-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.startUrl").isNotEmpty());
    mvc.perform(
            post(MIAS + "/" + mia + "/cancellation")
                .with(con(profe, "live-sessions:cancel-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Me enfermé\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELADA"));
  }
}
