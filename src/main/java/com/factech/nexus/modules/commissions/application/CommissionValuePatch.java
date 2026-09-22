package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.CommissionValue;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Convierte los tres campos parcheables del valor de una comisión en <b>uno solo</b>.
 *
 * <h2>Por qué el valor NO se parchea por campos, cuando todo lo demás del proyecto sí</h2>
 *
 * <p>En el resto del sistema una corrección es parcial: se manda lo que cambia y lo demás se queda
 * como está. Aquí <b>la forma y el valor viajan juntos o no viajan</b>, y romper esa costumbre es
 * deliberado.
 *
 * <p>El motivo es que <b>por separado no se puede saber qué se pidió</b>. Un importe suelto sobre
 * una tasa que era de porcentaje puede ser «cámbiala a importe fijo» o «me equivoqué de campo», y
 * las dos peticiones <b>se escriben igual</b>. Deducirlo es lo que el alta ya decidió que no se
 * hace, y <b>corregir es donde más caro sale</b>: al registrar solo se pierde un alta; aquí se
 * cambia lo que ya está pagando.
 *
 * <p><b>Existe como clase aparte porque lo usan las dos correcciones</b> —la de rol y la
 * personalizada— y son dos {@code record}, que no pueden heredar. Escribirlo dos veces dejaría dos
 * sitios donde la regla podría divergir.
 *
 * <p><b>La asimetría con el fin de vigencia es intencionada y hay que defenderla:</b> allí el nulo
 * explícito <b>se obedece</b> —significa «rige indefinidamente»— y aquí <b>se rechaza</b>, porque
 * media forma vacía no significa nada. Parece una inconsistencia; unificarlas rompería una de las
 * dos.
 */
final class CommissionValuePatch {

  private CommissionValuePatch() {}

  /**
   * Los tres campos de la petición, como un solo campo parcheable.
   *
   * @return {@code ausente} si no se está corrigiendo el valor; {@code de(null)} si se pidió
   *     vaciarlo —que el agregado rechaza con `VAL-002`—; y {@code de(valor)} si se declaró forma y
   *     valor
   * @throws ValidationException `VAL-011` si llega un valor <b>sin</b> su forma
   */
  static Patchable<CommissionValue> resolver(
      Patchable<CommissionRateType> rateType,
      Patchable<BigDecimal> percentage,
      Patchable<BigDecimal> fixedAmount) {

    boolean llegaAlgunValor = percentage.presente() || fixedAmount.presente();

    if (!rateType.presente()) {
      if (!llegaAlgunValor) {
        return Patchable.ausente();
      }
      // Un valor sin su forma. NO se deduce: es exactamente la ambigüedad que
      // esta clase existe para no resolver a ojo.
      String mensaje =
          "Una comisión por porcentaje lleva porcentaje y no valor fijo; "
              + "una comisión por valor fijo, al revés.";
      throw new ValidationException(
          "VAL-011", mensaje, List.of(new FieldError("rateType", "VAL-011", mensaje)));
    }

    CommissionRateType tipo = rateType.valor();
    if (tipo == null) {
      // Presente y nulo: se pidió vaciar la forma. Lo rechaza el agregado, que
      // es donde vive el mensaje, para que decirlo dos veces no las desalinee.
      return Patchable.de(null);
    }

    return Patchable.de(CommissionValue.of(tipo, percentage.valor(), fixedAmount.valor()));
  }
}
