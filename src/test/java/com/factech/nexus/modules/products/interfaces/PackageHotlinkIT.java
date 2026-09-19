package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * El hotlink del paquete, sin token (`RF-PM-026`, `CA-PM-327` a `CA-PM-334`).
 *
 * <p>La que define el requerimiento es <b>`CA-PM-329`</b>: desactivar un producto del paquete apaga
 * el enlace, reactivarlo lo enciende. La que más pesa es <b>`CA-PM-328`</b>: el cuerpo del {@code
 * 404} es el mismo que el del hotlink del producto, en todos los casos.
 */
@AutoConfigureMockMvc
class PackageHotlinkIT extends IntegrationTestBase {

  /** `AGENTE`, sembrado por `V7` con `role_type = VENDEDOR`. */
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  /** `CLIENTE`, sembrado por `V30`: es de tipo `CONSUMIDOR`, no fuerza comercial. */
  private static final String CLIENTE = "01a02a33-4c00-7008-9c4f-5e7ad1000008";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private Statistics estadisticas;
  private Membresias membresias;
  private UUID paquete;
  private UUID oro;
  private UUID bot;

  @BeforeEach
  void preparar() {
    limpiarPersonas();
    membresias = PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
    persona("vendedora", "Ana", "Ruiz", AGENTE);
    persona("cliente", "Bea", "Soto", CLIENTE);

    paquete =
        PackageTestSupport.paquete(jdbc, "PACK_ORO_BOTS", "Oro con señales.", "ACTIVO", "AMBOS");
    oro =
        PackageTestSupport.upgrade(
            jdbc, "UPGRADE_ORO", "500.00", membresias.beca(), membresias.oro());
    bot = PackageTestSupport.bot(jdbc, "BOT_SENALES", "100.00");
    // El bot es de alcance TIENDA a propósito: el alcance de los productos NO
    // filtra dentro del paquete (`CA-PM-332`).
    jdbc.update("UPDATE products SET scope = 'TIENDA', purchase_price = 40.00 WHERE id = ?", bot);
    PackageTestSupport.asociar(jdbc, paquete, oro, "PORCENTAJE", "10");
    PackageTestSupport.asociar(jdbc, paquete, bot, "FIJO", "20.00");

    estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
  }

  @AfterEach
  void vaciar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
    PackageTestSupport.limpiarMonedasDePrueba(jdbc);
    limpiarPersonas();
  }

  @Test
  @DisplayName(
      "`CA-PM-327`, `CA-PM-331` y `CA-PM-334` — sin token devuelve el vendedor y el paquete con la cuenta hecha y cada producto en la forma del hotlink")
  void elEnlaceResuelve() throws Exception {
    estadisticas.clear();
    enlace("hl-vendedora", "pack_oro_bots")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.seller.firstName").value("Ana"))
        .andExpect(jsonPath("$.seller.lastName").value("Ruiz"))
        .andExpect(jsonPath("$.package.code").value("PACK_ORO_BOTS"))
        .andExpect(jsonPath("$.package.name").value("Paquete PACK_ORO_BOTS"))
        .andExpect(jsonPath("$.package.currency.code").value("USD"))
        .andExpect(jsonPath("$.package.items", hasSize(2)))
        .andExpect(jsonPath("$.package.items[0].product.code").value("UPGRADE_ORO"))
        .andExpect(jsonPath("$.package.items[0].product.membership.code").value("ORO"))
        .andExpect(jsonPath("$.package.items[0].product.rating.count").value(0))
        .andExpect(jsonPath("$.package.items[0].product.price").value(500.00))
        .andExpect(jsonPath("$.package.items[0].discount.type").value("PORCENTAJE"))
        .andExpect(jsonPath("$.package.items[0].priceInPackage").value(450.00))
        .andExpect(jsonPath("$.package.items[1].product.code").value("BOT_SENALES"))
        .andExpect(jsonPath("$.package.items[1].priceInPackage").value(80.00))
        .andExpect(jsonPath("$.package.listPrice").value(600.00))
        .andExpect(jsonPath("$.package.price").value(530.00))
        .andExpect(jsonPath("$.package.savings").value(70.00))
        .andExpect(jsonPath("$.package.exchange").value(nullValue()));
    // Tres: vendedor, paquete con productos, moneda de casa — sin tasa, porque
    // el paquete ya está en ella.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("`CA-PM-331` — sin purchasePrice ni status en ningún nivel")
  void sinCostoNiEstado() throws Exception {
    String cuerpo =
        enlace("hl-vendedora", "PACK_ORO_BOTS").andReturn().getResponse().getContentAsString();
    assertThat(cuerpo).doesNotContain("purchasePrice").doesNotContain("\"status\"");
  }

  @Test
  @DisplayName(
      "`CA-PM-328` — el MISMO 404 del hotlink del producto: usuario, no vendedor, código, inactivo, retirado, TIENDA")
  void elMismoCuatroCientosCuatro() throws Exception {
    String delProducto =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "NO_EXISTE"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    igualQue(delProducto, "hl-nadie", "PACK_ORO_BOTS");
    igualQue(delProducto, "hl-cliente", "PACK_ORO_BOTS");
    igualQue(delProducto, "hl-vendedora", "NO_EXISTE");

    jdbc.update("UPDATE product_packages SET status = 'INACTIVO' WHERE id = ?", paquete);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
    jdbc.update(
        "UPDATE product_packages SET status = 'ACTIVO', deleted_at = now() WHERE id = ?", paquete);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
    jdbc.update(
        "UPDATE product_packages SET deleted_at = NULL, scope = 'TIENDA' WHERE id = ?", paquete);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
    jdbc.update("UPDATE product_packages SET scope = 'NINGUNO' WHERE id = ?", paquete);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
    jdbc.update("UPDATE product_packages SET scope = 'HOTLINK' WHERE id = ?", paquete);
    enlace("hl-vendedora", "PACK_ORO_BOTS").andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "`CA-PM-329` — desactivar un producto del paquete APAGA el enlace; reactivarlo lo enciende")
  void unProductoInactivoApagaElEnlace() throws Exception {
    String delProducto =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "NO_EXISTE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    jdbc.update("UPDATE products SET status = 'INACTIVO' WHERE id = ?", bot);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
    jdbc.update("UPDATE products SET status = 'ACTIVO' WHERE id = ?", bot);
    enlace("hl-vendedora", "PACK_ORO_BOTS").andExpect(status().isOk());
    // Y el retirado también lo apaga.
    jdbc.update("UPDATE products SET deleted_at = now() WHERE id = ?", oro);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
  }

  @Test
  @DisplayName("`CA-PM-330` — con menos de dos productos o sin descripción, el mismo 404")
  void menosDeDosOSinDescripcion() throws Exception {
    String delProducto =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "NO_EXISTE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    jdbc.update("UPDATE product_packages SET description = NULL WHERE id = ?", paquete);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
    jdbc.update("UPDATE product_packages SET description = 'Vuelve.' WHERE id = ?", paquete);
    jdbc.update("DELETE FROM product_package_items WHERE product_id = ?", bot);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
    jdbc.update("DELETE FROM product_package_items");
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
  }

  @Test
  @DisplayName(
      "`CA-PM-332` — el alcance de los productos no filtra dentro del paquete, y el paquete HOTLINK resuelve")
  void elAlcanceEsDelPaquete() throws Exception {
    jdbc.update("UPDATE products SET scope = 'TIENDA'");
    jdbc.update("UPDATE product_packages SET scope = 'HOTLINK' WHERE id = ?", paquete);
    enlace("hl-vendedora", "PACK_ORO_BOTS")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.package.items", hasSize(2)));
  }

  @Test
  @DisplayName(
      "`CA-PM-334` y `FA-001` — en otra moneda, exchange sobre price y una sola tasa; sin tasa, exchange vacía y presente")
  void conversion() throws Exception {
    UUID cop = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active)"
            + " VALUES (?, 'COP', 'Peso', '$', 0, false, true)",
        cop);
    jdbc.update("UPDATE product_packages SET currency_id = ? WHERE id = ?", cop, paquete);
    jdbc.update(
        "UPDATE products SET currency_id = ?, price = price * 4000 WHERE id IN (?, ?)",
        cop,
        oro,
        bot);

    // Sin tasa: presente y nula.
    String sinTasa =
        enlace("hl-vendedora", "PACK_ORO_BOTS")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(sinTasa).contains("\"exchange\":null");

    jdbc.update(
        "INSERT INTO exchange_rates (id, source_currency_id, target_currency_id, price,"
            + " valid_from, valid_to, is_active)"
            + " VALUES (?, ?, CAST(? AS uuid), 0.00025, CURRENT_DATE - 1, NULL, true)",
        UUID.randomUUID(),
        cop,
        PackageTestSupport.USD);
    estadisticas.clear();
    enlace("hl-vendedora", "PACK_ORO_BOTS")
        .andExpect(status().isOk())
        // 2000000 × 0.9 + 400000 − 20 = 1800000 + 399980 = 2199980
        .andExpect(jsonPath("$.package.price").value(2199980))
        .andExpect(jsonPath("$.package.exchange.currency.code").value("USD"))
        .andExpect(jsonPath("$.package.exchange.amount").value(550.00))
        .andExpect(jsonPath("$.package.items[0].product.exchange.currency.code").value("USD"));
    // Cuatro: vendedor, paquete, moneda de casa y UNA tasa para las líneas y el total.
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(4);
  }

  @Test
  @DisplayName("`FA-002` — con un token válido, la misma respuesta")
  void conTokenLaMismaRespuesta() throws Exception {
    String sinToken =
        enlace("hl-vendedora", "PACK_ORO_BOTS").andReturn().getResponse().getContentAsString();
    String conToken =
        mvc.perform(
                get("/api/v1/hotlinks/{u}/packages/{c}", "hl-vendedora", "PACK_ORO_BOTS")
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.user(UUID.randomUUID().toString())
                            .authorities(() -> "packages:read", () -> "packages:list")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(conToken).isEqualTo(sinToken);
  }

  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-PM-379` — validFrom y validTo sin token; fuera de la vigencia el MISMO 404, y el que termina hoy resuelve")
  void fueraDeLaVigenciaElMismo404() throws Exception {
    LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
    enlace("hl-vendedora", "PACK_ORO_BOTS")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.package.validFrom").value(hoy.toString()))
        .andExpect(jsonPath("$.package.validTo").value(nullValue()));
    String delProducto =
        mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "hl-vendedora", "NO_EXISTE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    jdbc.update(
        "UPDATE product_packages SET valid_to = ? WHERE id = ?", Date.valueOf(hoy), paquete);
    enlace("hl-vendedora", "PACK_ORO_BOTS")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.package.validTo").value(hoy.toString()));

    jdbc.update(
        "UPDATE product_packages SET valid_from = ?, valid_to = ? WHERE id = ?",
        Date.valueOf(hoy.minusDays(10)),
        Date.valueOf(hoy.minusDays(1)),
        paquete);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");

    jdbc.update(
        "UPDATE product_packages SET valid_from = ?, valid_to = NULL WHERE id = ?",
        Date.valueOf(hoy.plusDays(1)),
        paquete);
    igualQue(delProducto, "hl-vendedora", "PACK_ORO_BOTS");
  }

  private ResultActions enlace(String usuario, String codigo) throws Exception {
    return mvc.perform(get("/api/v1/hotlinks/{u}/packages/{c}", usuario, codigo));
  }

  private void igualQue(String cuerpoEsperado, String usuario, String codigo) throws Exception {
    String cuerpo =
        enlace(usuario, codigo)
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(sinInstance(cuerpo)).isEqualTo(sinInstance(cuerpoEsperado));
  }

  /** `instance` lleva la ruta y `correlationId` es por petición: se compara todo lo demás. */
  private static String sinInstance(String cuerpo) {
    return cuerpo
        .replaceAll("\"instance\":\"[^\"]*\"", "")
        .replaceAll("\"correlationId\":\"[^\"]*\"", "");
  }

  private void persona(String usuario, String nombre, String apellido, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?, ?, 'x', 'ACTIVO',"
            + " (SELECT id FROM countries ORDER BY code LIMIT 1))",
        id.toString(),
        "hl-" + usuario,
        "hl-" + usuario + "@nexus.test",
        nombre,
        apellido);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT CAST(? AS uuid), CAST(? AS uuid), role_type FROM roles WHERE id = CAST(? AS uuid)",
        id.toString(),
        rol,
        rol);
  }

  private void limpiarPersonas() {
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'hl-%')");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'hl-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'hl-%'");
  }
}
