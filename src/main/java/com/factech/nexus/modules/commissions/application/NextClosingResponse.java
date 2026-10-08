package com.factech.nexus.modules.commissions.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El próximo cierre programado y cómo se pagará (`RF-CM-028`, `RF-CM-029`).
 *
 * <p><b>{@code windowOpen}</b> es lo que decide si el frontend muestra el botón de elegir; {@code
 * windowOpensAt} dice cuándo aparecerá. <b>Sin elección, {@code paymentMode} es {@code
 * AUTOMATICO}</b> y {@code chosen} es falso, sin persona ni fecha (`RN-CM-054`).
 */
@Schema(name = "NextClosingResponse")
public record NextClosingResponse(
    OffsetDateTime scheduledFor,
    OffsetDateTime windowOpensAt,
    boolean windowOpen,
    String paymentMode,
    boolean chosen,
    UUID chosenBy,
    OffsetDateTime chosenAt) {}
