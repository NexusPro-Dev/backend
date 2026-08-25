package com.factech.nexus.modules.system.users.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/users/{id}/roles} (`RF-SP-030`).
 *
 * <p><b>Los tres campos condicionales se declaran pero NO se validan aquí</b>, y es la decisión que
 * más fácil resulta implementar de más. Si {@code membershipId} sobra o falta depende de qué roles
 * porta ya la persona y de si tiene membresía; si {@code supervisorId} falta depende de cuál será
 * su rol vendedor de mayor rango <b>al terminar la operación</b>. Nada de eso se decide mirando el
 * cuerpo, de modo que nada de eso es un {@code 400}: son {@code 422}, y los produce el caso de uso
 * (`plan.md` §4, enmienda a `spec.md` §11).
 *
 * <p>La frontera importa más allá de este archivo: que un {@code 400} salga siempre del validador y
 * nunca del caso de uso es lo que hace legible el manejador global.
 *
 * @param roleIds al menos uno y como mucho cien. A diferencia del alta, aquí la lista <b>vacía no
 *     es válida</b>: una operación que no pide agregar nada no tiene nada que hacer, y admitirla
 *     obligaría a decidir qué devolver
 * @param membershipEndsAt solo se admite acompañando a {@code membershipId}; sus reglas son las de
 *     `RF-SP-032` §4 y no se duplican aquí
 */
public record AssignRolesRequest(
    @NotEmpty(message = "VAL-002: Debe indicar al menos un rol.")
        @Size(max = 100, message = "VAL-005: No se admiten más de 100 roles en una sola petición.")
        List<UUID> roleIds,
    UUID membershipId,
    OffsetDateTime membershipEndsAt,
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
