package com.factech.nexus.modules.system.users.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/users/{id}/roles} (`RF-SP-030`).
 *
 * <p><b>{@code supervisorId} se declara pero NO se valida aquí</b>, y es la decisión que más fácil
 * resulta implementar de más. Que falte depende de cuál será su rol vendedor de mayor rango <b>al
 * terminar la operación</b>, y eso no se decide mirando el cuerpo: no es un {@code 400}, es un
 * {@code 422} que produce el caso de uso (`plan.md` §4, enmienda a `spec.md` §11).
 *
 * <p>La frontera importa más allá de este archivo: que un {@code 400} salga siempre del validador y
 * nunca del caso de uso es lo que hace legible el manejador global.
 *
 * <p><b>{@code membershipId} y {@code membershipEndsAt} se retiraron el 05-09-2026</b>, con la
 * reescritura de `RN-SP-018`: toda persona tiene nivel desde el alta, de modo que cuando llega esta
 * petición <b>ya lo tiene</b>. Los dos únicos desenlaces que le quedaban a esos campos eran «ya
 * tiene membresía, use `RF-SP-032`» y «la vigencia sin membresía», los dos {@code 422}. <b>Un campo
 * que solo puede producir un error es peor que ningún campo</b>: promete algo que el sistema no
 * hace. Cambiar el nivel es `RF-SP-032`, que tiene su propio permiso.
 *
 * @param roleIds al menos uno y como mucho cien. A diferencia del alta, aquí la lista <b>vacía no
 *     es válida</b>: una operación que no pide agregar nada no tiene nada que hacer, y admitirla
 *     obligaría a decidir qué devolver
 */
public record AssignRolesRequest(
    @NotEmpty(message = "VAL-002: Debe indicar al menos un rol.")
        @Size(max = 100, message = "VAL-005: No se admiten más de 100 roles en una sola petición.")
        List<UUID> roleIds,
    UUID supervisorId) {

  public AssignRolesRequest {
    // Los duplicados se colapsan sin error: pedir dos veces el mismo rol no es
    // una petición inválida, es una petición redundante.
    roleIds =
        roleIds == null
            ? null
            : List.copyOf(new LinkedHashSet<>(roleIds.stream().filter(Objects::nonNull).toList()));
  }
}
