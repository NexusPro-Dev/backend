package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.modulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** La corrección del módulo (`RF-AC-023`): `CA-AC-094` a `CA-AC-098`. */
@AutoConfigureMockMvc
class CourseModuleUpdateIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID curso;
  private UUID modulo;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0);
    modulo = modulo(jdbc, curso, "Fundamentos", 0, "INACTIVO");
    jdbc.update(
        "UPDATE course_modules SET short_description = 'Corta', long_description = 'Larga',"
            + " presentation_video_url = 'https://v.io/m' WHERE id = ?",
        modulo);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-094` — corrige los cinco campos juntos y devuelve el detalle del módulo con updatedAt avanzado")
  void corrigeLosCinco() throws Exception {
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM course_modules WHERE id = ?", String.class, modulo);
    mvc.perform(
            corregir(
                curso,
                modulo,
                """
                {"title":"Bases","shortDescription":"Otra","longDescription":"Otra larga",
                 "presentationVideoUrl":"https://v.io/2","displayOrder":7}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Bases"))
        .andExpect(jsonPath("$.shortDescription").value("Otra"))
        .andExpect(jsonPath("$.presentationVideoUrl").value("https://v.io/2"))
        .andExpect(jsonPath("$.displayOrder").value(7))
        .andExpect(jsonPath("$.courseId").value(curso.toString()));
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at::text FROM course_modules WHERE id = ?", String.class, modulo))
        .isNotEqualTo(antes);
  }

  @Test
  @DisplayName(
      "`CA-AC-095` — el nulo vacía descripciones y video —también en un módulo ACTIVO— y se rechaza en título y orden, juntos")
  void elNulo() throws Exception {
    jdbc.update("UPDATE course_modules SET status = 'ACTIVO' WHERE id = ?", modulo);
    mvc.perform(
            corregir(
                curso,
                modulo,
                "{\"shortDescription\":null,\"longDescription\":null,\"presentationVideoUrl\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortDescription").value(nullValue()))
        .andExpect(jsonPath("$.presentationVideoUrl").value(nullValue()))
        .andExpect(jsonPath("$.status").value("ACTIVO"));
    mvc.perform(corregir(curso, modulo, "{\"title\":null,\"displayOrder\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItems("title", "displayOrder")));
  }

  @Test
  @DisplayName(
      "`CA-AC-096` — 400 un cuerpo vacío y uno con courseId, status, lessons o coverImageUrl")
  void cuerpoVacioYNoAdmitidos() throws Exception {
    mvc.perform(corregir(curso, modulo, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
    for (String extra :
        List.of(
            "\"courseId\":\"" + curso + "\"",
            "\"status\":\"ACTIVO\"",
            "\"lessons\":[]",
            "\"coverImageUrl\":\"/x\"")) {
      mvc.perform(corregir(curso, modulo, "{\"title\":\"X\"," + extra + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString("no admite")));
    }
  }

  @Test
  @DisplayName(
      "`CA-AC-097` — 409 el título de OTRO vivo del curso; se admite el de un retirado, el mismo en"
          + " otro curso y la caja del propio; 404 el retirado, inexistente o de otro curso")
  void tituloYPertenencia() throws Exception {
    UUID otro = modulo(jdbc, curso, "Otro", 1, "INACTIVO");
    UUID retirado = modulo(jdbc, curso, "Retirado", 2, "INACTIVO");
    CourseTestSupport.retirarModulo(jdbc, retirado);
    UUID otroCurso = curso(jdbc, "Otro curso", instructor, 1);
    modulo(jdbc, otroCurso, "Ajeno", 0, "INACTIVO");

    mvc.perform(corregir(curso, modulo, "{\"title\":\" otro \"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
    mvc.perform(corregir(curso, modulo, "{\"title\":\"Retirado\"}")).andExpect(status().isOk());
    mvc.perform(corregir(curso, modulo, "{\"title\":\"Ajeno\"}")).andExpect(status().isOk());
    mvc.perform(corregir(curso, otro, "{\"title\":\"OTRO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("OTRO"));

    mvc.perform(corregir(curso, retirado, "{\"title\":\"Vuelve\"}"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.detail")
                .value("No existe un módulo vivo con ese identificador en este curso."));
    mvc.perform(corregir(curso, UUID.randomUUID(), "{\"title\":\"Nada\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(corregir(otroCurso, modulo, "{\"title\":\"Ajeno2\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-098` — sin cambios no audita; con cambios la fila UPDATE lleva solo lo que cambió y el detalle del curso enseña el orden nuevo")
  void sinCambiosYConCambios() throws Exception {
    mvc.perform(
            corregir(curso, modulo, "{\"title\":\" Fundamentos \",\"shortDescription\":\"Corta\"}"))
        .andExpect(status().isOk());
    assertThat(auditadas()).isZero();

    modulo(jdbc, curso, "Otro", 1, "INACTIVO");
    mvc.perform(corregir(curso, modulo, "{\"displayOrder\":5,\"title\":\"Fundamentos\"}"))
        .andExpect(status().isOk());
    assertThat(auditadas()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT changes::text FROM audit_change_log WHERE entity = 'course_modules' AND entity_id = ? AND action = 'UPDATE'",
                String.class,
                modulo))
        .contains("display_order")
        .doesNotContain("title");
    mvc.perform(get("/api/v1/courses/" + curso).with(con("courses:read")))
        .andExpect(jsonPath("$.modules[*].title").value(contains("Otro", "Fundamentos")));
  }

  private MockHttpServletRequestBuilder corregir(UUID curso, UUID modulo, String cuerpo) {
    return patch("/api/v1/courses/" + curso + "/modules/" + modulo)
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private int auditadas() {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity = 'course_modules' AND entity_id = ? AND action = 'UPDATE'",
            Integer.class,
            modulo);
    return filas == null ? 0 : filas;
  }
}
