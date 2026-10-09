package com.factech.nexus.modules.academy.interfaces;

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

/**
 * El avance de quien mira en el aula: `RF-AC-033` (`CA-AC-250`) y `RF-AC-034` (`CA-AC-251`,
 * `CA-AC-252`), enmendados el 09-10-2026.
 *
 * <p>El curso «Velas» tiene un texto de 60 s y un video de 300 s: 360 s en total. Abrir el texto lo
 * completa (60) y ver 150 del video suma 210: 58 %.
 */
@AutoConfigureMockMvc
class ClassroomProgressIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private UUID instructor;
  private UUID alumno;
  private UUID otroAlumno;
  private Ofrecido velas;
  private UUID video;

  @BeforeEach
  void sembrar() {
    ClassroomTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    alumno = ClassroomTestSupport.alumno(jdbc);
    otroAlumno = ClassroomTestSupport.alumno(jdbc);
    velas = ofrecido(jdbc, "Velas", instructor, 0);
    video =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Video", "VIDEO", "https://youtu.be/abc123", 300, 1, "ACTIVO");
  }

  @AfterEach
  void limpiar() {
    ClassroomTestSupport.limpiar(jdbc);
  }

  private void estudiar(UUID quien) throws Exception {
    mvc.perform(
            get("/api/v1/courses/available/" + velas.curso() + "/lessons/" + velas.leccion())
                .with(con(quien, "lessons:learn")))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/v1/courses/available/" + velas.curso() + "/lessons/" + video + "/progress")
                .with(con(quien, "lessons:track-progress"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionSeconds\":150}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "`CA-AC-250` — cada curso del catálogo trae el avance de quien pregunta, en una sentencia"
          + " para toda la lista; cero si no empezó; el de otro no se mezcla")
  void elCatalogoTraeElAvance() throws Exception {
    Ofrecido otro = ofrecido(jdbc, "Otro", instructor, 1);
    estudiar(alumno);

    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.clear();
    mvc.perform(get("/api/v1/courses/available").with(con(alumno, "courses:learn")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses[0].id").value(velas.curso().toString()))
        .andExpect(jsonPath("$.courses[0].progress.percent").value(58))
        .andExpect(jsonPath("$.courses[0].progress.completedLessons").value(1))
        .andExpect(jsonPath("$.courses[0].progress.lessonCount").value(2))
        .andExpect(jsonPath("$.courses[0].progress.watchedSeconds").value(210))
        .andExpect(jsonPath("$.courses[0].progress.totalSeconds").value(360))
        .andExpect(jsonPath("$.courses[1].id").value(otro.curso().toString()))
        .andExpect(jsonPath("$.courses[1].progress.percent").value(0))
        .andExpect(jsonPath("$.courses[1].progress.lessonCount").value(1));
    long conAvance = estadisticas.getPrepareStatementCount();

    mvc.perform(get("/api/v1/courses/available").with(con(otroAlumno, "courses:learn")))
        .andExpect(jsonPath("$.courses[0].progress.percent").value(0))
        .andExpect(jsonPath("$.courses[0].progress.completedLessons").value(0));

    // Con tres cursos más la cuenta no crece: el avance es una sentencia para toda la lista.
    ofrecido(jdbc, "Tercero", instructor, 2);
    ofrecido(jdbc, "Cuarto", instructor, 3);
    estadisticas.clear();
    mvc.perform(get("/api/v1/courses/available").with(con(alumno, "courses:learn")))
        .andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(conAvance);
  }

  @Test
  @DisplayName(
      "`CA-AC-251` — el detalle trae el avance del curso y el de cada lección; la no abierta, en"
          + " cero")
  void elDetalleTraeElAvance() throws Exception {
    UUID sinAbrir =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Sin abrir", "VIDEO", "https://youtu.be/zzz", 40, 2, "ACTIVO");
    estudiar(alumno);

    mvc.perform(
            get("/api/v1/courses/available/" + velas.curso())
                .with(con(alumno, "courses:read-available")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.progress.percent").value(52))
        .andExpect(jsonPath("$.progress.completedLessons").value(1))
        .andExpect(jsonPath("$.progress.lessonCount").value(3))
        .andExpect(jsonPath("$.modules[0].lessons[0].completed").value(true))
        .andExpect(jsonPath("$.modules[0].lessons[0].completedAt").isNotEmpty())
        .andExpect(jsonPath("$.modules[0].lessons[1].id").value(video.toString()))
        .andExpect(jsonPath("$.modules[0].lessons[1].watchedSeconds").value(150))
        .andExpect(jsonPath("$.modules[0].lessons[1].completed").value(false))
        .andExpect(jsonPath("$.modules[0].lessons[2].id").value(sinAbrir.toString()))
        .andExpect(jsonPath("$.modules[0].lessons[2].watchedSeconds").value(0))
        .andExpect(jsonPath("$.modules[0].lessons[2].completedAt").value(nullValue()));
  }

  @Test
  @DisplayName(
      "`CA-AC-252` — una completada que deja de ofrecerse sale del árbol y de la cuenta sin perder"
          + " su fila; al volver, vuelve completada")
  void loNoOfrecidoSaleDeLaCuentaYVuelve() throws Exception {
    estudiar(alumno);
    jdbc.update("UPDATE lessons SET status = 'INACTIVO' WHERE id = ?", velas.leccion());

    mvc.perform(
            get("/api/v1/courses/available/" + velas.curso())
                .with(con(alumno, "courses:read-available")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.progress.lessonCount").value(1))
        .andExpect(jsonPath("$.progress.completedLessons").value(0))
        .andExpect(jsonPath("$.progress.percent").value(50));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM lesson_progress WHERE lesson_id = ? AND completed_at IS NOT"
                    + " NULL",
                Long.class,
                velas.leccion()))
        .isEqualTo(1);

    jdbc.update("UPDATE lessons SET status = 'ACTIVO' WHERE id = ?", velas.leccion());
    mvc.perform(
            get("/api/v1/courses/available/" + velas.curso())
                .with(con(alumno, "courses:read-available")))
        .andExpect(jsonPath("$.progress.completedLessons").value(1))
        .andExpect(jsonPath("$.progress.percent").value(58));
  }
}
