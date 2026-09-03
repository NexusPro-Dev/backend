package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.CommissionValue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/commission-rates} (`RF-CM-001`).
 *
 * <p><b>Cuatro campos, de los que siempre llegan tres.</b> El alta anterior tenía dos y ninguno
 * opcional; desde el 02-09-2026 hay una elección que hacer —porcentaje o valor fijo— y esa elección
 * <b>se declara en lugar de deducirse</b>.
 *
 * <h2>Por qué {@code rateType} se pide, si parece deducible</h2>
 *
 * <p>Un lector razonable dirá que sobra: si llega un porcentaje, la forma es porcentaje. Y
 * funcionaría <b>mientras llegue exactamente uno</b>.
 *
 * <p>El problema es el resto. Si llegan <b>los dos</b>, o <b>ninguno</b>, hay que rechazar la
 * petición diciendo qué está mal — y sin la forma <b>no se puede saber</b>: no se distingue a quien
 * quiso declarar un porcentaje y se equivocó de campo, de quien quiso declarar un importe y lo dejó
 * vacío. El mensaje sería el mismo para dos errores distintos.
 *
 * <h2>Este cuerpo rompe el contrato anterior, a propósito</h2>
 *
 * <p>Una petición que hoy funciona —solo {@code roleId} y {@code percentage}— pasa a devolver
 * {@code 400}. La alternativa compatible era suponer {@code PORCENTAJE} cuando falta la forma, y
 * <b>reintroduce el defecto que el esquema se molesta en quitar</b>: la forma volvería a deducirse,
 * y la petición equivocada quedaría aceptada en vez de rechazada.
 *
 * <p><b>Lo que se valida aquí es solo lo que mira UN campo.</b> «Una forma y solo una» necesita los
 * tres a la vez y vive en {@link CommissionValue}, para no escribirse cuatro veces —las dos altas y
 * las dos correcciones—.
 *
 * <p><b>El cero es válido en las dos formas</b> (`RN-CM-007`): significa «esto no comisiona». De
 * ahí que los dos mínimos sean inclusivos.
 *
 * <p><b>Y {@code fixedAmount} no tiene máximo</b>, que es la asimetría con el porcentaje.
 * `RN-CM-018`: cien es un límite que el negocio conoce sin mirar nada, y para el importe <b>no
 * existe ese número</b>.
 */
public record RegisterCommissionRateRequest(
    @NotNull(message = "VAL-001: El rol de la tasa es obligatorio.") UUID roleId,
    @NotNull(message = "VAL-002: La forma de la comisión es obligatoria: porcentaje o valor fijo.")
        CommissionRateType rateType,
    @DecimalMin(value = "0.00", message = "VAL-003: El porcentaje debe estar entre cero y cien.")
        @DecimalMax(
            value = "100.00",
            message = "VAL-003: El porcentaje debe estar entre cero y cien.")
        @Digits(
            integer = 3,
            fraction = 2,
            message = "VAL-003: El porcentaje admite como mucho dos decimales.")
        BigDecimal percentage,
    @DecimalMin(value = "0.0000", message = "VAL-012: El valor fijo no puede ser negativo.")
        @Digits(
            integer = 10,
            fraction = 4,
            message = "VAL-012: El valor fijo admite como mucho cuatro decimales.")
        BigDecimal fixedAmount) {

  /**
   * La forma y el valor, <b>construidos juntos</b>.
   *
   * <p>Es donde se comprueba `RN-CM-016`, y por eso el caso de uso no tiene que saber nada de
   * formas: pide el valor y recibe uno válido o una excepción.
   */
  public CommissionValue valor() {
    return CommissionValue.of(rateType, percentage, fixedAmount);
  }
}
