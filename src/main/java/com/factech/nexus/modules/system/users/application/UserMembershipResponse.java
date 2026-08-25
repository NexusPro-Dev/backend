package com.factech.nexus.modules.system.users.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * La membresía de una persona (`RF-SP-032`).
 *
 * <p>Lleva el <b>nivel</b> porque es lo que da sentido al código: sin él, quien recibe la respuesta
 * no puede saber si la operación fue un ascenso o una bajada sin consultar la cadena.
 *
 * <p>{@code endsAt} viaja <b>siempre</b>, en nulo cuando la membresía es indefinida: distinguir
 * «indefinida» de «este endpoint no informa de la vigencia» es justo lo que {@code ALWAYS}
 * garantiza.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserMembershipResponse(
    UUID id, String code, String name, short level, OffsetDateTime endsAt) {

  public static UserMembershipResponse de(
      UUID id, String code, String name, short level, OffsetDateTime endsAt) {
    return new UserMembershipResponse(
        id,
        code,
        name,
        level,
        endsAt == null ? null : endsAt.withOffsetSameInstant(ZoneOffset.UTC));
  }
}
