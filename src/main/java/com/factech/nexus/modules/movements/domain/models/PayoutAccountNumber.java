package com.factech.nexus.modules.movements.domain.models;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * El número de una cuenta de cobro (`RF-MV-035` · `VAL-002`): se escribe como sale en el extracto
 * —con espacios y guiones— y <b>se guarda solo con dígitos</b>, que es lo que permite reconocer el
 * duplicado escrito de otra forma (`CA-MV-384`).
 */
public final class PayoutAccountNumber {

  private static final Pattern SEPARADORES = Pattern.compile("[\\s-]+");
  private static final Pattern DIGITOS = Pattern.compile("[0-9]{4,20}");

  private PayoutAccountNumber() {}

  /** Los dígitos, sin separadores, si la forma general vale (4 a 20 dígitos); si no, vacío. */
  public static Optional<String> normalize(String escrito) {
    if (escrito == null) {
      return Optional.empty();
    }
    String limpio = SEPARADORES.matcher(escrito.trim()).replaceAll("");
    return DIGITOS.matcher(limpio).matches() ? Optional.of(limpio) : Optional.empty();
  }

  /** Lo que se puede escribir en un registro: los cuatro últimos dígitos (`plan.md` §6). */
  public static String masked(String numero) {
    if (numero == null) {
      return null;
    }
    return numero.length() <= 4 ? "****" : "****" + numero.substring(numero.length() - 4);
  }
}
