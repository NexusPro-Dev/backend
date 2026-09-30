package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.DirectCommission;
import com.factech.nexus.modules.products.domain.models.DirectCommissionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * La comisión por venta directa en el contrato (`RN-PM-051`): la misma forma en la entrada y en la
 * salida.
 *
 * <p><b>El nombre del esquema lleva el prefijo del módulo</b>: springdoc funde en un solo esquema
 * dos {@code record} con el mismo nombre simple, y `CM` tiene sus propias comisiones.
 *
 * <p><b>Aquí no se valida nada</b>, ni con anotaciones: si el porcentaje sobra o falta depende del
 * tipo, y el tope depende del precio y de la moneda. Lo dice {@code DirectCommissionRules}, que
 * puede nombrar qué está mal. El tipo fuera de dominio lo rechaza Jackson con {@code 400}.
 */
@Schema(
    name = "ProductDirectCommission",
    description =
        "Lo que cobra en su venta propia quien no es el último eslabón de la fuerza comercial,"
            + " en lugar de su tasa de rol. Porcentaje o importe fijo, nunca los dos.")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductDirectCommission(
    @Schema(description = "PORCENTAJE o FIJO.") DirectCommissionType type,
    @Schema(description = "De 0 a 100. Solo si `type` es PORCENTAJE.") BigDecimal percentage,
    @Schema(description = "Por unidad, en la moneda del producto. Solo si `type` es FIJO.")
        BigDecimal fixedAmount) {

  public DirectCommission toDomain() {
    return new DirectCommission(type, percentage, fixedAmount);
  }

  /** Nulo en un FTD. */
  public static ProductDirectCommission from(DirectCommission directa) {
    return directa == null
        ? null
        : new ProductDirectCommission(directa.type(), directa.percentage(), directa.fixedAmount());
  }
}
