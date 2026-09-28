package com.factech.nexus.modules.movements.domain.models;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * El número legible de una cuenta (`requirements/mv.md` §7.8): {@code CTA-} y diez caracteres del
 * alfabeto de Crockford, como el comprobante (`RN-MV-016`), porque también se dicta y se teclea. La
 * unicidad la garantiza {@code uq_accounts_number}, no el generador.
 */
public final class AccountNumber {

  private static final RandomGenerator AZAR = new SecureRandom();

  private AccountNumber() {}

  public static String generar() {
    StringBuilder numero = new StringBuilder("CTA-");
    for (int i = 0; i < 10; i++) {
      numero.append(MovementCode.ALFABETO.charAt(AZAR.nextInt(MovementCode.ALFABETO.length())));
    }
    return numero.toString();
  }
}
