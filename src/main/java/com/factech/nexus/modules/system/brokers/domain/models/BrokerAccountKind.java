package com.factech.nexus.modules.system.brokers.domain.models;

import java.util.Arrays;
import java.util.Optional;

/**
 * De qué tipo es una cuenta de broker (`RN-SP-068`, {@code ck_user_brokers_kind}).
 *
 * <p><b>Lo pone el sistema al declararla, por el tipo de rol del titular</b>, y no cambia después:
 * es una foto y no una derivación viva de {@code user_roles}. Un cliente que asciende a vendedor
 * conserva sus cuentas de consumidor, y con ellas su FTD.
 *
 * <p><b>La de vendedor no tiene FTD</b>: nunca pasa a {@link UserBrokerStatus#FIRST_DEPOSIT} —lo
 * impide {@code ck_user_brokers_ftd_solo_consumidor}— y no cuenta en los indicadores de la red
 * (`RF-SP-058`).
 */
public enum BrokerAccountKind {

  /** La cuenta de quien porta un rol de tipo {@code VENDEDOR}. Sin FTD. */
  VENDEDOR,

  /** La cuenta de un consumidor. La única que cuenta en el embudo del FTD. */
  CONSUMIDOR;

  /**
   * El tipo que designa ese texto, o vacío; sin normalizar la caja, como {@link
   * UserBrokerStatus#de}.
   */
  public static Optional<BrokerAccountKind> de(String valor) {
    if (valor == null || valor.isBlank()) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(tipo -> tipo.name().equals(valor.trim())).findFirst();
  }
}
