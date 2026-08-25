package com.factech.nexus.modules.system.users.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Resultado de cambiar el estado de una persona (`RF-SP-028`).
 *
 * <p><b>{@code lockedUntil} nulo con estado {@code BLOQUEADO} significa bloqueo MANUAL</b>, y esa
 * es la mitad de lo que este cuerpo tiene que dejar observable. La otra mitad es que un bloqueo por
 * intentos fallidos —que este endpoint no produce pero sí puede encontrarse— lo devuelva informado.
 * Sin el campo, los dos bloqueos serían indistinguibles desde fuera y nadie sabría cuál se levanta
 * solo.
 *
 * <p><b>No se devuelven los roles ni la membresía.</b> Cambiar el estado no los toca, y devolverlos
 * invitaría a leer de aquí un estado que se consulta con el detalle.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserStatusResponse(
    UUID id, String username, String status, OffsetDateTime lockedUntil, OffsetDateTime updatedAt) {

  public static UserStatusResponse de(
      UUID id,
      String username,
      String status,
      OffsetDateTime bloqueadoHasta,
      OffsetDateTime ahora) {
    return new UserStatusResponse(
        id,
        username,
        status,
        bloqueadoHasta == null ? null : bloqueadoHasta.withOffsetSameInstant(ZoneOffset.UTC),
        ahora == null ? null : ahora.withOffsetSameInstant(ZoneOffset.UTC));
  }
}
