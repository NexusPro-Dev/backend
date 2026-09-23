package com.factech.nexus.modules.system.teams.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/teams/{id}/members} (`RF-SP-069` §6.1).
 *
 * <p><b>Los duplicados se colapsan sin error</b>, como en `RF-SP-030`: pedir dos veces a la misma
 * persona no es una petición inválida, es una petición redundante, y repetir un identificador no
 * cambia la intención (`FA-003`).
 *
 * <p><b>Ni {@code startedAt} ni {@code status}</b>, y su ausencia es la implementación (`VAL-006`):
 * la pertenencia rige desde el momento de ejecutarse y una fecha declarada permitiría reescribir a
 * qué equipo se atribuía una venta de hace tres meses; una pertenencia vigente, por su parte, no
 * tiene estados. Enviar cualquiera de los dos es `400` por campo desconocido.
 *
 * <p><b>Las cuatro validaciones de forma viven aquí y no en el caso de uso</b>, que es la frontera
 * que `RF-SP-030` dejó escrita: un `400` sale siempre del validador y nunca del caso de uso, de
 * modo que el manejador global se lee de una sola forma. Lo que el caso de uso decide son los `422`
 * —quién no existe y quién no es de la cúspide—, que no se pueden ver mirando el cuerpo.
 *
 * @param memberIds al menos uno (`VAL-001`) y como mucho cien (`VAL-003`), inclusive
 * @param reason obligatorio (`VAL-004`) y de hasta 500 caracteres (`VAL-005`): el historial de a
 *     qué equipo perteneció cada manager decide a quién se atribuye lo que su red produjo, y las
 *     comisiones lo leerán — un tramo sin explicación es un agujero cuando alguien discuta una
 *     liquidación
 */
public record AssignTeamMembersRequest(
    @NotEmpty(message = "VAL-001: Debe indicar al menos una persona.")
        @Size(
            max = 100,
            message = "VAL-003: No es posible asignar más de 100 personas en una sola solicitud.")
        List<UUID> memberIds,
    @NotBlank(message = "VAL-004: El motivo del cambio es obligatorio.")
        @Size(max = 500, message = "VAL-005: El motivo no puede exceder 500 caracteres.")
        String reason) {

  public AssignTeamMembersRequest {
    memberIds =
        memberIds == null
            ? null
            : List.copyOf(
                new LinkedHashSet<>(memberIds.stream().filter(Objects::nonNull).toList()));
    reason = reason == null ? null : reason.trim();
  }
}
