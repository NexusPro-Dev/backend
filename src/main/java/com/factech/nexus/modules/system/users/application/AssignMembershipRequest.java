package com.factech.nexus.modules.system.users.application;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cuerpo de {@code PUT /api/v1/users/{id}/membership} (`RF-SP-032`).
 *
 * <p><b>`PUT` y no `POST`</b>, al revés que en `RF-SP-030`, y la diferencia no es de gusto: aquí el
 * cuerpo <b>sí</b> representa el estado final del recurso. La persona tiene una membresía o
 * ninguna, de modo que enviar una la deja como la única — que es exactamente la semántica de {@code
 * PUT}. Y de ahí sale gratis la idempotencia que `FA-002` exige.
 *
 * @param endsAt opcional. <b>Ausente significa indefinida</b>, no «sin fecha conocida»: enviarlo
 *     ausente sobre una membresía que tenía fecha la convierte en indefinida, y es un caso normal
 *     de `FA-003`, no un olvido que haya que interpretar. Su comprobación —posterior al momento de
 *     la asignación— <b>no</b> se hace con una anotación, porque {@code @Future} compara contra el
 *     reloj del sistema y haría la prueba dependiente de la hora a la que se ejecute
 */
public record AssignMembershipRequest(
    @NotNull(message = "VAL-001: La membresía es obligatoria.") UUID membershipId,
    OffsetDateTime endsAt) {}
