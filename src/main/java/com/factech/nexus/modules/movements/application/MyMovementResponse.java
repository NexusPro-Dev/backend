package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una fila del listado de movimientos propios (`RF-MV-008`).
 *
 * <p><b>No lleva las líneas del movimiento</b>, y no por ahorrar: una venta puede llevar varias, de
 * modo que incluirlas multiplicaría la respuesta por un dato que solo se mira al abrir uno. Van en
 * el detalle, que devuelve un {@code SaleResponse} igual al de `RF-MV-001`.
 *
 * <p><b>Trae al sujeto y a los vendedores, y no «la contraparte»</b>. Calcular quién es el otro
 * obligaría a decidir qué devolver cuando quien pregunta es {@link MovementRole#BOTH}, y esa
 * decisión no tiene respuesta buena. Con las partes y el papel, quien pinta la pantalla elige.
 *
 * <p><b>{@code sellers} es una lista y no un objeto nulable, y es a propósito</b> (16-09-2026). El
 * vendedor es de cada línea (`RN-MV-003`) y las líneas de una venta pueden llevar vendedores
 * distintos: un objeto obligaría a elegir uno o a mentir. La lista dice la verdad con un elemento
 * hoy y con varios el día que exista el caso, <b>sin repetir</b>, y <b>vacía</b> dice «este
 * movimiento no tiene vendedor» sin que ningún consumidor tenga que interpretar un nulo. Nunca es
 * nula, y con {@code @JsonInclude(ALWAYS)} viaja también vacía.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "MyMovement", description = "Un movimiento en el que participa quien consulta.")
public record MyMovementResponse(
    UUID id,
    String code,
    String status,
    @Schema(description = "El papel de quien consulta en ESTE movimiento.") MovementRole role,
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
    OffsetDateTime occurredAt) {

  /**
   * Misma forma que {@code SaleResponse.Party}, con nombre propio para no chocar en el contrato.
   */
  @Schema(name = "MyMovementParty")
  public record Party(UUID id, String username, String name) {}

  @Schema(name = "MyMovementCurrency")
  public record Money(UUID id, String code) {}
}
