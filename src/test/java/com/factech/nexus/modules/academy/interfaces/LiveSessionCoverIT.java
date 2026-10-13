package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.AcademyCoverIT.GIF;
import static com.factech.nexus.modules.academy.interfaces.AcademyCoverIT.PNG;
import static com.factech.nexus.modules.academy.interfaces.AcademyCoverIT.TOPE;
import static com.factech.nexus.modules.academy.interfaces.AcademyCoverIT.relleno;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.cuerpo;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.manana;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * La portada de una clase en vivo: `RF-AC-058` a `RF-AC-061` (`CA-AC-320`, `CA-AC-321`). Es la del
 * curso (`AcademyCoverIT`) sobre otra tabla, así que aquí se prueba lo propio de la clase: el
 * estado, la propiedad y que la imagen se sirva.
 */
@AutoConfigureMockMvc
class LiveSessionCoverIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/live-sessions";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeZoomMeetings zoom;

  private UUID admin;
  private UUID profe;
  private UUID otroProfe;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
    admin = CourseTestSupport.persona(jdbc, "Ada", "Admin", ProgressTestSupport.ADMIN);
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

  private UUID programar(UUID instructor) throws Exception {
    String respuesta =
        mvc.perform(
                post(RUTA)
                    .with(con(admin, "live-sessions:create"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo("Con portada", instructor, manana(19), manana(20), "", "")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(respuesta, "$.id"));
  }

  private ResultActions subir(String ruta, UUID id, byte[] bytes, RequestPostProcessor quien)
      throws Exception {
    return mvc.perform(
        multipart(HttpMethod.PUT, ruta, id)
            .file(new MockMultipartFile("file", "portada.png", "image/png", bytes))
            .with(quien));
  }

  private UUID portadaDe(UUID clase) {
    return jdbc.queryForObject(
        "SELECT cover_image_id FROM live_sessions WHERE id = ?", UUID.class, clase);
  }

  private long imagen(UUID id) {
    return jdbc.queryForObject("SELECT count(*) FROM academy_images WHERE id = ?", Long.class, id);
  }

  @Test
  @DisplayName(
      "`CA-AC-320` — pone la portada, que se sirve sin token; otra la reemplaza y borra la"
          + " anterior; archivo malo, 400; se audita")
  void pone() throws Exception {
    UUID clase = programar(null);
    subir(RUTA + "/{id}/cover", clase, relleno(PNG, 2_048), con(admin, "live-sessions:set-cover"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(startsWith("/api/v1/academy-images/")));
    UUID primera = portadaDe(clase);
    mvc.perform(get("/api/v1/academy-images/" + primera)).andExpect(status().isOk());

    subir(RUTA + "/{id}/cover", clase, relleno(PNG, 100), con(admin, "live-sessions:set-cover"))
        .andExpect(status().isOk());
    UUID segunda = portadaDe(clase);
    assertThat(segunda).isNotEqualTo(primera);
    assertThat(imagen(primera)).isZero();

    subir(RUTA + "/{id}/cover", clase, GIF, con(admin, "live-sessions:set-cover"))
        .andExpect(status().isBadRequest());
    subir(
            RUTA + "/{id}/cover",
            clase,
            relleno(PNG, TOPE + 1),
            con(admin, "live-sessions:set-cover"))
        .andExpect(status().isBadRequest());
    subir(RUTA + "/{id}/cover", clase, PNG, con(admin, "live-sessions:update"))
        .andExpect(status().isForbidden());
    assertThat(portadaDe(clase)).isEqualTo(segunda);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'live_sessions'"
                    + " AND entity_id = ? AND action = 'UPDATE'"
                    + " AND changes -> 'cover_image_id' IS NOT NULL",
                Long.class,
                clase))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-AC-320`, `CA-AC-321` — la propia sobre una suya y no sobre una ajena (404); cancelada,"
          + " 409")
  void propiaYEstado() throws Exception {
    UUID mia = programar(profe);
    UUID ajena = programar(otroProfe);
    subir(RUTA + "/mine/{id}/cover", mia, PNG, con(profe, "live-sessions:set-cover-own"))
        .andExpect(status().isOk());
    subir(RUTA + "/mine/{id}/cover", ajena, PNG, con(profe, "live-sessions:set-cover-own"))
        .andExpect(status().isNotFound());
    mvc.perform(
            delete(RUTA + "/mine/{id}/cover", ajena)
                .with(con(profe, "live-sessions:remove-cover-own")))
        .andExpect(status().isNotFound());

    mvc.perform(
            post(RUTA + "/" + mia + "/cancellation")
                .with(con(admin, "live-sessions:cancel"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Se movió\"}"))
        .andExpect(status().isOk());
    subir(RUTA + "/{id}/cover", mia, PNG, con(admin, "live-sessions:set-cover"))
        .andExpect(status().isConflict());
    mvc.perform(delete(RUTA + "/{id}/cover", mia).with(con(admin, "live-sessions:remove-cover")))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("`CA-AC-321` — quitar la borra y deja coverImageUrl nulo; sin portada, 200")
  void quita() throws Exception {
    UUID mia = programar(profe);
    subir(RUTA + "/mine/{id}/cover", mia, PNG, con(profe, "live-sessions:set-cover-own"))
        .andExpect(status().isOk());
    UUID imagen = portadaDe(mia);

    mvc.perform(
            delete(RUTA + "/mine/{id}/cover", mia)
                .with(con(profe, "live-sessions:remove-cover-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()));
    assertThat(portadaDe(mia)).isNull();
    assertThat(imagen(imagen)).isZero();

    mvc.perform(delete(RUTA + "/{id}/cover", mia).with(con(admin, "live-sessions:remove-cover")))
        .andExpect(status().isOk());
    mvc.perform(delete(RUTA + "/{id}/cover", mia).with(con(admin, "live-sessions:set-cover")))
        .andExpect(status().isForbidden());
  }
}
