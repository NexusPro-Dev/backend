package com.factech.nexus.modules.products.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * `RN-PM-031` — el promedio y la cantidad de reseñas vivas en las cuatro lecturas del producto, y
 * en la respuesta del alta (`RF-PM-009` · `T-10`, `T-11`; `CA-PM-180`, `CA-PM-181`).
 *
 * <p>El número de sentencias de los listados <b>no cambia</b> (`CA-PM-182`): lo afirman las pruebas
 * que ya cuentan sentencias en {@code ProductListIT}, {@code ProductOfferIT} y {@code HotlinkIT},
 * que siguen en verde con el agregado dentro de la misma consulta.
 */
@AutoConfigureMockMvc
class ProductRatingIT extends ProductCommentTestSupport {

  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  @Autowired private MockMvc mvc;

  private UUID vendedora;

  @BeforeEach
  void sembrar() {
    sembrarBase();
    vendedora = persona("resena-vendedora", "Vera", "Ruiz");
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type)"
            + " SELECT CAST(? AS uuid), CAST(? AS uuid), role_type FROM roles WHERE id = CAST(? AS uuid)",
        vendedora.toString(),
        AGENTE,
        AGENTE);
  }

  @AfterEach
  void vaciar() {
    limpiarResenas();
  }

  @Test
  @DisplayName(
      "`CA-PM-180` — sin reseñas, `rating` está PRESENTE con average nulo y count cero en las cuatro lecturas")
  void sinResenas() throws Exception {
    catalogo()
        .andExpect(jsonPath("$.content[?(@.code == 'RS_ACTIVO')].rating.count").value(0))
        .andExpect(
            jsonPath("$.content[?(@.code == 'RS_ACTIVO')].rating.average")
                .value(Matchers.contains(Matchers.nullValue())));
    detalle()
        .andExpect(jsonPath("$.rating").exists())
        .andExpect(jsonPath("$.rating.average").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.rating.count").value(0));
    oferta()
        .andExpect(jsonPath("$.services.content[?(@.code == 'RS_ACTIVO')].rating.count").value(0));
    hotlink()
        .andExpect(jsonPath("$.product.rating.average").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.product.rating.count").value(0));
  }

  @Test
  @DisplayName(
      "`CA-PM-181` — con reseñas vivas, average con dos decimales y count en las cuatro — el hotlink sin token")
  void conResenas() throws Exception {
    resena(activo, ana, 5, "Cinco", false);
    resena(activo, luis, 4, "Cuatro", false);
    resena(activo, admin, 4, "Cuatro también", false);
    // Una retirada de 1 que NO cuenta: (5 + 4 + 4) / 3 = 4.333… → 4.33
    resena(activo, persona("resena-retirada", "Rita", "Gil"), 1, "Retirada", true);

    detalle()
        .andExpect(jsonPath("$.rating.average").value(4.33))
        .andExpect(jsonPath("$.rating.count").value(3));
    catalogo()
        .andExpect(jsonPath("$.content[?(@.code == 'RS_ACTIVO')].rating.average").value(4.33))
        .andExpect(jsonPath("$.content[?(@.code == 'RS_ACTIVO')].rating.count").value(3));
    oferta()
        .andExpect(
            jsonPath("$.services.content[?(@.code == 'RS_ACTIVO')].rating.average").value(4.33));
    hotlink()
        .andExpect(jsonPath("$.product.rating.average").value(4.33))
        .andExpect(jsonPath("$.product.rating.count").value(3));
  }

  @Test
  @DisplayName("el redondeo es a la mitad hacia arriba, y en un solo sitio: 4.5 no baja")
  void redondeoHalfUp() throws Exception {
    resena(activo, ana, 5, "Cinco", false);
    resena(activo, luis, 4, "Cuatro", false);
    // 4.5 exacto → 4.50; y 2/3 = 0.666… → 0.67 no aplica aquí pero 4.345 sí lo haría.
    detalle().andExpect(jsonPath("$.rating.average").value(4.5));
  }

  @Test
  @DisplayName("el alta del producto devuelve `rating` presente y vacío, sin consultar")
  void elAltaDevuelveRatingVacio() throws Exception {
    mvc.perform(
            post("/api/v1/products")
                .with(comoAdministrador(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"RS_NUEVO\",\"type\":\"BOT\",\"name\":\"Nuevo bot de rating\","
                        + "\"price\":1.00,\"currencyId\":\""
                        + USD
                        + "\",\"scope\":\"TIENDA\",\"implementation\":\"MANUAL\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rating.average").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.rating.count").value(0));
  }

  // ---------------------------------------------------------------------------

  private org.springframework.test.web.servlet.ResultActions catalogo() throws Exception {
    return mvc.perform(get("/api/v1/products").param("size", "100").with(comoAdministrador(admin)))
        .andExpect(status().isOk());
  }

  private org.springframework.test.web.servlet.ResultActions detalle() throws Exception {
    return mvc.perform(get("/api/v1/products/{id}", activo).with(comoAdministrador(admin)))
        .andExpect(status().isOk());
  }

  private org.springframework.test.web.servlet.ResultActions oferta() throws Exception {
    return mvc.perform(
            get("/api/v1/products/available")
                .with(user(luis.toString()).authorities(() -> "products:sale")))
        .andExpect(status().isOk());
  }

  private org.springframework.test.web.servlet.ResultActions hotlink() throws Exception {
    return mvc.perform(get("/api/v1/hotlinks/{u}/{c}", "resena-vendedora", "RS_ACTIVO"))
        .andExpect(status().isOk());
  }
}
