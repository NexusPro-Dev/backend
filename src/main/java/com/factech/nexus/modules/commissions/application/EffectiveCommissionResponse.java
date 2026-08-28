package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.RateScope;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository.RateRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * La comision efectiva de una persona sobre un producto en una fecha (`RF-CM-005`).
 *
 * <h2>Tres desenlaces, y ninguno es un error</h2>
 *
 * <ul>
 *   <li><b>Hay tarifa</b>: {@code percentage} con valor, {@code rateId} y {@code scope} con la que
 *       gano.
 *   <li><b>No hay tarifa declarada</b>: {@code percentage} <b>nulo y presente</b>, y {@code
 *       outcome} lo dice.
 *   <li><b>La persona no comisiona</b>: igual, con su propio {@code outcome} — no es que falte
 *       declararla, es que esa persona no vende.
 * </ul>
 *
 * <p><b>{@code percentage} nulo y cero son cosas distintas y este contrato no las confunde.</b>
 * Cero es una decision declarada —no comisiona—; nulo es que nadie la tomo. Devolver cero en la
 * ausencia haria indistinguible lo pensado de lo olvidado, y quien consuma esto va a pagar con esa
 * cifra.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record EffectiveCommissionResponse(
    Outcome outcome,
    BigDecimal percentage,
    UUID rateId,
    RateScope scope,
    LocalDate validFrom,
    LocalDate validTo,
    UUID roleId,
    LocalDate onDate) {

  /** Cual de los tres desenlaces ocurrio. */
  public enum Outcome {
    /** Se resolvio una tarifa. El porcentaje puede ser cero, y eso es «no comisiona». */
    RESUELTA,
    /** La persona porta un rol vendedor y nadie declaro tarifa aplicable. */
    SIN_TARIFA,
    /** La persona no porta ningun rol de tipo vendedor. */
    NO_COMISIONA
  }

  public static EffectiveCommissionResponse resuelta(RateRow fila, UUID rolId, LocalDate fecha) {
    return new EffectiveCommissionResponse(
        Outcome.RESUELTA,
        fila.percentage(),
        fila.id(),
        RateScope.de(fila.userId() != null, fila.productId() != null),
        fila.validFrom(),
        fila.validTo(),
        rolId,
        fecha);
  }

  public static EffectiveCommissionResponse sinTarifa(UUID rolId, LocalDate fecha) {
    return new EffectiveCommissionResponse(
        Outcome.SIN_TARIFA, null, null, null, null, null, rolId, fecha);
  }

  public static EffectiveCommissionResponse noComisiona(LocalDate fecha) {
    return new EffectiveCommissionResponse(
        Outcome.NO_COMISIONA, null, null, null, null, null, null, fecha);
  }
}
