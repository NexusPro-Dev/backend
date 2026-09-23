package com.factech.nexus.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * `RF-SP-062` · `T-07` — <b>autenticarse no autoriza nada</b> (`RN-SEG-015`, `CA-SP-724`,
 * `CA-SP-728`).
 *
 * <p>Las once operaciones que hasta el 21-09-2026 se atendían con solo el token responden {@code
 * 403} a un actor autenticado <b>sin</b> el permiso, y dejan de responderlo con él. Es la mitad que
 * {@link EndpointPermissionsIT} no puede afirmar: allí se comprueba que la anotación existe; aquí,
 * que <b>declarar es exigir</b>, con el mismo molde que {@link PermissionSplitIT}.
 *
 * <p>Con el permiso puesto no se afirma {@code 200} sino «no {@code 403}»: la respuesta que
 * corresponda —{@code 200}, {@code 400} por un cuerpo vacío, {@code 404} por un identificador que
 * no existe— la afirma la suite de cada requerimiento. Aquí basta con que la puerta se abra.
 */
@AutoConfigureMockMvc
class OwnScopePermissionsIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;

  private record Caso(
      String operacion, String permiso, Supplier<MockHttpServletRequestBuilder> p) {}

  private static final UUID ID = UUID.fromString("01a0c143-2c00-7fff-9c4f-00000000ffff");
  private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

  private static final List<Caso> CASOS =
      List.of(
          new Caso("GET /users/me", "users:read-own-profile", () -> get("/api/v1/users/me")),
          new Caso(
              "PATCH /users/me",
              "users:update-own-profile",
              () -> patch("/api/v1/users/me").contentType(JSON).content("{}")),
          new Caso(
              "POST /auth/password",
              "users:change-own-password",
              () -> post("/api/v1/auth/password").contentType(JSON).content("{}")),
          new Caso(
              "GET /users/me/sellers",
              "users:read-own-sellers",
              () -> get("/api/v1/users/me/sellers")),
          new Caso(
              "GET /users/me/clients",
              "users:read-own-clients",
              () -> get("/api/v1/users/me/clients")),
          new Caso(
              "GET /users/me/team/broker-accounts",
              "broker-accounts:read-own-team",
              () -> get("/api/v1/users/me/team/broker-accounts")),
          new Caso(
              "GET /users/{id}/broker-accounts",
              "broker-accounts:read-team-member",
              () -> get("/api/v1/users/{id}/broker-accounts", ID)),
          new Caso(
              "GET /movements/mine/shopping",
              "movements:list-own",
              () -> get("/api/v1/movements/mine/shopping")),
          new Caso(
              "GET /movements/mine/{id}",
              "movements:read-own",
              () -> get("/api/v1/movements/mine/{id}", ID)),
          new Caso(
              "GET /movements/mine/products",
              "movements:read-own-products",
              () -> get("/api/v1/movements/mine/products")),
          new Caso(
              "POST /packages/{code}/purchases",
              "packages:buy",
              () ->
                  post("/api/v1/packages/{code}/purchases", "NADA")
                      .contentType(JSON)
                      .content("{}")));

  @TestFactory
  @DisplayName(
      "CA-SP-724: autenticado y sin el permiso, 403 en las once; con él, la puerta se abre")
  List<DynamicTest> autenticadoNoBasta() {
    return CASOS.stream()
        .map(
            caso ->
                DynamicTest.dynamicTest(
                    caso.operacion() + " exige " + caso.permiso(),
                    () -> {
                      // Sin ningún permiso: era lo que hasta el 21-09-2026 bastaba.
                      mvc.perform(caso.p().get().with(user(SUPERADMIN.toString()).authorities()))
                          .andExpect(status().isForbidden());
                      // Con un vecino de la misma familia tampoco (RN-SEG-014).
                      mvc.perform(
                              caso.p()
                                  .get()
                                  .with(
                                      user(SUPERADMIN.toString())
                                          .authorities(() -> "users:read", () -> "movements:read")))
                          .andExpect(status().isForbidden());
                      // Con el suyo, lo que sea menos 403.
                      int estado =
                          mvc.perform(
                                  caso.p()
                                      .get()
                                      .with(
                                          user(SUPERADMIN.toString())
                                              .authorities(() -> caso.permiso())))
                              .andReturn()
                              .getResponse()
                              .getStatus();
                      org.assertj.core.api.Assertions.assertThat(estado)
                          .as("%s con %s no debe ser 403", caso.operacion(), caso.permiso())
                          .isNotEqualTo(403);
                    }))
        .toList();
  }

  @Test
  @DisplayName(
      "CA-SP-728: broker-accounts:read-team-member abre la ruta y RN-SP-046 sigue decidiendo —sin"
          + " estructura ni broker-accounts:read, 404—")
  void elPermisoAbreYLaEstructuraDecide() throws Exception {
    // La persona existe (el superadministrador de V9) y el actor porta el permiso
    // de la ruta, pero no es su superior, ni su principal, ni trae el amplio:
    // el servicio responde 404, el mismo que a un identificador inexistente.
    mvc.perform(
            get("/api/v1/users/{id}/broker-accounts", SUPERADMIN)
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(() -> "broker-accounts:read-team-member")))
        .andExpect(status().isNotFound());
    // Y con el amplio además, ve a cualquiera: la segunda capa sigue entera.
    mvc.perform(
            get("/api/v1/users/{id}/broker-accounts", SUPERADMIN)
                .with(
                    user(UUID.randomUUID().toString())
                        .authorities(
                            () -> "broker-accounts:read-team-member",
                            () -> "broker-accounts:read")))
        .andExpect(status().isOk());
  }
}
