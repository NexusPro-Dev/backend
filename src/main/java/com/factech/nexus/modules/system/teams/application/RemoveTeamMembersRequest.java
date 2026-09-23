package com.factech.nexus.modules.system.teams.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/teams/{id}/members/removals} (`RF-SP-070` §6.1).
 *
 * <p>La misma forma que {@link AssignTeamMembersRequest}, y a propósito: quien integra una ya sabe
 * integrar la otra. Los repetidos se colapsan, el motivo es obligatorio y las cuatro validaciones
 * de forma viven aquí, no en el caso de uso.
 *
 * <p><b>Ni {@code endedAt} ni {@code teamId}</b> (`VAL-006`): el equipo va en la ruta —recibirlo
 * también en el cuerpo abriría la pregunta de qué hacer si no coinciden— y la fecha de fin es el
 * momento de ejecutarse. Una fecha declarada permitiría reescribir hasta cuándo se atribuyó a un
 * equipo lo que una red produjo, que es exactamente lo que el historial existe para fijar.
 */
public record RemoveTeamMembersRequest(
    @NotEmpty(message = "VAL-001: Debe indicar al menos una persona.")
        @Size(
            max = 100,
            message = "VAL-003: No es posible retirar más de 100 personas en una sola solicitud.")
        List<UUID> memberIds,
    @NotBlank(message = "VAL-004: El motivo del cambio es obligatorio.")
        @Size(max = 500, message = "VAL-005: El motivo no puede exceder 500 caracteres.")
        String reason) {

  public RemoveTeamMembersRequest {
    memberIds =
        memberIds == null
            ? null
            : List.copyOf(
                new LinkedHashSet<>(memberIds.stream().filter(Objects::nonNull).toList()));
    reason = reason == null ? null : reason.trim();
  }
}
