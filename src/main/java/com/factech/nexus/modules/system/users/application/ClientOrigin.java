package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.Locale;

/**
 * El filtro por origen de la cartera de un vendedor (`RF-SP-061` §6.1): {@code REGISTRO} —los que
 * registró, suyos— o {@code HOTLINK} —los que le compraron por su enlace, vinculados—.
 *
 * <p>Se admite en cualquier caja y se resuelve al literal que guarda {@code client_sellers}; otro
 * valor es {@code VAL-001}, como el {@code status} de `RF-SP-056`. Ausente significa «todos», y ese
 * nulo viaja hasta el SQL, que lo resuelve en el predicado.
 */
public enum ClientOrigin {
  REGISTRO,
  HOTLINK;

  /** El literal para la consulta, o {@code null} si no se filtra. */
  public static String resolver(String origin) {
    if (origin == null || origin.isBlank()) {
      return null;
    }
    String valor = origin.trim().toUpperCase(Locale.ROOT);
    for (ClientOrigin candidato : values()) {
      if (candidato.name().equals(valor)) {
        return candidato.name();
      }
    }
    String mensaje = "El origen debe ser REGISTRO o HOTLINK.";
    throw new ValidationException(
        "VAL-001", mensaje, List.of(new FieldError("origin", "VAL-001", mensaje)));
  }
}
