package com.factech.nexus.modules.academy.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Las peticiones de las señales de trading (`RF-AC-067` a `RF-AC-071`). */
public final class SignalRequests {

  private SignalRequests() {}

  /** Registrar una (`RF-AC-068`), y cada elemento de registrar varias (`RF-AC-069`). */
  @Schema(name = "RegisterSignalRequest")
  public record Register(
      @Schema(
              description =
                  "El par, hasta 20 caracteres de letras, cifras y / . _ -; se guarda en"
                      + " mayúsculas.",
              example = "EUR/USD")
          String currencyPair,
      @Schema(description = "La hora de la señal, HH:mm en America/Bogota.", example = "10:30")
          String signalTime,
      @Schema(description = "El tipo, un entero mayor o igual que cero.", example = "1")
          Integer signalType,
      @Schema(
              description =
                  "UP si sube o DOWN si baja; también SUBE/BAJA, CALL/PUT o BUY/SELL, sin"
                      + " distinguir mayúsculas. Se guarda UP o DOWN.",
              example = "UP")
          String direction,
      @Schema(
              description = "FREE, PREMIUM o PERSONALIZED, sin distinguir mayúsculas.",
              example = "FREE")
          String subscriptionType,
      @Schema(
              description =
                  "Las membresías que la ven: una o más con PERSONALIZED; ninguna con las otras"
                      + " dos.")
          List<UUID> membershipIds) {}

  /** Retirar (`RF-AC-071`): el motivo, que valida {@code DeletionReason}. */
  @Schema(name = "DeleteSignalRequest")
  public record Delete(String reason) {}

  /** Los filtros del listado de administración (`RF-AC-067`). */
  public record ListFilter(
      Integer page,
      Integer size,
      @Schema(description = "FREE, PREMIUM o PERSONALIZED.") String subscriptionType,
      @Schema(
              description =
                  "true, las que se muestran ahora; false, las que ya no; ausente, todas.")
          Boolean visible,
      @Schema(description = "true para incluir las retiradas; por omisión, no.")
          Boolean includeDeleted) {}
}
