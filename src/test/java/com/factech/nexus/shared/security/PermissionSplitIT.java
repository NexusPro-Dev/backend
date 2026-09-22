package com.factech.nexus.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * `RF-SP-060` · `T-10`, `T-11`, `T-12` — <b>la mitad negativa de `CA-SP-690`</b>: con el PADRE y
 * sin el HIJO, cada operación que cambió de permiso responde `403`.
 *
 * <p>La mitad positiva —que cada operación declara exactamente el código de `spec.md` §6.2— vive en
 * {@link EndpointPermissionsIT}. Esta prueba comprueba que <b>declarar</b> es <b>exigir</b>: quien
 * porta `roles:update` y no `roles:assign-permissions` ya no reparte permisos, que es la frase
 * entera del requerimiento. Hasta el 19-09-2026 el padre bastaba, y por eso ninguna suite de módulo
 * tenía este caso.
 *
 * <p>Los cuerpos son válidos y los identificadores no existen: con el permiso correcto la respuesta
 * sería `404`; con el padre, `403` antes de mirar nada — que es lo que se afirma.
 *
 * <p>{@code CA-SP-694} va aparte: un rol con `users:list` y sin `users:read` lista y no ve el
 * detalle, que es exactamente lo que se pidió poder hacer.
 */
@AutoConfigureMockMvc
class PermissionSplitIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;

  private record Caso(String operacion, String padre, Supplier<MockHttpServletRequestBuilder> p) {}

  private static final UUID ID = UUID.fromString("01a0b6f6-7400-7fff-9c4f-00000000ffff");
  private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

  private static final List<Caso> CASOS =
      List.of(
          // ---- SP ----
          new Caso("GET /roles", "roles:read", () -> get("/api/v1/roles")),
          new Caso(
              "PATCH /roles/{id}/status",
              "roles:update",
              () ->
                  patch("/api/v1/roles/{id}/status", ID)
                      .contentType(JSON)
                      .content("{\"status\":\"INACTIVO\"}")),
          new Caso(
              "PATCH /roles/{id}/parent",
              "roles:update",
              () ->
                  patch("/api/v1/roles/{id}/parent", ID)
                      .contentType(JSON)
                      .content("{\"parentRoleId\":\"" + ID + "\"}")),
          new Caso(
              "POST /roles/{id}/permissions",
              "roles:update",
              () ->
                  post("/api/v1/roles/{id}/permissions", ID)
                      .contentType(JSON)
                      .content("{\"permissionIds\":[\"" + ID + "\"]}")),
          new Caso(
              "POST /roles/{id}/permissions/revocations",
              "roles:update",
              () ->
                  post("/api/v1/roles/{id}/permissions/revocations", ID)
                      .contentType(JSON)
                      .content("{\"permissionIds\":[\"" + ID + "\"]}")),
          new Caso("GET /permissions", "permissions:read", () -> get("/api/v1/permissions")),
          new Caso("GET /memberships", "memberships:read", () -> get("/api/v1/memberships")),
          new Caso("GET /users", "users:read", () -> get("/api/v1/users")),
          new Caso("GET /users/{id}/team", "users:read", () -> get("/api/v1/users/{id}/team", ID)),
          new Caso(
              "PATCH /users/{id}/status",
              "users:update",
              () ->
                  patch("/api/v1/users/{id}/status", ID)
                      .contentType(JSON)
                      .content("{\"status\":\"INACTIVO\",\"reason\":\"Prueba\"}")),
          new Caso(
              "POST /users/{id}/roles/revocations",
              "users:assign-roles",
              () ->
                  post("/api/v1/users/{id}/roles/revocations", ID)
                      .contentType(JSON)
                      .content("{\"roleIds\":[\"" + ID + "\"]}")),
          new Caso(
              "DELETE /users/{id}/membership",
              "users:assign-membership",
              () -> delete("/api/v1/users/{id}/membership", ID)),
          new Caso(
              "GET /broker-accounts/indicators",
              "broker-accounts:read",
              () -> get("/api/v1/broker-accounts/indicators")),
          // ---- PM ----
          new Caso("GET /products", "products:read", () -> get("/api/v1/products")),
          new Caso(
              "PATCH /products/{id}/status",
              "products:update",
              () ->
                  patch("/api/v1/products/{id}/status", ID)
                      .contentType(JSON)
                      .content("{\"status\":\"INACTIVO\"}")),
          new Caso(
              "PUT /products/{id}/cover",
              "products:update",
              () -> portada("/api/v1/products/{id}/cover")),
          new Caso(
              "DELETE /products/{id}/cover",
              "products:update",
              () -> delete("/api/v1/products/{id}/cover", ID)),
          new Caso(
              "GET /products/{id}/comments/mine",
              "products:comment",
              () -> get("/api/v1/products/{id}/comments/mine", ID)),
          new Caso(
              "PATCH /products/{id}/comments/{commentId}",
              "products:comment",
              () ->
                  patch("/api/v1/products/{id}/comments/{commentId}", ID, ID)
                      .contentType(JSON)
                      .content("{\"rating\":5}")),
          new Caso(
              "DELETE /products/{id}/comments/{commentId}",
              "products:comment",
              () -> delete("/api/v1/products/{id}/comments/{commentId}", ID, ID)),
          new Caso("GET /packages", "packages:read", () -> get("/api/v1/packages")),
          new Caso(
              "PATCH /packages/{id}/status",
              "packages:update",
              () ->
                  patch("/api/v1/packages/{id}/status", ID)
                      .contentType(JSON)
                      .content("{\"status\":\"INACTIVO\"}")),
          new Caso(
              "PUT /packages/{id}/cover",
              "packages:update",
              () -> portada("/api/v1/packages/{id}/cover")),
          new Caso(
              "DELETE /packages/{id}/cover",
              "packages:update",
              () -> delete("/api/v1/packages/{id}/cover", ID)),
          new Caso(
              "POST /packages/{id}/products",
              "packages:update",
              () ->
                  post("/api/v1/packages/{id}/products", ID)
                      .contentType(JSON)
                      .content(
                          "{\"productId\":\""
                              + ID
                              + "\",\"discountType\":\"PORCENTAJE\",\"discountValue\":10}")),
          new Caso(
              "PATCH /packages/{id}/products/{productId}",
              "packages:update",
              () ->
                  patch("/api/v1/packages/{id}/products/{productId}", ID, ID)
                      .contentType(JSON)
                      .content("{\"discountType\":\"PORCENTAJE\",\"discountValue\":10}")),
          new Caso(
              "DELETE /packages/{id}/products/{productId}",
              "packages:update",
              () -> delete("/api/v1/packages/{id}/products/{productId}", ID, ID)),
          // ---- CM ----
          new Caso(
              "GET /commissions/effective",
              "commissions:read",
              () ->
                  get("/api/v1/commissions/effective")
                      .param("userId", ID.toString())
                      .param("productId", ID.toString())),
          new Caso(
              "POST /user-commission-rates",
              "commissions:create",
              () ->
                  post("/api/v1/user-commission-rates")
                      .contentType(JSON)
                      .content(
                          "{\"userId\":\""
                              + ID
                              + "\",\"productId\":\""
                              + ID
                              + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":10,"
                              + "\"validFrom\":\"2026-09-19\"}")),
          new Caso(
              "GET /user-commission-rates",
              "commissions:read",
              () -> get("/api/v1/user-commission-rates")),
          new Caso(
              "PATCH /user-commission-rates/{id}",
              "commissions:update",
              () ->
                  patch("/api/v1/user-commission-rates/{id}", ID)
                      .contentType(JSON)
                      .content("{\"percentage\":11}")),
          new Caso(
              "POST /user-commission-rates/{id}/deletion",
              "commissions:delete",
              () ->
                  post("/api/v1/user-commission-rates/{id}/deletion", ID)
                      .contentType(JSON)
                      .content("{\"reason\":\"Prueba\"}")),
          new Caso(
              "GET /product-commission-rates",
              "commissions:read",
              () -> get("/api/v1/product-commission-rates").param("productId", ID.toString())));

  @TestFactory
  @DisplayName(
      "CA-SP-690, la mitad negativa: con el padre y sin el hijo, 403 en las treinta y tres")
  List<DynamicTest> conElPadreYSinElHijo() {
    return CASOS.stream()
        .map(
            caso ->
                DynamicTest.dynamicTest(
                    caso.operacion() + " con solo " + caso.padre(),
                    () ->
                        mvc.perform(
                                caso.p()
                                    .get()
                                    .with(
                                        user(SUPERADMIN.toString())
                                            .authorities(() -> caso.padre())))
                            .andExpect(status().isForbidden())))
        .toList();
  }

  @Test
  @DisplayName("CA-SP-694: con users:list y sin users:read se lista y no se ve el detalle")
  void listarSinVerElDetalle() throws Exception {
    var soloListar = user(SUPERADMIN.toString()).authorities(() -> "users:list");

    mvc.perform(get("/api/v1/users").with(soloListar)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/users/{id}", SUPERADMIN).with(soloListar))
        .andExpect(status().isForbidden());
  }

  private static MockMultipartHttpServletRequestBuilder portada(String ruta) {
    MockMultipartHttpServletRequestBuilder b =
        multipart(ruta, ID)
            .file(new MockMultipartFile("file", "portada.png", "image/png", new byte[] {1}));
    b.with(
        request -> {
          request.setMethod("PUT");
          return request;
        });
    return b;
  }
}
