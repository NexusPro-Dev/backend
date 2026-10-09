package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ofrecido;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.ProgressTestSupport.progreso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.Ofrecido;
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
 * El progreso de un alumno en un curso: `RF-AC-041` (`CA-AC-253` a `CA-AC-257`).
 *
 * <p>Una red —manager, agente que le reporta, cliente del agente— y un cliente ajeno, de un agente
 * de fuera. «Velas» lo dicta {@code profe}, una persona sin rol: alcanza por ser instructor y por
 * nada más. <b>La que define el requerimiento es `CA-AC-255`</b>, el alcance.
 */
@AutoConfigureMockMvc
class StudentCourseProgressIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID admin;
  private UUID manager;
  private UUID agente;
  private UUID cliente;
  private UUID ajeno;
  private UUID profe;
  private Ofrecido velas;
  private Ofrecido otro;
  private UUID video;

  @BeforeEach
  void sembrar() {
    ProgressTestSupport.limpiar(jdbc);
    admin = CourseTestSupport.persona(jdbc, "Ada", "Admin", ProgressTestSupport.ADMIN);
    manager = CourseTestSupport.persona(jdbc, "Mia", "Manager", ProgressTestSupport.MANAGER);
    agente = CourseTestSupport.persona(jdbc, "Abel", "Agente", ProgressTestSupport.AGENTE);
    UUID agenteDeFuera =
        CourseTestSupport.persona(jdbc, "Fito", "Fuera", ProgressTestSupport.AGENTE);
    cliente = CourseTestSupport.persona(jdbc, "Cleo", "Cliente", ProgressTestSupport.CLIENTE);
    ajeno = CourseTestSupport.persona(jdbc, "Ajo", "Ajeno", ProgressTestSupport.CLIENTE);
    profe = CourseTestSupport.persona(jdbc, "Pablo", "Profe", null);
    ProgressTestSupport.reportaA(jdbc, agente, manager);
    ProgressTestSupport.esClienteDe(jdbc, cliente, agente);
    ProgressTestSupport.esClienteDe(jdbc, ajeno, agenteDeFuera);

    velas = ofrecido(jdbc, "Velas", profe, 0);
    otro = ofrecido(jdbc, "Otro", CourseTestSupport.instructor(jdbc), 1);
    video =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Video", "VIDEO", "https://youtu.be/abc123", 300, 1, "ACTIVO");
  }

  @AfterEach
  void limpiar() {
    ProgressTestSupport.limpiar(jdbc);
  }

  private ResultActions pedir(UUID actor, UUID curso, UUID alumno) throws Exception {
    return mvc.perform(
        get("/api/v1/courses/" + curso + "/progress/" + alumno)
            .with(con(actor, "courses:read-progress")));
  }

  @Test
  @DisplayName(
      "`CA-AC-253` — el árbol ofrecido con el avance de cada lección; el del curso cuadra con"
          + " RN-AC-023")
  void elArbolConSuAvance() throws Exception {
    UUID sinAbrir =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Sin abrir", "VIDEO", "https://youtu.be/zzz", 40, 2, "ACTIVO");
    progreso(jdbc, cliente, velas.leccion(), 0, true, 30);
    progreso(jdbc, cliente, video, 150, false, 10);

    pedir(admin, velas.curso(), cliente)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.student.id").value(cliente.toString()))
        .andExpect(jsonPath("$.student.fullName").value("Cleo Cliente"))
        .andExpect(jsonPath("$.course.title").value("Velas"))
        .andExpect(jsonPath("$.course.deleted").value(false))
        .andExpect(jsonPath("$.progress.percent").value(52))
        .andExpect(jsonPath("$.progress.completedLessons").value(1))
        .andExpect(jsonPath("$.progress.lessonCount").value(3))
        .andExpect(jsonPath("$.progress.watchedSeconds").value(210))
        .andExpect(jsonPath("$.progress.totalSeconds").value(400))
        .andExpect(jsonPath("$.progress.firstOpenedAt").isNotEmpty())
        .andExpect(jsonPath("$.progress.lastActivityAt").isNotEmpty())
        .andExpect(jsonPath("$.modules[0].lessons", hasSize(3)))
        .andExpect(jsonPath("$.modules[0].lessons[0].completed").value(true))
        .andExpect(jsonPath("$.modules[0].lessons[0].percent").value(100))
        .andExpect(jsonPath("$.modules[0].lessons[1].watchedSeconds").value(150))
        .andExpect(jsonPath("$.modules[0].lessons[1].percent").value(50))
        .andExpect(jsonPath("$.modules[0].lessons[2].id").value(sinAbrir.toString()))
        .andExpect(jsonPath("$.modules[0].lessons[2].watchedSeconds").value(0))
        .andExpect(jsonPath("$.modules[0].lessons[2].firstOpenedAt").value(nullValue()))
        .andExpect(jsonPath("$.notOffered", hasSize(0)));
  }

  @Test
  @DisplayName(
      "`CA-AC-254` — lo visto de una lección que hoy no se ofrece va a notOffered y no cuenta")
  void loNoOfrecidoVaAparte() throws Exception {
    progreso(jdbc, cliente, velas.leccion(), 0, true, 30);
    progreso(jdbc, cliente, video, 300, true, 10);
    CourseTestSupport.retirarLeccion(jdbc, video);

    pedir(admin, velas.curso(), cliente)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modules[0].lessons", hasSize(1)))
        .andExpect(jsonPath("$.progress.lessonCount").value(1))
        .andExpect(jsonPath("$.progress.totalSeconds").value(60))
        .andExpect(jsonPath("$.progress.percent").value(100))
        .andExpect(jsonPath("$.notOffered", hasSize(1)))
        .andExpect(jsonPath("$.notOffered[0].id").value(video.toString()))
        .andExpect(jsonPath("$.notOffered[0].completed").value(true))
        .andExpect(jsonPath("$.notOffered[0].moduleId").value(velas.modulo().toString()));
  }

  @Test
  @DisplayName(
      "`CA-AC-255` — administración a cualquiera; el manager a su red y a sus clientes y no al"
          + " ajeno; el instructor a cualquiera de su curso y no de otro")
  void elAlcance() throws Exception {
    pedir(admin, velas.curso(), ajeno).andExpect(status().isOk());

    pedir(manager, velas.curso(), agente).andExpect(status().isOk());
    pedir(manager, velas.curso(), cliente).andExpect(status().isOk());
    pedir(manager, velas.curso(), manager).andExpect(status().isOk());
    pedir(manager, velas.curso(), ajeno).andExpect(status().isNotFound());

    pedir(profe, velas.curso(), ajeno).andExpect(status().isOk());
    pedir(profe, velas.curso(), cliente).andExpect(status().isOk());
    pedir(profe, otro.curso(), ajeno).andExpect(status().isNotFound());

    // Un agente solo se alcanza a sí mismo y a sus clientes: no sube a su manager.
    pedir(agente, velas.curso(), cliente).andExpect(status().isOk());
    pedir(agente, velas.curso(), manager).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-256` — fuera del alcance, curso inexistente y alumno inexistente: el mismo 404;"
          + " alcanzado sin empezar, 200 en cero")
  void elMismoNoExiste() throws Exception {
    String fuera =
        pedir(manager, velas.curso(), ajeno)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String sinCurso =
        pedir(admin, UUID.randomUUID(), cliente)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String sinAlumno =
        pedir(admin, velas.curso(), UUID.randomUUID())
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(sinQuien(fuera)).isEqualTo(sinQuien(sinCurso)).isEqualTo(sinQuien(sinAlumno));

    pedir(manager, velas.curso(), cliente)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.progress.percent").value(0))
        .andExpect(jsonPath("$.progress.firstOpenedAt").value(nullValue()));
  }

  @Test
  @DisplayName(
      "`CA-AC-257` — sin courses:read-progress es 403 aunque porte courses:list-progress; un"
          + " identificador mal formado, 400")
  void permisoYFormato() throws Exception {
    mvc.perform(
            get("/api/v1/courses/" + velas.curso() + "/progress/" + cliente)
                .with(con(admin, "courses:list-progress")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/courses/" + velas.curso() + "/progress/no-es-uuid")
                .with(con(admin, "courses:read-progress")))
        .andExpect(status().isBadRequest());
  }

  /** El cuerpo sin lo que cambia de una respuesta a otra: la ruta y el instante. */
  private static String sinQuien(String cuerpo) {
    return cuerpo
        .replaceAll("\"instance\":\"[^\"]*\"", "")
        .replaceAll("\"timestamp\":\"[^\"]*\"", "")
        .replaceAll("\"traceId\":\"[^\"]*\"", "")
        .replaceAll("\"correlationId\":\"[^\"]*\"", "");
  }
}
