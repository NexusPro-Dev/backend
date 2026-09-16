package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.domain.models.ProductImage;
import com.factech.nexus.modules.products.domain.models.ProductImageTest;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-PM-028` (subir o reemplazar) y `RF-PM-029` (quitar) la portada de un paquete, y las enmiendas
 * de `coverImageUrl` a las cuatro lecturas y al alta (`CA-PM-367` a `CA-PM-371`).
 *
 * <p><b>Es {@link ProductCoverIT} con el paquete, y la diferencia que importa es {@code
 * CA-PM-362}</b>: quitar nunca se rechaza. Los bytes se generan ({@link ProductImageTest}); ningún
 * archivo entra al repositorio. <b>Los techos de sentencias de las lecturas no se repiten aquí</b>:
 * los guardan sus suites, y siguen en verde porque {@code cover_image_id} viaja en la sentencia que
 * ya traía el paquete (`CA-PM-361`).
 *
 * <p>El paquete de las pruebas es <b>solo de bots</b> a propósito: se ofrece a todo el mundo, de
 * modo que la oferta y el hotlink lo devuelven sin montar membresías en la persona.
 */
@AutoConfigureMockMvc
class PackageCoverIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/product-images/";

  /** `AGENTE`, sembrado por `V7` con `role_type = VENDEDOR`. */
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID combo;
  private UUID vacio;
  private UUID cliente;

  @BeforeEach
  void sembrar() {
    limpiarPersonas();
    PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");

    combo = PackageTestSupport.paquete(jdbc, "COMBO", "Dos bots.", "ACTIVO", "AMBOS");
    UUID a = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");
    UUID b = PackageTestSupport.bot(jdbc, "BOT_B", "20.00");
    PackageTestSupport.asociar(jdbc, combo, a, "FIJO", "1.00");
    PackageTestSupport.asociar(jdbc, combo, b, "PORCENTAJE", "10");
    // Inactivo, vacío y sin descripción: todo lo que un paquete puede tener
    // «a medias», y nada de eso condiciona la portada (`CA-PM-359`).
    vacio = PackageTestSupport.paquete(jdbc, "VACIO", null, "INACTIVO", "TIENDA");

    persona("vendedora", AGENTE);
    cliente = persona("cliente", null);
  }

  @AfterEach
  void vaciar() {
    // Los paquetes ANTES que las imágenes: `fk_product_packages_cover_image`.
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM product_images");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
    limpiarPersonas();
  }

  // ---------------------------------------------------------------------------
  // RF-PM-028 — subir o reemplazar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-354` — sube un PNG: el paquete con su cuenta, `coverImageUrl` y los bytes")
  void subeUnaPortada() throws Exception {
    byte[] png = ProductImageTest.relleno(ProductImageTest.PNG, 2_048);

    mvc.perform(subir(combo, png, "portada.png", MediaType.IMAGE_PNG_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(combo.toString()))
        .andExpect(jsonPath("$.coverImageUrl").value(startsWith(RUTA)))
        // La respuesta de las ocho operaciones: con la cuenta hecha.
        .andExpect(jsonPath("$.price").value(27.00))
        .andExpect(jsonPath("$.offerable").value(true));

    UUID imagen = portadaDe(combo);
    assertThat(imagen).isNotNull();
    Map<String, Object> fila = imagenDe(imagen);
    assertThat(fila.get("content_type")).isEqualTo("image/png");
    assertThat((byte[]) fila.get("content")).isEqualTo(png);
  }

  @Test
  @DisplayName("`CA-PM-355` · `CA-PM-356` — reemplaza: otra dirección, UNA fila, y la auditoría")
  void reemplazaLaPortada() throws Exception {
    UUID primera = subirYLeer(combo, ProductImageTest.PNG);
    assertThat(ultimoCambio(combo))
        .contains("\"cover_image_id\"")
        .contains("\"before\": \"\"")
        .contains("\"after\": \"" + primera + "\"")
        .doesNotContain("\"name\"")
        .doesNotContain("\"description\"");

    UUID segunda = subirYLeer(combo, ProductImageTest.JPEG);

    assertThat(segunda).isNotEqualTo(primera);
    assertThat(cuantasImagenes()).isEqualTo(1);
    assertThat(existeImagen(primera)).isFalse();
    // La vieja responde 404 y la nueva 200, sin token (`RF-PM-016`, la misma ruta).
    mvc.perform(get(RUTA + primera)).andExpect(status().isNotFound());
    mvc.perform(get(RUTA + segunda)).andExpect(status().isOk());

    assertThat(ultimoCambio(combo))
        .contains("\"before\": \"" + primera + "\"")
        .contains("\"after\": \"" + segunda + "\"");
    assertThat(eventosDe(combo)).isEqualTo(2);
  }

  @Test
  @DisplayName("`CA-PM-357` — los mismos rechazos que el producto, y nada queda escrito")
  void losRechazos() throws Exception {
    mvc.perform(subir(combo, ProductImageTest.GIF, "foto.png", MediaType.IMAGE_PNG_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"))
        .andExpect(jsonPath("$.errors[0].field").value("file"));

    mvc.perform(
            subir(
                combo,
                ProductImageTest.relleno(ProductImageTest.PNG, ProductImage.TAMANO_MAXIMO + 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));

    mvc.perform(multipart(HttpMethod.PUT, "/api/v1/packages/{id}/cover", combo).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[0].field").value("file"));
    mvc.perform(subir(combo, new byte[0]))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    mvc.perform(
            put("/api/v1/packages/{id}/cover", combo)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(portadaDe(combo)).isNull();
    assertThat(cuantasImagenes()).isZero();
    assertThat(eventosDe(combo)).isZero();
  }

  @Test
  @DisplayName("`CA-PM-358` — inexistente y retirado 404 igual; `products:update` no basta")
  void paqueteInexistenteRetiradoOSinPermiso() throws Exception {
    String inexistente =
        mvc.perform(subir(UUID.randomUUID(), ProductImageTest.PNG))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", vacio);
    String retirado =
        mvc.perform(subir(vacio, ProductImageTest.PNG))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(sinIdentidad(retirado))
        .isEqualTo(sinIdentidad(inexistente))
        .contains("No existe un paquete vivo con ese identificador.");

    mvc.perform(
            multipart(HttpMethod.PUT, "/api/v1/packages/{id}/cover", combo)
                .file(new MockMultipartFile("file", "p.png", "image/png", ProductImageTest.PNG))
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:update")))
        .andExpect(status().isForbidden());
    assertThat(portadaDe(combo)).isNull();
  }

  @Test
  @DisplayName("`CA-PM-359` — inactivo, vacío y sin descripción: se sube igual, y sigue como está")
  void sinCondicionDeEstadoNiContenido() throws Exception {
    mvc.perform(subir(vacio, ProductImageTest.WEBP))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(startsWith(RUTA)))
        .andExpect(jsonPath("$.status").value("INACTIVO"))
        .andExpect(jsonPath("$.items").isEmpty())
        // La portada no es «algo con qué activarse»: sigue sin poderse ofrecer.
        .andExpect(jsonPath("$.offerable").value(false));
  }

  @Test
  @DisplayName(
      "`CA-PM-360` — las cuatro lecturas devuelven la dirección DEL PAQUETE, no la de sus productos")
  void lasCuatroLecturas() throws Exception {
    UUID imagen = subirYLeer(combo, ProductImageTest.PNG);
    String esperada = RUTA + imagen;

    mvc.perform(get("/api/v1/packages/{id}", combo).with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(esperada));
    mvc.perform(get("/api/v1/packages").with(lector()).param("q", "COMBO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].code").value("COMBO"))
        .andExpect(jsonPath("$.content[0].coverImageUrl").value(esperada));
    mvc.perform(get("/api/v1/products/available").with(comprador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.packages.content[0].code").value("COMBO"))
        .andExpect(jsonPath("$.packages.content[0].coverImageUrl").value(esperada))
        // Los productos del paquete no tienen portada: la del paquete es suya.
        .andExpect(
            jsonPath("$.packages.content[0].items[0].product.coverImageUrl").value(nullValue()));
    mvc.perform(get("/api/v1/hotlinks/{u}/packages/{c}", "pk-vendedora", "combo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.package.coverImageUrl").value(esperada))
        .andExpect(jsonPath("$.package.items[0].product.coverImageUrl").value(nullValue()));
  }

  @Test
  @DisplayName(
      "`CA-PM-367` a `CA-PM-371` — sin portada, `coverImageUrl` presente y nula en las cuatro lecturas y en el alta")
  void presenteYNulaSinPortada() throws Exception {
    String detalle = cuerpo(get("/api/v1/packages/{id}", combo).with(lector()));
    assertThat(detalle).contains("\"coverImageUrl\":null");

    String listado = cuerpo(get("/api/v1/packages").with(lector()).param("q", "COMBO"));
    assertThat(listado).contains("\"coverImageUrl\":null");

    String oferta = cuerpo(get("/api/v1/products/available").with(comprador()));
    assertThat(oferta).contains("\"code\":\"COMBO\"").contains("\"coverImageUrl\":null");

    String hotlink = cuerpo(get("/api/v1/hotlinks/{u}/packages/{c}", "pk-vendedora", "combo"));
    assertThat(hotlink).contains("\"coverImageUrl\":null");

    // También en un retirado (`CA-PM-368`).
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", vacio);
    assertThat(cuerpo(get("/api/v1/packages/{id}", vacio).with(lector())))
        .contains("\"coverImageUrl\":null");

    // Y en el alta (`CA-PM-371`): lo único que puede traer un paquete recién registrado.
    mvc.perform(
            post("/api/v1/packages")
                .with(user(UUID.randomUUID().toString()).authorities(() -> "packages:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"NUEVO","name":"Nuevo","currencyId":"%s","scope":"TIENDA"}
                    """
                        .formatted(PackageTestSupport.USD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()));
  }

  // ---------------------------------------------------------------------------
  // RF-PM-029 — quitar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-362` · `CA-PM-363` — se quita siempre: la imagen se borra y se audita")
  void quitaLaPortada() throws Exception {
    UUID imagen = subirYLeer(combo, ProductImageTest.PNG);

    String cuerpo =
        mvc.perform(quitar(combo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.coverImageUrl").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).contains("\"coverImageUrl\":null");
    assertThat(portadaDe(combo)).isNull();
    assertThat(existeImagen(imagen)).isFalse();
    mvc.perform(get(RUTA + imagen)).andExpect(status().isNotFound());

    assertThat(ultimoCambio(combo))
        .contains("\"before\": \"" + imagen + "\"")
        .contains("\"after\": \"\"")
        .doesNotContain("\"name\"");
  }

  @Test
  @DisplayName("`CA-PM-364` — sin portada responde 200 sin escribir nada")
  void sinPortadaNoEscribeNada() throws Exception {
    OffsetDateTime actualizado = actualizadoDe(combo);
    long antes = eventosDe(combo);

    mvc.perform(quitar(combo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()));

    assertThat(actualizadoDe(combo)).isEqualTo(actualizado);
    assertThat(eventosDe(combo)).isEqualTo(antes);
  }

  @Test
  @DisplayName("`CA-PM-365` — activo y ofrecible sigue en la oferta y en el hotlink sin portada")
  void sigueOfreciendoseSinPortada() throws Exception {
    subirYLeer(combo, ProductImageTest.PNG);
    mvc.perform(quitar(combo)).andExpect(status().isOk());

    mvc.perform(get("/api/v1/products/available").with(comprador()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.packages.content[0].code").value("COMBO"))
        .andExpect(jsonPath("$.packages.content[0].coverImageUrl").value(nullValue()));
    mvc.perform(get("/api/v1/hotlinks/{u}/packages/{c}", "pk-vendedora", "combo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.package.coverImageUrl").value(nullValue()));
  }

  @Test
  @DisplayName("`CA-PM-366` — inexistente y retirado 404; inactivo se quita; sin permiso 403")
  void quitarSobreInexistenteRetiradoInactivoOSinPermiso() throws Exception {
    mvc.perform(quitar(UUID.randomUUID())).andExpect(status().isNotFound());

    subirYLeer(vacio, ProductImageTest.PNG);
    mvc.perform(
            delete("/api/v1/packages/{id}/cover", vacio)
                .with(user(UUID.randomUUID().toString()).authorities(() -> "products:update")))
        .andExpect(status().isForbidden());
    // Inactivo (`VACIO` nace así): se quita.
    mvc.perform(quitar(vacio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(nullValue()));

    subirYLeer(vacio, ProductImageTest.PNG);
    jdbc.update("UPDATE product_packages SET deleted_at = now() WHERE id = ?", vacio);
    mvc.perform(quitar(vacio)).andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------

  private UUID subirYLeer(UUID paquete, byte[] bytes) throws Exception {
    mvc.perform(subir(paquete, bytes)).andExpect(status().isOk());
    UUID imagen = portadaDe(paquete);
    assertThat(imagen).isNotNull();
    return imagen;
  }

  private MockHttpServletRequestBuilder subir(UUID paquete, byte[] bytes) {
    return subir(paquete, bytes, "portada.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE);
  }

  private MockHttpServletRequestBuilder subir(
      UUID paquete, byte[] bytes, String nombre, String tipo) {
    return multipart(HttpMethod.PUT, "/api/v1/packages/{id}/cover", paquete)
        .file(new MockMultipartFile("file", nombre, tipo, bytes))
        .with(admin());
  }

  private MockHttpServletRequestBuilder quitar(UUID paquete) {
    return delete("/api/v1/packages/{id}/cover", paquete).with(admin());
  }

  private String cuerpo(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString()).authorities(() -> "packages:update");
  }

  private static RequestPostProcessor lector() {
    return user(UUID.randomUUID().toString()).authorities(() -> "packages:read");
  }

  private RequestPostProcessor comprador() {
    return user(cliente.toString()).authorities(() -> "products:sale");
  }

  private static String sinIdentidad(String cuerpo) {
    return cuerpo
        .replaceAll("\"correlationId\":\"[^\"]*\"", "")
        .replaceAll("\"instance\":\"[^\"]*\"", "");
  }

  private UUID portadaDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT cover_image_id FROM product_packages WHERE id = ?", UUID.class, id);
  }

  private OffsetDateTime actualizadoDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT updated_at FROM product_packages WHERE id = ?", OffsetDateTime.class, id);
  }

  private Map<String, Object> imagenDe(UUID id) {
    return jdbc.queryForMap(
        "SELECT content_type, content FROM product_images WHERE id = CAST(? AS uuid)",
        id.toString());
  }

  private boolean existeImagen(UUID id) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM product_images WHERE id = CAST(? AS uuid)",
            Integer.class,
            id.toString());
    return n != null && n > 0;
  }

  private int cuantasImagenes() {
    Integer n = jdbc.queryForObject("SELECT count(*) FROM product_images", Integer.class);
    return n == null ? 0 : n;
  }

  private String ultimoCambio(UUID id) {
    return jdbc.queryForObject(
        "SELECT changes::text FROM audit_change_log WHERE entity = 'product_packages'"
            + " AND entity_id = ? AND action = 'UPDATE' ORDER BY occurred_at DESC LIMIT 1",
        String.class,
        id);
  }

  private long eventosDe(UUID id) {
    Long filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity = 'product_packages'"
                + " AND entity_id = ?",
            Long.class,
            id);
    return filas == null ? 0 : filas;
  }

  private UUID persona(String usuario, String rol) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash,"
            + " must_change_password, status, country_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, 'Ana', 'Ruiz', 'x', false, 'ACTIVO',"
            + " (SELECT id FROM countries ORDER BY code LIMIT 1))",
        id.toString(),
        "pk-" + usuario,
        "pk-" + usuario + "@nexus.test");
    if (rol != null) {
      jdbc.update(
          "INSERT INTO user_roles (user_id, role_id, role_type)"
              + " SELECT CAST(? AS uuid), CAST(? AS uuid), role_type FROM roles WHERE id = CAST(? AS uuid)",
          id.toString(),
          rol,
          rol);
    }
    return id;
  }

  private void limpiarPersonas() {
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'pk-%')");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'pk-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'pk-%'");
  }
}
