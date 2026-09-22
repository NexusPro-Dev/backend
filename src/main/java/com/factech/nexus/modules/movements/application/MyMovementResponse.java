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
 * <p><b>Desde el 22-09-2026 esta fila es SIEMPRE una compra</b> (`RF-MV-008` · `spec.md` §2), por
 * decisión del responsable del proyecto: el listado propio trae solo lo comprado, y lo vendido se
 * consulta por `RF-MV-015`. Con la mitad de vendedor se fue <b>el papel</b>: {@code role} — {@code
 * BUYER}, {@code SELLER}, {@code BOTH}— valdría siempre lo mismo, y un campo constante miente por
 * omisión (el argumento de `RF-MV-006`). Es un cambio rompedor, declarado en `api/index.md`.
 *
 * <p><b>Trae al sujeto y a los vendedores, y no «la contraparte»</b>. El sujeto es quien pregunta;
 * los vendedores, quienes le vendieron cada línea.
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
    // DESDE EL 21-09-2026, y a la vez que el filtro `type`: filtrar por lo que
    // la fila no dice sería una respuesta que quien la lee no puede comprobar.
    // Es el mismo campo que `MovementResponse` lleva desde el 17-09-2026.
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
    OffsetDateTime occurredAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time",
            description = "Cuándo entró el dinero. NULO mientras no esté confirmado.")
        OffsetDateTime confirmedAt) {

  /**
   * Misma forma que {@code SaleResponse.Party}, con nombre propio para no chocar en el contrato.
   */
  @Schema(name = "MyMovementParty")
  public record Party(UUID id, String username, String name) {}

  @Schema(name = "MyMovementCurrency")
  public record Money(UUID id, String code) {}
}
