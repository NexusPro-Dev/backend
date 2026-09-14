package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Pruebas de API de `RF-PM-010` — corregir la reseña propia. */
@AutoConfigureMockMvc
class ProductCommentUpdateIT extends ProductCommentTestSupport {

  @Autowired private MockMvc mvc;

  private UUID deAna;

  @BeforeEach
  void sembrar() {
    sembrarBase();
    deAna = resena(activo, ana, 4, "Buen bot", false);
    // Que la fila tenga una marca CLARAMENTE anterior: así «avanza» se ve.
    jdbc.update(
        "UPDATE product_comments SET created_at = now() - interval '1 day',"
            + " updated_at = now() - interval '1 day' WHERE id = CAST(? AS uuid)",
        deAna.toString());
  }

  @AfterEach
  void vaciar() {
    limpiarResenas();
  }

  @Test
  @DisplayName(
      "`CA-PM-184` — el autor corrige puntuación, texto o los dos; updatedAt avanza y createdAt no")
  void elAutorCorrige() throws Exception {
    String antes = campo("created_at");

    corregir(comoResenador(ana), deAna, "{\"rating\": 2}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(deAna.toString()))
        .andExpect(jsonPath("$.rating").value(2))
        .andExpect(jsonPath("$.comment").value("Buen bot"));

    corregir(comoResenador(ana), deAna, "{\"comment\": \"  Ya no tan bueno  \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rating").value(2))
        .andExpect(jsonPath("$.comment").value("Ya no tan bueno"));

    corregir(comoResenador(ana), deAna, cuerpo(5, "Me retracto"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rating").value(5))
        .andExpect(jsonPath("$.comment").value("Me retracto"));

    assertThat(campo("created_at")).isEqualTo(antes);
    Boolean avanzo =
        jdbc.queryForObject(
            "SELECT updated_at > created_at FROM product_comments WHERE id = CAST(? AS uuid)",
            Boolean.class,
            deAna.toString());
    assertThat(avanzo).isTrue();
  }

  @Test
  @DisplayName(
      "`CA-PM-185` — otro cliente CON el permiso recibe 403 sobre una ajena, y la reseña no cambia")
  void otroClienteNoPuede() throws Exception {
    corregir(comoResenador(luis), deAna, "{\"rating\": 1}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Solo el autor puede corregir su reseña."));

    assertThat(campo("rating")).isEqualTo("4");
    Long denegaciones =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE event_type = 'AUTHORIZATION_DENIED'",
            Long.class);
    assertThat(denegaciones).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-PM-186` — un ADMINISTRADOR con el permiso recibe el mismo 403: el permiso habilita, no autoriza")
  void elAdministradorTampoco() throws Exception {
    corregir(comoAdministrador(admin), deAna, "{\"rating\": 1}").andExpect(status().isForbidden());

    assertThat(campo("rating")).isEqualTo("4");
  }

  @Test
  @DisplayName(
      "`CA-PM-187` — inexistente, retirada (también para su autor) y de otro producto: el mismo 404")
  void elCuatroCientosCuatroEsUniforme() throws Exception {
    UUID retirada = resena(activo, luis, 3, "Retirada", true);
    UUID otro = bot("RS_OTRO", "Otro bot", "ACTIVO", false);

    String porInexistente =
        cuerpoDe(corregir(comoResenador(ana), UUID.randomUUID(), "{\"rating\": 1}"));
    String porRetirada = cuerpoDe(corregir(comoResenador(luis), retirada, "{\"rating\": 1}"));
    String porOtroProducto =
        cuerpoDe(
            mvc.perform(
                patch("/api/v1/products/{p}/comments/{c}", otro, deAna)
                    .with(comoResenador(ana))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rating\": 1}")));

    assertThat(ProductCommentCreateIT.normalizar(porInexistente))
        .isEqualTo(ProductCommentCreateIT.normalizar(porRetirada))
        .isEqualTo(ProductCommentCreateIT.normalizar(porOtroProducto));
    assertThat(porInexistente).contains("La reseña no existe.");
    // Y la de Ana sigue como estaba: la ruta con otro producto no la tocó.
    assertThat(campo("rating")).isEqualTo("4");
  }

  @Test
  @DisplayName(
      "`CA-PM-188` — el nulo explícito se rechaza en los dos campos: ninguno admite vaciarse")
  void elNuloExplicitoSeRechaza() throws Exception {
    corregir(comoResenador(ana), deAna, "{\"rating\": null}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    corregir(comoResenador(ana), deAna, "{\"comment\": null}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    corregir(comoResenador(ana), deAna, "{\"rating\": 4.5}").andExpect(status().isBadRequest());
    corregir(comoResenador(ana), deAna, "{\"rating\": 9}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    // `productId` y `userId` en el cuerpo: campos desconocidos.
    corregir(comoResenador(ana), deAna, "{\"userId\": \"" + luis + "\"}")
        .andExpect(status().isBadRequest());

    assertThat(campo("rating")).isEqualTo("4");
  }

  @Test
  @DisplayName(
      "`CA-PM-189` — un cuerpo vacío responde 400; uno sin cambios de valor, 200 sin escribir ni auditar")
  void sinCambiosNoSeEscribe() throws Exception {
    corregir(comoResenador(ana), deAna, "{}")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("Debe informar al menos uno de los campos corregibles."));

    corregir(comoResenador(ana), deAna, cuerpo(4, "Buen bot")).andExpect(status().isOk());

    Boolean avanzo =
        jdbc.queryForObject(
            "SELECT updated_at > created_at FROM product_comments WHERE id = CAST(? AS uuid)",
            Boolean.class,
            deAna.toString());
    assertThat(avanzo).isFalse();
    assertThat(filasDeAuditoria()).isZero();
  }

  @Test
  @DisplayName("`CA-PM-190` — audita UPDATE con el antes y el después de cada campo tocado")
  void auditaElCambio() throws Exception {
    corregir(comoResenador(ana), deAna, "{\"rating\": 1}").andExpect(status().isOk());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT action, actor_id::text AS actor, changes::text AS cambios FROM audit_change_log"
                + " WHERE entity = 'product_comments' AND entity_id = CAST(? AS uuid)",
            deAna.toString());
    assertThat(fila.get("action")).isEqualTo("UPDATE");
    assertThat(fila.get("actor")).isEqualTo(ana.toString());
    String cambios = (String) fila.get("cambios");
    assertThat(cambios).contains("\"rating\"").contains("\"before\": 4").contains("\"after\": 1");
    // El texto no se tocó, y no aparece.
    assertThat(cambios).doesNotContain("\"comment\"");
  }

  @Test
  @DisplayName(
      "`CA-PM-191` — corregir la puntuación mueve el promedio del producto en el acto, y count no cambia")
  void muevePromedio() throws Exception {
    resena(activo, luis, 2, "Regular", false);
    // (4 + 2) / 2 = 3.00
    detalle()
        .andExpect(jsonPath("$.rating.average").value(3.00))
        .andExpect(jsonPath("$.rating.count").value(2));

    corregir(comoResenador(ana), deAna, "{\"rating\": 5}").andExpect(status().isOk());

    // (5 + 2) / 2 = 3.50
    detalle()
        .andExpect(jsonPath("$.rating.average").value(3.50))
        .andExpect(jsonPath("$.rating.count").value(2));
  }

  @Test
  @DisplayName(
      "`CA-PM-192` — el autor corrige su reseña sobre un producto inactivo y sobre uno retirado")
  void seCorrigeAunqueElProductoNoSeVenda() throws Exception {
    UUID sobreInactivo = resena(inactivo, ana, 3, "Sobre el inactivo", false);
    UUID sobreRetirado = resena(retirado, ana, 3, "Sobre el retirado", false);

    mvc.perform(
            patch("/api/v1/products/{p}/comments/{c}", inactivo, sobreInactivo)
                .with(comoResenador(ana))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 1}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/v1/products/{p}/comments/{c}", retirado, sobreRetirado)
                .with(comoResenador(ana))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 1}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("sin `products:comment` no se llega a la reseña: 403 antes de mirarla")
  void sinPermiso() throws Exception {
    corregir(comoAdministradorSinComment(ana), deAna, "{\"rating\": 1}")
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------

  private ResultActions corregir(RequestPostProcessor quien, UUID resena, String json)
      throws Exception {
    return mvc.perform(
        patch("/api/v1/products/{p}/comments/{c}", activo, resena)
            .with(quien)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions detalle() throws Exception {
    return mvc.perform(get("/api/v1/products/{id}", activo).with(comoAdministrador(admin)))
        .andExpect(status().isOk());
  }

  private String campo(String columna) {
    return jdbc.queryForObject(
        "SELECT " + columna + "::text FROM product_comments WHERE id = CAST(? AS uuid)",
        String.class,
        deAna.toString());
  }

  private long filasDeAuditoria() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'product_comments'", Long.class);
  }

  private static String cuerpoDe(ResultActions resultado) throws Exception {
    return resultado.andReturn().getResponse().getContentAsString();
  }
}
