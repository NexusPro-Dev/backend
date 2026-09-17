package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.interfaces.PackageTestSupport.Membresias;
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
 * Activar y desactivar un paquete (`RF-PM-021`, `CA-PM-291` a `CA-PM-297`).
 *
 * <p>La que define el requerimiento es <b>`CA-PM-294`</b>: se activa con un producto inactivo
 * dentro, y el detalle lo nombra. Activar mira lo que es del paquete; lo que es de sus productos lo
 * mira la oferta cada vez.
 */
@AutoConfigureMockMvc
class PackageStatusIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private Membresias membresias;
  private UUID paquete;
  private UUID botA;
  private UUID botB;

  @BeforeEach
  void prepararCatalogo() {
    membresias = PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
    paquete = PackageTestSupport.paquete(jdbc, "COMBO", "Dos bots.", "INACTIVO", "AMBOS");
    botA = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    botB = PackageTestSupport.bot(jdbc, "BOT_B", "20.00");
    PackageTestSupport.asociar(jdbc, paquete, botA, "FIJO", "0");
    PackageTestSupport.asociar(jdbc, paquete, botB, "FIJO", "0");
    assertThat(membresias.oro()).isNotNull();
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
  }

  @Test
  @DisplayName(
      "`CA-PM-291` y `CA-PM-296` — activa con descripción y dos productos, avanza updatedAt y deja la fila UPDATE")
  void activa() throws Exception {
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM product_packages WHERE id = ?", String.class, paquete);
    mvc.perform(cambiar(paquete, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(true));
    String despues =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM product_packages WHERE id = ?", String.class, paquete);
    assertThat(despues).isNotEqualTo(antes);
    assertThat(
            jdbc.queryForObject(
                "SELECT changes::text FROM audit_change_log WHERE module = 'PM' AND entity = 'product_packages'"
                    + " AND action = 'UPDATE' AND entity_id = ?",
                String.class,
                paquete))
        .contains("\"before\": \"INACTIVO\"")
        .contains("\"after\": \"ACTIVO\"");
  }

  @Test
  @DisplayName(
      "`CA-PM-292` y `CA-PM-293` — sin descripción 409; con cero o uno 409; sin descripción Y con uno, LOS DOS motivos")
  void noSePublicaLoQueNoSeExplicaNiLoQueNoEsPaquete() throws Exception {
    jdbc.update("UPDATE product_packages SET description = NULL WHERE id = ?", paquete);
    mvc.perform(cambiar(paquete, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    jdbc.update(
        "UPDATE product_packages SET description = 'Con descripción.' WHERE id = ?", paquete);
    jdbc.update("DELETE FROM product_package_items WHERE product_id = ?", botB);
    mvc.perform(cambiar(paquete, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("1 productos")));
    jdbc.update("DELETE FROM product_package_items WHERE product_id = ?", botA);
    mvc.perform(cambiar(paquete, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    jdbc.update("UPDATE product_packages SET description = NULL WHERE id = ?", paquete);
    PackageTestSupport.asociar(jdbc, paquete, botA, "FIJO", "0");
    mvc.perform(cambiar(paquete, "ACTIVO"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.errors[1].code").value("EX-003"));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM product_packages WHERE id = ?", String.class, paquete))
        .isEqualTo("INACTIVO");
  }

  @Test
  @DisplayName("`CA-PM-294` — SE ACTIVA con un producto inactivo dentro, y el detalle lo nombra")
  void seActivaConUnProductoInactivoDentro() throws Exception {
    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE id = ?", botA);
    mvc.perform(cambiar(paquete, "ACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value(containsString("BOT_A")));
  }

  @Test
  @DisplayName("`CA-PM-295` — desactivar no exige nada y no toca los productos")
  void desactiva() throws Exception {
    jdbc.update(
        "UPDATE product_packages SET status = 'ACTIVO', description = NULL WHERE id = ?", paquete);
    jdbc.update("DELETE FROM product_package_items");
    mvc.perform(cambiar(paquete, "INACTIVO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVO"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE status = 'ACTIVO'", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("`CA-PM-296` — el estado que ya tiene responde 200 sin avanzar updatedAt ni auditar")
  void elMismoEstadoNoEscribe() throws Exception {
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM product_packages WHERE id = ?", String.class, paquete);
    mvc.perform(cambiar(paquete, "inactivo")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at::text FROM product_packages WHERE id = ?",
                String.class,
                paquete))
        .isEqualTo(antes);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'PM' AND entity_id = ?",
                Integer.class,
                paquete))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-297` — el retirado es 404; un status fuera de dominio, 400; sin packages:update, 403")
  void retiradoYDominio() throws Exception {
    mvc.perform(cambiar(paquete, "PUBLICADO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(cambiar(paquete, "")).andExpect(status().isBadRequest());
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", paquete);
    mvc.perform(cambiar(paquete, "ACTIVO")).andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/v1/packages/" + paquete + "/status")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVO\"}"))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder cambiar(UUID id, String estado) {
    return patch("/api/v1/packages/" + id + "/status")
        .with(user(UUID.randomUUID().toString()).authorities(() -> "packages:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"" + estado + "\"}");
  }
}
