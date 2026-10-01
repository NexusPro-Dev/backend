package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Una entidad de cobro —banco o billetera móvil— del catálogo (`RF-MV-032` a `RF-MV-034`). */
@Schema(name = "PayoutInstitution")
public record PayoutInstitutionResponse(
    UUID id,
    String code,
    String name,
    @Schema(description = "BANCO o BILLETERA_MOVIL.") String kind,
    Country country,
    @Schema(
            description =
                "false: no se ofrece, no admite cuentas nuevas ni retiros hacia las que ya tiene.")
        boolean active,
    OffsetDateTime createdAt) {

  @Schema(name = "PayoutInstitutionCountry")
  public record Country(UUID id, String code, String name) {}
}
