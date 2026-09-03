package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
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
 *   <li><b>Hay tasa</b>: {@code rateType} y {@code value} con valor, {@code rateId} y {@code
 *       source} con la que ganó.
 *   <li><b>No hay tasa aplicable</b>: {@code rateType} y {@code value} <b>nulos y presentes</b>, y
 *       {@code outcome} lo dice. Con el modelo del 01-09-2026 la causa más probable es que <b>nadie
 *       asoció</b> la tasa del rol a ese producto (`RN-CM-012`), no que nadie la declarara.
 *   <li><b>La persona no comisiona</b>: no porta rol vendedor <b>y</b> no tiene tasa personalizada.
 * </ul>
 *
 * <p><b>{@code value} nulo y cero son cosas distintas y este contrato no las confunde.</b> Cero es
 * una decisión declarada —no comisiona—; nulo es que nadie la tomó. Devolver cero en la ausencia
 * haría indistinguible lo pensado de lo olvidado, y quien consuma esto va a pagar con esa cifra.
 *
 * <h2>El valor es UN campo, y este contrato NO se parece al del catálogo</h2>
 *
 * <p>Decisión del responsable del proyecto, 02-09-2026. {@code GET /commission-rates} devuelve
 * {@code rateType} con {@code percentage} <b>o</b> {@code fixedAmount}; aquí se devuelve {@code
 * rateType} con un solo {@code value}. La asimetría es deliberada y hay que documentarla, porque un
 * consumidor que use las dos lecturas la notará.
 *
 * <p><b>El motivo es el párrafo de arriba.</b> Con dos campos separados, el nulo pasaría a tener
 * <b>dos causas</b>: una tasa de importe fijo dejaría {@code percentage} vacío <b>sin que eso
 * signifique «nadie la tomó»</b>, y el aviso que impide pagar cero donde no había tarifa dejaría de
 * poder escribirse en una frase. Con un campo, el nulo vuelve a significar exactamente una cosa.
 *
 * <p><b>{@code percentage} se retiró del contrato, y romper así es lo correcto.</b> Conservarlo con
 * el importe fijo dentro haría que el campo mintiera en su nombre; conservarlo junto a {@code
 * fixedAmount} devolvería el nulo ambiguo. Quien lea {@code percentage} obtendrá un campo que no
 * existe — un fallo ruidoso, y no un número que se lee mal.
 *
 * <p><b>Y no lleva moneda.</b> Esta consulta recibe el producto y es <b>el único punto del sistema
 * donde un importe fijo y su moneda existen a la vez</b>; se preguntó al responsable del proyecto y
 * se decidió que no la devuelva, porque empezaría a mezclar la tarifa con la venta (`cm.md` §1.4).
 * La consecuencia: la misma persona sobre dos productos de monedas distintas obtiene <b>la misma
 * respuesta</b> y ninguna señal (`RN-CM-017`).
 *
 * <p><b>{@code roleId} puede llegar nulo con {@code outcome} igual a {@code RESUELTA}</b>, y no es
 * una incoherencia: significa que ganó la tasa <b>personalizada</b> de alguien que ya no porta rol
 * vendedor. Es la consecuencia declarada de haberle quitado el rol a esas tasas (`cm.md` §5.3).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record EffectiveCommissionResponse(
    Outcome outcome,
    CommissionRateType rateType,
    BigDecimal value,
    UUID rateId,
    RateSource source,
    LocalDate validFrom,
    LocalDate validTo,
    UUID roleId,
    LocalDate onDate) {

  /** Cuál de los tres desenlaces ocurrió. */
  public enum Outcome {
    /** Se resolvió una tasa. El valor puede ser cero, y eso es «no comisiona». */
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
        tasa.rateType(),
        tasa.value(),
        tasa.rateId(),
        tasa.source(),
        tasa.validFrom(),
        tasa.validTo(),
        rolId,
        fecha);
  }

  public static EffectiveCommissionResponse sinTarifa(UUID rolId, LocalDate fecha) {
    return new EffectiveCommissionResponse(
        Outcome.SIN_TARIFA, null, null, null, null, null, null, rolId, fecha);
  }

  public static EffectiveCommissionResponse noComisiona(LocalDate fecha) {
    return new EffectiveCommissionResponse(
        Outcome.NO_COMISIONA, null, null, null, null, null, null, null, fecha);
  }
}
