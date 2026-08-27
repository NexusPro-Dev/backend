package com.factech.nexus.modules.products.application;

import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/products} (`RF-PM-002`).
 *
 * <p><b>{@code targetMembershipId} no se valida contra el catálogo de `SP`.</b> Filtrar por un
 * destino que no existe devuelve la colección vacía y <b>no es un error</b> (`spec.md` §13):
 * validarlo costaría una llamada al puerto por petición para producir un fallo que la
 * especificación no quiere. Lo único que se exige de él es la forma canónica, y de eso se encarga
 * el editor transversal de {@code shared/error}.
 *
 * <p><b>{@code includeDeleted} es {@code Boolean} y no {@code boolean} a propósito</b>: un tipo
 * primitivo en un {@code @ModelAttribute} hace que la petición <b>sin el parámetro</b> falle con
 * {@code 400}, porque Spring intenta convertir la ausencia. Es el mismo defecto que el catálogo de
 * monedas tuvo que corregir en `SP`.
 *
 * <p><b>{@code type} y {@code status} son texto y no sus enumerados.</b> Enlazarlos como enumerado
 * dejaría que Spring rechazara el valor fuera de dominio antes de entrar al caso de uso, y el
 * rechazo saldría <b>solo</b> —no junto a los demás—, que es justo lo que `CA-PM-020` no admite.
 */
public record ListProductsRequest(
    Integer page,
    Integer size,
    String sort,
    String type,
    String status,
    UUID targetMembershipId,
    String search,
    Boolean includeDeleted) {

  public ListProductsRequest {
    // Recortado, y en blanco equivale a ausente (`CA-PM-017`): buscar por
    // espacios es no buscar, y añadir el predicado devolvería el catálogo
    // entero igualmente pero pagando el recorrido.
    search = search == null || search.isBlank() ? null : search.trim();
    type = type == null || type.isBlank() ? null : type.trim();
    status = status == null || status.isBlank() ? null : status.trim();
    sort = sort == null || sort.isBlank() ? null : sort.trim();
  }

  /** Por omisión, los retirados <b>no</b> se devuelven (`CA-PM-018`). */
  public boolean incluirEliminados() {
    return Boolean.TRUE.equals(includeDeleted);
  }
}
