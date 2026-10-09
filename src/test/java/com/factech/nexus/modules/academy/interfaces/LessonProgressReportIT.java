package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ORO;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.abrirAMembresia;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ofrecido;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.Ofrecido;
import java.sql.Timestamp;
import java.util.Map;
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
 * El rastro de abrir una lección y el avance de un video: `RF-AC-035` enmendado (`CA-AC-240` a
 * `CA-AC-242`) y `RF-AC-039` (`CA-AC-243` a `CA-AC-249`).
 *
 * <p><b>Las que definen el requerimiento son `CA-AC-243`</b> —el avance nunca baja— <b>y
 * `CA-AC-247`</b> —quien no puede abrir la lección no deja rastro—.
 */
@AutoConfigureMockMvc
class LessonProgressReportIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID alumno;
  private Ofrecido velas;
  private UUID video;

  @BeforeEach
  void sembrar() {
    ClassroomTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    alumno = ClassroomTestSupport.alumno(jdbc);
    velas = ofrecido(jdbc, "Velas", instructor, 0);
    video =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Video", "VIDEO", "https://youtu.be/abc123", 300, 1, "ACTIVO");
  }

  @AfterEach
  void limpiar() {
    ClassroomTestSupport.limpiar(jdbc);
  }

  private ResultActions abrir(UUID curso, UUID leccion) throws Exception {
    return mvc.perform(
        get("/api/v1/courses/available/" + curso + "/lessons/" + leccion)
            .with(con(alumno, "lessons:learn")));
  }

  private ResultActions reportar(UUID curso, UUID leccion, String cuerpo) throws Exception {
    return mvc.perform(
        put("/api/v1/courses/available/" + curso + "/lessons/" + leccion + "/progress")
            .with(con(alumno, "lessons:track-progress"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));
  }

  private ResultActions reportar(UUID leccion, int posicion) throws Exception {
    return reportar(velas.curso(), leccion, "{\"positionSeconds\":" + posicion + "}");
  }

  private Map<String, Object> fila(UUID leccion) {
    return jdbc.queryForMap(
        "SELECT * FROM lesson_progress WHERE user_id = ? AND lesson_id = ?", alumno, leccion);
  }

  private long filas() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM lesson_progress WHERE user_id = ?", Long.class, alumno);
  }

  @Test
  @DisplayName(
      "`CA-AC-240` — abrir un VIDEO crea la fila con cero segundos; abrirlo otra vez mueve la"
          + " última apertura y conserva la primera")
  void abrirVideoDejaRastro() throws Exception {
    abrir(velas.curso(), video)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.watchedSeconds").value(0))
        .andExpect(jsonPath("$.completedAt").value(nullValue()));
    Map<String, Object> primera = fila(video);
    assertThat(primera.get("watched_seconds")).isEqualTo(0);
    assertThat(primera.get("completed_at")).isNull();

    jdbc.update(
        "UPDATE lesson_progress SET first_opened_at = first_opened_at - interval '1 hour',"
            + " last_opened_at = last_opened_at - interval '1 hour' WHERE lesson_id = ?",
        video);
    Timestamp antes = (Timestamp) fila(video).get("first_opened_at");
    abrir(velas.curso(), video).andExpect(status().isOk());
    Map<String, Object> segunda = fila(video);
    assertThat(segunda.get("first_opened_at")).isEqualTo(antes);
    assertThat((Timestamp) segunda.get("last_opened_at")).isAfter(antes);
  }

  @Test
  @DisplayName(
      "`CA-AC-241` — abrir un TEXTO lo completa; un 403 y un 404 no dejan fila; nada se audita")
  void abrirTextoLoCompleta() throws Exception {
    long auditoria = jdbc.queryForObject("SELECT count(*) FROM audit_change_log", Long.class);
    abrir(velas.curso(), velas.leccion())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedAt").isNotEmpty());
    assertThat(fila(velas.leccion()).get("completed_at")).isNotNull();

    Ofrecido deOro = ofrecido(jdbc, "De oro", instructor, 1);
    abrirAMembresia(jdbc, deOro.curso(), ORO);
    abrir(deOro.curso(), deOro.leccion()).andExpect(status().isForbidden());
    abrir(velas.curso(), deOro.leccion()).andExpect(status().isNotFound());
    assertThat(filas()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_change_log", Long.class))
        .isEqualTo(auditoria);
  }

  @Test
  @DisplayName("`CA-AC-242` — el contenido trae los segundos que reportó, para retomar")
  void elContenidoTraeLoVisto() throws Exception {
    reportar(video, 120).andExpect(status().isOk());
    abrir(velas.curso(), video)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.watchedSeconds").value(120))
        .andExpect(jsonPath("$.completedAt").value(nullValue()));
  }

  @Test
  @DisplayName(
      "`CA-AC-243` — guarda la posición; una menor no la baja, una mayor sí; responde lo guardado")
  void elAvanceNoBaja() throws Exception {
    reportar(video, 100)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.watchedSeconds").value(100))
        .andExpect(jsonPath("$.durationSeconds").value(300))
        .andExpect(jsonPath("$.percent").value(33))
        .andExpect(jsonPath("$.completed").value(false));
    reportar(video, 40).andExpect(jsonPath("$.watchedSeconds").value(100));
    reportar(video, 150).andExpect(jsonPath("$.watchedSeconds").value(150));
    assertThat(fila(video).get("watched_seconds")).isEqualTo(150);
  }

  @Test
  @DisplayName(
      "`CA-AC-244` — completa en el reporte que llega al 90 % y no antes; después no se descompleta")
  void completaAlNoventa() throws Exception {
    reportar(video, 269).andExpect(jsonPath("$.completed").value(false));
    reportar(video, 270)
        .andExpect(jsonPath("$.completed").value(true))
        .andExpect(jsonPath("$.percent").value(100))
        .andExpect(jsonPath("$.completedAt").isNotEmpty());
    Object completada = fila(video).get("completed_at");
    reportar(video, 10).andExpect(jsonPath("$.completed").value(true));
    reportar(video, 300).andExpect(jsonPath("$.completed").value(true));
    assertThat(fila(video).get("completed_at")).isEqualTo(completada);
  }

  @Test
  @DisplayName("`CA-AC-245` — una posición mayor que la duración se guarda como la duración")
  void seAcota() throws Exception {
    reportar(video, 5000)
        .andExpect(jsonPath("$.watchedSeconds").value(300))
        .andExpect(jsonPath("$.completed").value(true));
  }

  @Test
  @DisplayName("`CA-AC-246` — una lección TEXTO responde 422 EX-003 y no escribe nada")
  void elTextoNoReporta() throws Exception {
    reportar(velas.leccion(), 30)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    assertThat(filas()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-247` — las puertas de RF-AC-035: otro curso, inactiva, retirada, no ofrecido → 404;"
          + " cerrada → 403 con listas; ninguno deja fila")
  void lasPuertas() throws Exception {
    Ofrecido otro = ofrecido(jdbc, "Otro", instructor, 1);
    reportar(otro.curso(), video, "{\"positionSeconds\":10}").andExpect(status().isNotFound());

    UUID inactiva =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Inactiva", "VIDEO", "https://youtu.be/x", 60, 2, "INACTIVO");
    reportar(inactiva, 10).andExpect(status().isNotFound());
    UUID retirada =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Retirada", "VIDEO", "https://youtu.be/y", 60, 3, "ACTIVO");
    CourseTestSupport.retirarLeccion(jdbc, retirada);
    reportar(retirada, 10).andExpect(status().isNotFound());

    abrirAMembresia(jdbc, velas.curso(), ORO);
    reportar(video, 10)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.memberships[0].code").value("ORO"));

    jdbc.update("UPDATE courses SET status = 'INACTIVO' WHERE id = ?", velas.curso());
    reportar(video, 10).andExpect(status().isNotFound());
    assertThat(filas()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-248` — sin lessons:track-progress es 403 aunque porte lessons:learn; posición"
          + " ausente o negativa, 400")
  void permisoYValidacion() throws Exception {
    mvc.perform(
            put("/api/v1/courses/available/" + velas.curso() + "/lessons/" + video + "/progress")
                .with(con(alumno, "lessons:learn"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionSeconds\":10}"))
        .andExpect(status().isForbidden());
    reportar(velas.curso(), video, "{}").andExpect(status().isBadRequest());
    reportar(video, -1)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("positionSeconds"));
    assertThat(filas()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-249` — un reporte sin apertura previa crea la fila con sus dos fechas; no se audita")
  void reportarSinAbrir() throws Exception {
    long auditoria = jdbc.queryForObject("SELECT count(*) FROM audit_change_log", Long.class);
    reportar(video, 30)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstOpenedAt").isNotEmpty())
        .andExpect(jsonPath("$.lastOpenedAt").isNotEmpty());
    Map<String, Object> creada = fila(video);
    assertThat(creada.get("first_opened_at")).isNotNull();
    assertThat(creada.get("watched_seconds")).isEqualTo(30);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_change_log", Long.class))
        .isEqualTo(auditoria);
  }
}
