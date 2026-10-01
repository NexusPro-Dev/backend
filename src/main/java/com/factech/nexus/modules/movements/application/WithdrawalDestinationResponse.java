package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A dónde se paga un retiro, <b>tal como era al pedirlo</b> (`RN-MV-056`). Editar la cuenta, darla
 * de baja, renombrar la entidad o corregir el documento de la persona no lo cambia.
 */
@Schema(name = "WithdrawalDestination")
public record WithdrawalDestinationResponse(
    @Schema(description = "La cuenta de la que se copió; puede estar dada de baja.")
        UUID payoutAccountId,
    Institution institution,
    @Schema(
            types = {"string", "null"},
            description = "AHORROS o CORRIENTE; nulo en una billetera.")
        String accountType,
    String number,
    Holder holder) {

  @Schema(name = "WithdrawalDestinationInstitution")
  public record Institution(String code, String name, String kind) {}

  @Schema(name = "WithdrawalDestinationHolder")
  public record Holder(String name, String documentType, String documentNumber) {}
}
