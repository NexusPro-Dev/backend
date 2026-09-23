package com.factech.nexus.modules.system.teams.application;

import java.util.Locale;

/**
 * Filtros, orden y paginación del listado de equipos (`RF-SP-064` §6.1).
 *
 * <p>Búsqueda por nombre, filtro por estado e {@code includeDeleted}. <b>«Vacío» no es filtro</b>:
 * {@code memberCount} se publica por fila y quien pinta la pantalla separa, por lo mismo que {@code
 * offerable} no filtra en `PM` (spec §14.3).
 *
 * <p><b>{@code includeDeleted} llega como texto y no como {@code Boolean}</b>, al contrario que en
 * `RF-SP-025` y `RF-AC-002`. No es un descuido: con {@code Boolean}, Spring rechaza el valor mal
 * escrito <b>antes</b> de entrar al caso de uso y el cliente recibe ese error <b>solo</b>, mientras
 * que `EX-001` exige que los cuatro `400` de esta consulta viajen <b>juntos</b>. Recibiéndolo como
 * texto, la forma la comprueba {@link
 * com.factech.nexus.modules.system.teams.domain.service.ListTeamsService} con `VAL-004` y se suma a
 * los demás problemas de la misma petición.
 *
 * <p>Los valores en blanco equivalen a ausentes: filtrar por espacios es no filtrar, y añadir el
 * predicado devolvería lo mismo pagando el recorrido.
 */
public record ListTeamsRequest(
    Integer page, Integer size, String sort, String q, String status, String includeDeleted) {

  public ListTeamsRequest {
    q = q == null || q.isBlank() ? null : q.trim();
    sort = sort == null || sort.isBlank() ? null : sort.trim();
    status = status == null || status.isBlank() ? null : status.trim();
    includeDeleted =
        includeDeleted == null || includeDeleted.isBlank() ? null : includeDeleted.trim();
  }

  /** El estado en mayúsculas, que es como se guarda y como se publica; nulo si no vino. */
  public String estadoNormalizado() {
    return status == null ? null : status.toUpperCase(Locale.ROOT);
  }

  /**
   * Verdadero <b>solo</b> con el literal {@code true}, sin distinguir caja. Un valor que no sea
   * {@code true} ni {@code false} no llega hasta aquí: lo rechaza antes `VAL-004`.
   */
  public boolean incluirEliminados() {
    return "true".equalsIgnoreCase(includeDeleted);
  }

  /** Si {@code includeDeleted} vino con algo que no es un booleano (`VAL-004`). */
  public boolean incluirEliminadosMalEscrito() {
    return includeDeleted != null
        && !"true".equalsIgnoreCase(includeDeleted)
        && !"false".equalsIgnoreCase(includeDeleted);
  }
}
