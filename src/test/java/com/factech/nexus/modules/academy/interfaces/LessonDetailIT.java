package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.leccion;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.modulo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** El detalle de una lección para administración (`RF-AC-036`): `CA-AC-209` a `CA-AC-213`. */
@AutoConfigureMockMvc
class LessonDetailIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private ObjectMapper json;

  private Statistics estadisticas;
  private UUID instructor;
  private UUID curso;
  private UUID modulo;
  private UUID viva;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0);
    modulo = modulo(jdbc, curso, "Fundamentos", 0, "INACTIVO");
    viva = leccion(jdbc, modulo, "Viva", "TEXTO", "# Viva\n\nTexto.", 10, 0, "ACTIVO");
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-209` — devuelve la lección viva con su contenido, campo a campo igual que la respuesta del alta")
  void laMismaFormaQueElAlta() throws Exception {
    JsonNode alta =
        json.readTree(
            mvc.perform(
                    post("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons")
                        .with(con("courses:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"type\":\"VIDEO\",\"title\":\"Nueva\",\"content\":\"https://v.io/n\",\"durationMinutes\":4,\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode detalle =
        json.readTree(
            mvc.perform(detalle(UUID.fromString(alta.get("id").asText())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(detalle).isEqualTo(alta);

    mvc.perform(detalle(viva))
        .andExpect(jsonPath("$.content").value("# Viva\n\nTexto."))
        .andExpect(jsonPath("$.deletedAt").doesNotExist());
    UUID sinContenido = leccion(jdbc, modulo, "Vacía", "TEXTO", null, 1, 2, "ACTIVO");
    mvc.perform(detalle(sinContenido))
        .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.status").value("ACTIVO"));
  }

  @Test
  @DisplayName(
      "`CA-AC-210` — la retirada se devuelve con deletedAt y deletionReason; la arrastrada, con el motivo del retiro del curso")
  void retiradaYArrastrada() throws Exception {
    mvc.perform(
            post("/api/v1/courses/"
                    + curso
                    + "/modules/"
                    + modulo
                    + "/lessons/"
                    + viva
                    + "/deletion")
                .with(con("courses:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Obsoleta.\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(detalle(viva))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.deletionReason").value("Obsoleta."));

    UUID arrastrada = leccion(jdbc, modulo, "Arrastrada", "TEXTO", "# a", 1, 1, "ACTIVO");
    mvc.perform(
            post("/api/v1/courses/" + curso + "/deletion")
                .with(con("courses:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Se cierra el curso.\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(detalle(arrastrada))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletionReason").value("Se cierra el curso."));
  }

  @Test
  @DisplayName(
      "`CA-AC-211` — 404 si no existe, si es de otro módulo o si el módulo es de otro curso; 400 mal formado")
  void pertenencia() throws Exception {
    mvc.perform(detalle(UUID.randomUUID())).andExpect(status().isNotFound());
    UUID otroModulo = modulo(jdbc, curso, "Otro", 1, "INACTIVO");
    mvc.perform(
            get("/api/v1/courses/" + curso + "/modules/" + otroModulo + "/lessons/" + viva)
                .with(con("courses:read")))
        .andExpect(status().isNotFound());
    UUID otroCurso = curso(jdbc, "Otro", instructor, 1);
    mvc.perform(
            get("/api/v1/courses/" + otroCurso + "/modules/" + modulo + "/lessons/" + viva)
                .with(con("courses:read")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons/no-uuid")
                .with(con("courses:read")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-AC-212` — una sentencia, y una más con motivo de retiro")
  void sentencias() throws Exception {
    estadisticas.clear();
    mvc.perform(detalle(viva)).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
    CourseTestSupport.retirarLeccion(jdbc, viva);
    estadisticas.clear();
    mvc.perform(detalle(viva)).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-AC-213` — sin courses:read responde 403 aunque el actor porte courses:learn o courses:update")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons/" + viva)
                .with(con("courses:learn", "courses:update")))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder detalle(UUID leccion) {
    return get("/api/v1/courses/" + curso + "/modules/" + modulo + "/lessons/" + leccion)
        .with(con("courses:read"));
  }
}
