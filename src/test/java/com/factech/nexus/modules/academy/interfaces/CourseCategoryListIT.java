package com.factech.nexus.modules.academy.interfaces;

import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.categoria;
import static com.factech.nexus.modules.academy.interfaces.CourseCategoryTestSupport.con;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
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
 * El listado de categorías (`RF-AC-002` · `T-05`): `CA-AC-010` a `CA-AC-015`.
 *
 * <p>La que define el requerimiento es <b>`CA-AC-011`</b>: el orden por omisión es el declarado y
 * no la fecha de alta, con la más antigua primero entre dos que compartan número. `CA-AC-010` es
 * trivial hasta `RF-AC-016` —{@code courseCount} es cero en todas— y ese requerimiento la enmienda.
 */
@AutoConfigureMockMvc
class CourseCategoryListIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private UUID segundaConCero;

  @BeforeEach
  void sembrar() {
    CourseCategoryTestSupport.limpiar(jdbc);
    categoria(jdbc, "Mentalidad", 1);
    categoria(jdbc, "Trading", 0); // la más antigua con cero
    segundaConCero = categoria(jdbc, "Análisis técnico", 0);
    UUID retirada = categoria(jdbc, "Retirada", 0);
    CourseCategoryTestSupport.retirar(jdbc, retirada);
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
      "`CA-AC-010` — cada fila trae coverImageUrl y courseCount, y courseCount cuadra con el"
          + " detalle")
  void filasConPortadaYCuenta() throws Exception {
    String id =
        com.jayway.jsonpath.JsonPath.read(
            mvc.perform(listar(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].coverImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.content[0].courseCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.content[0].id");
    mvc.perform(get("/api/v1/course-categories/" + id).with(con("course-categories:read")))
        .andExpect(jsonPath("$.courseCount").value(0));
  }

  @Test
  @DisplayName(
      "`CA-AC-011` — por omisión displayOrder ascendente con la más antigua primero; name y"
          + " createdAt se admiten; otro campo es 400")
  void ordenPorOmision() throws Exception {
    mvc.perform(listar(""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sort").value("displayOrder,asc"))
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].name").value("Trading"))
        .andExpect(jsonPath("$.content[1].id").value(segundaConCero.toString()))
        .andExpect(jsonPath("$.content[2].name").value("Mentalidad"));

    mvc.perform(listar("?sort=name"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Análisis técnico"))
        .andExpect(jsonPath("$.content[1].name").value("Mentalidad"))
        .andExpect(jsonPath("$.content[2].name").value("Trading"));

    mvc.perform(listar("?sort=createdAt"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sort").value("createdAt,desc"))
        .andExpect(jsonPath("$.content[0].name").value("Análisis técnico"));

    mvc.perform(listar("?sort=color"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName(
      "`CA-AC-012` — excluye las retiradas salvo includeDeleted=true, y entonces con deletedAt")
  void retiradas() throws Exception {
    mvc.perform(listar("")).andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(listar("?includeDeleted=true"))
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[?(@.name == 'Retirada')].deletedAt").exists());
    mvc.perform(listar("?includeDeleted=false"))
        .andExpect(jsonPath("$.content[*].deletedAt").doesNotExist());
  }

  @Test
  @DisplayName("`CA-AC-013` — q busca por contenido sin distinguir mayúsculas ni acentos")
  void busqueda() throws Exception {
    mvc.perform(listar("?q=ANALISIS"))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].name").value("Análisis técnico"));
    mvc.perform(listar("?q=a")).andExpect(jsonPath("$.content", hasSize(3)));
  }

  @Test
  @DisplayName("`CA-AC-014` — la página de uno y la de veinte cuestan lo mismo: dos sentencias")
  void lasSentenciasNoCrecen() throws Exception {
    estadisticas.clear();
    mvc.perform(listar("?size=1")).andExpect(status().isOk());
    long deUno = estadisticas.getPrepareStatementCount();

    estadisticas.clear();
    mvc.perform(listar("?size=20&includeDeleted=true"))
        .andExpect(jsonPath("$.content", hasSize(4)));
    long deVeinte = estadisticas.getPrepareStatementCount();

    assertThat(deUno).isEqualTo(2);
    assertThat(deVeinte).isEqualTo(deUno);
  }

  @Test
  @DisplayName(
      "`CA-AC-015` — los parámetros inválidos se devuelven juntos con 400, y sin"
          + " course-categories:read responde 403 aunque el actor porte courses:read")
  void invalidosJuntosYPermiso() throws Exception {
    mvc.perform(listar("?sort=color&size=0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].field").value(hasItems("sort", "size")));

    mvc.perform(get("/api/v1/course-categories").with(con("courses:read")))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder listar(String query) {
    return get("/api/v1/course-categories" + query).with(con("course-categories:read"));
  }
}
