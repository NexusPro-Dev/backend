package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.categoria;
import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El detalle de una categoría (`RF-AC-003` · `T-05`): `CA-AC-016` a `CA-AC-020`.
 *
 * <p>La que define el requerimiento es <b>`CA-AC-017`</b> —el retirado no aparece, el inactivo sí—
 * y sus tres casos —vivo, inactivo, retirado— viven desde `RF-AC-016` en `CourseClassificationIT`
 * (`CA-AC-127`); aquí la categoría no tiene cursos y la prueba fija la forma.
 */
@AutoConfigureMockMvc
class CourseCategoryDetailIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID viva;
  private UUID retirada;

  @BeforeEach
  void sembrar() {
    CourseCategoryTestSupport.limpiar(jdbc);
    viva = categoria(jdbc, "Trading", 0, "1E88E5", "chart-line", "Lo básico.");
    retirada = categoria(jdbc, "Retirada", 1);
    CourseCategoryTestSupport.retirar(jdbc, retirada);
    jdbc.update(
        """
        INSERT INTO audit_deletion_log (id, occurred_at, actor_id, module, entity, entity_id,
                                        deletion_type, reason, snapshot)
        VALUES (gen_random_uuid(), now(), NULL, 'AC', 'course_categories', ?, 'LOGICAL',
                'Ya no se usa.', '{}'::jsonb)
        """,
        retirada);
    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void limpiar() {
    CourseCategoryTestSupport.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-AC-016` y `CA-AC-017` — la categoría con sus campos, coverImageUrl presente y nula, y"
          + " sin cursos, courses vacío")
  void detalleCompleto() throws Exception {
    mvc.perform(detalle(viva))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(viva.toString()))
        .andExpect(jsonPath("$.name").value("Trading"))
        .andExpect(jsonPath("$.description").value("Lo básico."))
        .andExpect(jsonPath("$.color").value("1E88E5"))
        .andExpect(jsonPath("$.icon").value("chart-line"))
        .andExpect(jsonPath("$.displayOrder").value(0))
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()))
        .andExpect(jsonPath("$.courseCount").value(0))
        .andExpect(jsonPath("$.courses", hasSize(0)))
        .andExpect(jsonPath("$.updatedAt").exists())
        .andExpect(jsonPath("$.deletedAt").doesNotExist())
        .andExpect(jsonPath("$.deletionReason").doesNotExist());
  }

  @Test
  @DisplayName(
      "`CA-AC-018` — la retirada se devuelve con deletedAt y deletionReason; la inexistente es 404;"
          + " el identificador mal formado, 400")
  void retiradaInexistenteYMalFormado() throws Exception {
    mvc.perform(detalle(retirada))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.deletionReason").value("Ya no se usa."))
        .andExpect(jsonPath("$.courses", hasSize(0)));

    mvc.perform(detalle(UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe una categoría con ese identificador."));

    mvc.perform(get("/api/v1/course-categories/no-es-uuid").with(con("course-categories:read")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`CA-AC-019` — la lectura cuesta UNA sentencia sin cursos y UNA MÁS con el motivo de retiro")
  void sentencias() throws Exception {
    estadisticas.clear();
    mvc.perform(detalle(viva)).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);

    estadisticas.clear();
    mvc.perform(detalle(retirada)).andExpect(status().isOk());
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "caso límite — una retirada sin registro de eliminación responde 200 con el motivo nulo")
  void retiradaSinRegistro() throws Exception {
    jdbc.update("DELETE FROM audit_deletion_log WHERE entity_id = ?", retirada);
    mvc.perform(detalle(retirada))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.deletionReason").doesNotExist());
  }

  @Test
  @DisplayName("`CA-AC-020` — sin course-categories:read responde 403 aunque porte courses:read")
  void permiso() throws Exception {
    mvc.perform(get("/api/v1/course-categories/" + viva).with(con("courses:read")))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder detalle(UUID id) {
    return get("/api/v1/course-categories/" + id).with(con("course-categories:read"));
  }
}
