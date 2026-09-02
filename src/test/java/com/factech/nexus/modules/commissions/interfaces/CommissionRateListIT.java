package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
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
 * El listado del catálogo (`RF-CM-002`).
 *
 * <p><b>Lo que este listado tiene que dejar claro es cuáles de sus filas no pagan nada.</b> Una
 * tasa sin asociar aparece con su rol y su porcentaje y <b>no rige</b> — sin {@code
 * associatedProducts}, el listado diría exactamente lo mismo en los dos casos y el malentendido se
 * descubriría liquidando.
 */
@AutoConfigureMockMvc
class CommissionRateListIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID asociada;

  @BeforeEach
  void preparar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);

    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");
    UUID otro = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");

    asociada = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
    CommissionFixtures.asociar(jdbc, asociada, producto, MANAGER);
    CommissionFixtures.asociar(jdbc, asociada, otro, MANAGER);

    // Declarada y nunca asociada: existe, tiene porcentaje y NO PAGA NADA.
    CommissionFixtures.sembrarTasaDeRol(jdbc, DIRECTOR, "4.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @Test
  @DisplayName("cada fila dice sobre cuántos productos rige, y el cero significa «sobre ninguno»")
  void cuentaLasAsociaciones() throws Exception {
    mvc.perform(listado().param("roleId", MANAGER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].associatedProducts").value(2));

    mvc.perform(listado().param("roleId", DIRECTOR))
        .andExpect(status().isOk())
        // Esta tasa parece configurada y no paga nada a nadie.
        .andExpect(jsonPath("$.content[0].associatedProducts").value(0));
  }

  @Test
  @DisplayName("la cuenta de asociaciones no multiplica las filas del listado")
  void laCuentaNoMultiplicaFilas() throws Exception {
    // La tasa de MANAGER tiene DOS asociaciones. Con un LEFT JOIN agrupado mal,
    // aparecería dos veces y el LIMIT de la paginación contaría filas del
    // producto cartesiano en vez de tasas — devolviendo menos tasas de las
    // pedidas sin que nada fallara.
    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("las retiradas no salen salvo que se pidan, y salen marcadas")
  void lasRetiradas() throws Exception {
    UUID retirada = CommissionFixtures.sembrarTasaDeRol(jdbc, AGENTE, "2.00");
    jdbc.update(
        "UPDATE commission_rates SET deleted_at = now() WHERE id = CAST(? AS uuid)",
        retirada.toString());

    mvc.perform(listado()).andExpect(jsonPath("$.totalElements").value(2));

    mvc.perform(listado().param("includeDeleted", "true"))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(
            jsonPath("$.content[?(@.role.code == 'AGENTE')].deletedAt")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())));
  }

  @Test
  @DisplayName("el orden es por código de rol y se publica en la respuesta")
  void elOrdenSePublica() throws Exception {
    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sort").value("role.code,asc"))
        .andExpect(jsonPath("$.content[0].role.code").value("DIRECTOR"))
        .andExpect(jsonPath("$.content[1].role.code").value("MANAGER"));
  }

  @Test
  @DisplayName("varias tasas del mismo rol salen de mayor a menor porcentaje")
  void desempatePorPorcentaje() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, DIRECTOR, "8.00");

    mvc.perform(listado().param("roleId", DIRECTOR))
        .andExpect(jsonPath("$.content[0].percentage").value(8.00))
        .andExpect(jsonPath("$.content[1].percentage").value(4.00));
  }

  @Test
  @DisplayName("el listado exige commissions:read")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/commission-rates")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create")))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"));
  }
}
