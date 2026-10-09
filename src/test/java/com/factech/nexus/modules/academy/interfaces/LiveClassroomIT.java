package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ORO;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.PLATINO;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.zoom.FakeZoomMeetings;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Las clases en vivo del alumno: `RF-AC-053` (`CA-AC-276` a `278`) y `RF-AC-054` (`CA-AC-279` a
 * `284`). <b>La que define el requerimiento es `CA-AC-281`</b>: quien no tiene acceso no recibe
 * enlace ni Zoom se entera.
 */
@AutoConfigureMockMvc
class LiveClassroomIT extends IntegrationTestBase {

  private static final String AULA = "/api/v1/live-sessions/available";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeZoomMeetings zoom;

  private UUID admin;
  private UUID alumno;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
    admin = CourseTestSupport.persona(jdbc, "Ada", "Admin", ProgressTestSupport.ADMIN);
    alumno = ClassroomTestSupport.alumno(jdbc);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
  }

  private ResultActions entrar(UUID clase) throws Exception {
    return mvc.perform(
        post(AULA + "/" + clase + "/registration").with(con(alumno, "live-sessions:join")));
  }

  @Test
  @DisplayName(
      "`CA-AC-276` a `CA-AC-278` — la vitrina: programadas sin terminar, accessible, registered,"
          + " onlyAccessible; sin enlaces")
  void vitrina() throws Exception {
    UUID libre = LiveSessionTestSupport.clase(jdbc, null, admin, 120, 180, 11L);
    UUID deOro = LiveSessionTestSupport.clase(jdbc, null, admin, 60, 120, 12L);
    LiveSessionTestSupport.abrirAMembresia(jdbc, deOro, ORO);
    UUID enCurso = LiveSessionTestSupport.clase(jdbc, null, admin, -10, 50, 13L);
    LiveSessionTestSupport.clase(jdbc, null, admin, -120, -60, 14L);
    UUID cancelada = LiveSessionTestSupport.clase(jdbc, null, admin, 60, 120, 15L);
    jdbc.update(
        "UPDATE live_sessions SET status = 'CANCELADA', cancelled_at = now(),"
            + " cancellation_reason = 'x' WHERE id = ?",
        cancelada);
    jdbc.update(
        "INSERT INTO live_session_registrations (live_session_id, user_id, zoom_registrant_id,"
            + " join_url) VALUES (?, ?, 'r', 'https://zoom.us/w/personal')",
        libre,
        alumno);

    mvc.perform(get(AULA).with(con(alumno, "live-sessions:learn")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].id")
                .value(contains(enCurso.toString(), deOro.toString(), libre.toString())))
        .andExpect(jsonPath("$[0].inProgress").value(true))
        .andExpect(jsonPath("$[1].accessible").value(false))
        .andExpect(jsonPath("$[1].memberships[0].code").value("ORO"))
        .andExpect(jsonPath("$[2].accessible").value(true))
        .andExpect(jsonPath("$[2].registered").value(true))
        .andExpect(jsonPath("$[2].joinUrl").doesNotExist());

    mvc.perform(get(AULA).param("onlyAccessible", "true").with(con(alumno, "live-sessions:learn")))
        .andExpect(jsonPath("$", hasSize(2)));
    ClassroomTestSupport.conMembresia(jdbc, alumno, ORO);
    mvc.perform(get(AULA).param("onlyAccessible", "true").with(con(alumno, "live-sessions:learn")))
        .andExpect(jsonPath("$", hasSize(3)));
    mvc.perform(get(AULA).with(con(alumno, "courses:learn"))).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-279`, `CA-AC-280` — con acceso, se registra en Zoom y recibe su enlace; la segunda"
          + " vez el mismo sin llamar a Zoom")
  void entra() throws Exception {
    UUID clase = LiveSessionTestSupport.clase(jdbc, null, admin, 60, 120, 21L);
    String enlace =
        entrar(clase)
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.joinUrl")
                    .value(org.hamcrest.Matchers.startsWith("https://zoom.us/w/21")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(zoom.llamadas()).hasSize(1).first().asString().startsWith("register:21:");
    assertThat(zoom.llamadas().get(0)).contains("@nexus.test");

    String otraVez =
        entrar(clase).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(com.jayway.jsonpath.JsonPath.<String>read(otraVez, "$.joinUrl"))
        .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(enlace, "$.joinUrl"));
    assertThat(zoom.llamadas()).hasSize(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-281`, `CA-AC-282` — sin acceso 403 con la invitación y sin Zoom; perder la membresía"
          + " cierra; cancelada, terminada o inexistente 404")
  void sinAcceso() throws Exception {
    UUID deOro = LiveSessionTestSupport.clase(jdbc, null, admin, 60, 120, 31L);
    LiveSessionTestSupport.abrirAMembresia(jdbc, deOro, ORO);
    ClassroomTestSupport.conMembresia(jdbc, alumno, PLATINO);
    entrar(deOro)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.memberships[0].code").value("ORO"));
    assertThat(zoom.llamadas()).isEmpty();

    ClassroomTestSupport.conMembresia(jdbc, alumno, ORO);
    entrar(deOro).andExpect(status().isCreated());
    ClassroomTestSupport.conMembresia(jdbc, alumno, PLATINO);
    entrar(deOro).andExpect(status().isForbidden()).andExpect(jsonPath("$.joinUrl").doesNotExist());

    UUID terminada = LiveSessionTestSupport.clase(jdbc, null, admin, -120, -60, 32L);
    entrar(terminada).andExpect(status().isNotFound());
    entrar(UUID.randomUUID()).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-283`, `CA-AC-284` — Zoom falla: 503 sin fila; sin live-sessions:join, 403 aunque"
          + " porte learn")
  void fallosYPermiso() throws Exception {
    UUID clase = LiveSessionTestSupport.clase(jdbc, null, admin, 60, 120, 41L);
    zoom.fallarLaSiguiente();
    entrar(clase).andExpect(status().isServiceUnavailable());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM live_session_registrations", Long.class))
        .isZero();
    mvc.perform(post(AULA + "/" + clase + "/registration").with(con(alumno, "live-sessions:learn")))
        .andExpect(status().isForbidden());
  }
}
