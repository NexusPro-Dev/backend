package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ORO;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.cuerpo;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.ids;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.manana;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Las dos lecturas del instructor: `RF-AC-056` (`CA-AC-312` a `CA-AC-314`), el detalle de una clase
 * de sus cursos, y `RF-AC-057` (`CA-AC-315`, `CA-AC-316`), los cursos que dicta.
 */
@AutoConfigureMockMvc
class InstructorReadsIT extends IntegrationTestBase {

  private static final String MIAS = "/api/v1/live-sessions/mine";
  private static final String MIS_CURSOS = "/api/v1/courses/mine";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeZoomMeetings zoom;

  private UUID profe;
  private UUID otroProfe;
  private UUID suyo;
  private UUID ajeno;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
    profe = CourseTestSupport.persona(jdbc, "Pablo", "Profe", null);
    otroProfe = CourseTestSupport.persona(jdbc, "Olga", "Otra", null);
    suyo = CourseTestSupport.curso(jdbc, "Suyo", profe, 1);
    ajeno = CourseTestSupport.curso(jdbc, "Ajeno", otroProfe, 0);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
    zoom.reiniciar();
  }

  private UUID programar(UUID quien, UUID curso) throws Exception {
    String respuesta =
        mvc.perform(
                post(MIAS)
                    .with(con(quien, "live-sessions:create-own"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo("Clase", curso, manana(19), manana(20), ids(ORO), "")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(respuesta, "$.id"));
  }

  @Test
  @DisplayName(
      "`CA-AC-312`, `CA-AC-313` — el detalle entero de una clase suya, también cancelada; la"
          + " ajena, la suelta y la inexistente, 404")
  void detalle() throws Exception {
    UUID mia = programar(profe, suyo);
    UUID deOtro = programar(otroProfe, ajeno);
    UUID suelta = LiveSessionTestSupport.clase(jdbc, null, profe, 60, 120, 77L);

    String respuesta =
        mvc.perform(get(MIAS + "/" + mia).with(con(profe, "live-sessions:read-own")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Agenda de Clase"))
            .andExpect(jsonPath("$.course.id").value(suyo.toString()))
            .andExpect(jsonPath("$.memberships[0].code").value("ORO"))
            .andExpect(jsonPath("$.registrations", hasSize(0)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(respuesta).doesNotContain("zoom.us");

    mvc.perform(
            post(MIAS + "/" + mia + "/cancellation")
                .with(con(profe, "live-sessions:cancel-own"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Se movió\"}"))
        .andExpect(status().isOk());
    mvc.perform(get(MIAS + "/" + mia).with(con(profe, "live-sessions:read-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELADA"));

    String noExiste =
        mvc.perform(get(MIAS + "/" + UUID.randomUUID()).with(con(profe, "live-sessions:read-own")))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (UUID otra : new UUID[] {deOtro, suelta}) {
      String ajena =
          mvc.perform(get(MIAS + "/" + otra).with(con(profe, "live-sessions:read-own")))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(JsonPath.<String>read(ajena, "$.detail"))
          .isEqualTo(JsonPath.<String>read(noExiste, "$.detail"));
    }
  }

  @Test
  @DisplayName(
      "`CA-AC-314`, `CA-AC-316` — cada ruta con su permiso; V104 los siembra donde está"
          + " courses:teach")
  void permisos() throws Exception {
    UUID mia = programar(profe, suyo);
    mvc.perform(
            get(MIAS + "/" + mia).with(con(profe, "live-sessions:read", "live-sessions:list-own")))
        .andExpect(status().isForbidden());
    mvc.perform(get(MIS_CURSOS).with(con(profe, "courses:teach")))
        .andExpect(status().isForbidden());

    String sinMaestro =
        """
        SELECT count(*) FROM role_permissions rp
          JOIN permissions p ON p.id = rp.permission_id AND p.code = ?
         WHERE NOT EXISTS (SELECT 1 FROM role_permissions x
                             JOIN permissions t ON t.id = x.permission_id
                            WHERE x.role_id = rp.role_id AND t.code = 'courses:teach')
        """;
    String conMaestro =
        """
        SELECT count(*) FROM role_permissions rp
          JOIN permissions t ON t.id = rp.permission_id AND t.code = 'courses:teach'
         WHERE NOT EXISTS (SELECT 1 FROM role_permissions x
                             JOIN permissions p ON p.id = x.permission_id
                            WHERE x.role_id = rp.role_id AND p.code = ?)
        """;
    for (String codigo : new String[] {"live-sessions:read-own", "courses:list-own"}) {
      assertThat(jdbc.queryForObject(sinMaestro, Long.class, codigo)).isZero();
      assertThat(jdbc.queryForObject(conMaestro, Long.class, codigo)).isZero();
    }
  }

  @Test
  @DisplayName(
      "`CA-AC-315`, `CA-AC-316` — los cursos no retirados que dicta, por orden; sin cursos, []")
  void misCursos() throws Exception {
    UUID segundo = CourseTestSupport.curso(jdbc, "Segundo", profe, 0);
    UUID retirado = CourseTestSupport.curso(jdbc, "Retirado", profe, 2);
    jdbc.update("UPDATE courses SET deleted_at = now() WHERE id = ?", retirado);

    mvc.perform(get(MIS_CURSOS).with(con(profe, "courses:list-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(contains(segundo.toString(), suyo.toString())))
        .andExpect(jsonPath("$[0].title").value("Segundo"))
        .andExpect(jsonPath("$[0].status").value("INACTIVO"));

    UUID sinCursos = CourseTestSupport.persona(jdbc, "Sara", "Sin", null);
    mvc.perform(get(MIS_CURSOS).with(con(sinCursos, "courses:list-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }
}
