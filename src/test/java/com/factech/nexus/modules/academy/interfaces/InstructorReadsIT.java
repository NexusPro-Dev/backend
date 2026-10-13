package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.ClassroomTestSupport.ORO;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.cuerpo;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.ids;
import static com.factech.nexus.modules.academy.interfaces.LiveSessionTestSupport.manana;
import static org.assertj.core.api.Assertions.assertThat;
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
 * La lectura propia del instructor, `RF-AC-056` (`CA-AC-312` a `CA-AC-314`), y lo que `V105` dejó
 * en el esquema y en el catálogo (`CA-AC-323`): sin curso, sin `GET /courses/mine`, con los cuatro
 * permisos de portada.
 */
@AutoConfigureMockMvc
class InstructorReadsIT extends IntegrationTestBase {

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

  private UUID programar(UUID quien) throws Exception {
    String respuesta =
        mvc.perform(
                post(MIAS)
                    .with(con(quien, "live-sessions:create-own"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo("Clase", null, manana(19), manana(20), ids(ORO), "")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(respuesta, "$.id"));
  }

  @Test
  @DisplayName(
      "`CA-AC-312`, `CA-AC-313` — el detalle entero de una clase suya, también cancelada; la"
          + " ajena, la sin instructores y la inexistente, 404")
  void detalle() throws Exception {
    UUID mia = programar(profe);
    UUID deOtro = programar(otroProfe);
    UUID sinNadie = LiveSessionTestSupport.clase(jdbc, null, profe, 60, 120, 77L);

    String respuesta =
        mvc.perform(get(MIAS + "/" + mia).with(con(profe, "live-sessions:read-own")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Agenda de Clase"))
            .andExpect(jsonPath("$.instructors[0].id").value(profe.toString()))
            .andExpect(jsonPath("$.instructors[0].fullName").value("Pablo Profe"))
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
    for (UUID otra : new UUID[] {deOtro, sinNadie}) {
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
      "`CA-AC-314`, `CA-AC-323` — cada ruta con su permiso; V105 quitó el curso y"
          + " courses:list-own, y sembró los cuatro de portada")
  void permisosYEsquema() throws Exception {
    UUID mia = programar(profe);
    mvc.perform(
            get(MIAS + "/" + mia).with(con(profe, "live-sessions:read", "live-sessions:list-own")))
        .andExpect(status().isForbidden());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns"
                    + " WHERE table_name = 'live_sessions' AND column_name = 'course_id'",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM permissions WHERE code = 'courses:list-own'", Long.class))
        .isZero();
    mvc.perform(get("/api/v1/courses/mine").with(con(profe, "courses:read")))
        .andExpect(status().is4xxClientError());

    String sinMaestro =
        """
        SELECT count(*) FROM role_permissions rp
          JOIN permissions p ON p.id = rp.permission_id AND p.code = ?
         WHERE NOT EXISTS (SELECT 1 FROM role_permissions x
                             JOIN permissions t ON t.id = x.permission_id
                            WHERE x.role_id = rp.role_id AND t.code = 'courses:teach')
        """;
    // Los roles sembrados por las migraciones: el de instructor de la prueba nace después.
    String conMaestro =
        """
        SELECT count(*) FROM role_permissions rp
          JOIN permissions t ON t.id = rp.permission_id AND t.code = 'courses:teach'
         WHERE rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                              '01a02a33-4c00-7002-9c4f-5e7ad1000002')
           AND NOT EXISTS (SELECT 1 FROM role_permissions x
                             JOIN permissions p ON p.id = x.permission_id
                            WHERE x.role_id = rp.role_id AND p.code = ?)
        """;
    for (String codigo :
        new String[] {
          "live-sessions:read-own", "live-sessions:set-cover-own", "live-sessions:remove-cover-own"
        }) {
      assertThat(jdbc.queryForObject(sinMaestro, Long.class, codigo)).as(codigo).isZero();
      assertThat(jdbc.queryForObject(conMaestro, Long.class, codigo)).as(codigo).isZero();
    }
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permissions rp
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE p.code IN ('live-sessions:set-cover', 'live-sessions:remove-cover')
                """,
                Long.class))
        .isEqualTo(4);
  }
}
