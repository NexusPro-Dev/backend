package com.factech.nexus.modules.system.documenttypes.application;

/**
 * Parámetros de {@code GET /api/v1/document-types} (`RF-SP-051`).
 *
 * <p>{@code includeInactive} es {@code Boolean} y no {@code boolean} a propósito: un tipo primitivo
 * en un {@code @ModelAttribute} hace que la petición <b>sin el parámetro</b> falle con {@code 400},
 * porque Spring intenta convertir la ausencia. Es el mismo defecto que el catálogo de monedas tuvo
 * que corregir.
 */
public record ListDocumentTypesRequest(Boolean includeInactive) {

  public boolean incluirInactivos() {
    return Boolean.TRUE.equals(includeInactive);
  }
}
