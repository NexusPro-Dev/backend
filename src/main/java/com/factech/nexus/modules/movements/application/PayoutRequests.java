package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Las peticiones con cuerpo de las cuentas de cobro (`RF-MV-032` a `RF-MV-038`).
 *
 * <p><b>Los cuerpos de edición NO declaran lo que no se edita</b> —el código, el tipo y el país de
 * una entidad; la entidad de una cuenta—, y {@code FAIL_ON_UNKNOWN_PROPERTIES} convierte enviarlos
 * en un {@code 400}: un cambio que no se aplica no se ignora en silencio.
 */
public final class PayoutRequests {

  private PayoutRequests() {}

  /** `RF-MV-032`. */
  @Schema(name = "RegisterPayoutInstitutionRequest")
  public record RegisterInstitution(
      @Schema(
              description =
                  "De 2 a 30 caracteres: letra, y después mayúsculas, dígitos o guion bajo. Se"
                      + " guarda en mayúsculas y no se puede cambiar.",
              example = "BANCOLOMBIA")
          String code,
      @Schema(description = "El nombre que ve la persona. Hasta 100 caracteres.") String name,
      @Schema(description = "BANCO o BILLETERA_MOVIL. No se puede cambiar.") String kind,
      @Schema(description = "El país donde opera. No se puede cambiar.") UUID countryId) {}

  /** `RF-MV-034`: al menos uno de los dos. */
  @Schema(name = "UpdatePayoutInstitutionRequest")
  public record UpdateInstitution(
      @Schema(
              types = {"string", "null"},
              description = "El nombre nuevo.")
          String name,
      @Schema(
              types = {"boolean", "null"},
              description = "false la deja de ofrecer y de admitir cuentas y retiros nuevos.")
          Boolean active) {}

  /** `RF-MV-035`. */
  @Schema(name = "RegisterPayoutAccountRequest")
  public record RegisterAccount(
      @Schema(description = "El banco o la billetera, de `GET /movements/payout-institutions`.")
          UUID institutionId,
      @Schema(
              types = {"string", "null"},
              description =
                  "AHORROS o CORRIENTE. Obligatorio en un banco; en una billetera móvil no debe"
                      + " venir.")
          String accountType,
      @Schema(
              description =
                  "Número de cuenta, o de celular en una billetera. Admite espacios y guiones, que"
                      + " se quitan.")
          String number,
      @Schema(
              types = {"boolean", "null"},
              description =
                  "true la hace la principal y desmarca la anterior. La primera cuenta lo es sin"
                      + " pedirlo.")
          Boolean principal) {}

  /** `RF-MV-037`: al menos uno de los tres. */
  @Schema(name = "UpdatePayoutAccountRequest")
  public record UpdateAccount(
      @Schema(
              types = {"string", "null"},
              description = "AHORROS o CORRIENTE; solo en un banco.")
          String accountType,
      @Schema(
              types = {"string", "null"},
              description = "El número corregido.")
          String number,
      @Schema(
              types = {"boolean", "null"},
              description =
                  "Solo true: la hace la principal. La principal no se quita; se marca otra.")
          Boolean principal) {}
}
