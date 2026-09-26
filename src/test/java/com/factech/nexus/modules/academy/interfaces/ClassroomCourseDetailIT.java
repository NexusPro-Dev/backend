package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ORO;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.abrirAMembresia;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.abrirAServicio;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.abrirLeccion;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ofrecido;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * El curso como lo ve el alumno: `RF-AC-034` (`CA-AC-195` a `CA-AC-201`; `CA-AC-199` espera a
 * `RF-AC-018`).
 *
 * <p><b>La que define el requerimiento es `CA-AC-198`</b>: el curso cerrado que se enseña entero,
 * con la demostración abierta.
 */
@AutoConfigureMockMvc
class ClassroomCourseDetailIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private UUID instructor;
  private UUID alumno;

  @BeforeEach
  void sembrar() {
    ClassroomTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    alumno = ClassroomTestSupport.alumno(jdbc);
  }

  @AfterEach
  void limpiar() {
    ClassroomTestSupport.limpiar(jdbc);
  }

  private ResultActions pedir(UUID curso) throws Exception {
    return mvc.perform(
        get("/api/v1/courses/available/" + curso).with(con(alumno, "courses:read-available")));
  }

  @Test
  @DisplayName(
      "`CA-AC-195` — el curso ofrecido con sus campos, llaves y árbol; sin estados, offerable ni"
          + " contenido")
  void laForma() throws Exception {
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    abrirAMembresia(jdbc, velas.curso(), ORO);
    UUID bot = ClassroomTestSupport.servicio(jdbc, "ACL_BOT");
    abrirAServicio(jdbc, velas.curso(), bot);

    pedir(velas.curso())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Velas"))
        .andExpect(jsonPath("$.longDescription").value("Larga"))
        .andExpect(jsonPath("$.introVideoUrl").value(nullValue()))
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.instructor.fullName").value("Juan Pérez"))
        .andExpect(jsonPath("$.memberships[*].code").value(contains("ORO")))
        .andExpect(jsonPath("$.memberships[0].color").value("FFB300"))
        .andExpect(jsonPath("$.products[*].code").value(contains("ACL_BOT")))
        .andExpect(jsonPath("$.recommendedCourses", hasSize(0)))
        .andExpect(jsonPath("$.currentMembership.code").value("BECA"))
        .andExpect(jsonPath("$.modules", hasSize(1)))
        .andExpect(jsonPath("$.modules[0].lessons", hasSize(1)))
        .andExpect(jsonPath("$.modules[0].lessons[0].title").value("Lección de Velas"))
        .andExpect(jsonPath("$.status").doesNotExist())
        .andExpect(jsonPath("$.offerable").doesNotExist())
        .andExpect(jsonPath("$.modules[0].status").doesNotExist())
        .andExpect(jsonPath("$.modules[0].lessons[0].content").doesNotExist())
        .andExpect(jsonPath("$.modules[0].lessons[0].status").doesNotExist());
  }

  @Test
  @DisplayName(
      "`CA-AC-196` — inexistente, retirado, inactivo o incompleto: 404 con el mismo mensaje; id"
          + " mal formado, 400")
  void noSeOfrece() throws Exception {
    String mensaje = "No existe un curso con ese identificador.";
    pedir(UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(mensaje));

    Ofrecido retirado = ofrecido(jdbc, "Retirado", instructor, 0);
    CourseTestSupport.retirar(jdbc, retirado.curso());
    pedir(retirado.curso())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(mensaje));

    Ofrecido inactivo = ofrecido(jdbc, "Inactivo", instructor, 1);
    jdbc.update("UPDATE courses SET status = 'INACTIVO' WHERE id = ?", inactivo.curso());
    pedir(inactivo.curso())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(mensaje));

    Ofrecido incompleto = ofrecido(jdbc, "Incompleto", instructor, 2);
    jdbc.update("UPDATE courses SET short_description = NULL WHERE id = ?", incompleto.curso());
    pedir(incompleto.curso())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(mensaje));

    mvc.perform(
            get("/api/v1/courses/available/no-es-uuid").with(con(alumno, "courses:read-available")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`CA-AC-197` — el árbol es solo lo ofrecido, y las sumas cuentan solo lo que aparece")
  void elArbolOfrecido() throws Exception {
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    CourseTestSupport.leccion(jdbc, velas.modulo(), "Inactiva", "TEXTO", "# x", 7, 1, "INACTIVO");
    CourseTestSupport.leccion(jdbc, velas.modulo(), "Vacía", "TEXTO", null, 9, 2, "ACTIVO");
    UUID retirada = CourseTestSupport.leccionActiva(jdbc, velas.modulo(), "Retirada", 11);
    CourseTestSupport.retirarLeccion(jdbc, retirada);
    UUID apagado = CourseTestSupport.modulo(jdbc, velas.curso(), "Apagado", 1, "INACTIVO");
    CourseTestSupport.leccionActiva(jdbc, apagado, "Escondida", 500);
    UUID sinLecciones = CourseTestSupport.modulo(jdbc, velas.curso(), "Sin lecciones", 2, "ACTIVO");
    UUID quitado = CourseTestSupport.modulo(jdbc, velas.curso(), "Quitado", 3, "ACTIVO");
    CourseTestSupport.leccionActiva(jdbc, quitado, "De un retirado", 13);
    CourseTestSupport.retirarModulo(jdbc, quitado);
    UUID segundo = CourseTestSupport.modulo(jdbc, velas.curso(), "Segundo", 4, "ACTIVO");
    UUID demo = CourseTestSupport.leccionActiva(jdbc, segundo, "Demo", 40);
    abrirLeccion(jdbc, demo);

    pedir(velas.curso())
        .andExpect(jsonPath("$.modules[*].title").value(contains("Módulo de Velas", "Segundo")))
        .andExpect(jsonPath("$.modules[0].lessons[*].title").value(contains("Lección de Velas")))
        .andExpect(jsonPath("$.modules[0].durationSeconds").value(60))
        .andExpect(jsonPath("$.modules[1].durationSeconds").value(40))
        .andExpect(jsonPath("$.totalDurationSeconds").value(100))
        .andExpect(jsonPath("$.lessonCount").value(2))
        .andExpect(jsonPath("$.openLessonCount").value(1));
    assertThat(sinLecciones).isNotNull();
  }

  @Test
  @DisplayName(
      "`CA-AC-198` — accessible del curso por membresía, servicio o gratuidad; el de la lección es"
          + " el del curso o open")
  void queSeLeAbre() throws Exception {
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    UUID demo = CourseTestSupport.leccionActiva(jdbc, velas.modulo(), "Demo", 10);
    abrirLeccion(jdbc, demo);

    // Sin llaves: de todos.
    pedir(velas.curso())
        .andExpect(jsonPath("$.accessible").value(true))
        .andExpect(jsonPath("$.modules[0].lessons[*].accessible").value(contains(true, true)));

    abrirAMembresia(jdbc, velas.curso(), ORO);
    pedir(velas.curso())
        .andExpect(jsonPath("$.accessible").value(false))
        .andExpect(
            jsonPath("$.modules[0].lessons[*].title").value(contains("Lección de Velas", "Demo")))
        .andExpect(jsonPath("$.modules[0].lessons[*].accessible").value(contains(false, true)));

    UUID bot = ClassroomTestSupport.servicio(jdbc, "ACL_BOT");
    abrirAServicio(jdbc, velas.curso(), bot);
    ClassroomTestSupport.conServicio(jdbc, alumno, bot, -1, null);
    pedir(velas.curso())
        .andExpect(jsonPath("$.accessible").value(true))
        .andExpect(jsonPath("$.modules[0].lessons[*].accessible").value(contains(true, true)));

    ClassroomTestSupport.cerrarServicios(jdbc, alumno);
    ClassroomTestSupport.conMembresiaVencida(jdbc, alumno);
    pedir(velas.curso())
        .andExpect(jsonPath("$.currentMembership").value(nullValue()))
        .andExpect(jsonPath("$.accessible").value(false))
        .andExpect(jsonPath("$.modules[0].lessons[*].accessible").value(contains(false, true)));

    ClassroomTestSupport.conMembresia(jdbc, alumno, ORO);
    jdbc.update(
        "UPDATE user_products SET started_at = now() - interval '1 day', ends_at = NULL"
            + " WHERE user_id = ? AND membership_id IS NOT NULL",
        alumno);
    pedir(velas.curso()).andExpect(jsonPath("$.accessible").value(true));
  }

  @Test
  @DisplayName(
      "`CA-AC-200` — seis sentencias más las de los dos puertos; sin módulos ofrecibles, una menos;"
          + " el 404 cuesta una")
  void cuentaDeSentencias() throws Exception {
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);

    estadisticas.clear();
    pedir(velas.curso()).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(8);

    estadisticas.clear();
    pedir(UUID.randomUUID()).andExpect(status().isNotFound());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-AC-201` — sin courses:read-available, 403 aunque porte courses:read y courses:learn")
  void permiso() throws Exception {
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    mvc.perform(
            get("/api/v1/courses/available/" + velas.curso())
                .with(con(alumno, "courses:read", "courses:learn", "lessons:learn")))
        .andExpect(status().isForbidden());
  }
}
