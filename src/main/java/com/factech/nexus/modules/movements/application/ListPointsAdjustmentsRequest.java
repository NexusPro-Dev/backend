package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/movements/points-adjustments} (`RF-MV-053`).
 *
 * @param sort {@code occurredAt} —por omisión, descendente—, {@code points} o {@code code}, con
 *     {@code ,asc} o {@code ,desc}
 * @param from desde cuándo ocurrió, inclusive
 * @param to hasta cuándo ocurrió, exclusive
 * @param sign {@code SUMA} o {@code RESTA}
 * @param q fragmento del comprobante, el motivo, la referencia, o el nombre, usuario o correo de la
 *     persona
 */
public record ListPointsAdjustmentsRequest(
    Integer page,
    Integer size,
    @Schema(description = "occurredAt (por omisión, desc), points o code; con ,asc o ,desc.")
        String sort,
    UUID userId,
    UUID currencyId,
    @Schema(description = "Desde cuándo ocurrió, inclusive.") OffsetDateTime from,
    @Schema(description = "Hasta cuándo ocurrió, exclusive.") OffsetDateTime to,
    @Schema(description = "SUMA (puntos positivos) o RESTA (negativos).") String sign,
    @Schema(
            description =
                "Fragmento del comprobante, el motivo, la referencia, o el nombre, usuario o correo"
                    + " de la persona; sin distinguir acentos ni mayúsculas. No busca por documento.")
        String q) {

  public ListPointsAdjustmentsRequest {
    sign = sign == null || sign.isBlank() ? null : sign.trim().toUpperCase(Locale.ROOT);
    q = q == null || q.isBlank() ? null : q.trim();
  }
}
