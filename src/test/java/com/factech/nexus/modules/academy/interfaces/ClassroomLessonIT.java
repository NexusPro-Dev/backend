package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ORO;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.PLATINO;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.abrirAMembresia;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.abrirAServicio;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.abrirLeccion;
import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ofrecido;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
 * El contenido de una lección: `RF-AC-035` (`CA-AC-202` a `CA-AC-208`, `CA-AC-239`).
 *
 * <p><b>Las que definen el requerimiento son `CA-AC-204`</b> —el `403` con las listas— <b>y
 * `CA-AC-206`</b> —`404` antes que `403`—.
 */
@AutoConfigureMockMvc
class ClassroomLessonIT extends IntegrationTestBase {

  private static final String NO_EXISTE =
      "No existe una lección con ese identificador en ese curso.";

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

  private ResultActions pedir(UUID curso, UUID leccion) throws Exception {
    return mvc.perform(
        get("/api/v1/courses/available/" + curso + "/lessons/" + leccion)
            .with(con(alumno, "lessons:learn")));
  }

  @Test
  @DisplayName(
      "`CA-AC-202` — con la membresía de la lista, el contenido de VIDEO y de TEXTO byte a byte,"
          + " <script> incluido")
  void elContenidoTalCual() throws Exception {
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    abrirAMembresia(jdbc, velas.curso(), ORO);
    ClassroomTestSupport.conMembresia(jdbc, alumno, ORO);
    String markdown = "# Título\n\n<script>alert(1)</script>\n\n*énfasis*";
    UUID texto =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Texto", "TEXTO", markdown, 30, 1, "ACTIVO");
    UUID video =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Video", "VIDEO", "https://youtu.be/abc123", 90, 2, "ACTIVO");

    pedir(velas.curso(), texto)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value(markdown))
        .andExpect(jsonPath("$.courseId").value(velas.curso().toString()))
        .andExpect(jsonPath("$.moduleId").value(velas.modulo().toString()))
        .andExpect(jsonPath("$.description").hasJsonPath())
        .andExpect(jsonPath("$.status").doesNotExist())
        .andExpect(jsonPath("$.accessible").doesNotExist());
    pedir(velas.curso(), video)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("VIDEO"))
        .andExpect(jsonPath("$.content").value("https://youtu.be/abc123"));
  }

  @Test
  @DisplayName(
      "`CA-AC-203` — la abierta, a cualquiera con lessons:learn; la cerrada de un curso sin"
          + " llaves, también")
  void abiertaYGratuita() throws Exception {
    Ofrecido libre = ofrecido(jdbc, "Libre", instructor, 0);
    pedir(libre.curso(), libre.leccion()).andExpect(status().isOk());

    Ofrecido deOro = ofrecido(jdbc, "De oro", instructor, 1);
    abrirAMembresia(jdbc, deOro.curso(), ORO);
    abrirLeccion(jdbc, deOro.leccion());
    ClassroomTestSupport.conMembresia(jdbc, alumno, PLATINO);
    pedir(deOro.curso(), deOro.leccion()).andExpect(status().isOk());
    ClassroomTestSupport.conMembresiaVencida(jdbc, alumno);
    pedir(deOro.curso(), deOro.leccion()).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "`CA-AC-204` — la cerrada responde 403 EX-002 con memberships y products, fuera de la lista y"
          + " sin vigente; PLATINO no abre ORO")
  void cerradaConInvitacion() throws Exception {
    Ofrecido deOro = ofrecido(jdbc, "De oro", instructor, 0);
    abrirAMembresia(jdbc, deOro.curso(), ORO);
    UUID bot = ClassroomTestSupport.servicio(jdbc, "ACL_BOT");
    abrirAServicio(jdbc, deOro.curso(), bot);
    ClassroomTestSupport.conMembresia(jdbc, alumno, PLATINO);

    pedir(deOro.curso(), deOro.leccion())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Ni tu membresía ni tus servicios abren este curso."))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.memberships[*].code").value(contains("ORO")))
        .andExpect(jsonPath("$.memberships[0].color").value("FFB300"))
        .andExpect(jsonPath("$.products[*].code").value(contains("ACL_BOT")))
        .andExpect(jsonPath("$.products[0].name").value("Servicio ACL_BOT"));

    ClassroomTestSupport.conMembresiaVencida(jdbc, alumno);
    pedir(deOro.curso(), deOro.leccion())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.memberships", hasSize(1)));

    // Y no se audita: no es una intrusión.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_security_log WHERE detail::text LIKE '%EX-002%'",
                Long.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-AC-205` — 404 con el mismo mensaje por cada motivo de cada nivel; id mal formado, 400")
  void noSeOfrece() throws Exception {
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    Ofrecido otro = ofrecido(jdbc, "Otro", instructor, 1);

    noExiste(velas.curso(), UUID.randomUUID());
    noExiste(otro.curso(), velas.leccion());

    UUID inactiva =
        CourseTestSupport.leccion(
            jdbc, velas.modulo(), "Inactiva", "TEXTO", "# x", 5, 1, "INACTIVO");
    noExiste(velas.curso(), inactiva);
    UUID vacia =
        CourseTestSupport.leccion(jdbc, velas.modulo(), "Vacía", "TEXTO", null, 5, 2, "ACTIVO");
    noExiste(velas.curso(), vacia);
    UUID retirada = CourseTestSupport.leccionActiva(jdbc, velas.modulo(), "Retirada", 5);
    CourseTestSupport.retirarLeccion(jdbc, retirada);
    noExiste(velas.curso(), retirada);

    UUID apagado = CourseTestSupport.modulo(jdbc, velas.curso(), "Apagado", 1, "INACTIVO");
    UUID enApagado = CourseTestSupport.leccionActiva(jdbc, apagado, "En apagado", 5);
    noExiste(velas.curso(), enApagado);
    UUID quitado = CourseTestSupport.modulo(jdbc, velas.curso(), "Quitado", 2, "ACTIVO");
    UUID enQuitado = CourseTestSupport.leccionActiva(jdbc, quitado, "En quitado", 5);
    CourseTestSupport.retirarModulo(jdbc, quitado);
    noExiste(velas.curso(), enQuitado);

    jdbc.update("UPDATE courses SET long_description = NULL WHERE id = ?", otro.curso());
    noExiste(otro.curso(), otro.leccion());

    mvc.perform(
            get("/api/v1/courses/available/no-es-uuid/lessons/" + velas.leccion())
                .with(con(alumno, "lessons:learn")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`CA-AC-206` — 404 antes que 403: la cerrada y la abierta de un curso que no se ofrece")
  void noSeOfreceAntesQueNoSeAbre() throws Exception {
    Ofrecido deOro = ofrecido(jdbc, "De oro", instructor, 0);
    abrirAMembresia(jdbc, deOro.curso(), ORO);
    UUID abierta = CourseTestSupport.leccionActiva(jdbc, deOro.modulo(), "Abierta", 5);
    abrirLeccion(jdbc, abierta);
    jdbc.update("UPDATE courses SET status = 'INACTIVO' WHERE id = ?", deOro.curso());

    noExiste(deOro.curso(), deOro.leccion());
    noExiste(deOro.curso(), abierta);
  }

  @Test
  @DisplayName(
      "`CA-AC-207` — una sentencia la abierta, la gratuita y el 404; tres la cerrada más los"
          + " puertos")
  void cuentaDeSentencias() throws Exception {
    Ofrecido libre = ofrecido(jdbc, "Libre", instructor, 0);
    Ofrecido deOro = ofrecido(jdbc, "De oro", instructor, 1);
    abrirAMembresia(jdbc, deOro.curso(), ORO);
    UUID abierta = CourseTestSupport.leccionActiva(jdbc, deOro.modulo(), "Abierta", 5);
    abrirLeccion(jdbc, abierta);
    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);

    estadisticas.clear();
    pedir(libre.curso(), libre.leccion()).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);

    estadisticas.clear();
    pedir(deOro.curso(), abierta).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);

    estadisticas.clear();
    pedir(deOro.curso(), UUID.randomUUID()).andExpect(status().isNotFound());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);

    estadisticas.clear();
    pedir(deOro.curso(), deOro.leccion()).andExpect(status().isForbidden());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(5);
  }

  @Test
  @DisplayName(
      "`CA-AC-208` — sin lessons:learn, 403 sin memberships aunque porte courses:read y"
          + " courses:learn")
  void permiso() throws Exception {
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    mvc.perform(
            get("/api/v1/courses/available/" + velas.curso() + "/lessons/" + velas.leccion())
                .with(con(alumno, "courses:read", "courses:learn", "courses:read-available")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.memberships").doesNotExist())
        .andExpect(jsonPath("$.errors", hasSize(0)));
  }

  @Test
  @DisplayName("`CA-AC-239` — un servicio vigente abre la cerrada; vencido, 403")
  void elServicioAbre() throws Exception {
    Ofrecido conBot = ofrecido(jdbc, "Con bot", instructor, 0);
    UUID bot = ClassroomTestSupport.servicio(jdbc, "ACL_BOT");
    abrirAServicio(jdbc, conBot.curso(), bot);

    ClassroomTestSupport.conServicio(jdbc, alumno, bot, -10, -1);
    pedir(conBot.curso(), conBot.leccion()).andExpect(status().isForbidden());

    ClassroomTestSupport.conServicio(jdbc, alumno, bot, -1, 30);
    pedir(conBot.curso(), conBot.leccion()).andExpect(status().isOk());
  }

  private void noExiste(UUID curso, UUID leccion) throws Exception {
    pedir(curso, leccion)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(NO_EXISTE));
  }
}
