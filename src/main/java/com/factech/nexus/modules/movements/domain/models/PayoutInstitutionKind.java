package com.factech.nexus.modules.movements.domain.models;

import java.util.Locale;
import java.util.Optional;

/**
 * El tipo de una entidad de cobro (`RN-MV-054`), y con él <b>qué se le pide a una cuenta</b> en
 * ella (`RN-MV-055`): un banco lleva tipo de cuenta y un número de 4 a 20 dígitos; una billetera
 * móvil, solo el celular, de 7 a 15.
 */
public enum PayoutInstitutionKind {
  BANCO(true, 4, 20),
  BILLETERA_MOVIL(false, 7, 15);

  private final boolean llevaTipoDeCuenta;
  private final int minimo;
  private final int maximo;

  PayoutInstitutionKind(boolean llevaTipoDeCuenta, int minimo, int maximo) {
    this.llevaTipoDeCuenta = llevaTipoDeCuenta;
    this.minimo = minimo;
    this.maximo = maximo;
  }

  public boolean requiresAccountType() {
    return llevaTipoDeCuenta;
  }

  public int minDigits() {
    return minimo;
  }

  public int maxDigits() {
    return maximo;
  }

  /** El tipo, sin distinguir mayúsculas, o vacío si no es ninguno. */
  public static Optional<PayoutInstitutionKind> parse(String valor) {
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
