package com.factech.nexus.modules.system.permissions.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Serialización del contrato del catálogo (`RF-SP-010` · `T-08`).
 *
 * <p>Prueba unitaria: se construye un {@code ObjectMapper} con la misma política de inclusión que
 * declara {@code application.yml} —{@code non_null} para todo el sistema— y se comprueba que este
 * DTO se sale de ella a propósito.
 */
class PermissionResponseTest {

  private final ObjectMapper mapper =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static PermissionItem item(String description) {
    return new PermissionItem(
        UUID.fromString("01a029fc-5d80-7001-9c4f-5e7ad0000001"),
        "roles:read",
        "roles",
        "read",
        "Consultar roles",
        description);
  }

  @Test
  @DisplayName("la descripción nula viaja como null y no se omite")
  void descripcionNulaViajaComoNull() throws Exception {
    String json = mapper.writeValueAsString(PermissionResponse.from(item(null)));

    // Sin @JsonInclude(ALWAYS) en el DTO, la política global non_null de
    // application.yml borraría la propiedad y el cliente recibiría objetos con
    // distinto número de claves según el permiso.
    assertThat(json).contains("\"description\":null");
  }

  @Test
  @DisplayName("el permiso serializa exactamente sus seis campos")
  void seisCampos() throws Exception {
    var arbol = mapper.readTree(mapper.writeValueAsString(PermissionResponse.from(item("X"))));

    assertThat(arbol.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("id", "code", "resource", "action", "name", "description");
  }

  @Test
  @DisplayName("el catálogo no expone ningún campo de paginación")
  void sinCamposDePaginacion() throws Exception {
    String json =
        mapper.writeValueAsString(PermissionCatalogResponse.from(List.of(item("X"), item(null))));

    assertThat(json).contains("\"content\":[");
    assertThat(json)
        .doesNotContain("page")
        .doesNotContain("size")
        .doesNotContain("totalElements")
        .doesNotContain("totalPages");
  }

  @Test
  @DisplayName("la colección va envuelta en content, no desnuda en la raíz")
  void coleccionEnvuelta() throws Exception {
    String json = mapper.writeValueAsString(PermissionCatalogResponse.from(List.of(item("X"))));

    assertThat(json).startsWith("{").doesNotStartWith("[");
  }

  @Test
  @DisplayName("un catálogo vacío serializa como content vacío, no como null")
  void catalogoVacio() throws Exception {
    String json = mapper.writeValueAsString(PermissionCatalogResponse.from(List.of()));

    assertThat(json).isEqualTo("{\"content\":[]}");
  }

  @Test
  @DisplayName("la petición admite exactamente tres parámetros")
  void tresParametros() {
    assertThat(ListPermissionsRequest.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactlyInAnyOrder("resource", "action", "search");
  }
}
