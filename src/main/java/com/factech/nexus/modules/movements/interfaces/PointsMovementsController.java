package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.ListPointsMovementsRequest;
import com.factech.nexus.modules.movements.application.PointsMovementDetail;
import com.factech.nexus.modules.movements.application.PointsMovementItem;
import com.factech.nexus.modules.movements.application.PointsReceiptFile;
import com.factech.nexus.modules.movements.domain.service.PointsMovementReader;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los movimientos de puntos —compras y ajustes— en una sola consulta (`RF-MV-055`, el alcance
 * propio; `RF-MV-056`, el de administración), con su detalle y el comprobante de cada ajuste.
 * Sustituyen a {@code GET /mine/points-purchases} y {@code GET /points-adjustments}.
 */
@Tag(name = "Puntos")
@RestController
@RequestMapping("/api/v1/movements")
public class PointsMovementsController {

  private final PointsMovementReader lector;

  public PointsMovementsController(PointsMovementReader lector) {
    this.lector = lector;
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-055` — lo propio
  // ---------------------------------------------------------------------------

  @GetMapping("/mine/points-movements")
  @PreAuthorize("hasAuthority('movements:list-own-points-movements')")
  @Operation(
      summary = "Consultar mis movimientos de puntos",
      description =
          """
          **Mis compras de puntos y los ajustes que administración me hizo**, en una sola lista
          paginada y **los más recientes primero** (`RF-MV-055`). Cada fila dice su `type`
          —`COMPRA_PUNTOS` o `AJUSTE_PUNTOS`— y lo que no aplica va nulo: una compra trae
          `amount` y no `concept`; un ajuste trae `concept`, `reference` y `hasReceipt`, y no
          `amount`. **Los puntos van con su signo**: la compra suma siempre —pendiente, los que
          dará—; el ajuste suma o resta. **`adjustedBy` va siempre nulo aquí**: quién hizo el
          ajuste lo ve administración.

          **Filtros**, combinables: `type`, `status` (`PENDIENTE`, `CONFIRMADA`, `RECHAZADA`),
          `currencyId`, `from` (inclusive) y `to` (exclusive), `sign` —`SUMA` o `RESTA`— y `q`,
          un fragmento del comprobante, el motivo o la referencia, sin distinguir acentos ni
          mayúsculas. **`sort`**: `occurredAt` (por omisión, `desc`), `points` o `code`, con
          `,asc` o `,desc`. **Sustituye a `GET /mine/points-purchases`**, retirada el 06-10-2026.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Una página; vacía si no hay ninguno."),
    @ApiResponse(
        responseCode = "400",
        description = "Página, orden, tipo, estado, sentido o periodo inválidos, todos juntos",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:list-own-points-movements` (`AUTH-002`)",
        content = @Content)
  })
  public PageResponse<PointsMovementItem> misMovimientos(
      @ParameterObject @ModelAttribute ListPointsMovementsRequest filtros) {
    return lector.listMine(filtros);
  }

  @GetMapping("/mine/points-movements/{id}")
  @PreAuthorize("hasAuthority('movements:read-own-points-movement')")
  @Operation(
      summary = "Consultar el detalle de un movimiento de puntos propio",
      description =
          """
          Una compra de puntos o un ajuste **propio** (`RF-MV-055`): la fila de la lista
          (`movement`), y además, en una compra, **la tasa** con que se compró, **sus pagos** y,
          si se rechazó, **el motivo**; en un ajuste, **los datos del comprobante** —nombre,
          tipo, tamaño y resumen—, sin el archivo, que se descarga aparte. **Uno de otra persona
          responde `404`, igual que uno que no existe.**
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El detalle."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:read-own-points-movement` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, no es de puntos o es de otra persona (`EX-002`)",
        content = @Content)
  })
  public PointsMovementDetail miMovimiento(@PathVariable UUID id) {
    return lector.detailMine(id);
  }

  @GetMapping("/mine/points-movements/{id}/receipt")
  @PreAuthorize("hasAuthority('movements:download-own-points-receipt')")
  @Operation(
      summary = "Descargar el comprobante de un ajuste propio",
      description =
          """
          **El archivo tal como se subió** —PDF, PNG o JPG— con el tipo con que se guardó,
          **siempre como adjunto** y con `X-Content-Type-Options: nosniff` (`RF-MV-055`,
          `RN-MV-077`).
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El archivo.",
        content = {
          @Content(
              mediaType = "application/pdf",
              schema = @Schema(type = "string", format = "binary")),
          @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
          @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary"))
        }),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:download-own-points-receipt` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description =
            "No existe, no es de puntos o es de otra persona (`EX-002`), o no tiene comprobante"
                + " (`EX-003`)",
        content = @Content)
  })
  public ResponseEntity<byte[]> miComprobante(@PathVariable UUID id) {
    return descarga(lector.receiptMine(id));
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-056` — administración
  // ---------------------------------------------------------------------------

  @GetMapping("/points-movements")
  @PreAuthorize("hasAuthority('movements:list-points-movements')")
  @Operation(
      summary = "Consultar los movimientos de puntos",
      description =
          """
          **Las compras de puntos y los ajustes de todas las personas**, en una sola lista
          paginada y **los más recientes primero** (`RF-MV-056`), con la fila de la lista
          propia más **quién hizo cada ajuste** (`adjustedBy`, nulo en una compra y en los
          ajustes anteriores a que se guardara). La persona sale con nombre, usuario y correo,
          **sin documento**.

          **Filtros**: los de la lista propia, más `userId`; y `q` busca además en el nombre,
          usuario o correo de la persona. **Sustituye a `GET /points-adjustments`**, retirada el
          06-10-2026; ajustar sigue siendo `POST /points-adjustments`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Una página; vacía si no hay ninguno."),
    @ApiResponse(
        responseCode = "400",
        description = "Página, orden, tipo, estado, sentido o periodo inválidos, todos juntos",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:list-points-movements` (`AUTH-002`)",
        content = @Content)
  })
  public PageResponse<PointsMovementItem> movimientos(
      @ParameterObject @ModelAttribute ListPointsMovementsRequest filtros,
      @RequestParam(required = false) UUID userId) {
    return lector.list(filtros, userId);
  }

  @GetMapping("/points-movements/{id}")
  @PreAuthorize("hasAuthority('movements:read-points-movement')")
  @Operation(
      summary = "Consultar el detalle de un movimiento de puntos",
      description =
          """
          Cualquier compra de puntos o ajuste (`RF-MV-056`), con lo del detalle propio y **quién
          hizo el ajuste**.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El detalle."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:read-points-movement` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe o no es de puntos (`EX-002`)",
        content = @Content)
  })
  public PointsMovementDetail movimiento(@PathVariable UUID id) {
    return lector.detail(id);
  }

  @GetMapping("/points-movements/{id}/receipt")
  @PreAuthorize("hasAuthority('movements:download-points-receipt')")
  @Operation(
      summary = "Descargar el comprobante de un ajuste",
      description =
          """
          El archivo del comprobante de cualquier ajuste, como en la descarga propia
          (`RF-MV-056`, `RN-MV-077`).
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El archivo.",
        content = {
          @Content(
              mediaType = "application/pdf",
              schema = @Schema(type = "string", format = "binary")),
          @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")),
          @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary"))
        }),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:download-points-receipt` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe o no es de puntos (`EX-002`), o no tiene comprobante (`EX-003`)",
        content = @Content)
  })
  public ResponseEntity<byte[]> comprobante(@PathVariable UUID id) {
    return descarga(lector.receipt(id));
  }

  /** Siempre como adjunto, sin que el navegador adivine el tipo ni lo guarde en caché. */
  private static ResponseEntity<byte[]> descarga(PointsReceiptFile archivo) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(archivo.contentType()))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(archivo.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(archivo.content());
  }
}
