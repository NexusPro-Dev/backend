package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.domain.service.GetHotlinkCatalogService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-PM-027` — el catálogo de hotlinks: lo que un vendedor puede repartir.
 *
 * <p>La siembra mezcla lo que entra y lo que no: dos alcances, dos estados, un retirado y un costo
 * declarado. Lo que se afirma es <b>el conjunto</b>, y que la membresía de quien llama no lo
 * recorta.
 */
@AutoConfigureMockMvc
class HotlinkCatalogIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private org.hibernate.SessionFactory sessionFactory;
  @Autowired private GetHotlinkCatalogService servicio;

  private UUID oro;
  private UUID free;
  private UUID imagen;

  @BeforeEach
  void sembrar() {
    limpiar();
    oro = membresia("ORO", "Oro", 1, null);
    UUID vip = membresia("VIP", "Vip", 2, oro);
    free = membresia("BECA", "Beca", 3, vip);

    // ENTRAN: activos y de alcance HOTLINK o AMBOS, de los dos tipos, con distinto
    // nivel de destino para que el orden sea observable.
    producto("HL_VIP", "UPGRADE_MEMBRESIA", free, vip, "20.00", "ACTIVO", "AMBOS", BASE, false);
    producto("HL_ORO", "UPGRADE_MEMBRESIA", free, oro, "100.00", "ACTIVO", "AMBOS", BASE, false);
    producto("HL_BOT_B", "BOT", null, null, "9.00", "ACTIVO", "AMBOS", BASE.plusDays(2), false);
    producto("HL_BOT_A", "BOT", null, null, "7.00", "ACTIVO", "AMBOS", BASE.plusDays(1), false);

    // NO ENTRAN: alcance TIENDA, inactivo, retirado. El de TIENDA sale desde
    // VIP: `uq_products_upgrade_target` admite un solo activo por pareja.
    producto("TIENDA_ORO", "UPGRADE_MEMBRESIA", vip, oro, "90.00", "ACTIVO", "TIENDA", BASE, false);
    producto("HL_INACTIVO", "BOT", null, null, "1.00", "INACTIVO", "AMBOS", BASE, false);
    producto("HL_RETIRADO", "BOT", null, null, "1.00", "ACTIVO", "AMBOS", BASE, true);

    imagen = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_images (id, content_type, content) VALUES (CAST(? AS uuid),"
            + " 'image/png', decode('89504E470D0A1A0A00', 'hex'))",
        imagen.toString());
    jdbc.update(
        "UPDATE products SET cover_image_id = CAST(? AS uuid),"
            + " purchase_price = 60.00 WHERE code = 'HL_ORO'",
        imagen.toString());
    ProductLinkTestSupport.enlace(
        jdbc, "HL_ORO", "VIDEO_PRESENTACION", "https://vimeo.com/1", null);
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  @Test
  @DisplayName(
      "`CA-PM-340` · `CA-PM-343` — solo los activos de HOTLINK o AMBOS, en las dos listas y en orden")
  void elConjuntoYElOrden() throws Exception {
    mvc.perform(catalogo())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content", Matchers.hasSize(2)))
        // Upgrades por nivel de destino, del más bajo al más alto (`m.level DESC`).
        .andExpect(jsonPath("$.upgrades.content[0].code").value("HL_VIP"))
        .andExpect(jsonPath("$.upgrades.content[1].code").value("HL_ORO"))
        .andExpect(jsonPath("$.services.content", Matchers.hasSize(2)))
        // Bots por fecha de alta.
        .andExpect(jsonPath("$.services.content[0].code").value("HL_BOT_A"))
        .andExpect(jsonPath("$.services.content[1].code").value("HL_BOT_B"))
        .andExpect(jsonPath("$..code", Matchers.not(Matchers.hasItem("TIENDA_ORO"))))
        .andExpect(jsonPath("$..code", Matchers.not(Matchers.hasItem("HL_INACTIVO"))))
        .andExpect(jsonPath("$..code", Matchers.not(Matchers.hasItem("HL_RETIRADO"))));
  }

  @Test
  @DisplayName("`CA-PM-341` — no mira la membresía del actor: en ORO se ve el BECA → ORO")
  void noMiraLaMembresia() throws Exception {
    UUID vendedor = persona("hlcat-vendedor");
    asignar(vendedor, oro);

    String cuerpo =
        mvc.perform(catalogo(vendedor))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upgrades.content[*].code", Matchers.hasItem("HL_ORO")))
            .andExpect(jsonPath("$.upgrades.content[1].targetMembership.code").value("ORO"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).doesNotContain("currentMembership");
  }

  @Test
  @DisplayName(
      "`CA-PM-342` — la forma de venta: sin `purchasePrice`, con precio, conversión, video, portada y rating")
  void laFormaDeVenta() throws Exception {
    String cuerpo =
        mvc.perform(catalogo())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upgrades.content[1].code").value("HL_ORO"))
            .andExpect(jsonPath("$.upgrades.content[1].price").value(100.00))
            .andExpect(jsonPath("$.upgrades.content[1].currency.code").value("USD"))
            .andExpect(jsonPath("$.upgrades.content[1]").value(Matchers.hasKey("exchange")))
            .andExpect(jsonPath("$.upgrades.content[1].links.length()").value(1))
            .andExpect(jsonPath("$.upgrades.content[1].links[0].url").value("https://vimeo.com/1"))
            .andExpect(
                jsonPath("$.upgrades.content[1].coverImageUrl")
                    .value("/api/v1/product-images/" + imagen))
            .andExpect(jsonPath("$.upgrades.content[1].rating.count").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).doesNotContain("purchasePrice").doesNotContain("60.00");
  }

  @Test
  @DisplayName("`CA-PM-399` — el catálogo no trae el cupón, y sí el video RESUELTO")
  void elCatalogoNoTraeElCupon() throws Exception {
    // Quien reparte hotlinks tiene token, pero NO es administración: se le
    // enseña lo mismo que a quien abre el enlace y nada más. Comparte la
    // proyección con la oferta (`RF-PM-007`), de modo que esta prueba no
    // vigila una consulta propia sino que la compartida siga siendo la buena.
    ProductLinkTestSupport.enlace(jdbc, "HL_ORO", "CUPON_BOT", "https://t.me/nexusbot", "cupon-15");
    jdbc.update("DELETE FROM product_links WHERE type = 'VIDEO_PRESENTACION'");
    ProductLinkTestSupport.enlace(
        jdbc, "HL_ORO", "VIDEO_PRESENTACION", "https://vimeo.com/canal", "1");

    String cuerpo =
        mvc.perform(catalogo())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upgrades.content[1].code").value("HL_ORO"))
            .andExpect(jsonPath("$.upgrades.content[1].links.length()").value(1))
            .andExpect(
                jsonPath("$.upgrades.content[1].links[0].url").value("https://vimeo.com/canal/1"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(cuerpo)
        .doesNotContain("CUPON_BOT")
        .doesNotContain("t.me/nexusbot")
        .doesNotContain("cupon-15");
  }

  @Test
  @DisplayName("`CA-PM-353` — un HOTLINK activo entra aunque no esté en la tienda; un NINGUNO no")
  void soloHotlinkYAmbos() throws Exception {
    jdbc.update("UPDATE products SET scope = 'HOTLINK' WHERE code = 'HL_VIP'");
    jdbc.update("UPDATE products SET scope = 'NINGUNO' WHERE code = 'HL_BOT_A'");

    mvc.perform(catalogo())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content[*].code", Matchers.contains("HL_VIP", "HL_ORO")))
        .andExpect(jsonPath("$.upgrades.content[0].scope").value("HOTLINK"))
        .andExpect(jsonPath("$.services.content[*].code", Matchers.contains("HL_BOT_B")));
  }

  @Test
  @DisplayName("`CA-PM-344` — sin nada publicable, 200 con las dos listas vacías")
  void vacio() throws Exception {
    jdbc.update("UPDATE products SET cover_image_id = NULL");
    ProductLinkTestSupport.limpiar(jdbc);
    jdbc.update("DELETE FROM products");
    mvc.perform(catalogo())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upgrades.content", Matchers.hasSize(0)))
        .andExpect(jsonPath("$.services.content", Matchers.hasSize(0)));
  }

  @Test
  @DisplayName("`CA-PM-345` — solo `products:hotlink`: los otros dos permisos de vista reciben 403")
  void soloConElPermiso() throws Exception {
    for (String permiso : new String[] {"products:sale", "products:read", "products:create"}) {
      mvc.perform(
              get("/api/v1/products/hotlinks")
                  .with(user(UUID.randomUUID().toString()).authorities(() -> permiso)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/v1/products/hotlinks")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("`CA-PM-346` — `/products/hotlinks` no cae en `/products/{id}`")
  void noCaeEnElDetalle() throws Exception {
    // Con `products:hotlink` y sin `products:read`: si cayera en `/{id}`
    // respondería 403 (permiso del detalle) o 400 (identificador inválido).
    mvc.perform(catalogo()).andExpect(status().isOk()).andExpect(jsonPath("$.upgrades").exists());
  }

  @Test
  @DisplayName("`CA-PM-347` — una sentencia más la conversión, y no sube con los productos")
  void lasSentenciasNoSubenConLosProductos() {
    var estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    servicio.catalog();
    long conCuatro = estadisticas.getPrepareStatementCount();

    for (int i = 0; i < 5; i++) {
      producto("HL_MAS_" + i, "BOT", null, null, "3.00", "ACTIVO", "AMBOS", BASE, false);
    }
    estadisticas.clear();
    servicio.catalog();
    long conNueve = estadisticas.getPrepareStatementCount();

    // El catálogo, la moneda de casa y las tasas: tres como máximo, y las mismas.
    assertThat(conCuatro).isLessThanOrEqualTo(3);
    assertThat(conNueve).isEqualTo(conCuatro);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder catalogo() {
    return catalogo(UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder catalogo(UUID quien) {
    return get("/api/v1/products/hotlinks").with(comoVendedor(quien));
  }

  /** Autenticado con exactamente {@code products:hotlink}. */
  private static RequestPostProcessor comoVendedor(UUID quien) {
    return user(quien.toString()).authorities(() -> "products:hotlink");
  }

  private void limpiar() {
    jdbc.update("UPDATE products SET cover_image_id = NULL");
    ProductLinkTestSupport.limpiar(jdbc);
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM product_images");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'hlcat-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'hlcat-%'");
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM memberships");
  }

  private UUID membresia(String codigo, String nombre, int nivel, UUID superior) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO memberships (id, code, name, parent_membership_id, level, color)"
            + " VALUES (CAST(? AS uuid), ?, ?, CAST(? AS uuid), ?,"
            + " upper(lpad(to_hex(? * 4919), 6, '0')))",
        id.toString(),
        codigo,
        nombre,
        superior == null ? null : superior.toString(),
        nivel,
        nivel);
    return id;
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (CAST(? AS uuid), ?, ?, 'Ana', 'Ruiz', 'no-se-usa-en-esta-prueba', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id.toString(),
        username,
        username + "@nexus.test");
    return id;
  }

  private void asignar(UUID quien, UUID membresia) {
    jdbc.update(
        """
        INSERT INTO user_memberships (id, user_id, membership_id, started_at, ends_at,
                                      created_at, updated_at)
        VALUES (gen_random_uuid(), CAST(? AS uuid), CAST(? AS uuid), now() - interval '30 days',
                NULL, now(), now())
        """,
        quien.toString(),
        membresia.toString());
  }

  private void producto(
      String codigo,
      String tipo,
      UUID origen,
      UUID destino,
      String precio,
      String estado,
      String alcance,
      OffsetDateTime creado,
      boolean retirado) {
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, icon,"
            + " source_membership_id, target_membership_id, price, currency_id, status,"
            + " created_at, updated_at, deleted_at)"
            + " VALUES (?, 'MANUAL', CAST(? AS uuid), ?, ?, ?, 'Descripción de prueba', ?,"
            + " CAST(? AS uuid), CAST(? AS uuid), CAST(? AS numeric), CAST(? AS uuid), ?, ?, ?,"
            + " CAST(? AS timestamptz))",
        alcance,
        UUID.randomUUID().toString(),
        codigo,
        tipo,
        "Producto " + codigo,
        "BOT".equals(tipo) ? null : "crown",
        origen == null ? null : origen.toString(),
        destino == null ? null : destino.toString(),
        precio,
        USD,
        estado,
        creado,
        creado,
        retirado ? creado.toString() : null);
  }
}
