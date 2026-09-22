package com.factech.nexus.modules.products.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.ResultActions;

/** Pruebas de API de `RF-PM-009` — reseñar un producto. */
@AutoConfigureMockMvc
class ProductCommentCreateIT extends ProductCommentTestSupport {

  @Autowired private MockMvc mvc;

  @BeforeEach
  void sembrar() {
    sembrarBase();
  }

  @AfterEach
  void vaciar() {
    limpiarResenas();
  }

  @Test
  @DisplayName(
      "`CA-PM-170` — registra la reseña con 201 y la devuelve recortada y con las dos fechas")
  void registraLaResena() throws Exception {
    resenar(ana, activo, "{\"rating\": 5, \"comment\": \"   Llegan a tiempo.   \"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.productId").value(activo.toString()))
        .andExpect(jsonPath("$.rating").value(5))
        .andExpect(jsonPath("$.comment").value("Llegan a tiempo."))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.updatedAt").isNotEmpty())
        // Sin autor: quien la recibe es quien la escribió.
        .andExpect(jsonPath("$.author").doesNotExist())
        .andExpect(jsonPath("$.userId").doesNotExist());

    assertThat(resenasVivas(activo)).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-PM-171` — una puntuación fuera de 1..5, ausente o decimal responde 400")
  void rechazaLaPuntuacionInvalida() throws Exception {
    resenar(ana, activo, cuerpo(0, "Texto"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    resenar(ana, activo, cuerpo(6, "Texto"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[0].message").value("La puntuación debe ser un entero entre 1 y 5."));
    resenar(ana, activo, "{\"comment\": \"Texto\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    // El decimal SE RECHAZA, no se redondea: Jackson lo convertiría en 4 por
    // omisión, y eso sería decidir por el autor lo que el autor no dijo.
    resenar(ana, activo, "{\"rating\": 4.5, \"comment\": \"Texto\"}")
        .andExpect(status().isBadRequest());
    // Y la cadena tampoco es una puntuación.
    resenar(ana, activo, "{\"rating\": \"5\", \"comment\": \"Texto\"}")
        .andExpect(status().isBadRequest());

    assertThat(resenasVivas(activo)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-172` — un texto ausente, vacío, de solo espacios o de más de mil responde 400")
  void rechazaElTextoInvalido() throws Exception {
    resenar(ana, activo, "{\"rating\": 4}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    resenar(ana, activo, cuerpo(4, ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    resenar(ana, activo, cuerpo(4, "      "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    resenar(ana, activo, cuerpo(4, "x".repeat(1001)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));

    // Mil exactos, con espacios alrededor que no cuentan: se admite. Es lo que
    // hace que `VAL-005` y el CHECK de la columna midan lo mismo.
    resenar(ana, activo, cuerpo(4, "   " + "x".repeat(1000) + "   "))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "`CA-PM-173` — la segunda reseña del mismo actor sobre el mismo producto responde 409")
  void rechazaLaSegunda() throws Exception {
    resenar(ana, activo, cuerpo(5, "Primera")).andExpect(status().isCreated());

    resenar(ana, activo, cuerpo(1, "Segunda"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail").value("Ya reseñaste este producto. Puedes corregir tu reseña."));

    assertThat(resenasVivas(activo)).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-PM-174` — retirada la anterior, se admite una nueva y es otra fila")
  void admiteUnaNuevaTrasRetirar() throws Exception {
    UUID retirada = resena(activo, ana, 2, "Ya no pienso esto", true);

    String creada =
        resenar(ana, activo, cuerpo(5, "Ahora sí"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(creada).doesNotContain(retirada.toString());
    assertThat(resenasVivas(activo)).isEqualTo(1);
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM product_comments WHERE product_id = CAST(? AS uuid)",
            Long.class,
            activo.toString());
    assertThat(total).isEqualTo(2);
  }

  @Test
  @DisplayName("`CA-PM-175` — inactivo, retirado e inexistente responden 404 con el MISMO cuerpo")
  void elCuatroCientosCuatroEsUniforme() throws Exception {
    String porInactivo = cuerpoDe(resenar(ana, inactivo, cuerpo(5, "Texto")));
    String porRetirado = cuerpoDe(resenar(ana, retirado, cuerpo(5, "Texto")));
    String porInexistente = cuerpoDe(resenar(ana, UUID.randomUUID(), cuerpo(5, "Texto")));

    // El cuerpo entero, salvo la ruta pedida: lo que se afirma es que no
    // dicen cuál de los tres ocurrió.
    assertThat(normalizar(porInactivo))
        .isEqualTo(normalizar(porRetirado))
        .isEqualTo(normalizar(porInexistente));
    assertThat(porInactivo).contains("El producto no existe o no está a la venta.");
  }

  @Test
  @DisplayName(
      "`CA-PM-176` — sin `products:comment` responde 403, aunque porte los cuatro de administración")
  void sinElPermisoNoSeResena() throws Exception {
    mvc.perform(
            post("/api/v1/products/{id}/comments", activo)
                .with(comoAdministradorSinComment(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(5, "Texto")))
        .andExpect(status().isForbidden());

    assertThat(resenasVivas(activo)).isZero();
  }

  @Test
  @DisplayName(
      "`CA-PM-177` — el autor es quien porta el token: un `userId` en el cuerpo es un campo desconocido")
  void elAutorSaleDelToken() throws Exception {
    resenar(ana, activo, "{\"rating\": 5, \"comment\": \"Texto\", \"userId\": \"" + luis + "\"}")
        .andExpect(status().isBadRequest());

    resenar(ana, activo, cuerpo(5, "Texto")).andExpect(status().isCreated());

    String autor =
        jdbc.queryForObject(
            "SELECT user_id::text FROM product_comments WHERE product_id = CAST(? AS uuid)",
            String.class,
            activo.toString());
    assertThat(autor).isEqualTo(ana.toString());
  }

  @Test
  @DisplayName("`CA-PM-178` — registra una fila CREATE en audit_change_log con el actor")
  void auditaLaCreacion() throws Exception {
    String id =
        com.jayway.jsonpath.JsonPath.read(
            cuerpoDe(resenar(ana, activo, cuerpo(4, "Texto")).andExpect(status().isCreated())),
            "$.id");

    java.util.Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT action, actor_id::text AS actor, changes::text AS cambios"
                + " FROM audit_change_log WHERE entity = 'product_comments'"
                + " AND entity_id = CAST(? AS uuid)",
            id);

    assertThat(fila.get("action")).isEqualTo("CREATE");
    assertThat(fila.get("actor")).isEqualTo(ana.toString());
    assertThat((String) fila.get("cambios"))
        .contains("\"rating\": 4")
        .contains("\"comment\": \"Texto\"");
  }

  @Test
  @DisplayName(
      "`CA-PM-183` — un administrador con el permiso escribe la suya como cualquiera, y una sola")
  void elAdministradorEscribeLaSuya() throws Exception {
    mvc.perform(
            post("/api/v1/products/{id}/comments", activo)
                .with(comoAdministrador(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(3, "Como cualquiera")))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/products/{id}/comments", activo)
                .with(comoAdministrador(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(3, "Otra vez")))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("el mismo actor reseña dos productos distintos: la unicidad es por pareja")
  void dosProductosDosResenas() throws Exception {
    UUID otro = bot("RS_OTRO", "Otro bot", "ACTIVO", false);
    resenar(ana, activo, cuerpo(5, "Uno")).andExpect(status().isCreated());
    resenar(ana, otro, cuerpo(2, "Dos")).andExpect(status().isCreated());
    assertThat(resenasVivas(activo)).isEqualTo(1);
    assertThat(resenasVivas(otro)).isEqualTo(1);
  }

  @Test
  @DisplayName("el texto se guarda tal cual, saltos de línea incluidos, y NO se sanea")
  void elTextoSeGuardaTalCual() throws Exception {
    resenar(ana, activo, "{\"rating\": 5, \"comment\": \"Línea 1\\nLínea 2 <b>negrita</b>\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.comment").value(Matchers.containsString("<b>negrita</b>")));
  }

  // ---------------------------------------------------------------------------

  private ResultActions resenar(UUID quien, UUID producto, String json) throws Exception {
    return mvc.perform(
        post("/api/v1/products/{id}/comments", producto)
            .with(comoResenador(quien))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private static String cuerpoDe(ResultActions resultado) throws Exception {
    return resultado.andReturn().getResponse().getContentAsString();
  }

  static String normalizar(String cuerpo) {
    return cuerpo
        .replaceAll("\"instance\"\s*:\s*\"[^\"]*\"", "\"instance\":\"?\"")
        .replaceAll("\"path\"\s*:\s*\"[^\"]*\"", "\"path\":\"?\"")
        .replaceAll("\"correlationId\"\s*:\s*\"[^\"]*\"", "\"correlationId\":\"?\"")
        .replaceAll("\"timestamp\"\s*:\s*\"[^\"]*\"", "\"timestamp\":\"?\"");
  }
}
