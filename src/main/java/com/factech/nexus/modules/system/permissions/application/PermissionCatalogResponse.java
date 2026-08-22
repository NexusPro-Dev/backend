package com.factech.nexus.modules.system.permissions.application;

import java.util.List;

/**
 * El catálogo de permisos tal como sale por la API (`RF-SP-010` · `T-08`).
 *
 * <p><b>La colección va envuelta y no desnuda.</b> Un {@code [...]} en la raíz cerraría la puerta a
 * añadir después cualquier metadato —un total, una marca de versión del catálogo— sin romper a
 * todos los clientes.
 *
 * <p>El campo se llama {@code content}, igual que en {@code PageResponse<T>} de `RF-SP-002`, y es
 * deliberado: la forma de leer la lista es idéntica y lo único ausente son los campos de
 * paginación, que es exactamente el mensaje que se quiere dar.
 *
 * <p><b>No se reutiliza {@code PageResponse} con valores inventados.</b> Rellenar {@code
 * totalPages: 1} diría que hay paginación, y `CA-SP-073` exige que no la haya.
 */
public record PermissionCatalogResponse(List<PermissionResponse> content) {

  public static PermissionCatalogResponse from(List<PermissionItem> items) {
    return new PermissionCatalogResponse(items.stream().map(PermissionResponse::from).toList());
  }
}
