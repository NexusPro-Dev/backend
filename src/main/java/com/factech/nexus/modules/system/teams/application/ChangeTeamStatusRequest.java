package com.factech.nexus.modules.system.teams.application;

import java.util.Locale;

/**
 * Cuerpo del cambio de estado de un equipo (`RF-SP-067` §6.1): <b>el estado destino</b>.
 *
 * <p><b>Se declara a dónde va, no se alterna.</b> Un «cambiar» sin destino haría que dos peticiones
 * idénticas dejaran resultados distintos según el orden en que llegaran, que es exactamente lo que
 * la idempotencia evita — y el reintento de un cliente con mala red se volvería peligroso.
 *
 * <p><b>Texto y no {@link com.factech.nexus.modules.system.teams.domain.models.TeamStatus}</b>, por
 * lo mismo que en {@link ListTeamsRequest}: con el {@code enum}, el editor canónico rechaza el
 * valor mal escrito <b>antes</b> de entrar al caso de uso, y el cliente recibe el mensaje genérico
 * de cuerpo ilegible en lugar del `VAL-001` que la spec exige —con el valor que envió y los
 * admitidos—. Resolverlo en el caso de uso permite además que el estado llegue en cualquier caja.
 *
 * <p><b>{@code reason} no está, y su ausencia es la implementación</b>: suspender se deshace con
 * una petición, y el motivo es la barrera de lo irreversible (Art. V.13). Exigirlo produce motivos
 * escritos por obligación, que no se leen. Enviarlo es {@code 400} por campo desconocido
 * (`VAL-003`) y lo rechaza {@code FAIL_ON_UNKNOWN_PROPERTIES} sin código que lo mire, igual que
 * {@code name} o {@code members}.
 */
public record ChangeTeamStatusRequest(String status) {

  public ChangeTeamStatusRequest {
    status = status == null || status.isBlank() ? null : status.trim();
  }

  /** El estado en mayúsculas, que es como se guarda y como se publica; nulo si no vino. */
  public String estadoNormalizado() {
    return status == null ? null : status.toUpperCase(Locale.ROOT);
  }
}
