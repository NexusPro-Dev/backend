package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.AfftrackRateQueryRepository.AfftrackRateRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un escalón afftrack de rol tal como sale de la API (`RF-CM-015` a `RF-CM-017`).
 *
 * <p><b>{@code amountAtThreshold} es {@code threshold × amountPerFtd}</b>: lo que paga el escalón
 * entero al alcanzarse, para que quien lo registra vea lo que cuesta sin hacer la cuenta (`spec.md`
 * §2.1). Sale de la misma sentencia que lo lee.
 *
 * <p>Los {@code record} anidados llevan nombre propio en el contrato: springdoc publica los
 * esquemas por el nombre simple de la clase, y un {@code ProductRef} suelto se fundiría con los de
 * otros módulos.
 */
@Schema(name = "AfftrackRate")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AfftrackRateResponse(
    UUID id,
    ProductRef product,
    RoleRef role,
    int threshold,
    BigDecimal amountPerFtd,
    BigDecimal amountAtThreshold,
    OffsetDateTime createdAt) {

  /** El producto FTD, con la moneda en que se paga el escalón (`RN-CM-017`). */
  @Schema(name = "AfftrackRateProduct")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ProductRef(UUID id, String code, String name, CurrencyRef currency) {}

  @Schema(name = "AfftrackRateProductCurrency")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CurrencyRef(UUID id, String code, int decimalPlaces) {}

  @Schema(name = "AfftrackRateRole")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name) {}

  public static AfftrackRateResponse from(AfftrackRateRow fila) {
    return new AfftrackRateResponse(
        fila.id(),
        producto(fila),
        new RoleRef(fila.roleId(), fila.roleCode(), fila.roleName()),
        fila.threshold(),
        fila.amountPerFtd(),
        fila.amountAtThreshold(),
        fila.createdAt());
  }

  static ProductRef producto(AfftrackRateRow fila) {
    return new ProductRef(
        fila.productId(),
        fila.productCode(),
        fila.productName(),
        new CurrencyRef(fila.currencyId(), fila.currencyCode(), fila.currencyDecimalPlaces()));
  }
}
