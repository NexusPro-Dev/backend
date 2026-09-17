package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * Los productos de un paquete: asociar (`RF-PM-023`, `CA-PM-304` a `CA-PM-314`), y más adelante
 * corregir el descuento (`RF-PM-024`) y desasociar (`RF-PM-025`).
 *
 * <p>Las dos pruebas que definen el requerimiento son <b>la del céntimo</b> —el fijo igual al
 * precio pasa y un céntimo más no— y <b>la del único upgrade</b> —el segundo se rechaza, sea del
 * origen que sea (`RN-PM-046`, desde el 16-09-2026)—.
 */
@AutoConfigureMockMvc
class PackageProductsIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private Membresias membresias;
  private UUID paquete;

  @BeforeEach
  void prepararCatalogo() {
    membresias = PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
    paquete = PackageTestSupport.paquete(jdbc, "COMBO", "Oro con señales.", "INACTIVO", "AMBOS");
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
  }

  @Test
  @DisplayName(
      "`CA-PM-304` y `CA-PM-305` — asocia y devuelve el paquete ENTERO con la cuenta rehecha:"
          + " 10 % de 299.00 y 39.00 fijo sobre 39.00")
  void asociaYDevuelveElPaqueteEntero() throws Exception {
    UUID oro =
        PackageTestSupport.upgrade(
            jdbc, "UPGRADE_ORO", "299.00", membresias.beca(), membresias.oro());
    UUID bot = PackageTestSupport.bot(jdbc, "BOT_SENALES", "39.00");

    mvc.perform(asociar(paquete, oro, "PORCENTAJE", "10"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/packages/" + paquete))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].product.code").value("UPGRADE_ORO"))
        .andExpect(jsonPath("$.items[0].product.price").value(299.00))
        .andExpect(jsonPath("$.items[0].discount.type").value("PORCENTAJE"))
        .andExpect(jsonPath("$.items[0].discount.value").value(10.00))
        .andExpect(jsonPath("$.items[0].priceInPackage").value(269.10))
        .andExpect(jsonPath("$.price").value(269.10))
        .andExpect(jsonPath("$.listPrice").value(299.00))
        .andExpect(jsonPath("$.savings").value(29.90))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value(containsString("menos de dos")));

    mvc.perform(asociar(paquete, bot, "FIJO", "39.00"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[1].product.code").value("BOT_SENALES"))
        .andExpect(jsonPath("$.items[1].priceInPackage").value(0.00))
        .andExpect(jsonPath("$.price").value(269.10))
        .andExpect(jsonPath("$.listPrice").value(338.00))
        .andExpect(jsonPath("$.savings").value(68.90))
        // Sigue inactivo: con dos productos y descripción, lo que falta es activarlo.
        .andExpect(jsonPath("$.offerableReason").value(containsString("inactivo")));
  }

  @Test
  @DisplayName(
      "`CA-PM-305` — el porcentaje redondea la rebaja a la mitad hacia arriba: 12.5 % de 49.99")
  void porcentajeRedondeaHalfUp() throws Exception {
    UUID bot = PackageTestSupport.bot(jdbc, "BOT_A", "49.99");
    mvc.perform(asociar(paquete, bot, "PORCENTAJE", "12.5"))
        .andExpect(status().isCreated())
        // 6.24875 → 6.25 → 43.74
        .andExpect(jsonPath("$.items[0].priceInPackage").value(43.74));
  }

  @Test
  @DisplayName("`CA-PM-306` — el descuento cero se admite en las dos formas y savings no cambia")
  void descuentoCero() throws Exception {
    UUID a = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    UUID b = PackageTestSupport.bot(jdbc, "BOT_B", "20.00");
    mvc.perform(asociar(paquete, a, "PORCENTAJE", "0")).andExpect(status().isCreated());
    mvc.perform(asociar(paquete, b, "FIJO", "0"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.price").value(30.00))
        .andExpect(jsonPath("$.savings").value(0.00));
  }

  @Test
  @DisplayName(
      "`CA-PM-307` — LA PRUEBA DEL CÉNTIMO: el fijo igual al precio pasa, 49.01 sobre 49.00 no")
  void laPruebaDelCentimo() throws Exception {
    UUID bot = PackageTestSupport.bot(jdbc, "BOT_A", "49.00");
    mvc.perform(asociar(paquete, bot, "FIJO", "49.01"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"))
        .andExpect(jsonPath("$.detail").value(containsString("49 USD")));
    mvc.perform(asociar(paquete, bot, "FIJO", "49.00"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].priceInPackage").value(0.00));
  }

  @Test
  @DisplayName("`CA-PM-308` — sobre un producto GRATUITO solo se admite cero, en las dos formas")
  void gratuitoSoloCero() throws Exception {
    UUID gratis = PackageTestSupport.bot(jdbc, "BOT_GRATIS", "0.00");
    mvc.perform(asociar(paquete, gratis, "PORCENTAJE", "10"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
    mvc.perform(asociar(paquete, gratis, "FIJO", "0.01"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
    mvc.perform(asociar(paquete, gratis, "PORCENTAJE", "0")).andExpect(status().isCreated());

    UUID otroGratis = PackageTestSupport.bot(jdbc, "BOT_GRATIS_2", "0.00");
    mvc.perform(asociar(paquete, otroGratis, "FIJO", "0"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.price").value(0.00));
  }

  @Test
  @DisplayName(
      "`CA-PM-309` — la forma se rechaza con 400: porcentaje > 100, negativo, tres decimales; fijo con más decimales que la moneda; campos ausentes")
  void formaInvalida() throws Exception {
    UUID bot = PackageTestSupport.bot(jdbc, "BOT_A", "49.00");
    mvc.perform(asociar(paquete, bot, "PORCENTAJE", "100.01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    mvc.perform(asociar(paquete, bot, "PORCENTAJE", "12.345"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    mvc.perform(asociar(paquete, bot, "PORCENTAJE", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    mvc.perform(asociar(paquete, bot, "FIJO", "1.999"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    mvc.perform(cuerpo(paquete, "{\"productId\":\"" + bot + "\",\"discountType\":\"FIJO\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(
            cuerpo(
                paquete,
                "{\"productId\":\""
                    + bot
                    + "\",\"discountType\":\"FIJO\",\"discountValue\":1,\"extra\":1}"))
        .andExpect(status().isBadRequest());
    assertThat(cuantasFilas()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-310` — inactivo y retirado son 409 (EX-003) y se distinguen del inexistente, que es 422 (EX-002)")
  void inactivoRetiradoEInexistente() throws Exception {
    UUID inactivo =
        PackageTestSupport.producto(jdbc, "BOT_INACTIVO", "BOT", "10.00", null, null, "INACTIVO");
    UUID retirado = PackageTestSupport.bot(jdbc, "BOT_RETIRADO", "10.00");
    jdbc.update("UPDATE products SET deleted_at = now() WHERE id = ?", retirado);

    mvc.perform(asociar(paquete, inactivo, "FIJO", "0"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    mvc.perform(asociar(paquete, retirado, "FIJO", "0"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    mvc.perform(asociar(paquete, UUID.randomUUID(), "FIJO", "0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName("`CA-PM-311` — un producto en otra moneda responde 409 nombrando las dos")
  void otraMoneda() throws Exception {
    UUID cop = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (?, 'COP', 'Peso', '$', 0, false, true)",
        cop);
    UUID enPesos = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO products (id, code, type, name, price, currency_id, status, scope, implementation)
        VALUES (?, 'BOT_PESOS', 'BOT', 'Bot en pesos', 4150, ?, 'ACTIVO', 'AMBOS', 'AUTOMATICA')
        """,
        enPesos,
        cop);

    mvc.perform(asociar(paquete, enPesos, "FIJO", "0"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"))
        .andExpect(jsonPath("$.detail").value(containsString("COP")))
        .andExpect(jsonPath("$.detail").value(containsString("USD")));

    jdbc.update("DELETE FROM products WHERE id = ?", enPesos);
  }

  @Test
  @DisplayName(
      "`CA-PM-312` — el producto que ya está responde 409 (EX-005) y la fila sigue siendo una")
  void yaEsta() throws Exception {
    UUID bot = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    mvc.perform(asociar(paquete, bot, "FIJO", "1.00")).andExpect(status().isCreated());
    mvc.perform(asociar(paquete, bot, "FIJO", "2.00"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
    assertThat(cuantasFilas()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-PM-313` — LA PRUEBA DEL ÚNICO UPGRADE: el segundo se rechaza —del mismo origen y de"
          + " otro— nombrando el que ya está; los bots entran antes y después; quitado el upgrade,"
          + " entra otro del origen que sea")
  void laPruebaDelUnicoUpgrade() throws Exception {
    UUID desdeBeca =
        PackageTestSupport.upgrade(
            jdbc, "UP_BECA_PLATINO", "100.00", membresias.beca(), membresias.platino());
    UUID otroDesdeBeca =
        PackageTestSupport.upgrade(
            jdbc, "UP_BECA_ORO", "300.00", membresias.beca(), membresias.oro());
    UUID desdePlatino =
        PackageTestSupport.upgrade(
            jdbc, "UP_PLATINO_ORO", "200.00", membresias.platino(), membresias.oro());
    UUID bot = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    UUID otroBot = PackageTestSupport.bot(jdbc, "BOT_B", "20.00");

    // Un bot primero: no cuenta como upgrade.
    mvc.perform(asociar(paquete, bot, "FIJO", "0")).andExpect(status().isCreated());
    // El upgrade ocupa el único sitio.
    mvc.perform(asociar(paquete, desdeBeca, "FIJO", "0")).andExpect(status().isCreated());
    // Otro del MISMO origen: hasta el 16-09-2026 entraba; hoy es el segundo upgrade.
    mvc.perform(asociar(paquete, otroDesdeBeca, "PORCENTAJE", "5"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-007"))
        .andExpect(jsonPath("$.detail").value(containsString("UP_BECA_PLATINO")));
    // Y uno de OTRO origen recibe exactamente el mismo rechazo: el sitio está ocupado.
    mvc.perform(asociar(paquete, desdePlatino, "FIJO", "0"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-007"))
        .andExpect(jsonPath("$.detail").value(containsString("UP_BECA_PLATINO")));
    // Los bots siguen entrando después del upgrade.
    mvc.perform(asociar(paquete, otroBot, "PORCENTAJE", "10")).andExpect(status().isCreated());
    assertThat(cuantasFilas()).isEqualTo(3);

    // Quitado el upgrade, el sitio queda libre y entra otro del origen que sea (§13).
    jdbc.update("DELETE FROM product_package_items WHERE product_id = ?", desdeBeca);
    mvc.perform(asociar(paquete, desdePlatino, "FIJO", "0")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "`CA-PM-314` — se asocia a un paquete INACTIVO, no a uno retirado (404), y queda una fila CREATE de product_package_items con el precio de hoy")
  void paqueteInactivoRetiradoYAuditoria() throws Exception {
    UUID bot = PackageTestSupport.bot(jdbc, "BOT_A", "49.00");
    UUID actor = UUID.randomUUID();
    mvc.perform(asociar(paquete, bot, "FIJO", "9.00", actor)).andExpect(status().isCreated());

    var fila =
        jdbc.queryForMap(
            "SELECT actor_id::text AS actor, changes::text AS changes FROM audit_change_log"
                + " WHERE module = 'PM' AND entity = 'product_package_items' AND action = 'CREATE'"
                + " AND entity_id = ? ORDER BY occurred_at DESC LIMIT 1",
            paquete);
    assertThat(fila.get("actor")).isEqualTo(actor.toString());
    assertThat((String) fila.get("changes"))
        .contains("\"product_id\": \"" + bot + "\"")
        .contains("\"discount_type\": \"FIJO\"")
        .contains("\"product_price\": \"49.0000\"");

    UUID retirado = PackageTestSupport.paquete(jdbc, "RETIRADO", null, "INACTIVO", "TIENDA");
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", retirado);
    UUID otro = PackageTestSupport.bot(jdbc, "BOT_B", "10.00");
    mvc.perform(asociar(retirado, otro, "FIJO", "0"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors", hasSize(0)));
    mvc.perform(asociar(UUID.randomUUID(), otro, "FIJO", "0")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("`products:update` no habilita: sin `packages:update` responde 403")
  void losProductsNoHabilitan() throws Exception {
    UUID bot = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    mvc.perform(
            post("/api/v1/packages/" + paquete + "/products")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(bot, "FIJO", "0")))
        .andExpect(status().isForbidden());
    assertThat(cuantasFilas()).isZero();
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder asociar(
      UUID paquete, UUID producto, String forma, String valor) {
    return asociar(paquete, producto, forma, valor, UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder asociar(
      UUID paquete, UUID producto, String forma, String valor, UUID actor) {
    return post("/api/v1/packages/" + paquete + "/products")
        .with(user(actor.toString()).authorities(() -> "packages:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(producto, forma, valor));
  }

  private MockHttpServletRequestBuilder cuerpo(UUID paquete, String json) {
    return post("/api/v1/packages/" + paquete + "/products")
        .with(user(UUID.randomUUID().toString()).authorities(() -> "packages:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  static String json(UUID producto, String forma, String valor) {
    return "{\"productId\":\"%s\",\"discountType\":\"%s\",\"discountValue\":%s}"
        .formatted(producto, forma, valor);
  }

  private int cuantasFilas() {
    Integer filas =
        jdbc.queryForObject("SELECT count(*) FROM product_package_items", Integer.class);
    return filas == null ? 0 : filas;
  }
}
