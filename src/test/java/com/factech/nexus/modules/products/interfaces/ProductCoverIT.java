package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.domain.models.ProductImage;
import com.factech.nexus.modules.products.domain.models.ProductImageTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
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
 * `RF-PM-014` (subir o reemplazar) y `RF-PM-015` (quitar) la portada de un producto.
 *
 * <p><b>Ningún archivo de imagen entra al repositorio</b>: los bytes se generan — una firma válida
 * más relleno ({@link ProductImageTest}). Es todo lo que el detector mira.
 */
@AutoConfigureMockMvc
class ProductCoverIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final String RUTA = "/api/v1/product-images/";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID oro;
  private UUID free;
  private UUID upgrade;
  private UUID bot;

  @BeforeEach
  void sembrarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM product_images");
    // Antes que las membresías: `user_memberships` las referencia, y `V57` da
    // una a toda persona (como en `HotlinkIT`; sin esto la suite depende del orden).
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM memberships");
    oro = membresia("ORO", "Oro", 1, null);
    free = membresia("BECA", "Beca", 2, oro);
    upgrade = crear("UPGRADE_ORO", "UPGRADE_MEMBRESIA", "Ascenso a Oro", "crown");
    bot = crear("ASESORIA", "BOT", "Asesoría", null);
  }

  @AfterEach
  void vaciarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM product_images");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  // ---------------------------------------------------------------------------
  // RF-PM-014 — subir o reemplazar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "`CA-PM-239` — sube un PNG: `coverImageUrl` por imagen, y la fila con los mismos bytes")
  void subeUnaPortada() throws Exception {
    byte[] png = ProductImageTest.relleno(ProductImageTest.PNG, 2_048);

    String direccion =
        mvc.perform(subir(upgrade, png, "portada.png", MediaType.IMAGE_PNG_VALUE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(upgrade.toString()))
            .andExpect(jsonPath("$.coverImageUrl").value(Matchers.startsWith(RUTA)))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"coverImageUrl\":\"([^\"]+)\".*", "$1");

    UUID imagen = UUID.fromString(direccion.substring(RUTA.length()));
    assertThat(portadaDe(upgrade)).isEqualTo(imagen);
    Map<String, Object> fila = imagenDe(imagen);
    assertThat(fila.get("content_type")).isEqualTo("image/png");
    assertThat((byte[]) fila.get("content")).isEqualTo(png);
  }

  @Test
  @DisplayName(
      "`CA-PM-240` — reemplaza: otra dirección, la anterior no existe, UNA fila por producto")
  void reemplazaLaPortada() throws Exception {
    UUID primera = subirYLeer(upgrade, ProductImageTest.PNG);
    UUID segunda = subirYLeer(upgrade, ProductImageTest.JPEG);

    assertThat(segunda).isNotEqualTo(primera);
    assertThat(portadaDe(upgrade)).isEqualTo(segunda);
    assertThat(cuantasImagenes()).isEqualTo(1);
    assertThat(existeImagen(primera)).isFalse();

    // La dirección vieja responde 404 y la nueva 200 (`RF-PM-016`).
    mvc.perform(get(RUTA + primera)).andExpect(status().isNotFound());
    mvc.perform(get(RUTA + segunda)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-PM-241` — la auditoría lleva `cover_image_id` antes y después, y nada más")
  void auditaElCambio() throws Exception {
    UUID primera = subirYLeer(upgrade, ProductImageTest.PNG);
    String alta = ultimoCambio(upgrade);
    assertThat(alta)
        .contains("\"cover_image_id\"")
        .contains("\"before\": \"\"")
        .contains("\"after\": \"" + primera + "\"")
        .doesNotContain("\"name\"")
        .doesNotContain("\"icon\"");

    UUID segunda = subirYLeer(upgrade, ProductImageTest.JPEG);
    assertThat(ultimoCambio(upgrade))
        .contains("\"before\": \"" + primera + "\"")
        .contains("\"after\": \"" + segunda + "\"");
    assertThat(eventosDe(upgrade)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "`CA-PM-242` — el tipo lo deciden los bytes: JPEG disfrazado de PNG; GIF, SVG y texto no")
  void elTipoLoDecidenLosBytes() throws Exception {
    // Un JPEG enviado como `image/png` y llamado `foto.png` se guarda como lo que es.
    UUID imagen = subirYLeer(upgrade, ProductImageTest.JPEG, "foto.png", MediaType.IMAGE_PNG_VALUE);
    assertThat(imagenDe(imagen).get("content_type")).isEqualTo("image/jpeg");

    // Y lo que no es una imagen se rechaza aunque diga serlo, nombrando `file`.
    for (byte[] malo :
        List.of(ProductImageTest.GIF, ProductImageTest.SVG, ProductImageTest.TEXTO)) {
      mvc.perform(subir(bot, malo, "foto.png", MediaType.IMAGE_PNG_VALUE))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-003"))
          .andExpect(jsonPath("$.errors[0].field").value("file"));
    }
    assertThat(portadaDe(bot)).isNull();
  }

  @Test
  @DisplayName("`CA-PM-243` — 5 242 880 bytes caben; 5 242 881 es VAL-004")
  void elTope() throws Exception {
    mvc.perform(
            subir(
                upgrade,
                ProductImageTest.relleno(ProductImageTest.PNG, ProductImage.TAMANO_MAXIMO)))
        .andExpect(status().isOk());

    mvc.perform(
            subir(
                bot,
                ProductImageTest.relleno(ProductImageTest.PNG, ProductImage.TAMANO_MAXIMO + 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"))
        .andExpect(jsonPath("$.errors[0].field").value("file"));
    assertThat(portadaDe(bot)).isNull();
  }

  @Test
  @DisplayName("`CA-PM-244` — sin parte y parte vacía es VAL-002; sin multipart, 400 con EX-003")
  void sinArchivo() throws Exception {
    mvc.perform(multipart(HttpMethod.PUT, "/api/v1/products/{id}/cover", upgrade).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[0].field").value("file"));

    mvc.perform(subir(upgrade, new byte[0], "vacio.png", MediaType.IMAGE_PNG_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));

    // Un PUT con JSON es un cliente equivocado de ruta: 400 legible, no 415 ni 500.
    mvc.perform(
            put("/api/v1/products/{id}/cover", upgrade)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(portadaDe(upgrade)).isNull();
  }

  @Test
  @DisplayName("`CA-PM-245` — inexistente y retirado responden 404 igual; inactivo se sube")
  void productoInexistenteRetiradoOInactivo() throws Exception {
    String inexistente =
        mvc.perform(subir(UUID.randomUUID(), ProductImageTest.PNG))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

    jdbc.update(
        "UPDATE products SET deleted_at = now() WHERE id = CAST(? AS uuid)", bot.toString());
    String retirado =
        mvc.perform(subir(bot, ProductImageTest.PNG))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // El mismo cuerpo salvo lo que identifica la petición: correlación e instancia.
    assertThat(sinIdentidad(retirado))
        .isEqualTo(sinIdentidad(inexistente))
        .contains("No existe un producto vivo con ese identificador.");

    // Inactivo (todos nacen así): se sube.
    mvc.perform(subir(upgrade, ProductImageTest.PNG)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("`CA-PM-246` — en un BOT y en un upgrade sin icono: ninguna condición para subir")
  void enLosDosTiposYSinCondicion() throws Exception {
    mvc.perform(subir(bot, ProductImageTest.WEBP)).andExpect(status().isOk());

    UUID viejo = crear("UPGRADE_VIEJO", "UPGRADE_MEMBRESIA", "Ascenso viejo", null);
    mvc.perform(subir(viejo, ProductImageTest.JPEG))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icon").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.coverImageUrl").value(Matchers.startsWith(RUTA)));
  }

  @Test
  @DisplayName("`CA-PM-247` — un rechazo no deja nada: ni fila, ni auditoría, ni cambio")
  void unRechazoNoDejaNada() throws Exception {
    long antes = eventosDe(upgrade);
    mvc.perform(subir(upgrade, ProductImageTest.GIF)).andExpect(status().isBadRequest());
    mvc.perform(subir(upgrade, new byte[0])).andExpect(status().isBadRequest());

    assertThat(cuantasImagenes()).isZero();
    assertThat(eventosDe(upgrade)).isEqualTo(antes);
    assertThat(portadaDe(upgrade)).isNull();
  }

  @Test
  @DisplayName("`CA-PM-248` — las cuatro lecturas devuelven la dirección nueva tras subir")
  void lasCuatroLecturas() throws Exception {
    UUID imagen = subirYLeer(upgrade, ProductImageTest.PNG);
    String esperada = RUTA + imagen;

    mvc.perform(get("/api/v1/products/{id}", upgrade).with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(esperada));
    mvc.perform(get("/api/v1/products").with(lector()).param("targetMembershipId", oro.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].coverImageUrl").value(esperada));
    // La oferta y el hotlink tienen su propia prueba de `coverImageUrl` con
    // sus fixtures (`CA-PM-237`, `CA-PM-238`); aquí, las dos de administración.
  }

  // ---------------------------------------------------------------------------
  // RF-PM-015 — quitar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`CA-PM-249` · `CA-PM-250` — quita la portada de un upgrade con icono, y la audita")
  void quitaLaPortada() throws Exception {
    UUID imagen = subirYLeer(upgrade, ProductImageTest.PNG);

    String cuerpo =
        mvc.perform(quitar(upgrade))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.coverImageUrl").value(Matchers.nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(cuerpo).contains("\"coverImageUrl\":null");
    assertThat(portadaDe(upgrade)).isNull();
    assertThat(existeImagen(imagen)).isFalse();
    mvc.perform(get(RUTA + imagen)).andExpect(status().isNotFound());

    assertThat(ultimoCambio(upgrade))
        .contains("\"before\": \"" + imagen + "\"")
        .contains("\"after\": \"\"")
        .doesNotContain("\"icon\"");
  }

  @Test
  @DisplayName("`CA-PM-251` — `RN-PM-034`: un upgrade SIN icono no se queda sin portada")
  void elUpgradeSinIconoConservaLaPortada() throws Exception {
    UUID imagen = subirYLeer(upgrade, ProductImageTest.PNG);
    // Con portada el icono se vacía (`CA-PM-235`)...
    mvc.perform(corregir(upgrade, "{\"icon\":null}")).andExpect(status().isOk());
    long antes = eventosDe(upgrade);

    // ...y entonces la portada es lo único con lo que se pinta: no se quita.
    mvc.perform(quitar(upgrade))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"))
        .andExpect(jsonPath("$.errors[0].field").value("icon"));

    assertThat(portadaDe(upgrade)).isEqualTo(imagen);
    assertThat(existeImagen(imagen)).isTrue();
    assertThat(eventosDe(upgrade)).isEqualTo(antes);
  }

  @Test
  @DisplayName("`CA-PM-252` — a un BOT se le quita siempre")
  void alBotSeLeQuitaSiempre() throws Exception {
    subirYLeer(bot, ProductImageTest.WEBP);
    mvc.perform(quitar(bot))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.coverImageUrl").value(Matchers.nullValue()));
    assertThat(cuantasImagenes()).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-253` — sin portada responde 200 sin escribir, también el upgrade viejo sin icono")
  void sinPortadaNoEscribeNada() throws Exception {
    UUID viejo = crear("UPGRADE_VIEJO", "UPGRADE_MEMBRESIA", "Ascenso viejo", null);
    for (UUID id : List.of(upgrade, viejo)) {
      OffsetDateTime actualizado = actualizadoDe(id);
      long antes = eventosDe(id);

      mvc.perform(quitar(id)).andExpect(status().isOk());

      assertThat(actualizadoDe(id)).isEqualTo(actualizado);
      assertThat(eventosDe(id)).isEqualTo(antes);
    }
  }

  @Test
  @DisplayName("`CA-PM-254` — inexistente y retirado 404; inactivo se quita")
  void quitarSobreInexistenteRetiradoOInactivo() throws Exception {
    mvc.perform(quitar(UUID.randomUUID())).andExpect(status().isNotFound());

    subirYLeer(bot, ProductImageTest.PNG);
    jdbc.update(
        "UPDATE products SET deleted_at = now() WHERE id = CAST(? AS uuid)", bot.toString());
    mvc.perform(quitar(bot)).andExpect(status().isNotFound());

    subirYLeer(upgrade, ProductImageTest.PNG);
    mvc.perform(quitar(upgrade)).andExpect(status().isOk());
  }

  // ---------------------------------------------------------------------------

  private UUID subirYLeer(UUID producto, byte[] bytes) throws Exception {
    return subirYLeer(producto, bytes, "portada.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE);
  }

  private UUID subirYLeer(UUID producto, byte[] bytes, String nombre, String tipo)
      throws Exception {
    mvc.perform(subir(producto, bytes, nombre, tipo)).andExpect(status().isOk());
    UUID imagen = portadaDe(producto);
    assertThat(imagen).isNotNull();
    return imagen;
  }

  private MockHttpServletRequestBuilder subir(UUID producto, byte[] bytes) {
    return subir(producto, bytes, "portada.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE);
  }

  private MockHttpServletRequestBuilder subir(
      UUID producto, byte[] bytes, String nombre, String tipo) {
    return multipart(HttpMethod.PUT, "/api/v1/products/{id}/cover", producto)
        .file(new MockMultipartFile("file", nombre, tipo, bytes))
        .with(admin());
  }

  private MockHttpServletRequestBuilder quitar(UUID producto) {
    return delete("/api/v1/products/{id}/cover", producto).with(admin());
  }

  private MockHttpServletRequestBuilder corregir(UUID id, String cuerpo) {
    return patch("/api/v1/products/{id}", id)
        .with(admin())
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo);
  }

  private static RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:update");
  }

  private static RequestPostProcessor lector() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:read");
  }

  private static String sinIdentidad(String cuerpo) {
    return cuerpo
        .replaceAll("\"correlationId\":\"[^\"]*\"", "")
        .replaceAll("\"instance\":\"[^\"]*\"", "");
  }

  private UUID portadaDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT cover_image_id FROM products WHERE id = CAST(? AS uuid)",
        UUID.class,
        id.toString());
  }

  private OffsetDateTime actualizadoDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT updated_at FROM products WHERE id = CAST(? AS uuid)",
        OffsetDateTime.class,
        id.toString());
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
        "SELECT changes::text FROM audit_change_log WHERE entity_id = CAST(? AS uuid)"
            + " AND action = 'UPDATE' ORDER BY occurred_at DESC LIMIT 1",
        String.class,
        id.toString());
  }

  private long eventosDe(UUID id) {
    Long filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_change_log WHERE entity_id = CAST(? AS uuid)",
            Long.class,
            id.toString());
    return filas == null ? 0 : filas;
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

  private UUID crear(String codigo, String tipo, String nombre, String icono) {
    UUID id = UUID.randomUUID();
    boolean esUpgrade = "UPGRADE_MEMBRESIA".equals(tipo);
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, icon,"
            + " source_membership_id, target_membership_id, price, currency_id, status,"
            + " created_at, updated_at)"
            + " VALUES ('TIENDA', 'MANUAL', CAST(? AS uuid), ?, ?, ?, 'Descripción.', ?,"
            + " CAST(? AS uuid), CAST(? AS uuid), 49.99, CAST(? AS uuid), 'INACTIVO', ?, ?)",
        id.toString(),
        codigo,
        tipo,
        nombre,
        icono,
        esUpgrade ? free.toString() : null,
        esUpgrade ? oro.toString() : null,
        USD,
        BASE,
        BASE);
    return id;
  }
}
