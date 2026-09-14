package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.modules.products.domain.service.GetOwnProductCommentService;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Pruebas de API de `RF-PM-013` — la reseña propia sobre un producto. */
@AutoConfigureMockMvc
class ProductCommentMineIT extends ProductCommentTestSupport {

  @Autowired private MockMvc mvc;
  @Autowired private SessionFactory sessionFactory;
  @Autowired private GetOwnProductCommentService servicio;

  private UUID deAna;
  private UUID deLuis;

  @BeforeEach
  void sembrar() {
    sembrarBase();
    deAna = resena(activo, ana, 5, "La de Ana", false);
    deLuis = resena(activo, luis, 2, "La de Luis", false);
  }

  @AfterEach
  void vaciar() {
    limpiarResenas();
  }

  @Test
  @DisplayName(
      "`CA-PM-212` — devuelve la reseña viva del actor con su identificador y la forma del alta")
  void devuelveLaPropia() throws Exception {
    mia(ana, activo)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(deAna.toString()))
        .andExpect(jsonPath("$.productId").value(activo.toString()))
        .andExpect(jsonPath("$.rating").value(5))
        .andExpect(jsonPath("$.comment").value("La de Ana"))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.updatedAt").isNotEmpty())
        .andExpect(jsonPath("$.author").doesNotExist());
  }

  @Test
  @DisplayName("`CA-PM-213` — sin reseña, retirada la suya, o producto inexistente: el mismo 404")
  void elCuatroCientosCuatroNoDiceNadaDelProducto() throws Exception {
    UUID otro = bot("RS_OTRO", "Sin reseña de Ana", "ACTIVO", false);
    UUID retiradaDeAdmin = resena(activo, admin, 1, "Retirada", true);

    String sinResena = cuerpoDe(mia(ana, otro).andExpect(status().isNotFound()));
    String retirada = cuerpoDe(mia(admin, activo).andExpect(status().isNotFound()));
    String inexistente = cuerpoDe(mia(ana, UUID.randomUUID()).andExpect(status().isNotFound()));

    assertThat(ProductCommentCreateIT.normalizar(sinResena))
        .isEqualTo(ProductCommentCreateIT.normalizar(retirada))
        .isEqualTo(ProductCommentCreateIT.normalizar(inexistente));
    assertThat(sinResena).contains("No has reseñado este producto.");
    assertThat(retirada).doesNotContain(retiradaDeAdmin.toString());
  }

  @Test
  @DisplayName(
      "`CA-PM-214` — responde sobre un producto inactivo y sobre uno retirado cuando la reseña existe")
  void respondeAunqueElProductoNoSeVenda() throws Exception {
    UUID sobreInactivo = resena(inactivo, ana, 3, "Sobre el inactivo", false);
    UUID sobreRetirado = resena(retirado, ana, 3, "Sobre el retirado", false);

    mia(ana, inactivo)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(sobreInactivo.toString()));
    mia(ana, retirado)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(sobreRetirado.toString()));
  }

  @Test
  @DisplayName(
      "`CA-PM-215` — dos autores sobre el mismo producto: cada uno recibe la suya y nunca la del otro")
  void cadaUnoLaSuya() throws Exception {
    mia(ana, activo).andExpect(jsonPath("$.id").value(deAna.toString()));
    mia(luis, activo).andExpect(jsonPath("$.id").value(deLuis.toString()));

    String deAnaSegunAna = cuerpoDe(mia(ana, activo));
    assertThat(deAnaSegunAna).doesNotContain(deLuis.toString()).doesNotContain("La de Luis");
  }

  @Test
  @DisplayName("`CA-PM-216` — sin `products:comment` responde 403")
  void sinPermiso() throws Exception {
    mvc.perform(
            get("/api/v1/products/{id}/comments/mine", activo)
                .with(comoAdministradorSinComment(ana)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "`CA-PM-217` — `/comments/mine` es la ruta literal: nunca 405 ni 400 por identificador inválido")
  void laRutaLiteralGana() throws Exception {
    int estado = mia(ana, activo).andReturn().getResponse().getStatus();
    assertThat(estado).isEqualTo(200);

    // Y un identificador de PRODUCTO malformado sí responde 400: aquí hay token.
    mvc.perform(
            get("/api/v1/products/{id}/comments/mine", "no-es-un-uuid").with(comoResenador(ana)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("`CA-PM-218` — la lectura cuesta UNA sentencia")
  void unaSentencia() {
    var estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();

    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                ana.toString(),
                "n/a",
                java.util.List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "products:comment"))));
    try {
      servicio.mine(activo);
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private ResultActions mia(UUID quien, UUID producto) throws Exception {
    return mvc.perform(
        get("/api/v1/products/{id}/comments/mine", producto).with(comoResenador(quien)));
  }

  private static String cuerpoDe(ResultActions resultado) throws Exception {
    return resultado.andReturn().getResponse().getContentAsString();
  }
}
