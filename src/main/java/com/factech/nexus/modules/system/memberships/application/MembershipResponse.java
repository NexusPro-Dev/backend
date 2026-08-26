package com.factech.nexus.modules.system.memberships.application;

import com.factech.nexus.modules.system.memberships.domain.models.Membership;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * La membresía recién creada, con la posición que ocupó (`RF-SP-016`).
 *
 * <p><b>Devuelve la posición resultante, no solo lo que se envió.</b> {@code level}, {@code
 * parentMembershipId} y {@code childMembershipId} son las tres cosas que el actor no podía saber
 * antes de la operación.
 *
 * <p><b>Los vecinos van como identificadores, no como objetos anidados.</b> Expandirlos obligaría a
 * dos consultas más para una información que el cliente casi siempre ya tiene: llegó aquí desde
 * `RF-SP-017`, que devuelve la cadena entera. `RF-SP-018` es el endpoint que sí los expande, porque
 * es su razón de ser.
 *
 * <p><b>No se devuelve la cadena completa reordenada.</b> Mezclaría la respuesta de la operación
 * con la del listado; quien necesite ver el resultado global llama a `RF-SP-017`.
 *
 * <p>{@code @JsonInclude(ALWAYS)} porque {@code application.yml} declara {@code non_null} para todo
 * el sistema: sin él, la membresía superior llegaría sin la clave {@code parentMembershipId} en
 * lugar de con {@code null}, y un campo ausente es indistinguible de uno que el cliente no conoce.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MembershipResponse(
    UUID id,
    String code,
    String name,
    String description,
    String color,
    int level,
    UUID parentMembershipId,
    UUID childMembershipId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static MembershipResponse from(Membership membresia, UUID childMembershipId) {
    return new MembershipResponse(
        membresia.getId(),
        membresia.getCode(),
        membresia.getName(),
        membresia.getDescription(),
        membresia.getColor(),
        membresia.getLevel(),
        membresia.getParentMembershipId(),
        childMembershipId,
        enUtc(membresia.getCreatedAt()),
        enUtc(membresia.getUpdatedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
