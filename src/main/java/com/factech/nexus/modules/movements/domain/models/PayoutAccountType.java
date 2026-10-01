package com.factech.nexus.modules.movements.domain.models;

import java.util.Locale;
import java.util.Optional;

/** El tipo de una cuenta bancaria (`RN-MV-055`). Una billetera móvil no lleva ninguno. */
public enum PayoutAccountType {
  AHORROS,
  CORRIENTE;

  /** El tipo, sin distinguir mayúsculas, o vacío si no es ninguno. */
  public static Optional<PayoutAccountType> parse(String valor) {
    if (valor == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(valor.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
