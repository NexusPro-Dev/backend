package com.factech.nexus.modules.movements.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila del listado de movimientos propios (`RF-MV-008`).
 *
 * <p><b>No lleva las líneas del movimiento</b>, y no por ahorrar: una venta puede llevar varias, de
 * modo que incluirlas multiplicaría la respuesta por un dato que solo se mira al abrir uno. Van en
 * el detalle, que devuelve un {@code SaleResponse} igual al de `RF-MV-001`.
 *
 * <p><b>Trae las DOS partes y no «la contraparte»</b>. Calcular quién es el otro obligaría a
 * decidir qué devolver cuando quien pregunta es {@link MovementRole#BOTH}, y esa decisión no tiene
 * respuesta buena. Con las dos partes y el papel, quien pinta la pantalla elige.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "MyMovement", description = "Un movimiento en el que participa quien consulta.")
public record MyMovementResponse(
    UUID id,
    String code,
    String status,
    @Schema(description = "El papel de quien consulta en ESTE movimiento.") MovementRole role,
    Party client,
    // LA NULABILIDAD SE DECLARA A MANO, igual que en `SaleResponse` y por lo
    // mismo: springdoc no la deduce de un `record`, y sin esto el contrato NO
    // DIRÍA que un movimiento puede no tener vendedor.
    //
    // Y con `types` y no con `nullable`, que es lo que uno escribe primero:
    // este contrato se publica como OPENAPI 3.1, donde `nullable` dejó de ser
    // palabra clave y springdoc LA DESCARTA EN SILENCIO — la anotación se
    // aplica, el contrato sale igual y nada avisa.
    @Schema(
            types = {"object", "null"},
            description =
                "A quién se atribuye. NULO cuando quien compró no cuelga de ningún vendedor.")
        Party seller,
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
