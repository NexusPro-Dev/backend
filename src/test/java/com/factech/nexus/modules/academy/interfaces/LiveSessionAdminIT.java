package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ORO;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.cuerpo;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.ids;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.manana;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.zoom.FakeZoomMeetings;
import com.factech.nexus.shared.zoom.ZoomMeetings.MeetingSpec;
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
 * Las clases en vivo para administración: `RF-AC-044` (`CA-AC-263` a `268`), `RF-AC-043` (`271`,
 * `272`), `RF-AC-042` (`273` a `275`), `RF-AC-047` (`285` a `287`), `RF-AC-045` (`288` a `292`) y
 * `RF-AC-046` (`293` a `296`). Zoom es el doble de la suite.
 */
@AutoConfigureMockMvc
class LiveSessionAdminIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/live-sessions";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeZoomMeetings zoom;

  private UUID admin;
  private UUID curso;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
    admin = CourseTestSupport.persona(jdbc, "Ada", "Admin", ProgressTestSupport.ADMIN);
    curso = CourseTestSupport.curso(jdbc, "Velas", CourseTestSupport.instructor(jdbc), 0);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
  }

  private ResultActions programar(String cuerpo) throws Exception {
    return mvc.perform(
        post(RUTA)
            .with(con(admin, "live-sessions:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private UUID programada(String titulo) throws Exception {
    String respuesta =
        programar(cuerpo(titulo, curso, manana(19), manana(20), ids(ORO), ""))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(respuesta, "$.id"));
  }

  private long filas() {
    return jdbc.queryForObject("SELECT count(*) FROM live_sessions", Long.class);
  }

  @Test
  @DisplayName(
      "`CA-AC-263`, `CA-AC-264` — programa la clase y su reunión con registro obligatorio; de"
          + " Zoom solo se guarda el identificador")
  void programa() throws Exception {
    String respuesta =
        programar(cuerpo("Velas en vivo", curso, manana(19), manana(20), ids(ORO), ""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PROGRAMADA"))
            .andExpect(jsonPath("$.ended").value(false))
            .andExpect(jsonPath("$.course.id").value(curso.toString()))
            .andExpect(jsonPath("$.memberships[0].code").value("ORO"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(zoom.llamadas()).containsExactly("create");
    MeetingSpec reunion = zoom.reuniones().values().iterator().next();
    assertThat(reunion.topic()).isEqualTo("Velas en vivo");
    assertThat(reunion.durationMinutes()).isEqualTo(60);
    assertThat(reunion.startsAt().getOffset().getTotalSeconds()).isEqualTo(-5 * 3600);
    long id = zoom.reuniones().keySet().iterator().next();
    assertThat(respuesta).contains("\"zoomMeetingId\":" + id).doesNotContain("zoom.us");
    assertThat(jdbc.queryForObject("SELECT zoom_meeting_id FROM live_sessions", Long.class))
        .isEqualTo(id);
  }

  @Test
  @DisplayName(
      "`CA-AC-265` — inicio en el pasado, fin anterior, menos de 15 min o más de 10 h: 400 y Zoom"
          + " no recibe nada")
  void horario() throws Exception {
    programar(cuerpo("Pasada", null, "2020-01-01T10:00:00", "2020-01-01T11:00:00", "", ""))
        .andExpect(status().isBadRequest());
    programar(cuerpo("Corta", null, manana(19), manana(19).replace(":00:00", ":10:00"), "", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("endsAt"));
    programar(cuerpo("Al revés", null, manana(19), manana(18), "", ""))
        .andExpect(status().isBadRequest());
    programar(cuerpo("Larga", null, manana(8), manana(19), "", ""))
        .andExpect(status().isBadRequest());
    programar("{\"title\":\"\"}").andExpect(status().isBadRequest());
    assertThat(zoom.llamadas()).isEmpty();
    assertThat(filas()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-266` — curso retirado, membresía inexistente o producto que no vale: 422 sin llamar"
          + " a Zoom")
  void referencias() throws Exception {
    CourseTestSupport.retirar(jdbc, curso);
    programar(cuerpo("X", curso, manana(19), manana(20), "", ""))
        .andExpect(status().isUnprocessableEntity());
    programar(cuerpo("X", null, manana(19), manana(20), ids(UUID.randomUUID()), ""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("membershipIds"));
    programar(cuerpo("X", null, manana(19), manana(20), "", ids(UUID.randomUUID())))
        .andExpect(status().isUnprocessableEntity());
    programar(cuerpo("X", null, manana(19), manana(20), ids(ORO, ORO), ""))
        .andExpect(status().isBadRequest());
    assertThat(zoom.llamadas()).isEmpty();
  }

  @Test
  @DisplayName("`CA-AC-267` — Zoom falla: 503 y ninguna fila")
  void zoomFalla() throws Exception {
    zoom.fallarLaSiguiente();
    programar(cuerpo("X", null, manana(19), manana(20), "", ""))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    assertThat(filas()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-268` — se audita como CREATE; sin live-sessions:create, 403 aunque porte create-own")
  void auditoriaYPermiso() throws Exception {
    UUID id = programada("Auditada");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'live_sessions'"
                    + " AND entity_id = ? AND action = 'CREATE'",
                Long.class,
                id))
        .isEqualTo(1);
    mvc.perform(
            post(RUTA)
                .with(con(admin, "live-sessions:create-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("X", null, manana(19), manana(20), "", "")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-271`, `CA-AC-272` — el detalle trae listas, registrados y ningún enlace; inexistente"
          + " 404")
  void detalle() throws Exception {
    UUID id = programada("Detalle");
    UUID alumno = ClassroomTestSupport.alumno(jdbc);
    jdbc.update(
        "INSERT INTO live_session_registrations (live_session_id, user_id, zoom_registrant_id,"
            + " join_url) VALUES (?, ?, 'r', 'https://zoom.us/w/personal')",
        id,
        alumno);
    mvc.perform(get(RUTA + "/" + id).with(con(admin, "live-sessions:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrations", hasSize(1)))
        .andExpect(jsonPath("$.registrations[0].userId").value(alumno.toString()))
        .andExpect(jsonPath("$.registrations[0].fullName").value("Ana Alumna"))
        .andExpect(jsonPath("$.registrations[0].joinUrl").doesNotExist())
        .andExpect(jsonPath("$.cancelledAt").value(nullValue()));
    mvc.perform(get(RUTA + "/" + UUID.randomUUID()).with(con(admin, "live-sessions:read")))
        .andExpect(status().isNotFound());
    mvc.perform(get(RUTA + "/" + id).with(con(admin, "live-sessions:list")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-273` a `CA-AC-275` — el listado con todas, filtros por estado, terminadas y curso")
  void listado() throws Exception {
    UUID futura = programada("Futura");
    UUID terminada = LiveSessionTestSupport.clase(jdbc, null, admin, -120, -60, 1L);
    mvc.perform(
            post(RUTA + "/" + futura + "/cancellation")
                .with(con(admin, "live-sessions:cancel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Se movió\"}"))
        .andExpect(status().isOk());
    UUID otra = programada("Otra");

    mvc.perform(get(RUTA).with(con(admin, "live-sessions:list")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].membershipCount").value(1));
    mvc.perform(get(RUTA).param("status", "CANCELADA").with(con(admin, "live-sessions:list")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(futura.toString()));
    mvc.perform(get(RUTA).param("ended", "true").with(con(admin, "live-sessions:list")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(terminada.toString()))
        .andExpect(jsonPath("$.content[0].ended").value(true));
    mvc.perform(
            get(RUTA)
                .param("courseId", curso.toString())
                .param("status", "PROGRAMADA")
                .with(con(admin, "live-sessions:list")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(otra.toString()));
    mvc.perform(get(RUTA).param("status", "OTRO").with(con(admin, "live-sessions:list")))
        .andExpect(status().isBadRequest());
    mvc.perform(get(RUTA).with(con(admin, "live-sessions:list-own")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-285` a `CA-AC-287` — el enlace de anfitrión, sin guardarlo y auditado; 409 si"
          + " terminó")
  void anfitrion() throws Exception {
    UUID id = programada("Con anfitrión");
    long antes =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE event_type ="
                + " 'LIVE_SESSION_HOST_LINK_ISSUED'",
            Long.class);
    mvc.perform(post(RUTA + "/" + id + "/host-link").with(con(admin, "live-sessions:host")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.startUrl").value(org.hamcrest.Matchers.containsString("zak=")));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_security_log WHERE event_type ="
                    + " 'LIVE_SESSION_HOST_LINK_ISSUED'",
                Long.class))
        .isEqualTo(antes + 1);

    UUID terminada = LiveSessionTestSupport.clase(jdbc, null, admin, -120, -60, 2L);
    mvc.perform(post(RUTA + "/" + terminada + "/host-link").with(con(admin, "live-sessions:host")))
        .andExpect(status().isConflict());
    mvc.perform(post(RUTA + "/" + id + "/host-link").with(con(admin, "live-sessions:read")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-288` a `CA-AC-292` — corrige lo que viene; Zoom solo si cambia título u horario;"
          + " listas reemplazadas; 409 cancelada; 503 sin cambios")
  void corrige() throws Exception {
    UUID id = programada("Antes");
    zoom.reiniciar();
    mvc.perform(
            patch(RUTA + "/" + id)
                .with(con(admin, "live-sessions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Otra agenda\",\"membershipIds\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Otra agenda"))
        .andExpect(jsonPath("$.memberships", hasSize(0)));
    assertThat(zoom.llamadas()).isEmpty();

    mvc.perform(
            patch(RUTA + "/" + id)
                .with(con(admin, "live-sessions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Después\",\"endsAt\":\"" + manana(21) + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Después"));
    assertThat(zoom.llamadas()).hasSize(1).first().asString().startsWith("update:");
    assertThat(zoom.reuniones().values().iterator().next().durationMinutes()).isEqualTo(120);

    zoom.fallarLaSiguiente();
    mvc.perform(
            patch(RUTA + "/" + id)
                .with(con(admin, "live-sessions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"No llega\"}"))
        .andExpect(status().isServiceUnavailable());
    assertThat(
            jdbc.queryForObject("SELECT title FROM live_sessions WHERE id = ?", String.class, id))
        .isEqualTo("Después");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'live_sessions'"
                    + " AND entity_id = ? AND action = 'UPDATE'",
                Long.class,
                id))
        .isEqualTo(2);

    mvc.perform(
            post(RUTA + "/" + id + "/cancellation")
                .with(con(admin, "live-sessions:cancel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Fin\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch(RUTA + "/" + id)
                .with(con(admin, "live-sessions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Tarde\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            patch(RUTA + "/mine/" + id)
                .with(con(admin, "live-sessions:update-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Tarde\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-293` a `CA-AC-296` — cancelar borra la reunión, conserva la fila con motivo; sin"
          + " motivo 400; dos veces 409; Zoom falla 503")
  void cancela() throws Exception {
    UUID id = programada("A cancelar");
    long reunion = zoom.reuniones().keySet().iterator().next();
    mvc.perform(
            post(RUTA + "/" + id + "/cancellation")
                .with(con(admin, "live-sessions:cancel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    zoom.fallarLaSiguiente();
    mvc.perform(
            post(RUTA + "/" + id + "/cancellation")
                .with(con(admin, "live-sessions:cancel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Lluvia\"}"))
        .andExpect(status().isServiceUnavailable());
    assertThat(
            jdbc.queryForObject("SELECT status FROM live_sessions WHERE id = ?", String.class, id))
        .isEqualTo("PROGRAMADA");

    mvc.perform(
            post(RUTA + "/" + id + "/cancellation")
                .with(con(admin, "live-sessions:cancel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Lluvia\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELADA"))
        .andExpect(jsonPath("$.cancellationReason").value("Lluvia"));
    assertThat(zoom.llamadas()).contains("delete:" + reunion);
    assertThat(zoom.reuniones()).doesNotContainKey(reunion);

    mvc.perform(
            post(RUTA + "/" + id + "/cancellation")
                .with(con(admin, "live-sessions:cancel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Otra vez\"}"))
        .andExpect(status().isConflict());
  }
}
