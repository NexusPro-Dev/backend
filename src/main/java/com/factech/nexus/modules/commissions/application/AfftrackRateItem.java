package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.AfftrackRateQueryRepository.AfftrackRateRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un escalón en el listado (`RF-CM-016`): la forma de {@link AfftrackRateResponse} y, en los
 * retirados, desde cuándo lo están. <b>Sin el motivo del retiro</b>: en bloque sería una
 * exportación de decisiones comerciales, el mismo criterio que {@link CommissionRateItem}.
 */
@Schema(name = "AfftrackRateItem")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AfftrackRateItem(
    UUID id,
    AfftrackRateResponse.ProductRef product,
    AfftrackRateResponse.RoleRef role,
    int threshold,
    BigDecimal amountPerFtd,
    BigDecimal amountAtThreshold,
    OffsetDateTime deletedAt) {

  public static AfftrackRateItem from(AfftrackRateRow fila) {
    AfftrackRateResponse base = AfftrackRateResponse.from(fila);
    return new AfftrackRateItem(
        base.id(),
        base.product(),
        base.role(),
        base.threshold(),
        base.amountPerFtd(),
        base.amountAtThreshold(),
        fila.deletedAt());
  }
}
