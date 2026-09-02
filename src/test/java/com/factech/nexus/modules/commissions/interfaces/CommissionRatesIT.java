package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.NO_VENDEDOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * El alta del catálogo de tasas por rol (`RF-CM-001`).
 *
 * <p><b>Lo que más importa aquí no es el camino feliz sino que lo registrado NO RIGE.</b> El alta
 * llena un catálogo; lo que pone una tasa en vigor es asociarla (`RN-CM-012`). Antes del 01-09-2026
 * era al revés —una tarifa sin producto valía para todo el catálogo—, y esa inversión es lo que hay
 * que dejar clavado en una prueba: si alguien la deshace, el sistema empezaría a pagar por
 * productos que nadie configuró.
 */
@AutoConfigureMockMvc
class CommissionRatesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void preparar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @Test
  @DisplayName("registra la tasa del rol, y nace SIN REGIR: cero productos asociados")
  void naceSinRegir() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "10.00")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/")))
        .andExpect(jsonPath("$.role.code").value("MANAGER"))
        .andExpect(jsonPath("$.percentage").value(10.00))
        // ESTE CERO ES LA PRUEBA. Sin asociación la tasa no paga nada a nadie, y
        // sin este campo el cliente vería un rol con su porcentaje y concluiría
        // que está configurada.
        .andExpect(jsonPath("$.associatedProducts").value(0));

    assertThat(cuantasAsociaciones()).isZero();
  }

  @Test
  @DisplayName("la respuesta ya no lleva producto, persona, vigencia ni grado")
  void loQuePerdioElAlta() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "10.00")))
        .andExpect(status().isCreated())
        // Los cuatro grados desaparecieron con el rediseño: no hay `scope` que
        // devolver porque no hay nada que graduar.
        .andExpect(jsonPath("$.scope").doesNotExist())
        .andExpect(jsonPath("$.product").doesNotExist())
        .andExpect(jsonPath("$.user").doesNotExist())
        .andExpect(jsonPath("$.validFrom").doesNotExist())
        .andExpect(jsonPath("$.validTo").doesNotExist());
  }

  @Test
  @DisplayName("VARIAS tasas del mismo rol son legítimas: se asociarán a productos distintos")
  void variasTasasPorRol() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "10.00"))).andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(MANAGER, "15.00"))).andExpect(status().isCreated());

    // Lo que no puede repetirse es un rol sobre el MISMO producto, y eso lo
    // cierra la clave primaria de la asociación — no esta tabla.
    assertThat(cuantasTasas()).isEqualTo(2);
  }

  @Test
  @DisplayName("el porcentaje CERO se registra, y no es lo mismo que no tener tasa")
  void elCeroSeRegistra() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "0")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.percentage").value(0));

    assertThat(cuantasTasas()).isEqualTo(1);
  }

  @Test
  @DisplayName("un rol que no es vendedor se rechaza con 400")
  void soloComisionanLosVendedores() throws Exception {
    mvc.perform(alta(cuerpo(NO_VENDEDOR, "10.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("el rol inexistente se distingue del que no es vendedor: 422 y no 400")
  void rolInexistente() throws Exception {
    mvc.perform(alta(cuerpo(UUID.randomUUID().toString(), "10.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("el porcentaje fuera de [0, 100] se rechaza")
  void porcentajeFueraDeRango() throws Exception {
    mvc.perform(alta(cuerpo(MANAGER, "100.01"))).andExpect(status().isBadRequest());
    mvc.perform(alta(cuerpo(MANAGER, "-1"))).andExpect(status().isBadRequest());

    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("el rol es obligatorio")
  void rolObligatorio() throws Exception {
    mvc.perform(alta("{\"percentage\":10.00}")).andExpect(status().isBadRequest());
    assertThat(cuantasTasas()).isZero();
  }

  @Test
  @DisplayName("sin el permiso de alta se rechaza")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/commission-rates")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(MANAGER, "10.00")))
        .andExpect(status().isForbidden());

    assertThat(cuantasTasas()).isZero();
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private static String cuerpo(String rol, String porcentaje) {
    return "{\"roleId\":\"" + rol + "\",\"percentage\":" + porcentaje + "}";
  }

  private MockHttpServletRequestBuilder alta(String json) {
    return post("/api/v1/commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private long cuantasTasas() {
    return jdbc.queryForObject("SELECT count(*) FROM commission_rates", Long.class);
  }

  private long cuantasAsociaciones() {
    return jdbc.queryForObject("SELECT count(*) FROM product_commission_rates", Long.class);
  }
}
