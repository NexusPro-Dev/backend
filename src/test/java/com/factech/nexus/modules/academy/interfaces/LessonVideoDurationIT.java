package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.shared.video.VideoDurationLookup;
import com.factech.nexus.shared.video.VideoDurationUnavailable;
import com.factech.nexus.shared.video.VideoLink;
import com.factech.nexus.shared.video.VideoProvider;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Los videos de academia desde el 25-09-2026 (`ac.md` §5.2.11): solo YouTube y Vimeo, y la duración
 * de una lección {@code VIDEO} leída del proveedor (`CA-AC-233` a `CA-AC-236`).
 *
 * <p><b>El proveedor está sustituido</b> ({@code @MockitoBean}): lo que se prueba aquí es cuándo se
 * le pregunta, qué se hace con su respuesta y qué se responde cuando no la da. Cómo se habla con
 * YouTube y con Vimeo lo prueba {@code ProviderVideoDurationLookupTest}, sin red.
 */
@AutoConfigureMockMvc
class LessonVideoDurationIT extends IntegrationTestBase {

  private static final String VIMEO = "https://vimeo.com/76979871";
  private static final String YOUTUBE = "https://youtu.be/dQw4w9WgXcQ";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private VideoDurationLookup proveedor;

  private UUID curso;
  private UUID modulo;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    curso = curso(jdbc, "Velas", CourseTestSupport.instructor(jdbc), 0);
    modulo = CourseTestSupport.modulo(jdbc, curso, "Uno", 0, "INACTIVO");
    reset(proveedor);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-233` — los tres campos de video admiten YouTube y Vimeo y rechazan cualquier otro"
          + " dominio con su VAL")
  void soloYoutubeYVimeo() throws Exception {
    alta("{\"type\":\"VIDEO\",\"title\":\"A\",\"content\":\"https://otro.com/v\",\"durationSeconds\":5,\"displayOrder\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("YouTube o de Vimeo")));
    for (String enlace :
        new String[] {
          "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
          "https://www.youtube.com/embed/dQw4w9WgXcQ",
          YOUTUBE,
          VIMEO,
          "https://player.vimeo.com/video/76979871"
        }) {
      alta("{\"type\":\"VIDEO\",\"title\":\"%s\",\"content\":\"%s\",\"durationSeconds\":5,\"displayOrder\":0}"
              .formatted(UUID.randomUUID(), enlace))
          .andExpect(status().isCreated());
    }

    mvc.perform(
            patch("/api/v1/courses/" + curso)
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"introVideoUrl\":\"https://otro.com/v\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));
    mvc.perform(
            patch("/api/v1/courses/" + curso)
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"introVideoUrl\":\"" + YOUTUBE + "\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/v1/courses/" + curso + "/modules/" + modulo)
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"presentationVideoUrl\":\"https://otro.com/v\"}"))
        .andExpect(status().isBadRequest());
    verify(proveedor, never()).segundosDe(any());
  }

  @Test
  @DisplayName(
      "`CA-AC-234` — sin durationSeconds la duración la da el proveedor; con ella manda la enviada"
          + " y no se le pregunta")
  void laDaElProveedor() throws Exception {
    when(proveedor.segundosDe(new VideoLink(VideoProvider.VIMEO, "76979871", null)))
        .thenReturn(754);
    alta("{\"type\":\"VIDEO\",\"title\":\"Leída\",\"content\":\""
            + VIMEO
            + "\",\"displayOrder\":0}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.durationSeconds").value(754));

    reset(proveedor);
    alta("{\"type\":\"VIDEO\",\"title\":\"A mano\",\"content\":\""
            + VIMEO
            + "\",\"durationSeconds\":90,\"displayOrder\":0}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.durationSeconds").value(90));
    verify(proveedor, never()).segundosDe(any());
  }

  @Test
  @DisplayName(
      "`CA-AC-235` — si el proveedor no la da, 422 EX-003 nombrándolo y sin dejar nada; VIDEO sin"
          + " enlace y TEXTO sin duración, 400 VAL-004")
  void siNoLaDa() throws Exception {
    when(proveedor.segundosDe(any()))
        .thenThrow(
            new VideoDurationUnavailable(VideoProvider.YOUTUBE, "el video no existe o es privado"));
    alta("{\"type\":\"VIDEO\",\"title\":\"Privado\",\"content\":\""
            + YOUTUBE
            + "\",\"displayOrder\":0}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(jsonPath("$.errors[0].field").value("durationSeconds"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("YouTube")))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("privado")));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM lessons", Integer.class)).isZero();

    alta("{\"type\":\"VIDEO\",\"title\":\"Sin enlace\",\"displayOrder\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    alta("{\"type\":\"TEXTO\",\"title\":\"Texto\",\"content\":\"# Hola\",\"displayOrder\":0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
  }

  @Test
  @DisplayName(
      "`CA-AC-236` — corregir el enlace sin duración la relee; con duración manda la enviada; otro"
          + " campo no pregunta; y si la relectura falla, 422 sin aplicar nada")
  void laCorreccion() throws Exception {
    when(proveedor.segundosDe(any())).thenReturn(100);
    String cuerpo =
        alta("{\"type\":\"VIDEO\",\"title\":\"Una\",\"content\":\""
                + VIMEO
                + "\",\"displayOrder\":0}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID leccion = UUID.fromString(cuerpo.replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1"));

    reset(proveedor);
    when(proveedor.segundosDe(any())).thenReturn(300);
    corregir(leccion, "{\"content\":\"" + YOUTUBE + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.durationSeconds").value(300));

    reset(proveedor);
    corregir(leccion, "{\"content\":\"" + VIMEO + "\",\"durationSeconds\":42}")
        .andExpect(jsonPath("$.durationSeconds").value(42));
    corregir(leccion, "{\"title\":\"Otra\"}").andExpect(status().isOk());
    verify(proveedor, never()).segundosDe(any());

    when(proveedor.segundosDe(any()))
        .thenThrow(
            new VideoDurationUnavailable(
                VideoProvider.YOUTUBE, "el proveedor no respondió a tiempo"));
    corregir(leccion, "{\"content\":\"" + YOUTUBE + "\",\"title\":\"No se aplica\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    assertThat(jdbc.queryForObject("SELECT title FROM lessons WHERE id = ?", String.class, leccion))
        .isEqualTo("Otra");
    assertThat(
            jdbc.queryForObject("SELECT content FROM lessons WHERE id = ?", String.class, leccion))
        .isEqualTo(VIMEO);
  }

  private ResultActions alta(String cuerpo) throws Exception {
    return mvc.perform(
        post("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons")
            .with(con("courses:update"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions corregir(UUID leccion, String cuerpo) throws Exception {
    return mvc.perform(
        patch("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons/" + leccion)
            .with(con("courses:update"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }
}
