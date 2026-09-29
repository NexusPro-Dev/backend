package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Cuerpo de {@code PATCH /api/v1/afftrack-rates/{id}} (`RF-CM-017`).
 *
 * <p><b>Distingue ausente de nulo</b>, como {@link UpdateCommissionRateRequest}: el rol y el
 * producto se aceptan en el cuerpo <b>solo para rechazarlos</b> (`EX-002`) —ignorarlos haría creer
 * que el cambio se aplicó— y vaciar el límite o el valor se rechaza (`VAL-002`).
 */
@Schema(name = "UpdateAfftrackRateRequest")
public record UpdateAfftrackRateRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> threshold,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<BigDecimal> amountPerFtd,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> roleId,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Object> productId) {

  public UpdateAfftrackRateRequest {
    threshold = threshold == null ? Patchable.ausente() : threshold;
    amountPerFtd = amountPerFtd == null ? Patchable.ausente() : amountPerFtd;
    roleId = roleId == null ? Patchable.ausente() : roleId;
    productId = productId == null ? Patchable.ausente() : productId;
  }

  /** ¿Trae el rol o el producto, que no se corrigen? */
  public boolean traeInmutables() {
    return roleId.presente() || productId.presente();
  }

  /** ¿Se envió algún campo corregible, con el valor que sea? */
  public boolean informaAlgo() {
    return threshold.presente() || amountPerFtd.presente();
  }
}
