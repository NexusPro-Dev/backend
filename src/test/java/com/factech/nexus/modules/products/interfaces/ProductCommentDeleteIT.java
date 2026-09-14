package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Pruebas de API de `RF-PM-011` — retirar la reseña propia. */
@AutoConfigureMockMvc
class ProductCommentDeleteIT extends ProductCommentTestSupport {

  @Autowired private MockMvc mvc;

  private UUID deAna;

  @BeforeEach
  void sembrar() {
    sembrarBase();
    deAna = resena(activo, ana, 4, "Buen bot", false);
  }

  @AfterEach
  void vaciar() {
    limpiarResenas();
  }

  @Test
  @DisplayName(
      "`CA-PM-193` — el autor retira con 204, SIN motivo ni cuerpo, y la fila queda marcada e intacta")
  void elAutorRetira() throws Exception {
    retirar(comoResenador(ana), activo, deAna).andExpect(status().isNoContent());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT rating, comment, deleted_at FROM product_comments WHERE id = CAST(? AS uuid)",
            deAna.toString());
    assertThat(fila.get("deleted_at")).isNotNull();
    assertThat(((Number) fila.get("rating")).intValue()).isEqualTo(4);
    assertThat(fila.get("comment")).isEqualTo("Buen bot");
  }

  @Test
  @DisplayName(
      "`CA-PM-194` — audita LOGICAL con la instantánea completa, el actor y el motivo fijo")
  void auditaConElMotivoFijo() throws Exception {
    retirar(comoResenador(ana), activo, deAna).andExpect(status().isNoContent());

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT deletion_type, reason, actor_id::text AS actor, snapshot::text AS foto"
                + " FROM audit_deletion_log WHERE entity = 'product_comments'"
                + " AND entity_id = CAST(? AS uuid)",
            deAna.toString());

    assertThat(fila.get("deletion_type")).isEqualTo("LOGICAL");
    assertThat(fila.get("reason")).isEqualTo("Retirada por su autor");
    assertThat(fila.get("actor")).isEqualTo(ana.toString());
    String foto = (String) fila.get("foto");
    assertThat(foto)
        .contains("\"rating\": 4")
        .contains("\"comment\": \"Buen bot\"")
        .contains("\"user_id\": \"" + ana + "\"")
        // Tomada ANTES de marcar: dice que estaba viva.
        .contains("\"deleted_at\": null");
  }

  @Test
  @DisplayName("`CA-PM-195` — otro cliente con el permiso recibe 403, y la reseña sigue viva")
  void otroClienteNoPuede() throws Exception {
    retirar(comoResenador(luis), activo, deAna)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Solo el autor puede retirar su reseña."));

    assertThat(resenasVivas(activo)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-PM-196` — el SUPERADMINISTRADOR recibe 403 sobre una ajena: no existe moderación")
  void elSuperadministradorTampoco() throws Exception {
    retirar(comoAdministrador(SUPERADMIN), activo, deAna).andExpect(status().isForbidden());

    assertThat(resenasVivas(activo)).isEqualTo(1);
    Long denegaciones =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_security_log WHERE event_type = 'AUTHORIZATION_DENIED'",
            Long.class);
    assertThat(denegaciones).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-PM-197` — inexistente, ya retirada (segundo DELETE del autor) y de otro producto: el mismo 404")
  void elCuatroCientosCuatroEsUniforme() throws Exception {
    UUID otro = bot("RS_OTRO", "Otro bot", "ACTIVO", false);

    String porInexistente = cuerpoDe(retirar(comoResenador(ana), activo, UUID.randomUUID()));
    String porOtroProducto = cuerpoDe(retirar(comoResenador(ana), otro, deAna));
    retirar(comoResenador(ana), activo, deAna).andExpect(status().isNoContent());
    String porRetirada = cuerpoDe(retirar(comoResenador(ana), activo, deAna));

    assertThat(ProductCommentCreateIT.normalizar(porInexistente))
        .isEqualTo(ProductCommentCreateIT.normalizar(porRetirada))
        .isEqualTo(ProductCommentCreateIT.normalizar(porOtroProducto));
    assertThat(porRetirada).contains("La reseña no existe.");

    // Y UNA sola fila de auditoría: el segundo DELETE no escribió nada.
    Long registros =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_deletion_log WHERE entity_id = CAST(? AS uuid)",
            Long.class,
            deAna.toString());
    assertThat(registros).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-PM-198` — la retirada sale de la lista, de la propia y del rating; nulo si era la única")
  void saleDeTodasPartes() throws Exception {
    UUID deLuis = resena(activo, luis, 2, "Regular", false);

    // (4 + 2) / 2 = 3.00 con dos.
    detalle()
        .andExpect(jsonPath("$.rating.average").value(3.00))
        .andExpect(jsonPath("$.rating.count").value(2));

    retirar(comoResenador(luis), activo, deLuis).andExpect(status().isNoContent());

    detalle()
        .andExpect(jsonPath("$.rating.average").value(4.00))
        .andExpect(jsonPath("$.rating.count").value(1));
    mvc.perform(get("/api/v1/products/{id}/comments", activo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[*].id", Matchers.not(Matchers.hasItem(deLuis.toString()))));
    mvc.perform(get("/api/v1/products/{id}/comments/mine", activo).with(comoResenador(luis)))
        .andExpect(status().isNotFound());

    retirar(comoResenador(ana), activo, deAna).andExpect(status().isNoContent());

    detalle()
        .andExpect(jsonPath("$.rating.average").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.rating.count").value(0));
  }

  @Test
  @DisplayName("`CA-PM-199` — retirada la suya, el autor escribe otra con 201, y es otra fila")
  void escribeOtra() throws Exception {
    retirar(comoResenador(ana), activo, deAna).andExpect(status().isNoContent());

    mvc.perform(
            post("/api/v1/products/{id}/comments", activo)
                .with(comoResenador(ana))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(5, "Segunda opinión")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(Matchers.not(deAna.toString())));

    assertThat(resenasVivas(activo)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "`CA-PM-200` — el autor retira su reseña sobre un producto inactivo y sobre uno retirado")
  void seRetiraAunqueElProductoNoSeVenda() throws Exception {
    UUID sobreInactivo = resena(inactivo, ana, 3, "Sobre el inactivo", false);
    UUID sobreRetirado = resena(retirado, ana, 3, "Sobre el retirado", false);

    retirar(comoResenador(ana), inactivo, sobreInactivo).andExpect(status().isNoContent());
    retirar(comoResenador(ana), retirado, sobreRetirado).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("`CA-PM-201` — un DELETE con cuerpo responde igual que sin él: el cuerpo se ignora")
  void elCuerpoSeIgnora() throws Exception {
    mvc.perform(
            delete("/api/v1/products/{p}/comments/{c}", activo, deAna)
                .with(comoResenador(ana))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"no hace falta\"}"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("no existe `POST /comments/{id}/deletion`: el retiro es un DELETE y nada más")
  void noHayPostDeletion() throws Exception {
    mvc.perform(
            post("/api/v1/products/{p}/comments/{c}/deletion", activo, deAna)
                .with(comoResenador(ana))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"por simetría\"}"))
        .andExpect(status().is4xxClientError())
        .andExpect(status().is(Matchers.not(204)));
    assertThat(resenasVivas(activo)).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private ResultActions retirar(RequestPostProcessor quien, UUID producto, UUID resena)
      throws Exception {
    return mvc.perform(delete("/api/v1/products/{p}/comments/{c}", producto, resena).with(quien));
  }

  private ResultActions detalle() throws Exception {
    return mvc.perform(get("/api/v1/products/{id}", activo).with(comoAdministrador(admin)))
        .andExpect(status().isOk());
  }

  private static String cuerpoDe(ResultActions resultado) throws Exception {
    return resultado.andReturn().getResponse().getContentAsString();
  }
}
