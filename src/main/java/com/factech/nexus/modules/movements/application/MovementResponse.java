package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una fila del listado global de movimientos (`RF-MV-006`).
 *
 * <p><b>Es una fila nueva y no {@link MyMovementResponse} sin {@code role}.</b> Comparten sujeto,
 * vendedores, moneda e importes, y aun así son dos contratos: aquella lleva el papel de quien
 * pregunta y esta lleva el tipo y la confirmación, y <b>cambian por motivos distintos</b> — el día
 * que la fila propia gane algo que quien administra no debe ver, o al revés, no tiene que arrastrar
 * a la otra.
 *
 * <p><b>No lleva {@code role}</b>: quien administra no participa en lo que mira, y un papel que
 * valiera siempre lo mismo sería un campo que miente por omisión. <b>Y no lleva las líneas</b>, por
 * lo mismo que el listado propio: multiplican la respuesta por un dato que solo se mira al abrir
 * uno, y abrirlo es `RF-MV-007`.
 *
 * <p>{@code sellers} nunca es nula y con {@code @JsonInclude(ALWAYS)} viaja también vacía; {@code
 * confirmedAt} viaja <b>nulo y presente</b> en todo lo que no está confirmado.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(
    name = "MovementSummary",
    description = "Un movimiento del libro, visto desde administración.")
public record MovementResponse(
    UUID id,
    String code,
    @Schema(description = "El código del tipo de movimiento. Hoy, siempre `VENTA`.") String type,
    String status,
    @Schema(
            description =
                "El SUJETO del movimiento: a nombre de quién es. En una venta, quien compra.")
        Party user,
    @Schema(
            description =
                "Los vendedores de sus líneas, sin repetir. Hoy una venta lleva uno; la lista"
                    + " va VACÍA —nunca nula— en los movimientos que no tienen vendedor.")
        List<Party> sellers,
    Money currency,
    String paymentMethod,
    BigDecimal totalAmount,
    BigDecimal discountAmount,
    BigDecimal payableAmount,
    @Schema(description = "Cuándo ocurrió el hecho.") OffsetDateTime occurredAt,
    // `types` y no `nullable`: este contrato es OpenAPI 3.1, donde `nullable`
    // dejó de ser una palabra clave y springdoc la descarta EN SILENCIO.
    @Schema(
            types = {"string", "null"},
            format = "date-time",
            description =
                "Cuándo entró el dinero. Presente solo en las confirmadas; NULO en las demás.")
        OffsetDateTime confirmedAt) {

  /** Misma forma que las otras partes del módulo, con nombre propio para no chocar. */
  @Schema(name = "MovementParty")
  public record Party(UUID id, String username, String name) {}

  @Schema(name = "MovementCurrency")
  public record Money(UUID id, String code) {}
}
