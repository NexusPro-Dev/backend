package com.factech.nexus.modules.academy.domain.models;

import java.util.Locale;
import java.util.Optional;

/**
 * Hacia dónde va una señal (`RN-AC-035`, `ac.md` §5.2.22): {@code UP} si sube, {@code DOWN} si
 * baja. En la entrada se aceptan, sin distinguir mayúsculas, los sinónimos de una hoja de cálculo y
 * de los brokers —{@code SUBE}/{@code BAJA}, {@code CALL}/{@code PUT}, {@code BUY}/{@code SELL}—;
 * se guarda y se devuelve siempre el nombre de la constante.
 */
public enum SignalDirection {
  UP,
  DOWN;

  /** El valor que nombra el texto, o vacío si no nombra ninguno. */
  public static Optional<SignalDirection> of(String texto) {
    if (texto == null) {
      return Optional.empty();
    }
    return switch (texto.trim().toUpperCase(Locale.ROOT)) {
      case "UP", "SUBE", "CALL", "BUY" -> Optional.of(UP);
      case "DOWN", "BAJA", "PUT", "SELL" -> Optional.of(DOWN);
      default -> Optional.empty();
    };
  }
}
