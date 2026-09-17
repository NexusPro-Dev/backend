package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.domain.models.ProductImageTest;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-PM-016` — la imagen de una portada, sin autenticación.
 *
 * <p>Se sube con `RF-PM-014` y se lee sin token: es la única ruta del sistema que sirve bytes.
 */
@AutoConfigureMockMvc
class ProductImageIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String RUTA = "/api/v1/product-images/";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID bot;

  @BeforeEach
  void sembrarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM product_images");
    bot = crearBot("ASESORIA", "TIENDA");
  }

  @AfterEach
  void vaciarCatalogo() {
    jdbc.update("DELETE FROM products");
    jdbc.update("DELETE FROM product_images");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
  }

  @Test
  @DisplayName(
      "`CA-PM-255` — sin token: los bytes exactos, el tipo detectado y las cinco cabeceras")
  void sirveLaImagenSinToken() throws Exception {
    // Un JPEG disfrazado de PNG: vuelve como lo que es.
    byte[] jpeg = ProductImageTest.relleno(ProductImageTest.JPEG, 4_096);
    UUID imagen = subir(bot, jpeg, "foto.png", MediaType.IMAGE_PNG_VALUE);

    MockHttpServletResponse respuesta =
        mvc.perform(get(RUTA + imagen))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/jpeg"))
            .andExpect(header().string("Content-Length", String.valueOf(jpeg.length)))
            .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Disposition", "inline"))
            .andReturn()
            .getResponse();
    assertThat(respuesta.getContentAsByteArray()).isEqualTo(jpeg);
  }

  @Test
  @DisplayName("`CA-PM-256` — con token responde lo mismo, cabeceras incluidas")
  void conTokenRespondeLoMismo() throws Exception {
    byte[] png = ProductImageTest.relleno(ProductImageTest.PNG, 1_024);
    UUID imagen = subir(bot, png, "foto.png", MediaType.IMAGE_PNG_VALUE);

    MockHttpServletResponse respuesta =
        mvc.perform(get(RUTA + imagen).with(cualquiera()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn()
            .getResponse();
    assertThat(respuesta.getContentAsByteArray()).isEqualTo(png);

    // Y un Accept que no es de imagen no cambia nada: la ruta no negocia.
    mvc.perform(get(RUTA + imagen).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/png"));
  }

  @Test
  @DisplayName("`CA-PM-257` — 404 con el mismo cuerpo: inexistente, reemplazada y quitada")
  void elCuatroCientosCuatroEsUniforme() throws Exception {
    String inexistente = cuerpo404(UUID.randomUUID());

    UUID reemplazada = subir(bot, ProductImageTest.PNG, "a.png", MediaType.IMAGE_PNG_VALUE);
    UUID vigente = subir(bot, ProductImageTest.JPEG, "b.jpg", MediaType.IMAGE_JPEG_VALUE);
    assertThat(sinIdentidad(cuerpo404(reemplazada))).isEqualTo(sinIdentidad(inexistente));

    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/products/{id}/cover", bot)
                .with(admin()))
        .andExpect(status().isOk());
    assertThat(sinIdentidad(cuerpo404(vigente))).isEqualTo(sinIdentidad(inexistente));
    assertThat(inexistente).contains("La imagen no existe.");
  }

  @Test
  @DisplayName(
      "`CA-PM-258` — no mira el producto: inactivo, retirado y de alcance TIENDA se sirven")
  void noMiraElProducto() throws Exception {
    // `bot` es TIENDA e inactivo desde que nace.
    UUID imagen = subir(bot, ProductImageTest.WEBP, "c.webp", "image/webp");
    mvc.perform(get(RUTA + imagen)).andExpect(status().isOk());

    jdbc.update(
        "UPDATE products SET deleted_at = now() WHERE id = CAST(? AS uuid)", bot.toString());
    mvc.perform(get(RUTA + imagen))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/webp"));
  }

  @Test
  @DisplayName("`CA-PM-259` — solo el GET es público; un identificador sin forma es VAL-001")
  void soloElGet() throws Exception {
    // `EndpointPermissionsIT` lista la ruta como pública en GET; aquí, que las
    // hermanas de escritura siguen exigiendo token.
    mvc.perform(
            multipart(HttpMethod.PUT, "/api/v1/products/{id}/cover", bot)
                .file(new MockMultipartFile("file", "a.png", "image/png", ProductImageTest.PNG)))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/v1/products/{id}/cover", bot))
        .andExpect(status().isUnauthorized());

    mvc.perform(get(RUTA + "no-es-un-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));
  }

  // ---------------------------------------------------------------------------

  private UUID subir(UUID producto, byte[] bytes, String nombre, String tipo) throws Exception {
    mvc.perform(
            multipart(HttpMethod.PUT, "/api/v1/products/{id}/cover", producto)
                .file(new MockMultipartFile("file", nombre, tipo, bytes))
                .with(admin()))
        .andExpect(status().isOk());
    return jdbc.queryForObject(
        "SELECT cover_image_id FROM products WHERE id = CAST(? AS uuid)",
        UUID.class,
        producto.toString());
  }

  private String cuerpo404(UUID imagen) throws Exception {
    return mvc.perform(get(RUTA + imagen))
        .andExpect(status().isNotFound())
        .andExpect(
            header()
                .string(
                    "Content-Type", org.hamcrest.Matchers.startsWith("application/problem+json")))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String sinIdentidad(String cuerpo) {
    return cuerpo
        .replaceAll("\"correlationId\":\"[^\"]*\"", "")
        .replaceAll("\"instance\":\"[^\"]*\"", "");
  }

  private static RequestPostProcessor admin() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:update");
  }

  private static RequestPostProcessor cualquiera() {
    return user(UUID.randomUUID().toString()).authorities(() -> "products:sale");
  }

  private UUID crearBot(String codigo, String alcance) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description, price,"
            + " currency_id, status, created_at, updated_at)"
            + " VALUES (?, 'MANUAL', CAST(? AS uuid), ?, 'BOT', ?, 'Descripción.', 49.99,"
            + " CAST(? AS uuid), 'INACTIVO', now(), now())",
        alcance,
        id.toString(),
        codigo,
        "Asesoría",
        USD);
    return id;
  }
}
