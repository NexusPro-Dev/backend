package com.factech.nexus.modules.academy.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Las respuestas de las señales de trading (`RF-AC-067` a `RF-AC-072`). */
public final class SignalResponses {

  private SignalResponses() {}

  /** Una membresía que ve una señal PERSONALIZED. */
  @Schema(name = "SignalMembershipRef")
  public record SignalMembershipRef(UUID id, String code, String name) {}

  /** La señal para administración: con sus membresías y sus fechas. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "SignalResponse")
  public record SignalResponse(
      UUID id,
      String currencyPair,
      @Schema(description = "HH:mm en America/Bogota.") String signalTime,
      int signalType,
      @Schema(
              description =
                  "UP si sube o DOWN si baja; nula solo en una señal retirada al migrar (V109).",
              allowableValues = {"UP", "DOWN"})
          String direction,
      String subscriptionType,
      @Schema(description = "Vacía salvo en una PERSONALIZED.")
          List<SignalMembershipRef> memberships,
      @Schema(
              description =
                  "Hasta cuándo se muestra: la medianoche de Bogotá que cierra el día en que se"
                      + " creó, aunque signalTime ya haya pasado.")
          OffsetDateTime visibleUntil,
      @Schema(description = "Si se muestra ahora: sin retirar y antes de visibleUntil.")
          boolean visible,
      OffsetDateTime createdAt,
      OffsetDateTime deletedAt) {}

  /** La señal que ve el alumno (`RF-AC-072`). */
  @Schema(name = "AvailableSignal")
  public record AvailableSignal(
      UUID id,
      String currencyPair,
      @Schema(description = "HH:mm en America/Bogota.") String signalTime,
      int signalType,
      @Schema(
              description = "UP si sube o DOWN si baja.",
              allowableValues = {"UP", "DOWN"})
          String direction,
      String subscriptionType,
      OffsetDateTime visibleUntil,
      OffsetDateTime createdAt) {}
}
