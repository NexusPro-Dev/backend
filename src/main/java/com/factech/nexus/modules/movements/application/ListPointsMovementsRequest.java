package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Los parámetros que comparten las dos listas de movimientos de puntos (`RF-MV-055`, `RF-MV-056`).
 * La persona no está aquí: en la propia es quien consulta, y la de administración la recibe aparte
 * para que el contrato de la propia no la ofrezca.
 */
public record ListPointsMovementsRequest(
    Integer page,
    Integer size,
    @Schema(description = "occurredAt (por omisión, desc), points o code; con ,asc o ,desc.")
        String sort,
    @Schema(description = "COMPRA_PUNTOS o AJUSTE_PUNTOS.") String type,
    @Schema(description = "PENDIENTE, CONFIRMADA o RECHAZADA.") String status,
    UUID currencyId,
    @Schema(description = "Desde cuándo ocurrió, inclusive.") OffsetDateTime from,
    @Schema(description = "Hasta cuándo ocurrió, exclusive.") OffsetDateTime to,
    @Schema(description = "SUMA (puntos positivos) o RESTA (negativos: solo ajustes).") String sign,
    @Schema(
            description =
                "Fragmento del comprobante, el motivo o la referencia, sin distinguir acentos ni"
                    + " mayúsculas. En la lista de administración, también el nombre, usuario o"
                    + " correo de la persona; nunca el documento.")
        String q) {

  public ListPointsMovementsRequest {
    type = mayusculas(type);
    status = mayusculas(status);
    sign = mayusculas(sign);
    q = q == null || q.isBlank() ? null : q.trim();
  }

  private static String mayusculas(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(Locale.ROOT);
  }
}
