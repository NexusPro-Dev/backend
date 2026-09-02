package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.RateSource;
import com.factech.nexus.modules.commissions.domain.repository.CommissionResolutionRepository.ResolvedRate;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * La comisión efectiva de una persona sobre un producto en una fecha (`RF-CM-005`).
 *
 * <h2>Tres desenlaces, y ninguno es un error</h2>
 *
 * <ul>
 *   <li><b>Hay tasa</b>: {@code percentage} con valor, {@code rateId} y {@code source} con la que
 *       ganó.
 *   <li><b>No hay tasa aplicable</b>: {@code percentage} <b>nulo y presente</b>, y {@code outcome}
 *       lo dice. Con el modelo del 01-09-2026 la causa más probable es que <b>nadie asoció</b> la
 *       tasa del rol a ese producto (`RN-CM-012`), no que nadie la declarara.
 *   <li><b>La persona no comisiona</b>: no porta rol vendedor <b>y</b> no tiene tasa personalizada.
 * </ul>
 *
 * <p><b>{@code percentage} nulo y cero son cosas distintas y este contrato no las confunde.</b>
 * Cero es una decisión declarada —no comisiona—; nulo es que nadie la tomó. Devolver cero en la
 * ausencia haría indistinguible lo pensado de lo olvidado, y quien consuma esto va a pagar con esa
 * cifra.
 *
 * <p><b>{@code roleId} puede llegar nulo con {@code outcome} igual a {@code RESUELTA}</b>, y no es
 * una incoherencia: significa que ganó la tasa <b>personalizada</b> de alguien que ya no porta rol
 * vendedor. Es la consecuencia declarada de haberle quitado el rol a esas tasas (`cm.md` §5.3).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record EffectiveCommissionResponse(
    Outcome outcome,
    BigDecimal percentage,
    UUID rateId,
    RateSource source,
    LocalDate validFrom,
    LocalDate validTo,
    UUID roleId,
    LocalDate onDate) {

  /** Cuál de los tres desenlaces ocurrió. */
  public enum Outcome {
    /** Se resolvió una tasa. El porcentaje puede ser cero, y eso es «no comisiona». */
    RESUELTA,
    /** Hay rol vendedor y ninguna tasa aplicable — casi siempre, ninguna asociación. */
    SIN_TARIFA,
    /** Ni rol vendedor ni tasa personalizada. */
    NO_COMISIONA
  }

  public static EffectiveCommissionResponse resuelta(
      ResolvedRate tasa, UUID rolId, LocalDate fecha) {
    return new EffectiveCommissionResponse(
        Outcome.RESUELTA,
        tasa.percentage(),
        tasa.rateId(),
        tasa.source(),
        tasa.validFrom(),
        tasa.validTo(),
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
