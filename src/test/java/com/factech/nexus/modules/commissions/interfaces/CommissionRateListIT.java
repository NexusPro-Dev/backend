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
 * <p><b>Desde el 15-09-2026 el listado dice sobre QUÉ producto rige cada tasa</b> (`RN-CM-021`), y
 * es lo que el responsable del proyecto pidió leer: todas las comisiones configuradas, en una sola
 * lista, cada una con su producto. Hasta esa fecha decía sobre <b>cuántos</b>, porque una tasa con
 * cero no pagaba nada; hoy toda tasa viva rige y no hay nada que contar.
 */
@AutoConfigureMockMvc
class CommissionRateListIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID productoA;
  private UUID productoB;

  @BeforeEach
  void preparar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);

    productoA = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");
    productoB = CommissionFixtures.sembrarProducto(jdbc, "BOT_B");

    // El mismo rol sobre dos productos: dos tasas, cada una con el suyo.
    CommissionFixtures.sembrarTasaDeRol(jdbc, productoA, MANAGER, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, productoB, MANAGER, "15.00");
    // Y otro rol sobre el primero.
    CommissionFixtures.sembrarTasaDeRol(jdbc, productoA, DIRECTOR, "4.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @Test
  @DisplayName("CA-CM-141 · cada fila trae SU producto resuelto, y ya no cuenta asociaciones")
  void cadaFilaTraeSuProducto() throws Exception {
    mvc.perform(listado().param("roleId", MANAGER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].product.id").value(productoA.toString()))
        .andExpect(jsonPath("$.content[0].product.code").value("BOT_A"))
        .andExpect(jsonPath("$.content[0].product.name").value("Producto BOT_A"))
        .andExpect(jsonPath("$.content[1].product.code").value("BOT_B"))
        .andExpect(jsonPath("$.content[0].associatedProducts").doesNotExist());
  }

  @Test
  @DisplayName(
      "CA-CM-145 · el producto de cada fila trae su PRECIO y su MONEDA, sea cual sea la forma")
  void elProductoTraePrecioYMoneda() throws Exception {
    // Un porcentaje es una parte del precio y un importe fijo es dinero en la
    // moneda del producto: sin los dos, la cifra de la fila no dice cuánto es.
    UUID caro = CommissionFixtures.sembrarProducto(jdbc, "BOT_CARO", false, "1500.50");
    CommissionFixtures.sembrarTasaDeRol(jdbc, caro, AGENTE, "FIJO", "300.00");
    var moneda =
        jdbc.queryForMap(
            "SELECT CAST(c.id AS text) AS id, c.code, c.decimal_places FROM products p"
                + " JOIN currencies c ON c.id = p.currency_id WHERE p.id = CAST(? AS uuid)",
            caro.toString());

    mvc.perform(listado().param("productId", caro.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].rateType").value("FIJO"))
        .andExpect(jsonPath("$.content[0].product.price").value(1500.50))
        .andExpect(jsonPath("$.content[0].product.currency.id").value(moneda.get("id")))
        .andExpect(jsonPath("$.content[0].product.currency.code").value(moneda.get("code")))
        .andExpect(
            jsonPath("$.content[0].product.currency.decimalPlaces")
                .value(((Number) moneda.get("decimal_places")).intValue()));

    // Y en porcentaje viaja igual: el cliente no pregunta la forma para saber
    // si el campo estará.
    mvc.perform(listado().param("productId", productoA.toString()))
        .andExpect(jsonPath("$.content[0].rateType").value("PORCENTAJE"))
        .andExpect(jsonPath("$.content[0].product.price").value(10.00))
        .andExpect(jsonPath("$.content[0].product.currency.code").value(moneda.get("code")));
  }

  @Test
  @DisplayName("CA-CM-141 · el filtro por producto devuelve solo las de ese producto")
  void elFiltroPorProducto() throws Exception {
    mvc.perform(listado().param("productId", productoA.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].role.code").value("DIRECTOR"))
        .andExpect(jsonPath("$.content[1].role.code").value("MANAGER"));

    // Se combina con el rol.
    mvc.perform(listado().param("productId", productoB.toString()).param("roleId", MANAGER))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].percentage").value(15.00));

    // Un producto sin tasas —o inexistente— devuelve la página vacía, sin error.
    mvc.perform(listado().param("productId", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("CA-CM-141 · la lectura de los productos de una tasa YA NO EXISTE")
  void laLecturaPorTasaSeRetiro() throws Exception {
    UUID cualquiera =
        UUID.fromString(
            jdbc.queryForObject(
                "SELECT CAST(id AS text) FROM commission_rates LIMIT 1", String.class));

    mvc.perform(
            get("/api/v1/commission-rates/" + cualquiera + "/products")
                .with(
                    user(SUPERADMIN.toString())
                        .authorities(
                            () -> "commissions:read",
                            () -> "commissions:read-effective",
                            () -> "user-commission-rates:read",
                            () -> "product-commission-rates:read")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("las retiradas no salen salvo que se pidan, y salen marcadas")
  void lasRetiradas() throws Exception {
    UUID retirada = CommissionFixtures.sembrarTasaDeRol(jdbc, productoB, AGENTE, "2.00");
    jdbc.update(
        "UPDATE commission_rates SET deleted_at = now() WHERE id = CAST(? AS uuid)",
        retirada.toString());

    mvc.perform(listado()).andExpect(jsonPath("$.totalElements").value(3));

    mvc.perform(listado().param("includeDeleted", "true"))
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(
            jsonPath("$.content[?(@.role.code == 'AGENTE')].deletedAt")
                .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())));
  }

  @Test
  @DisplayName("el orden es por código de producto y luego de rol, y se publica en la respuesta")
  void elOrdenSePublica() throws Exception {
    mvc.perform(listado())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sort").value("product.code,asc;role.code,asc"))
        .andExpect(jsonPath("$.content[0].product.code").value("BOT_A"))
        .andExpect(jsonPath("$.content[0].role.code").value("DIRECTOR"))
        .andExpect(jsonPath("$.content[1].product.code").value("BOT_A"))
        .andExpect(jsonPath("$.content[1].role.code").value("MANAGER"))
        .andExpect(jsonPath("$.content[2].product.code").value("BOT_B"));
  }

  // ---------------------------------------------------------------------------
  // El valor fijo (`cm.md` v0.7.0)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-096 · cada fila lleva la forma junto al valor, y el otro campo VACÍO")
  void laFormaViajaEnCadaFila() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, productoB, DIRECTOR, "FIJO", "5000");

    mvc.perform(listado().param("roleId", DIRECTOR).param("rateType", "FIJO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].rateType").value("FIJO"))
        .andExpect(jsonPath("$.content[0].fixedAmount").value(5000))
        .andExpect(jsonPath("$.content[0].percentage").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("CA-CM-097 · el filtro por forma filtra, y ausente NO filtra")
  void elFiltroPorForma() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, productoB, DIRECTOR, "FIJO", "5000");

    mvc.perform(listado().param("roleId", DIRECTOR).param("rateType", "FIJO"))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].rateType").value("FIJO"));

    mvc.perform(listado().param("roleId", DIRECTOR).param("rateType", "PORCENTAJE"))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].rateType").value("PORCENTAJE"));

    // Ausente: las dos.
    mvc.perform(listado().param("roleId", DIRECTOR))
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  @DisplayName(
      "CA-CM-098 · dentro de un producto el orden es por ROL, y la forma no lo altera — reescrito"
          + " el 15-09-2026")
  void elOrdenDentroDelProductoEsPorRol() throws Exception {
    // Hasta el 15-09-2026 esta prueba clavaba que las formas no se intercalaban
    // DENTRO DE UN ROL —«FIJO 10» delante de «80 %» y «50 %»—, porque un rol
    // podía tener varias tasas. Con una tasa viva por rol y producto
    // (`RN-CM-013` en el esquema) ya no hay dos filas del mismo grupo que
    // ordenar entre sí: lo que queda es que el rol manda y que un importe fijo
    // pequeño no se cuela delante de un porcentaje grande por la cifra.
    UUID p = CommissionFixtures.sembrarProducto(jdbc, "BOT_ORDEN");
    CommissionFixtures.sembrarTasaDeRol(jdbc, p, DIRECTOR, "PORCENTAJE", "80.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, p, MANAGER, "FIJO", "1.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, p, AGENTE, "PORCENTAJE", "4.00");

    mvc.perform(listado().param("productId", p.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.content[0].role.code").value("AGENTE"))
        .andExpect(jsonPath("$.content[1].role.code").value("DIRECTOR"))
        .andExpect(jsonPath("$.content[2].role.code").value("MANAGER"))
        .andExpect(jsonPath("$.content[2].rateType").value("FIJO"));
  }

  @Test
  @DisplayName("CA-CM-099 · la lectura POR PRODUCTO devuelve la forma de cada rol")
  void laLecturaPorProductoLlevaLaForma() throws Exception {
    // Es donde `RN-CM-011` se veía venir sumando porcentajes a ojo. Con formas
    // mezcladas YA NO HAY SUMA QUE HACER, y por eso la forma tiene que viajar:
    // sin ella quedaría una columna de cifras que nadie puede interpretar.
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_C");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "FIJO", "5000");

    mvc.perform(
            get("/api/v1/product-commission-rates")
                .param("productId", producto.toString())
                .with(
                    user(SUPERADMIN.toString())
                        .authorities(
                            () -> "commissions:read",
                            () -> "commissions:read-effective",
                            () -> "user-commission-rates:read",
                            () -> "product-commission-rates:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].product.code").value("BOT_C"))
        .andExpect(jsonPath("$.content[0].rateType").value("FIJO"))
        .andExpect(jsonPath("$.content[0].fixedAmount").value(5000))
        .andExpect(jsonPath("$.content[0].percentage").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty());
  }

  @Test
  @DisplayName("la lectura POR PRODUCTO no devuelve las retiradas: el producto dejó de pagarles")
  void laLecturaPorProductoOmiteLasRetiradas() throws Exception {
    UUID retirada = CommissionFixtures.sembrarTasaDeRol(jdbc, productoB, AGENTE, "2.00");
    jdbc.update(
        "UPDATE commission_rates SET deleted_at = now() WHERE id = CAST(? AS uuid)",
        retirada.toString());

    mvc.perform(
            get("/api/v1/product-commission-rates")
                .param("productId", productoB.toString())
                .with(
                    user(SUPERADMIN.toString())
                        .authorities(
                            () -> "commissions:read",
                            () -> "commissions:read-effective",
                            () -> "user-commission-rates:read",
                            () -> "product-commission-rates:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].role.code").value("MANAGER"));
  }

  @Test
  @DisplayName("el listado exige commissions:read")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/commission-rates")
                .with(
                    user(SUPERADMIN.toString())
                        .authorities(
                            () -> "commissions:create", () -> "user-commission-rates:create")))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/commission-rates")
        .with(
            user(SUPERADMIN.toString())
                .authorities(
                    () -> "commissions:read",
                    () -> "commissions:read-effective",
                    () -> "user-commission-rates:read",
                    () -> "product-commission-rates:read"));
  }
}
