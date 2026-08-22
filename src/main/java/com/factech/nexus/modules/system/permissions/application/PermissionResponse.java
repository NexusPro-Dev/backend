package com.factech.nexus.modules.system.permissions.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Un permiso en el contrato de la API (`RF-SP-010` · `T-08`).
 *
 * <p><b>{@code @JsonInclude(ALWAYS)} no es redundante.</b> {@code application.yml} declara {@code
 * default-property-inclusion: non_null} para todo el sistema, y sin esta anotación un permiso sin
 * descripción llegaría al cliente <b>sin la propiedad</b> en lugar de con {@code null}. `spec.md`
 * §13 y la verificación de `T-08` exigen lo contrario: el campo viaja siempre, y su ausencia de
 * valor se expresa como {@code null}. Un cliente que recorra las claves del objeto no debe
 * encontrarse con que unos permisos tienen seis y otros cinco.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PermissionResponse(
    UUID id, String code, String resource, String action, String name, String description) {

  public static PermissionResponse from(PermissionItem item) {
    return new PermissionResponse(
        item.id(), item.code(), item.resource(), item.action(), item.name(), item.description());
  }
}
