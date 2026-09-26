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
 * El catálogo del alumno: `RF-AC-033` (`CA-AC-186` a `CA-AC-193`, `CA-AC-237`, `CA-AC-238`).
 *
 * <p><b>La que define el requerimiento es `CA-AC-238`</b>: «los cursos que puedo ver», con {@code
 * onlyAccessible}.
 */
@AutoConfigureMockMvc
class ClassroomCatalogIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/courses/available";

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

  private ResultActions pedir(String consulta) throws Exception {
    return mvc.perform(get(RUTA + consulta).with(con(alumno, "courses:learn")));
  }

  @Test
  @DisplayName(
      "`CA-AC-186` — fuera por cada uno de los cuatro motivos de RN-AC-015; el curso sin llaves se"
          + " enseña y es accessible para todos")
  void soloLoOfrecido() throws Exception {
    Ofrecido libre = ofrecido(jdbc, "Libre", instructor, 0);
    Ofrecido retirado = ofrecido(jdbc, "Retirado", instructor, 1);
    CourseTestSupport.retirar(jdbc, retirado.curso());
    Ofrecido inactivo = ofrecido(jdbc, "Inactivo", instructor, 2);
    jdbc.update("UPDATE courses SET status = 'INACTIVO' WHERE id = ?", inactivo.curso());
    Ofrecido sinDescripcion = ofrecido(jdbc, "Sin descripción", instructor, 3);
    jdbc.update("UPDATE courses SET long_description = NULL WHERE id = ?", sinDescripcion.curso());
    Ofrecido sinModulo = ofrecido(jdbc, "Sin módulo", instructor, 4);
    jdbc.update("UPDATE lessons SET content = NULL WHERE id = ?", sinModulo.leccion());

    pedir("")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses", hasSize(1)))
        .andExpect(jsonPath("$.courses[0].id").value(libre.curso().toString()))
        .andExpect(jsonPath("$.courses[0].accessible").value(true))
        .andExpect(jsonPath("$.courses[0].coverImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.courses[0].instructor.fullName").value("Juan Pérez"));
  }

  @Test
  @DisplayName(
      "`CA-AC-187` — la membresía de la lista abre; otra no; sin vigente o vencida, false y"
          + " currentMembership nula")
  void laMarcaPorMembresia() throws Exception {
    Ofrecido deOro = ofrecido(jdbc, "De oro", instructor, 0);
    abrirAMembresia(jdbc, deOro.curso(), ORO);

    pedir("")
        .andExpect(jsonPath("$.currentMembership.code").value("BECA"))
        .andExpect(jsonPath("$.courses[0].accessible").value(false));

    ClassroomTestSupport.conMembresia(jdbc, alumno, ORO);
    pedir("")
        .andExpect(jsonPath("$.currentMembership.code").value("ORO"))
        .andExpect(jsonPath("$.currentMembership.color").value("FFB300"))
        .andExpect(jsonPath("$.courses[0].accessible").value(true));

    ClassroomTestSupport.conMembresiaVencida(jdbc, alumno);
    pedir("")
        .andExpect(jsonPath("$.currentMembership").value(nullValue()))
        .andExpect(jsonPath("$.courses", hasSize(1)))
        .andExpect(jsonPath("$.courses[0].accessible").value(false));
  }

  @Test
  @DisplayName("`CA-AC-188` — la lista es exacta: un curso de ORO no se abre a PLATINO")
  void laListaEsExacta() throws Exception {
    Ofrecido deOro = ofrecido(jdbc, "De oro", instructor, 0);
    abrirAMembresia(jdbc, deOro.curso(), ORO);
    ClassroomTestSupport.conMembresia(jdbc, alumno, PLATINO);

    pedir("").andExpect(jsonPath("$.courses[0].accessible").value(false));
  }

  @Test
  @DisplayName(
      "`CA-AC-189` — las categorías vivas, en orden, con las vacías y sin filtro; la retirada no"
          + " sale y su curso sí (hace real CA-AC-032)")
  void losCajones() throws Exception {
    UUID trading = CourseCategoryTestSupport.categoria(jdbc, "Trading", 1);
    UUID vacia = CourseCategoryTestSupport.categoria(jdbc, "Vacía", 0);
    UUID retirada = CourseCategoryTestSupport.categoria(jdbc, "Retirada", 2);
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    clasificar(velas.curso(), trading);
    clasificar(velas.curso(), retirada);
    CourseCategoryTestSupport.retirar(jdbc, retirada);

    pedir("?categoryId=" + trading)
        .andExpect(jsonPath("$.categories[*].name").value(contains("Vacía", "Trading")))
        .andExpect(jsonPath("$.categories[0].id").value(vacia.toString()))
        .andExpect(jsonPath("$.categories[0].coverImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.courses", hasSize(1)))
        .andExpect(jsonPath("$.courses[0].categories[*].name").value(contains("Trading")));
  }

  @Test
  @DisplayName(
      "`CA-AC-190` — categoryId y difficulty acotan; categoría retirada o inexistente da vacío;"
          + " dificultad fuera de dominio, id mal formado u onlyAccessible no booleano, 400")
  void losFiltros() throws Exception {
    UUID trading = CourseCategoryTestSupport.categoria(jdbc, "Trading", 0);
    Ofrecido velas = ofrecido(jdbc, "Velas", instructor, 0);
    Ofrecido otro = ofrecido(jdbc, "Otro", instructor, 1);
    jdbc.update("UPDATE courses SET difficulty = 'AVANZADO' WHERE id = ?", otro.curso());
    clasificar(velas.curso(), trading);

    pedir("?categoryId=" + trading)
        .andExpect(jsonPath("$.courses[*].title").value(contains("Velas")));
    pedir("?difficulty=AVANZADO").andExpect(jsonPath("$.courses[*].title").value(contains("Otro")));
    pedir("?categoryId=" + UUID.randomUUID())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses", hasSize(0)))
        .andExpect(jsonPath("$.categories", hasSize(1)));
    CourseCategoryTestSupport.retirar(jdbc, trading);
    pedir("?categoryId=" + trading).andExpect(jsonPath("$.courses", hasSize(0)));

    pedir("?difficulty=EXPERTO")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    pedir("?categoryId=no-es-uuid").andExpect(status().isBadRequest());
    pedir("?onlyAccessible=quizas").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`CA-AC-191` — en su orden; duración, lecciones y abiertas cuentan solo lo ofrecible de lo"
          + " ofrecible")
  void ordenYCuentas() throws Exception {
    Ofrecido segundo = ofrecido(jdbc, "Segundo", instructor, 1);
    Ofrecido primero = ofrecido(jdbc, "Primero", instructor, 0);
    // En el módulo ofrecible: una más ofrecible y abierta, una inactiva, una
    // vacía y una retirada, que no suman.
    UUID abierta = CourseTestSupport.leccionActiva(jdbc, primero.modulo(), "Abierta", 30);
    abrirLeccion(jdbc, abierta);
    UUID inactiva =
        CourseTestSupport.leccion(
            jdbc, primero.modulo(), "Inactiva", "TEXTO", "# x", 7, 2, "INACTIVO");
    abrirLeccion(jdbc, inactiva);
    CourseTestSupport.leccion(jdbc, primero.modulo(), "Vacía", "TEXTO", null, 9, 3, "ACTIVO");
    UUID retirada = CourseTestSupport.leccionActiva(jdbc, primero.modulo(), "Retirada", 11);
    CourseTestSupport.retirarLeccion(jdbc, retirada);
    // Un módulo inactivo con una lección ofrecible, que tampoco suma.
    UUID apagado = CourseTestSupport.modulo(jdbc, primero.curso(), "Apagado", 1, "INACTIVO");
    CourseTestSupport.leccionActiva(jdbc, apagado, "Escondida", 500);

    pedir("")
        .andExpect(jsonPath("$.courses[*].title").value(contains("Primero", "Segundo")))
        .andExpect(jsonPath("$.courses[0].totalDurationSeconds").value(90))
        .andExpect(jsonPath("$.courses[0].lessonCount").value(2))
        .andExpect(jsonPath("$.courses[0].openLessonCount").value(1))
        .andExpect(jsonPath("$.courses[1].id").value(segundo.curso().toString()))
        .andExpect(jsonPath("$.courses[1].openLessonCount").value(0));
  }

  @Test
  @DisplayName(
      "`CA-AC-192` — cuatro sentencias fijas más las de los dos puertos, con muchos; con cero, dos"
          + " menos")
  void cuentaDeSentencias() throws Exception {
    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);

    estadisticas.clear();
    pedir("").andExpect(status().isOk());
    long sinCursos = estadisticas.getPrepareStatementCount();

    for (int i = 0; i < 5; i++) {
      Ofrecido curso = ofrecido(jdbc, "Curso " + i, instructor, i);
      abrirAMembresia(jdbc, curso.curso(), ORO);
    }
    estadisticas.clear();
    pedir("").andExpect(jsonPath("$.courses", hasSize(5)));
    long conCursos = estadisticas.getPrepareStatementCount();

    // Dos puertos (una sentencia cada uno) + candidatos + categorías vivas, y
    // con cursos además llaves y categorías de los cursos.
    assertThat(sinCursos).isEqualTo(4);
    assertThat(conCursos).isEqualTo(6);
  }

  @Test
  @DisplayName(
      "`CA-AC-193` — sin courses:learn, 403 aunque porte courses:read; /available no cae en /{id}")
  void permiso() throws Exception {
    mvc.perform(get(RUTA).with(con(alumno, "courses:read", "courses:read-available")))
        .andExpect(status().isForbidden());
    pedir("").andExpect(status().isOk()).andExpect(jsonPath("$.courses").isArray());
  }

  @Test
  @DisplayName("`CA-AC-237` — un servicio vigente abre; vencido, cerrado o con inicio futuro no")
  void elServicioVigenteAbre() throws Exception {
    Ofrecido conBot = ofrecido(jdbc, "Con bot", instructor, 0);
    UUID bot = ClassroomTestSupport.servicio(jdbc, "ACL_BOT");
    abrirAServicio(jdbc, conBot.curso(), bot);

    pedir("").andExpect(jsonPath("$.courses[0].accessible").value(false));

    ClassroomTestSupport.conServicio(jdbc, alumno, bot, 1, 30);
    pedir("").andExpect(jsonPath("$.courses[0].accessible").value(false));

    ClassroomTestSupport.conServicio(jdbc, alumno, bot, -10, -1);
    pedir("").andExpect(jsonPath("$.courses[0].accessible").value(false));

    ClassroomTestSupport.conServicio(jdbc, alumno, bot, -1, null);
    pedir("").andExpect(jsonPath("$.courses[0].accessible").value(true));

    ClassroomTestSupport.cerrarServicios(jdbc, alumno);
    pedir("").andExpect(jsonPath("$.courses[0].accessible").value(false));
  }

  @Test
  @DisplayName(
      "`CA-AC-238` — onlyAccessible deja los accesibles y los cerrados con lección abierta"
          + " ofrecible; quita los demás; sin el filtro salen todos")
  void losCursosQuePuedoVer() throws Exception {
    ofrecido(jdbc, "Libre", instructor, 0);
    Ofrecido conDemo = ofrecido(jdbc, "Con demostración", instructor, 1);
    abrirAMembresia(jdbc, conDemo.curso(), ORO);
    abrirLeccion(jdbc, conDemo.leccion());
    Ofrecido cerrado = ofrecido(jdbc, "Cerrado", instructor, 2);
    abrirAMembresia(jdbc, cerrado.curso(), ORO);
    // Una lección abierta pero inactiva no cuenta como demostración.
    UUID apagada =
        CourseTestSupport.leccion(
            jdbc, cerrado.modulo(), "Apagada", "TEXTO", "# x", 5, 1, "INACTIVO");
    abrirLeccion(jdbc, apagada);
    Ofrecido mio = ofrecido(jdbc, "Mío", instructor, 3);
    abrirAMembresia(jdbc, mio.curso(), PLATINO);
    ClassroomTestSupport.conMembresia(jdbc, alumno, PLATINO);

    pedir("?onlyAccessible=true")
        .andExpect(
            jsonPath("$.courses[*].title").value(contains("Libre", "Con demostración", "Mío")))
        .andExpect(jsonPath("$.courses[1].accessible").value(false))
        .andExpect(jsonPath("$.courses[1].openLessonCount").value(1));
    pedir("").andExpect(jsonPath("$.courses", hasSize(4)));
    pedir("?onlyAccessible=false").andExpect(jsonPath("$.courses", hasSize(4)));
  }

  private void clasificar(UUID curso, UUID categoria) {
    jdbc.update(
        "INSERT INTO course_category_items (course_id, category_id) VALUES (?, ?)",
        curso,
        categoria);
  }
}
