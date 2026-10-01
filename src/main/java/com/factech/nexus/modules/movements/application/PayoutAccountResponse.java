package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Una cuenta de cobro (`RF-MV-035` a `RF-MV-039`). */
@Schema(name = "PayoutAccount")
public record PayoutAccountResponse(
    UUID id,
    Institution institution,
    @Schema(
            types = {"string", "null"},
            description = "AHORROS o CORRIENTE en un banco; nulo en una billetera móvil.")
        String accountType,
    @Schema(description = "Solo dígitos.") String number,
    @Schema(description = "La que se usa al pedir un retiro sin indicar cuenta.") boolean principal,
    @Schema(
            description =
                "Si sirve para pedir un retiro: false cuando su entidad está desactivada"
                    + " (`RN-MV-054`). Hay que registrar otra.")
        boolean usable,
    @Schema(
            description =
                "El titular: siempre el dueño de la cuenta, con el nombre y el documento de su"
                    + " usuario tal como están hoy (`RN-MV-055`).")
        Holder holder,
    OffsetDateTime createdAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time",
            description =
                "Cuándo se dio de baja. Solo lo ve administración (`RF-MV-039`); nulo en las vivas.")
        OffsetDateTime deletedAt) {

  @Schema(name = "PayoutAccountInstitution")
  public record Institution(UUID id, String code, String name, String kind, boolean active) {}

  @Schema(name = "PayoutAccountHolder")
  public record Holder(String name, String documentType, String documentNumber) {}
}
