package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Corregir el descuento de un producto en un paquete (`RF-PM-024`, `CA-PM-315` a `CA-PM-320`).
 *
 * <p>La que define el requerimiento es <b>`CA-PM-316`</b>: la cota se comprueba contra el precio de
 * <b>hoy</b>, también cuando el precio bajó después de asociar.
 */
@AutoConfigureMockMvc
class PackageDiscountIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID paquete;
  private UUID bot;
  private UUID otro;

  @BeforeEach
  void prepararCatalogo() {
    PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
    paquete = PackageTestSupport.paquete(jdbc, "COMBO", "Dos bots.", "ACTIVO", "AMBOS");
    bot = PackageTestSupport.bot(jdbc, "BOT_A", "100.00");
    otro = PackageTestSupport.bot(jdbc, "BOT_B", "50.00");
    PackageTestSupport.asociar(jdbc, paquete, bot, "FIJO", "20.00");
    PackageTestSupport.asociar(jdbc, paquete, otro, "PORCENTAJE", "10");
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
  }

  @Test
  @DisplayName(
      "`CA-PM-315` y `CA-PM-319` — corrige valor, forma o los dos, rehace la cuenta y audita type y value con antes y después")
  void corrigeYRehace() throws Exception {
    // Solo el valor.
    mvc.perform(corregir(paquete, bot, "FIJO", "30.00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].discount.value").value(30.00))
        .andExpect(jsonPath("$.items[0].priceInPackage").value(70.00))
        .andExpect(jsonPath("$.price").value(115.00));
    // La forma y el valor: de fijo a porcentaje.
    mvc.perform(corregir(paquete, bot, "PORCENTAJE", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].discount.type").value("PORCENTAJE"))
        .andExpect(jsonPath("$.items[0].priceInPackage").value(50.00))
        .andExpect(jsonPath("$.price").value(95.00))
        .andExpect(jsonPath("$.savings").value(55.00));

    String ultimo =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE module = 'PM'"
                + " AND entity = 'product_package_items' AND action = 'UPDATE' AND entity_id = ?"
                + " ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            paquete);
    assertThat(ultimo)
        .contains("\"product_id\": \"" + bot + "\"")
        .contains("\"type\": {\"after\": \"PORCENTAJE\", \"before\": \"FIJO\"}")
        .contains("\"value\": {\"after\": \"50\", \"before\": \"30.0000\"}");
  }

  @Test
  @DisplayName(
      "`CA-PM-316` — un céntimo por encima del precio DE HOY es 409, también cuando el precio bajó después de asociar")
  void laCotaEsLaDeHoy() throws Exception {
    mvc.perform(corregir(paquete, bot, "FIJO", "100.01"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(jsonPath("$.detail").value(containsString("100 USD")));
    mvc.perform(corregir(paquete, bot, "FIJO", "100.00")).andExpect(status().isOk());

    // El precio baja a 50 después de asociar con fijo 100: 60 no, 50 sí.
    jdbc.update("UPDATE products SET price = 50.00 WHERE id = ?", bot);
    mvc.perform(corregir(paquete, bot, "FIJO", "60.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(containsString("50 USD")));
    mvc.perform(corregir(paquete, bot, "FIJO", "50.00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].priceInPackage").value(0.00));
  }

  @Test
  @DisplayName(
      "`CA-PM-317` — forma o valor ausentes es 400: no es parcial; productId en el cuerpo, 400")
  void noEsParcial() throws Exception {
    mvc.perform(cuerpo(paquete, bot, "{\"discountValue\":10}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(cuerpo(paquete, bot, "{\"discountType\":\"FIJO\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(
            cuerpo(
                paquete,
                bot,
                "{\"discountType\":\"FIJO\",\"discountValue\":1,\"productId\":\"" + otro + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(corregir(paquete, bot, "PORCENTAJE", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
  }

  @Test
  @DisplayName("`CA-PM-318` — 404 al producto que no está en el paquete y al paquete retirado")
  void noEsta() throws Exception {
    UUID fuera = PackageTestSupport.bot(jdbc, "BOT_FUERA", "10.00");
    mvc.perform(corregir(paquete, fuera, "FIJO", "0"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Ese producto no está en el paquete."));
    mvc.perform(corregir(paquete, UUID.randomUUID(), "FIJO", "0")).andExpect(status().isNotFound());
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", paquete);
    mvc.perform(corregir(paquete, bot, "FIJO", "0")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`CA-PM-319` — sin cambio de valor responde 200 sin avanzar updatedAt ni auditar")
  void sinCambioNoEscribe() throws Exception {
    String antes =
        jdbc.queryForObject(
            "SELECT updated_at::text FROM product_package_items WHERE product_id = ?",
            String.class,
            bot);
    mvc.perform(corregir(paquete, bot, "FIJO", "20")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at::text FROM product_package_items WHERE product_id = ?",
                String.class,
                bot))
        .isEqualTo(antes);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE module = 'PM' AND action = 'UPDATE'",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-320` — un producto inactivo dentro del paquete se corrige igual, y el paquete sigue offerable false por él")
  void inactivoSeCorrigeIgual() throws Exception {
    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE id = ?", bot);
    mvc.perform(corregir(paquete, bot, "FIJO", "10.00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].priceInPackage").value(90.00))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value(containsString("BOT_A")));
  }

  private MockHttpServletRequestBuilder corregir(
      UUID paquete, UUID producto, String forma, String valor) {
    return cuerpo(
        paquete,
        producto,
        "{\"discountType\":\"%s\",\"discountValue\":%s}".formatted(forma, valor));
  }

  private MockHttpServletRequestBuilder cuerpo(UUID paquete, UUID producto, String json) {
    return patch("/api/v1/packages/" + paquete + "/products/" + producto)
        .with(user(UUID.randomUUID().toString()).authorities(() -> "packages:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }
}
