package com.factech.nexus.modules.products.application;

import java.util.UUID;

/**
 * Filtros, orden y paginación del listado de paquetes (`RF-PM-018` §6.1).
 *
 * <p><b>{@code offerable} no es filtro</b>: quien administra quiere ver precisamente los que no se
 * ofrecen, y se queda como columna de la fila (`spec.md` §14.1). Los filtros en blanco equivalen a
 * ausentes, como en {@link ListProductsRequest}.
 */
public record ListPackagesRequest(
    Integer page,
    Integer size,
    String sort,
    String status,
    String scope,
    UUID currencyId,
    String q,
    Boolean includeDeleted) {

  public ListPackagesRequest {
    q = q == null || q.isBlank() ? null : q.trim();
    status = status == null || status.isBlank() ? null : status.trim();
    scope = scope == null || scope.isBlank() ? null : scope.trim();
    sort = sort == null || sort.isBlank() ? null : sort.trim();
  }

  public boolean incluirEliminados() {
    return Boolean.TRUE.equals(includeDeleted);
  }
}
