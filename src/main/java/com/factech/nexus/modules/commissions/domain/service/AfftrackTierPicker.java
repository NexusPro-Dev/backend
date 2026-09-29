package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.models.RateSource;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * `RN-CM-041`, sin base de datos: de los FTD disponibles y una escala, <b>el escalón que se paga y
 * lo que sobra</b>.
 *
 * <p>Se paga <b>el mayor límite que no pase de los disponibles, una sola vez</b>: 55 con escalones
 * de 50 y 60 pagan el de 50 y guardan 5; 130 pagan el de 60 y guardan 70, no dos veces 60; 40 no
 * pagan nada y guardan 40. El valor del escalón no interviene en la elección: uno mayor que paga
 * menos por FTD sigue siendo el mayor alcanzado (`RF-CM-015` §13).
 */
public final class AfftrackTierPicker {

  private AfftrackTierPicker() {}

  /** Un escalón de la escala que rige, con su fuente (`RN-CM-039`). */
  public record Tier(UUID rateId, RateSource source, int threshold, BigDecimal amountPerFtd) {

    /** Lo que paga entero: {@code threshold × amountPerFtd}. */
    public BigDecimal importe() {
      return amountPerFtd.multiply(BigDecimal.valueOf(threshold));
    }
  }

  /** El escalón alcanzado, si lo hay, y el remanente que queda. */
  public record Pick(Optional<Tier> tier, int remanente) {

    public int pagados() {
      return tier.map(Tier::threshold).orElse(0);
    }
  }

  public static Pick elegir(int disponibles, Collection<Tier> escala) {
    Optional<Tier> alcanzado =
        escala.stream()
            .filter(t -> t.threshold() <= disponibles)
            .max(Comparator.comparingInt(Tier::threshold));
    return new Pick(alcanzado, disponibles - alcanzado.map(Tier::threshold).orElse(0));
  }
}
