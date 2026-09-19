package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.interfaces.PackageTestSupport.Membresias;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * El detalle del paquete (`RF-PM-019`, `CA-PM-277` a `CA-PM-284`).
 *
 * <p>La que define el requerimiento es <b>`CA-PM-278`</b>: corregir el precio de un producto cambia
 * el paquete en la siguiente lectura sin tocarlo, porque el precio se calcula.
 */
@AutoConfigureMockMvc
class PackageDetailIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private Membresias membresias;
  private UUID paquete;
  private UUID oro;
  private UUID bot;

  @BeforeEach
  void prepararCatalogo() {
    membresias = PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
    paquete = PackageTestSupport.paquete(jdbc, "COMBO", "Oro con señales.", "ACTIVO", "AMBOS");
    oro =
        PackageTestSupport.upgrade(
            jdbc, "UPGRADE_ORO", "299.00", membresias.beca(), membresias.oro());
    bot = PackageTestSupport.bot(jdbc, "BOT_SENALES", "39.00");
    jdbc.update("UPDATE products SET purchase_price = 20.00 WHERE id = ?", bot);
    PackageTestSupport.asociar(jdbc, paquete, oro, "PORCENTAJE", "10");
    PackageTestSupport.asociar(jdbc, paquete, bot, "FIJO", "39.00");

    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName(
      "`CA-PM-277` y `CA-PM-280` — los productos con su descuento y su priceInPackage, los totales cuadran, y offerable true sin motivo")
  void detalleConLaCuentaHecha() throws Exception {
    mvc.perform(detalle(paquete))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("COMBO"))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.scope").value("AMBOS"))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].product.code").value("UPGRADE_ORO"))
        .andExpect(jsonPath("$.items[0].product.type").value("UPGRADE_MEMBRESIA"))
        .andExpect(jsonPath("$.items[0].product.status").value("ACTIVO"))
        .andExpect(jsonPath("$.items[0].product.deleted").value(false))
        .andExpect(jsonPath("$.items[0].discount.type").value("PORCENTAJE"))
        .andExpect(jsonPath("$.items[0].discount.value").value(10.00))
        .andExpect(jsonPath("$.items[0].priceInPackage").value(269.10))
        .andExpect(jsonPath("$.items[1].product.code").value("BOT_SENALES"))
        .andExpect(jsonPath("$.items[1].priceInPackage").value(0.00))
        .andExpect(jsonPath("$.listPrice").value(338.00))
        .andExpect(jsonPath("$.price").value(269.10))
        .andExpect(jsonPath("$.savings").value(68.90))
        .andExpect(jsonPath("$.offerable").value(true))
        .andExpect(jsonPath("$.offerableReason").value(nullValue()))
        .andExpect(jsonPath("$.deletedAt").doesNotExist());
  }

  @Test
  @DisplayName(
      "`CA-PM-278` — corregir el precio de un producto CAMBIA el paquete en la siguiente lectura, sin tocarlo")
  void elPrecioSeCalcula() throws Exception {
    jdbc.update("UPDATE products SET price = 199.00 WHERE id = ?", oro);
    // El paquete no se toca: ni `updated_at` ni ninguna columna.
    mvc.perform(detalle(paquete))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].priceInPackage").value(179.10))
        .andExpect(jsonPath("$.listPrice").value(238.00))
        .andExpect(jsonPath("$.price").value(179.10));
  }

  @Test
  @DisplayName(
      "`CA-PM-279` — el fijo que HOY supera el precio cuenta cero y el total no baja de cero")
  void elFijoQueSuperaElPrecioCuentaCero() throws Exception {
    jdbc.update("UPDATE products SET price = 10.00 WHERE id = ?", bot);
    mvc.perform(detalle(paquete))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[1].priceInPackage").value(0.00))
        .andExpect(jsonPath("$.listPrice").value(309.00))
        .andExpect(jsonPath("$.price").value(269.10))
        .andExpect(jsonPath("$.savings").value(39.90));
  }

  @Test
  @DisplayName(
      "`CA-PM-281` — el motivo en su ORDEN: menos de dos, sin descripción, inactivo, retirado, producto nombrado")
  void elMotivoEnSuOrden() throws Exception {
    // 5º: un producto inactivo, nombrado por su código.
    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE id = ?", oro);
    mvc.perform(detalle(paquete))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value(containsString("UPGRADE_ORO")));
    // 4º: retirado gana sobre el producto.
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", paquete);
    mvc.perform(detalle(paquete))
        .andExpect(jsonPath("$.offerableReason").value("El paquete está retirado."));
    // 3º: inactivo gana sobre retirado.
    jdbc.update("UPDATE product_packages SET status = 'INACTIVO' WHERE id = ?", paquete);
    mvc.perform(detalle(paquete))
        .andExpect(jsonPath("$.offerableReason").value("El paquete está inactivo."));
    // 2º: sin descripción gana sobre inactivo.
    jdbc.update("UPDATE product_packages SET description = NULL WHERE id = ?", paquete);
    mvc.perform(detalle(paquete))
        .andExpect(jsonPath("$.offerableReason").value(containsString("descripción")));
    // 1º: menos de dos gana sobre todo.
    jdbc.update("DELETE FROM product_package_items WHERE product_id = ?", bot);
    mvc.perform(detalle(paquete))
        .andExpect(jsonPath("$.offerableReason").value(containsString("menos de dos")));
  }

  @Test
  @DisplayName(
      "`CA-PM-282` — un producto inactivo o retirado se devuelve con su estado y SIGUE SUMANDO")
  void elProductoInactivoSigueSumando() throws Exception {
    jdbc.update("UPDATE products SET deleted_at = now(), status = 'INACTIVO' WHERE id = ?", oro);
    mvc.perform(detalle(paquete))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].product.status").value("INACTIVO"))
        .andExpect(jsonPath("$.items[0].product.deleted").value(true))
        .andExpect(jsonPath("$.price").value(269.10))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(jsonPath("$.offerableReason").value(containsString("UPGRADE_ORO")))
        .andExpect(jsonPath("$.offerableReason").value(containsString("retirado")));
  }

  @Test
  @DisplayName(
      "`CA-PM-283` — purchasePrice presente (nulo si no se conoce) y exchange nula en la moneda de casa")
  void purchasePriceYExchange() throws Exception {
    String cuerpo =
        mvc.perform(detalle(paquete))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[1].product.purchasePrice").value(20.00))
            .andExpect(jsonPath("$.exchange").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).contains("\"purchasePrice\":null");
  }

  @Test
  @DisplayName(
      "`CA-PM-283` — en otra moneda, exchange se calcula sobre price y cuesta una sentencia más")
  void exchangeSobreElPrecio() throws Exception {
    UUID cop = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (?, 'COP', 'Peso', '$', 0, false, true)",
        cop);
    jdbc.update(
        "INSERT INTO exchange_rates (id, source_currency_id, target_currency_id, price,"
            + " valid_from, valid_to, is_active)"
            + " VALUES (?, ?, CAST(? AS uuid), 0.00025, CURRENT_DATE - 1, NULL, true)",
        UUID.randomUUID(),
        cop,
        PackageTestSupport.USD);
    UUID enPesos =
        PackageTestSupport.paquete(jdbc, "EN_PESOS", "Todo en pesos.", "ACTIVO", "TIENDA");
    jdbc.update("UPDATE product_packages SET currency_id = ? WHERE id = ?", cop, enPesos);
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    for (UUID id : new UUID[] {a, b}) {
      jdbc.update(
          """
          INSERT INTO products (id, code, type, name, price, currency_id, status, scope, implementation)
          VALUES (?, ?, 'BOT', ?, 100000, ?, 'ACTIVO', 'AMBOS', 'AUTOMATICA')
          """,
          id,
          id.equals(a) ? "BOT_COP_A" : "BOT_COP_B",
          "Bot en pesos " + (id.equals(a) ? "A" : "B"),
          cop);
    }
    PackageTestSupport.asociar(jdbc, enPesos, a, "FIJO", "0");
    PackageTestSupport.asociar(jdbc, enPesos, b, "PORCENTAJE", "50");

    estadisticas.clear();
    mvc.perform(detalle(enPesos))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(150000))
        .andExpect(jsonPath("$.exchange.currency.code").value("USD"))
        .andExpect(jsonPath("$.exchange.amount").value(37.50));
    // Tres: el paquete con sus filas, la moneda de casa y la tasa.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "`CA-PM-284` — el retirado se devuelve con su motivo, el inexistente es 404, y la lectura cuesta dos, tres con motivo")
  void retiradoInexistenteYSentencias() throws Exception {
    estadisticas.clear();
    mvc.perform(detalle(paquete)).andExpect(status().isOk());
    // Dos: el paquete con sus filas y la moneda de casa — la tasa no se pide
    // porque el paquete ya está en ella.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);

    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", paquete);
    jdbc.update(
        "INSERT INTO audit_deletion_log (id, occurred_at, module, entity, entity_id,"
            + " deletion_type, reason, snapshot)"
            + " VALUES (?, now(), 'PM', 'product_packages', ?, 'LOGICAL', 'Se descontinuó.', CAST('{}' AS jsonb))",
        UUID.randomUUID(),
        paquete);
    estadisticas.clear();
    mvc.perform(detalle(paquete))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedAt").exists())
        .andExpect(jsonPath("$.deletionReason").value("Se descontinuó."))
        .andExpect(jsonPath("$.offerable").value(false));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);

    mvc.perform(detalle(UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No existe un paquete con ese identificador."));
    mvc.perform(get("/api/v1/packages/no-es-un-uuid").with(lector()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "`FA-001` — el paquete vacío: items vacío, tres ceros, exchange nulo, y una sola sentencia")
  void paqueteVacio() throws Exception {
    UUID vacio = PackageTestSupport.paquete(jdbc, "VACIO", null, "INACTIVO", "TIENDA");
    estadisticas.clear();
    mvc.perform(detalle(vacio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.price").value(0.00))
        .andExpect(jsonPath("$.exchange").value(nullValue()))
        .andExpect(jsonPath("$.offerableReason").value(containsString("menos de dos")));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("sin `packages:read` responde 403, también con los cuatro `products:`")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/packages/" + paquete)
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(() -> "products:read", () -> "products:list")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-PM-377` — validFrom y validTo en el detalle; fuera de la vigencia el motivo lleva la fecha y va DESPUÉS de retirado y ANTES del producto; termina hoy e indefinido se ofrecen")
  void vigenciaEnElDetalle() throws Exception {
    LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
    mvc.perform(detalle(paquete))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validFrom").value(hoy.toString()))
        .andExpect(jsonPath("$.validTo").value(nullValue()))
        .andExpect(jsonPath("$.offerable").value(true));

    // Termina hoy: el día de fin cuenta entero.
    jdbc.update(
        "UPDATE product_packages SET valid_to = ? WHERE id = ?", Date.valueOf(hoy), paquete);
    mvc.perform(detalle(paquete))
        .andExpect(jsonPath("$.validTo").value(hoy.toString()))
        .andExpect(jsonPath("$.offerable").value(true));

    // Terminó ayer, y además un producto inactivo: gana la vigencia, que es del paquete.
    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE id = ?", bot);
    jdbc.update(
        "UPDATE product_packages SET valid_from = ?, valid_to = ? WHERE id = ?",
        Date.valueOf(hoy.minusDays(10)),
        Date.valueOf(hoy.minusDays(1)),
        paquete);
    mvc.perform(detalle(paquete))
        .andExpect(jsonPath("$.status").value("ACTIVO"))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("La vigencia del paquete terminó el " + hoy.minusDays(1) + "."));
    // Y retirado además: gana «retirado».
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", paquete);
    mvc.perform(detalle(paquete))
        .andExpect(jsonPath("$.offerableReason").value(containsString("retirado")));
    jdbc.update("UPDATE product_packages SET deleted_at = NULL WHERE id = ?", paquete);
    jdbc.update("UPDATE products SET status = 'ACTIVO' WHERE id = ?", bot);

    // Empieza mañana.
    jdbc.update(
        "UPDATE product_packages SET valid_from = ?, valid_to = NULL WHERE id = ?",
        Date.valueOf(hoy.plusDays(1)),
        paquete);
    mvc.perform(detalle(paquete))
        .andExpect(jsonPath("$.offerable").value(false))
        .andExpect(
            jsonPath("$.offerableReason")
                .value("El paquete todavía no está vigente: empieza el " + hoy.plusDays(1) + "."));
    // Empieza hoy, sin fin.
    jdbc.update(
        "UPDATE product_packages SET valid_from = ? WHERE id = ?", Date.valueOf(hoy), paquete);
    mvc.perform(detalle(paquete)).andExpect(jsonPath("$.offerable").value(true));
  }

  private MockHttpServletRequestBuilder detalle(UUID id) {
    return get("/api/v1/packages/" + id).with(lector());
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor lector() {
    return user(UUID.randomUUID().toString())
        .authorities(() -> "packages:read", () -> "packages:list");
  }
}
