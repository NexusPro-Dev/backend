package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.CommissionValue;
import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuerpo de {@code PATCH /api/v1/user-commission-rates/{id}}.
 *
 * <p>{@link Patchable} distingue los <b>tres</b> estados: campo ausente, campo presente con nulo
 * explícito, y campo con valor. Aquí la distinción decide dos comportamientos <b>opuestos</b>:
 * quitar el fin de vigencia es una orden que se cumple —la tasa vuelve a regir indefinidamente— y
 * quitar el porcentaje se rechaza.
 *
 * <p><b>Aquí corregir y cambiar SIGUEN siendo cosas distintas</b>, al revés que en el catálogo de
 * rol: como esta tabla conserva vigencia, cambiar lo que gana alguien a partir de una fecha es
 * <b>cerrar la vigente y registrar otra</b>. Corregir reescribe; cambiar añade.
 *
 * <h2>Los dos inmutables están declarados A PROPÓSITO</h2>
 *
 * <p>{@code userId} y {@code validFrom} <b>no se corrigen</b>: cambiarlos no corrige la tasa, crea
 * otra, y reescribiría a quién se le pagó qué. Se declaran igualmente para poder rechazarlos con
 * <b>su</b> mensaje: sin ellos, quien intentara cambiar la persona leería «propiedad desconocida» y
 * creería que se equivocó de nombre.
 */
public record UpdateUserCommissionRateRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<CommissionRateType> rateType,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> percentage,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> fixedAmount,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<LocalDate> validTo,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> userId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> validFrom) {

  public UpdateUserCommissionRateRequest {
    rateType = rateType == null ? Patchable.ausente() : rateType;
    percentage = percentage == null ? Patchable.ausente() : percentage;
    fixedAmount = fixedAmount == null ? Patchable.ausente() : fixedAmount;
    validTo = validTo == null ? Patchable.ausente() : validTo;
    userId = userId == null ? Patchable.ausente() : userId;
    validFrom = validFrom == null ? Patchable.ausente() : validFrom;
  }

  /** ¿Trae alguno de los dos que no se pueden corregir? (`VAL-009`) */
  public boolean traeInmutables() {
    return userId.presente() || validFrom.presente();
  }

  /** ¿Se envió algún campo corregible, con el valor que sea? */
  public boolean informaAlgo() {
    return rateType.presente()
        || percentage.presente()
        || fixedAmount.presente()
        || validTo.presente();
  }

  /**
   * Los tres campos del valor, como uno solo.
   *
   * <p><b>Aquí conviven los dos regímenes</b>, y es lo que más fácil se lee como una
   * inconsistencia: el valor se <b>sustituye entero</b> y el fin de vigencia <b>se parchea
   * solo</b>, con su nulo obediente. No se unifican porque significan cosas distintas — ver {@link
   * CommissionValuePatch}.
   */
  public Patchable<CommissionValue> valor() {
    return CommissionValuePatch.resolver(rateType, percentage, fixedAmount);
  }
}
