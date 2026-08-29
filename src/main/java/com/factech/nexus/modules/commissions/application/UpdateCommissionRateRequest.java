package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuerpo de {@code PATCH /api/v1/commission-rates/{id}} (`RF-CM-003`).
 *
 * <p>{@link Patchable} distingue los <b>tres</b> estados: campo ausente, campo presente con nulo
 * explicito, y campo con valor. Aqui la distincion decide dos comportamientos <b>opuestos</b>:
 * quitar el fin de vigencia es una orden que se cumple —la tarifa vuelve a regir indefinidamente— y
 * quitar el porcentaje se rechaza.
 *
 * <h2>Los cuatro inmutables estan declarados A PROPOSITO</h2>
 *
 * <p>{@code roleId}, {@code productId}, {@code userId} y {@code validFrom} <b>no se corrigen</b>:
 * cambiarlos no corrige la tarifa, crea otra, y reescribiria a quien se le pago. Se declaran
 * igualmente para poder rechazarlos con <b>su</b> mensaje: sin ellos, quien intentara cambiar el
 * rol leeria «propiedad desconocida» y creeria que se equivoco de nombre.
 */
public record UpdateCommissionRateRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> percentage,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<LocalDate> validTo,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> roleId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> productId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> userId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> validFrom) {

  public UpdateCommissionRateRequest {
    percentage = percentage == null ? Patchable.ausente() : percentage;
    validTo = validTo == null ? Patchable.ausente() : validTo;
    roleId = roleId == null ? Patchable.ausente() : roleId;
    productId = productId == null ? Patchable.ausente() : productId;
    userId = userId == null ? Patchable.ausente() : userId;
    validFrom = validFrom == null ? Patchable.ausente() : validFrom;
  }

  /** Trae alguno de los cuatro que no se pueden corregir? (`VAL-009`) */
  public boolean traeInmutables() {
    return roleId.presente() || productId.presente() || userId.presente() || validFrom.presente();
  }

  /** Se envio algun campo corregible, con el valor que sea? */
  public boolean informaAlgo() {
    return percentage.presente() || validTo.presente();
  }
}
