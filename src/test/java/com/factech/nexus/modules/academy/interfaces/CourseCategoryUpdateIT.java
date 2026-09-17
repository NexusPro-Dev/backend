package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.categoria;
import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * La corrección de una categoría (`RF-AC-004` · `T-06`): `CA-AC-021` a `CA-AC-027`.
 *
 * <p>La que define el requerimiento es <b>`CA-AC-027`</b>, porque une la corrección con el listado
 * ordenado: reordenar es corregir el número, y no mueve a las demás.
 */
@AutoConfigureMockMvc
class CourseCategoryUpdateIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID trading;
  private UUID mentalidad;

  @BeforeEach
  void sembrar() {
    CourseCategoryTestSupport.limpiar(jdbc);
    trading = categoria(jdbc, "Trading", 0, "1E88E5", "chart-line", "Lo básico.");
    mentalidad = categoria(jdbc, "Mentalidad", 1);
  }

  @AfterEach
  void limpiar() {
    CourseCategoryTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-021` — corrige los cinco campos por separado y juntos, y devuelve el detalle con"
          + " updatedAt avanzado")
  void corrigeCadaCampo() throws Exception {
    String antes = updatedAt(trading);
    mvc.perform(corregir(trading, "{\"name\":\"  Trading avanzado \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Trading avanzado"))
        .andExpect(jsonPath("$.color").value("1E88E5"));
    mvc.perform(corregir(trading, "{\"description\":\"Nueva.\"}"))
        .andExpect(jsonPath("$.description").value("Nueva."));
    mvc.perform(corregir(trading, "{\"icon\":\"star\"}"))
        .andExpect(jsonPath("$.icon").value("star"));
    mvc.perform(corregir(trading, "{\"displayOrder\":7}"))
        .andExpect(jsonPath("$.displayOrder").value(7));
    mvc.perform(
            corregir(
                trading,
                "{\"name\":\"Trading\",\"description\":\"Otra.\",\"color\":\"FF0000\","
                    + "\"icon\":\"chart-line\",\"displayOrder\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Trading"))
        .andExpect(jsonPath("$.description").value("Otra."))
        .andExpect(jsonPath("$.color").value("FF0000"))
        .andExpect(jsonPath("$.icon").value("chart-line"))
        .andExpect(jsonPath("$.displayOrder").value(0))
        .andExpect(jsonPath("$.courses").isArray());
    assertThat(updatedAt(trading)).isNotEqualTo(antes);
  }

  @Test
  @DisplayName(
      "`CA-AC-022` — el nulo explícito vacía la descripción y se rechaza en nombre, color, icono y"
          + " orden, con 400 y los errores juntos")
  void nuloExplicito() throws Exception {
    mvc.perform(corregir(trading, "{\"description\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value(nullValue()));

    mvc.perform(
            corregir(trading, "{\"name\":null,\"color\":null,\"icon\":null,\"displayOrder\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field").value(hasItems("name", "color", "icon", "displayOrder")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(hasItems("VAL-002", "VAL-003", "VAL-004", "VAL-005")));
    // Y nada cambió.
    mvc.perform(get("/api/v1/course-categories/" + trading).with(con("course-categories:read")))
        .andExpect(jsonPath("$.name").value("Trading"));
  }

  @Test
  @DisplayName("`CA-AC-023` — un cuerpo vacío y uno con coverImageUrl, courses o status son 400")
  void cuerpoVacioYCamposAjenos() throws Exception {
    mvc.perform(corregir(trading, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-006"));
    for (String extra :
        List.of("\"coverImageUrl\":\"/x\"", "\"courses\":[]", "\"status\":\"ACTIVO\"")) {
      mvc.perform(corregir(trading, "{\"name\":\"X\"," + extra + "}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString("no admite")));
    }
  }

  @Test
  @DisplayName(
      "`CA-AC-024` — 409 con el nombre de OTRA viva, se admite el de una retirada y cambiar solo la"
          + " caja del propio; 404 la retirada y la inexistente")
  void unicidadDelNombre() throws Exception {
    mvc.perform(corregir(trading, "{\"name\":\"mentalidad\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));

    mvc.perform(corregir(trading, "{\"name\":\"TRADING\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("TRADING"));

    UUID retirada = categoria(jdbc, "Retirada", 5);
    CourseCategoryTestSupport.retirar(jdbc, retirada);
    mvc.perform(corregir(trading, "{\"name\":\"Retirada\"}")).andExpect(status().isOk());

    mvc.perform(corregir(retirada, "{\"name\":\"Otra\"}"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.detail").value("No existe una categoría viva con ese identificador."));
    mvc.perform(corregir(UUID.randomUUID(), "{\"name\":\"Otra\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-AC-025` — el color en minúsculas se guarda y devuelve en mayúsculas; color y orden de"
          + " otra se admiten")
  void colorNormalizadoYNoUnico() throws Exception {
    mvc.perform(corregir(trading, "{\"color\":\"ff00aa\",\"displayOrder\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.color").value("FF00AA"))
        .andExpect(jsonPath("$.displayOrder").value(1));
    assertThat(
            jdbc.queryForObject(
                "SELECT color FROM course_categories WHERE id = ?", String.class, trading))
        .isEqualTo("FF00AA");
    // Mentalidad ya tiene el orden 1 y el color 1E88E5: se admiten los dos.
    mvc.perform(corregir(mentalidad, "{\"color\":\"FF00AA\"}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "`CA-AC-026` — sin cambios de valor: 200 sin avanzar updatedAt ni auditar; con cambios, la"
          + " fila UPDATE con antes y después de SOLO lo que cambió")
  void auditaSoloLoQueCambia() throws Exception {
    String antes = updatedAt(trading);
    // El mismo color en otra caja no es un cambio.
    mvc.perform(corregir(trading, "{\"name\":\"Trading\",\"color\":\"1e88e5\",\"displayOrder\":0}"))
        .andExpect(status().isOk());
    assertThat(updatedAt(trading)).isEqualTo(antes);
    assertThat(cuantasFilasDeAuditoria(trading)).isZero();

    UUID actor = UUID.randomUUID();
    mvc.perform(corregir(trading, "{\"icon\":\"star\",\"color\":\"1E88E5\"}", actor))
        .andExpect(status().isOk());
    var fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, action, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'AC' AND entity = 'course_categories' AND entity_id = ?",
            trading);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat(fila.get("action")).isEqualTo("UPDATE");
    assertThat((String) fila.get("changes"))
        .contains("\"icon\"")
        .contains("\"before\": \"chart-line\"")
        .contains("\"after\": \"star\"")
        .doesNotContain("\"color\"")
        .doesNotContain("\"name\"");
  }

  @Test
  @DisplayName(
      "`CA-AC-027` — corregir el orden de una no cambia el de ninguna otra, y el listado la enseña"
          + " en su sitio nuevo")
  void reordenar() throws Exception {
    mvc.perform(corregir(mentalidad, "{\"displayOrder\":0}")).andExpect(status().isOk());
    // Trading sigue en 0: las dos comparten número y desempata la antigüedad.
    mvc.perform(get("/api/v1/course-categories").with(con("course-categories:read")))
        .andExpect(jsonPath("$.content[0].name").value("Trading"))
        .andExpect(jsonPath("$.content[0].displayOrder").value(0))
        .andExpect(jsonPath("$.content[1].name").value("Mentalidad"))
        .andExpect(jsonPath("$.content[1].displayOrder").value(0));

    mvc.perform(corregir(trading, "{\"displayOrder\":9}")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/course-categories").with(con("course-categories:read")))
        .andExpect(jsonPath("$.content[0].name").value("Mentalidad"))
        .andExpect(jsonPath("$.content[1].name").value("Trading"));
  }

  private MockHttpServletRequestBuilder corregir(UUID id, String cuerpo) {
    return corregir(id, cuerpo, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder corregir(UUID id, String cuerpo, UUID actor) {
    return patch("/api/v1/course-categories/" + id)
        .with(con(actor, "course-categories:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private String updatedAt(UUID id) {
    return jdbc.queryForObject(
        "SELECT updated_at::text FROM course_categories WHERE id = ?", String.class, id);
  }

  private int cuantasFilasDeAuditoria(UUID id) {
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE module = 'AC' AND entity_id = ?"
                + " AND action = 'UPDATE'",
            Integer.class,
            id);
    return filas == null ? 0 : filas;
  }
}
