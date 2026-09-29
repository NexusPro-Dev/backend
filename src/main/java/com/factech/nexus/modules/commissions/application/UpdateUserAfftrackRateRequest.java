package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cuerpo de {@code PATCH /api/v1/user-afftrack-rates/{id}} (`RF-CM-019`).
 *
 * <p><b>Dos comportamientos opuestos ante el mismo gesto</b>, como en `RF-CM-006`: enviar {@code
 * validTo} vacío <b>quita el fin</b> —es una orden que se cumple—, y enviar vacío el límite o el
 * valor se rechaza. La persona, el producto y el inicio se aceptan solo para rechazarlos
 * (`EX-008`).
 */
@Schema(name = "UpdateUserAfftrackRateRequest")
public record UpdateUserAfftrackRateRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> threshold,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> amountPerFtd,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<LocalDate> validTo,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> userId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> productId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> validFrom) {

  public UpdateUserAfftrackRateRequest {
    threshold = threshold == null ? Patchable.ausente() : threshold;
    amountPerFtd = amountPerFtd == null ? Patchable.ausente() : amountPerFtd;
    validTo = validTo == null ? Patchable.ausente() : validTo;
    userId = userId == null ? Patchable.ausente() : userId;
    productId = productId == null ? Patchable.ausente() : productId;
    validFrom = validFrom == null ? Patchable.ausente() : validFrom;
  }

  public boolean traeInmutables() {
    return userId.presente() || productId.presente() || validFrom.presente();
  }

  public boolean informaAlgo() {
    return threshold.presente() || amountPerFtd.presente() || validTo.presente();
  }
}
