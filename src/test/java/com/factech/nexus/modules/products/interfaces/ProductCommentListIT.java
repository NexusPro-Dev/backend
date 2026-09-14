package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.modules.products.domain.service.GetProductCommentsService;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Pruebas de API de `RF-PM-012` — las reseñas de un producto, sin autenticación. */
@AutoConfigureMockMvc
class ProductCommentListIT extends ProductCommentTestSupport {

  @Autowired private MockMvc mvc;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private GetProductCommentsService servicio;

  private UUID antigua;
  private UUID reciente;
  private UUID retirada;

  @BeforeEach
  void sembrar() {
    sembrarBase();
    antigua = resena(activo, ana, 5, "La primera", false);
    reciente = resena(activo, luis, 3, "La segunda", false);
    retirada = resena(activo, admin, 1, "La retirada", true);
    // Orden claro: la de Ana es de ayer, la de Luis de hoy.
    jdbc.update(
        "UPDATE product_comments SET created_at = now() - interval '1 day', updated_at = now()"
            + " WHERE id = CAST(? AS uuid)",
        antigua.toString());
  }

  @AfterEach
  void vaciar() {
    limpiarResenas();
  }

  @Test
  @DisplayName(
      "`CA-PM-202` — devuelve, SIN token, las vivas paginadas de la más reciente a la más antigua")
  void devuelveLasVivasSinToken() throws Exception {
    lista(activo)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].id").value(reciente.toString()))
        .andExpect(jsonPath("$.content[1].id").value(antigua.toString()))
        .andExpect(jsonPath("$.content[0].rating").value(3))
        .andExpect(jsonPath("$.content[0].comment").value("La segunda"))
        .andExpect(jsonPath("$.content[0].author.firstName").value("Luis"))
        .andExpect(jsonPath("$.content[0].author.lastName").value("Paz"))
        .andExpect(jsonPath("$.content[1].author.firstName").value("Ana"));

    mvc.perform(get("/api/v1/products/{id}/comments", activo).param("page", "1").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(antigua.toString()))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  @DisplayName(
      "`CA-PM-203` — el CUERPO ENTERO de cada elemento: seis campos, y del autor solo dos cadenas")
  void elCuerpoEnteroDelElemento() throws Exception {
    String cuerpo = cuerpoDe(lista(activo));

    // Lo que NO está: es lo que sostiene `RN-PM-030`.
    assertThat(cuerpo)
        .doesNotContain("userId")
        .doesNotContain("username")
        .doesNotContain("email")
        .doesNotContain("resena-luis")
        .doesNotContain("nexus.test")
        .doesNotContain("status")
        .doesNotContain("roles")
        .doesNotContain(luis.toString())
        .doesNotContain(ana.toString());

    // Y la forma exacta del elemento, clave por clave.
    java.util.Map<String, Object> elemento =
        com.jayway.jsonpath.JsonPath.read(cuerpo, "$.content[0]");
    assertThat(elemento.keySet())
        .containsExactlyInAnyOrder("id", "rating", "comment", "author", "createdAt", "updatedAt");
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> autor = (java.util.Map<String, Object>) elemento.get("author");
    assertThat(autor.keySet()).containsExactlyInAnyOrder("firstName", "lastName");
  }

  @Test
  @DisplayName(
      "`CA-PM-204` — no devuelve las retiradas, y sí las de autores cuya cuenta está inactiva")
  void retiradasFueraAutoresInactivosDentro() throws Exception {
    jdbc.update("UPDATE users SET status = 'INACTIVO' WHERE id = CAST(? AS uuid)", ana.toString());

    String cuerpo = cuerpoDe(lista(activo).andExpect(status().isOk()));
    assertThat(cuerpo).doesNotContain(retirada.toString()).doesNotContain("La retirada");
    assertThat(cuerpo).contains(antigua.toString()).contains("Ana");
  }

  @Test
  @DisplayName("`CA-PM-205` — inactivo, retirado, inexistente y MALFORMADO responden el mismo 404")
  void elCuatroCientosCuatroEsUniforme() throws Exception {
    String porInactivo = cuerpoDe(lista(inactivo).andExpect(status().isNotFound()));
    String porRetirado = cuerpoDe(lista(retirado).andExpect(status().isNotFound()));
    String porInexistente = cuerpoDe(lista(UUID.randomUUID()).andExpect(status().isNotFound()));
    String porMalformado =
        cuerpoDe(
            mvc.perform(get("/api/v1/products/{id}/comments", "no-es-un-uuid"))
                .andExpect(status().isNotFound()));

    assertThat(ProductCommentCreateIT.normalizar(porInactivo))
        .isEqualTo(ProductCommentCreateIT.normalizar(porRetirado))
        .isEqualTo(ProductCommentCreateIT.normalizar(porInexistente))
        .isEqualTo(ProductCommentCreateIT.normalizar(porMalformado));
    assertThat(porMalformado).contains("El producto no existe o no está a la venta.");
  }

  @Test
  @DisplayName("`CA-PM-206` — un producto activo sin reseñas responde 200 con página vacía, no 404")
  void sinResenasEsPaginaVacia() throws Exception {
    UUID virgen = bot("RS_VIRGEN", "Sin reseñas", "ACTIVO", false);
    lista(virgen)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isEmpty());
  }

  @Test
  @DisplayName(
      "`CA-PM-207` — la misma respuesta con token que sin él, incluso con el del autor de una")
  void laMismaRespuestaConToken() throws Exception {
    String anonima = cuerpoDe(lista(activo));
    String comoLuis =
        cuerpoDe(
            mvc.perform(get("/api/v1/products/{id}/comments", activo).with(comoResenador(luis))));
    String comoAdmin =
        cuerpoDe(
            mvc.perform(
                get("/api/v1/products/{id}/comments", activo).with(comoAdministrador(admin))));

    assertThat(comoLuis).isEqualTo(anonima);
    assertThat(comoAdmin).isEqualTo(anonima);
    assertThat(anonima).doesNotContain("mine");
  }

  @Test
  @DisplayName(
      "`CA-PM-209` — tres sentencias, una cuando el producto no procede, e igual con página de 1 que de 20")
  void numeroDeSentencias() throws Exception {
    for (int i = 0; i < 18; i++) {
      resena(activo, persona("resena-extra-" + i, "Extra", "N" + i), 4, "Reseña " + i, false);
    }
    var estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);

    estadisticas.clear();
    servicio.list(activo.toString(), 0, 1);
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);

    estadisticas.clear();
    servicio.list(activo.toString(), 0, 20);
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);

    estadisticas.clear();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> servicio.list(inactivo.toString(), 0, 20))
        .isInstanceOf(com.factech.nexus.shared.error.ResourceNotFoundException.class);
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-PM-210` — una paginación inválida responde 400 con los mensajes del sistema")
  void paginacionInvalida() throws Exception {
    mvc.perform(get("/api/v1/products/{id}/comments", activo).param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("page"));
    mvc.perform(get("/api/v1/products/{id}/comments", activo).param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("size"));
  }

  @Test
  @DisplayName(
      "`CA-PM-211` — dos reseñas del mismo instante no se repiten ni se saltan entre páginas")
  void elIdentificadorDesempata() throws Exception {
    jdbc.update("DELETE FROM product_comments");
    for (int i = 0; i < 5; i++) {
      resena(activo, persona("resena-mismo-" + i, "Mismo", "N" + i), 4, "Reseña " + i, false);
    }
    jdbc.update("UPDATE product_comments SET created_at = '2026-09-14T12:00:00Z'");

    List<String> vistos = new java.util.ArrayList<>();
    for (int pagina = 0; pagina < 3; pagina++) {
      String cuerpo =
          cuerpoDe(
              mvc.perform(
                      get("/api/v1/products/{id}/comments", activo)
                          .param("page", String.valueOf(pagina))
                          .param("size", "2"))
                  .andExpect(status().isOk()));
      vistos.addAll(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.content[*].id"));
    }
    assertThat(vistos).hasSize(5).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName(
      "solo el GET es público: el POST, `/mine` y el PATCH/DELETE de `/{commentId}` exigen token")
  void lasRutasHermanasExigenToken() throws Exception {
    mvc.perform(
            post("/api/v1/products/{id}/comments", activo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(5, "Anónima")))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/products/{id}/comments/mine", activo))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            patch("/api/v1/products/{p}/comments/{c}", activo, antigua)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 1}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/v1/products/{p}/comments/{c}", activo, antigua))
        .andExpect(status().isUnauthorized());

    assertThat(resenasVivas(activo)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "el nombre del autor es el de HOY: corregirlo en el perfil se refleja sin copia que envejezca")
  void elNombreEsElDeHoy() throws Exception {
    jdbc.update(
        "UPDATE users SET first_name = 'Luisa' WHERE id = CAST(? AS uuid)", luis.toString());
    lista(activo).andExpect(jsonPath("$.content[0].author.firstName").value("Luisa"));
  }

  @Test
  @DisplayName("`updatedAt` distinto de `createdAt` es «corregida», sin campo aparte")
  void corregidaSeVeEnLasFechas() throws Exception {
    String cuerpo = cuerpoDe(lista(activo));
    String creada = com.jayway.jsonpath.JsonPath.read(cuerpo, "$.content[1].createdAt");
    String corregida = com.jayway.jsonpath.JsonPath.read(cuerpo, "$.content[1].updatedAt");
    assertThat(corregida).isNotEqualTo(creada);
  }

  // ---------------------------------------------------------------------------

  private ResultActions lista(UUID producto) throws Exception {
    return mvc.perform(get("/api/v1/products/{id}/comments", producto));
  }

  private static String cuerpoDe(ResultActions resultado) throws Exception {
    return resultado.andReturn().getResponse().getContentAsString();
  }
}
