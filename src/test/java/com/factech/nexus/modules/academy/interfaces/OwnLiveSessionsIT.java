package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.cuerpo;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.ids;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.manana;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
 * `CA-AC-305`) con la regla de `RN-AC-032` (`CA-AC-318`, `CA-AC-319`). <b>La que define el
 * requerimiento es la propiedad</b> (`RN-AC-028`): las clases de las que es instructor, y nada más,
 * con {@code 404} sobre lo ajeno.
 */
@AutoConfigureMockMvc
class OwnLiveSessionsIT extends IntegrationTestBase {

  private static final String MIAS = "/api/v1/live-sessions/mine";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeZoomMeetings zoom;

  private UUID profe;
  private UUID otroProfe;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
    profe =
        CourseTestSupport.persona(jdbc, "Pablo", "Profe", CourseTestSupport.rolInstructor(jdbc));
    otroProfe =
        CourseTestSupport.persona(jdbc, "Olga", "Otra", CourseTestSupport.rolInstructor(jdbc));
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
  }

  private UUID programar(UUID quien, String cuerpo) throws Exception {
    String respuesta =
        mvc.perform(
                post(MIAS)
                    .with(con(quien, "live-sessions:create-own"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(respuesta, "$.id"));
  }

  private UUID programar(UUID quien) throws Exception {
    return programar(quien, cuerpo("Clase", null, manana(19), manana(20), "", ""));
  }

  @Test
  @DisplayName(
      "`CA-AC-297` a `CA-AC-299`, `CA-AC-318` — quien programa queda entre los instructores, con"
          + " los que nombre; un nombrado sin courses:teach, 422 sin Zoom; sin create-own, 403")
  void programa() throws Exception {
    UUID sola = programar(profe);
    assertThat(
            jdbc.queryForList(
                "SELECT user_id FROM live_session_instructors WHERE live_session_id = ?",
                UUID.class,
                sola))
        .containsExactly(profe);

    String conOtro =
        cuerpo("Juntos", null, manana(19), manana(20), "", "")
            .replace("{", "{\"instructorIds\":[" + ids(otroProfe) + "],");
    mvc.perform(
            post(MIAS)
                .with(con(profe, "live-sessions:create-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(conOtro))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.instructors[*].id")
                .value(containsInAnyOrder(profe.toString(), otroProfe.toString())));
    assertThat(zoom.llamadas()).containsExactly("create", "create");

    UUID sinPermiso = CourseTestSupport.persona(jdbc, "Sin", "Teach", null);
    mvc.perform(
            post(MIAS)
                .with(con(profe, "live-sessions:create-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("X", sinPermiso, manana(19), manana(20), "", "")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("instructorIds"));
    assertThat(zoom.llamadas()).hasSize(2);

    mvc.perform(
            post(MIAS)
                .with(con(profe, "live-sessions:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("X", null, manana(19), manana(20), "", "")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-300`, `CA-AC-301`, `CA-AC-318` — lista las clases de las que es instructor, también"
          + " las que programó otro; al quitarlo de la lista, deja de verla")
  void lista() throws Exception {
    UUID mia = programar(profe);
    UUID deOtro = programar(otroProfe);
    LiveSessionTestSupport.clase(jdbc, null, profe, 60, 120, 77L);
    UUID compartida = LiveSessionTestSupport.clase(jdbc, profe, otroProfe, 60, 120, 78L);

    mvc.perform(get(MIAS).with(con(profe, "live-sessions:list-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(
            jsonPath("$.content[*].id")
                .value(containsInAnyOrder(mia.toString(), compartida.toString())));

    jdbc.update("DELETE FROM live_session_instructors WHERE live_session_id = ?", mia);
    mvc.perform(get(MIAS).with(con(profe, "live-sessions:list-own")))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(MIAS).with(con(otroProfe, "live-sessions:list-own")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(deOtro.toString()));
    mvc.perform(get(MIAS).with(con(profe, "live-sessions:list"))).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-AC-302` a `CA-AC-305`, `CA-AC-318`, `CA-AC-319` — corrige, cancela e inicia las suyas;"
          + " las ajenas y las sin instructores, 404; no se quita de la lista")
  void gobierna() throws Exception {
    UUID mia = programar(profe);
    UUID deOtro = programar(otroProfe);
    UUID sinNadie = LiveSessionTestSupport.clase(jdbc, null, profe, 60, 120, 78L);

    mvc.perform(
            patch(MIAS + "/" + mia)
                .with(con(profe, "live-sessions:update-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Nuevo título\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Nuevo título"));
    for (UUID otra : new UUID[] {deOtro, sinNadie}) {
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
                .content("{\"instructorIds\":[" + ids(otroProfe) + "]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    mvc.perform(
            patch(MIAS + "/" + mia)
                .with(con(profe, "live-sessions:update-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instructorIds\":[" + ids(profe, otroProfe) + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructors.length()").value(2));
    mvc.perform(get(MIAS + "/" + mia).with(con(otroProfe, "live-sessions:read-own")))
        .andExpect(status().isOk());

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

  @Test
  @DisplayName(
      "`CA-AC-319` — administración reemplaza la lista entera, y vacía la deja sin instructores")
  void administracionReemplaza() throws Exception {
    UUID mia = programar(profe);
    mvc.perform(
            patch("/api/v1/live-sessions/" + mia)
                .with(con(profe, "live-sessions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instructorIds\":[" + ids(otroProfe) + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructors[0].id").value(otroProfe.toString()))
        .andExpect(jsonPath("$.instructors.length()").value(1));
    mvc.perform(
            patch("/api/v1/live-sessions/" + mia)
                .with(con(profe, "live-sessions:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instructorIds\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructors.length()").value(0));
    mvc.perform(get(MIAS + "/" + mia).with(con(profe, "live-sessions:read-own")))
        .andExpect(status().isNotFound());
  }
}
