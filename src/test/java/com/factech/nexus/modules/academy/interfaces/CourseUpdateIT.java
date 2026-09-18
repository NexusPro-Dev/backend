package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.con;
import static com.factech.nexus.modules.academy.interfaces.CourseTestSupport.curso;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
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

/**
 * La corrección del curso (`RF-AC-011` · `T-07`): `CA-AC-057` a `CA-AC-063` y `CA-AC-214`.
 *
 * <p>Lo propio del curso frente a la categoría: <b>el instructor se reasigna</b> con la
 * comprobación del alta, y <b>vaciar una descripción de un curso activo lo deja activo y lo saca de
 * la oferta</b> (`CA-AC-214`, 18-09-2026).
 */
@AutoConfigureMockMvc
class CourseUpdateIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID instructor;
  private UUID curso;

  @BeforeEach
  void sembrar() {
    CourseTestSupport.limpiar(jdbc);
    instructor = CourseTestSupport.instructor(jdbc);
    curso = curso(jdbc, "Velas", instructor, 0, "PRINCIPIANTE", "Corta", "Larga", "INACTIVO");
  }

  @AfterEach
  void limpiar() {
    CourseTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-057` — corrige los siete campos juntos y devuelve el detalle con updatedAt avanzado")
  void corrigeLosSiete() throws Exception {
    UUID otra =
        CourseTestSupport.persona(jdbc, "Ana", "Gómez", CourseTestSupport.rolInstructor(jdbc));
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM courses WHERE id = ?", String.class, curso);
    mvc.perform(
            corregir(
                curso,
                """
                {"title":"Velas japonesas","instructorId":"%s","difficulty":"AVANZADO",
                 "shortDescription":"Otra corta","longDescription":"Otra larga",
                 "introVideoUrl":"https://v.io/2","displayOrder":7}
                """
                    .formatted(otra)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Velas japonesas"))
        .andExpect(jsonPath("$.instructor.id").value(otra.toString()))
        .andExpect(jsonPath("$.instructor.fullName").value("Ana Gómez"))
        .andExpect(jsonPath("$.difficulty").value("AVANZADO"))
        .andExpect(jsonPath("$.shortDescription").value("Otra corta"))
        .andExpect(jsonPath("$.introVideoUrl").value("https://v.io/2"))
        .andExpect(jsonPath("$.displayOrder").value(7))
        .andExpect(jsonPath("$.status").value("INACTIVO"));
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at::text FROM courses WHERE id = ?", String.class, curso))
        .isNotEqualTo(antes);
  }

  @Test
  @DisplayName(
      "`CA-AC-058` — el nulo vacía descripciones y video —también en un curso ACTIVO, que sigue"
          + " ACTIVO— y se rechaza en título, instructor, dificultad y orden, juntos")
  void elNulo() throws Exception {
    jdbc.update("UPDATE courses SET status = 'ACTIVO' WHERE id = ?", curso);
    mvc.perform(
            corregir(
                curso,
                "{\"shortDescription\":null,\"longDescription\":null,\"introVideoUrl\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortDescription").value(nullValue()))
        .andExpect(jsonPath("$.longDescription").value(nullValue()))
        .andExpect(jsonPath("$.introVideoUrl").value(nullValue()))
        .andExpect(jsonPath("$.status").value("ACTIVO"));

    mvc.perform(
            corregir(
                curso,
                "{\"title\":null,\"instructorId\":null,\"difficulty\":null,\"displayOrder\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(hasItems("title", "instructorId", "difficulty", "displayOrder")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(hasItems("VAL-002", "VAL-003", "VAL-004", "VAL-005")));
  }

  @Test
  @DisplayName(
      "`CA-AC-059` — un cuerpo vacío y uno con status, categories, memberships, modules, coverImageUrl o code responden 400")
  void cuerpoVacioYCamposNoAdmitidos() throws Exception {
    mvc.perform(corregir(curso, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));
    for (String extra :
        List.of(
            "\"status\":\"ACTIVO\"",
            "\"categories\":[]",
            "\"memberships\":[]",
            "\"modules\":[]",
            "\"coverImageUrl\":\"/x\"",
            "\"code\":\"C1\"")) {
      mvc.perform(corregir(curso, "{\"title\":\"X\"," + extra + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString("no admite")));
    }
  }

  @Test
  @DisplayName(
      "`CA-AC-060` — 409 con el título de OTRO vivo; se admite el de un retirado y cambiar la caja"
          + " del propio; 404 un curso retirado o inexistente")
  void tituloYRetirado() throws Exception {
    UUID otro = curso(jdbc, "Otro", instructor, 1);
    UUID retirado = curso(jdbc, "Retirado", instructor, 2);
    CourseTestSupport.retirar(jdbc, retirado);

    mvc.perform(corregir(curso, "{\"title\":\"  OTRO \"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
    mvc.perform(corregir(curso, "{\"title\":\"Retirado\"}")).andExpect(status().isOk());
    mvc.perform(corregir(otro, "{\"title\":\"OTRO\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("OTRO"));

    mvc.perform(corregir(retirado, "{\"title\":\"Vuelve\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un curso vivo con ese identificador."));
    mvc.perform(corregir(UUID.randomUUID(), "{\"title\":\"Nada\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-061` — reasignar comprueba RN-AC-006: 422 el inexistente, el retirado y el sin permiso;"
          + " el que sí, con antes y después en la auditoría")
  void reasignarInstructor() throws Exception {
    mvc.perform(corregir(curso, "{\"instructorId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    UUID retirada =
        CourseTestSupport.persona(jdbc, "Ret", "Irada", CourseTestSupport.rolInstructor(jdbc));
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", retirada);
    mvc.perform(corregir(curso, "{\"instructorId\":\"" + retirada + "\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    UUID sinPermiso = CourseTestSupport.persona(jdbc, "Sin", "Permiso", null);
    mvc.perform(corregir(curso, "{\"instructorId\":\"" + sinPermiso + "\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    UUID nueva =
        CourseTestSupport.persona(jdbc, "Ana", "Gómez", CourseTestSupport.rolInstructor(jdbc));
    mvc.perform(corregir(curso, "{\"instructorId\":\"" + nueva + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructor.id").value(nueva.toString()));
    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'courses' AND entity_id = ?"
                + " AND action = 'UPDATE'",
            String.class,
            curso);
    assertThat(cambios)
        .contains("\"instructor_id\"")
        .contains(instructor.toString())
        .contains(nueva.toString());
  }

  @Test
  @DisplayName(
      "`CA-AC-062` — sin cambios de valor —incluido reasignar al mismo instructor— responde 200 sin"
          + " auditar; con cambios, la fila UPDATE lleva solo lo que cambió")
  void sinCambiosYConCambios() throws Exception {
    mvc.perform(
            corregir(
                curso,
                "{\"title\":\" Velas \",\"instructorId\":\"%s\",\"shortDescription\":\"Corta\"}"
                    .formatted(instructor)))
        .andExpect(status().isOk());
    assertThat(auditadas()).isZero();

    mvc.perform(corregir(curso, "{\"displayOrder\":3,\"title\":\"Velas\"}"))
        .andExpect(status().isOk());
    assertThat(auditadas()).isEqualTo(1);
    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'courses' AND entity_id = ?"
                + " AND action = 'UPDATE'",
            String.class,
            curso);
    assertThat(cambios).contains("display_order").doesNotContain("title");
  }

  @Test
  @DisplayName(
      "`CA-AC-063` — un video mal formado responde 400 junto a los demás errores; uno bien formado se guarda")
  void video() throws Exception {
    mvc.perform(corregir(curso, "{\"introVideoUrl\":\"ftp://x\",\"displayOrder\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItems("introVideoUrl", "displayOrder")));
    mvc.perform(corregir(curso, "{\"introVideoUrl\":\"https://no-existe.invalid/v\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.introVideoUrl").value("https://no-existe.invalid/v"));
  }

  @Test
  @DisplayName(
      "`CA-AC-214` — vaciar una descripción de un curso ACTIVO lo deja ACTIVO con offerable false"
          + " «sin descripción», y reponerla lo devuelve al motivo siguiente sin tocar el estado")
  void vaciarSacaDeLaOferta() throws Exception {
    jdbc.update("UPDATE courses SET status = 'ACTIVO' WHERE id = ?", curso);
    mvc.perform(corregir(curso, "{\"longDescription\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason").value("El curso no tiene descripción corta o larga."));
    mvc.perform(corregir(curso, "{\"longDescription\":\"De vuelta\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El curso no tiene ninguna membresía que lo abra."));
  }

  private MockHttpServletRequestBuilder corregir(UUID id, String cuerpo) {
    return patch("/api/v1/courses/" + id)
        .with(con("courses:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private int auditadas() {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity = 'courses' AND entity_id = ? AND"
                + " action = 'UPDATE'",
            Integer.class,
            curso);
    return filas == null ? 0 : filas;
  }
}
