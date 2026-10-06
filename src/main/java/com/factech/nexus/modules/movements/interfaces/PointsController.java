package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.PointsAdjustmentResponse;
import com.factech.nexus.modules.movements.application.PointsPurchaseResponse;
import com.factech.nexus.modules.movements.application.PointsRateResponse;
import com.factech.nexus.modules.movements.application.PointsReceiptInfo;
import com.factech.nexus.modules.movements.application.PointsRequests;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.models.PointsReceipt;
import com.factech.nexus.modules.movements.domain.service.AttachPointsReceiptService;
import com.factech.nexus.modules.movements.domain.service.PointsAdjustmentService;
import com.factech.nexus.modules.movements.domain.service.PointsPurchaseService;
import com.factech.nexus.modules.movements.domain.service.PointsRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * La etapa 3 de `MV` (`requirements/mv.md` §4.4): las tasas de puntos y la compra de puntos. Bajo
 * {@code /movements} porque la compra <b>es un movimiento</b>, y la tasa solo existe para ella.
 */
@Tag(
    name = "Puntos",
    description =
        "A cuánto se venden los puntos en cada moneda, su compra y su ajuste a mano. Confirmar o"
            + " rechazar el pago de una compra es `POST /api/v1/movements/payments/{paymentId}/…`,"
            + " como el de una venta. Pagar con puntos no tiene ruta propia: es el método `POINTS`"
            + " en las compras.")
@RestController
@RequestMapping("/api/v1/movements")
public class PointsController {

  private final PointsRateService tasas;
  private final PointsPurchaseService compras;
  private final PointsAdjustmentService ajustes;
  private final AttachPointsReceiptService comprobantes;

  public PointsController(
      PointsRateService tasas,
      PointsPurchaseService compras,
      PointsAdjustmentService ajustes,
      AttachPointsReceiptService comprobantes) {
    this.tasas = tasas;
    this.compras = compras;
    this.ajustes = ajustes;
    this.comprobantes = comprobantes;
  }

  @PostMapping("/points-adjustments")
  @PreAuthorize("hasAuthority('movements:adjust-points')")
  @Operation(
      summary = "Ajustar los puntos de una persona",
      description =
          """
          Suma o resta **puntos** a mano en la cuenta de puntos de una persona, en una moneda
          (`RF-MV-052`, `RN-MV-076`): para lo que se pagó **por fuera de la plataforma** —una
          consignación directa a la cuenta de la empresa— o para corregir un error. `points` va
          **con signo**: positivo suma, negativo resta. **No hay importe en dinero ni tasa**.
          Nace `CONFIRMADA`, sin pago, y mueve los puntos en el acto desde o hacia la cuenta de
          puntos emitidos de la empresa. **Una resta nunca deja el saldo por debajo de cero**:
          si no alcanza, `422` y nada cambia. **El motivo (`concept`) y la cabecera
          `Idempotency-Key` son obligatorios**; `reference`, el comprobante, es opcional. La
          misma petición repetida responde `200` con el ajuste ya hecho. No se revierte: se
          compensa con otro ajuste. No comisiona.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Ajustado."),
    @ApiResponse(responseCode = "200", description = "La misma petición repetida."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos ausentes o malformados —puntos en cero o con más de dos decimales, motivo"
                + " vacío, referencia en blanco o larga—, o clave ausente o malformada",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:adjust-points` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "La clave es de otra petición (`EX-005`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description =
            "La persona o la moneda no existen (`EX-002`), o la resta es mayor que los puntos que"
                + " tiene (`EX-006`, con los disponibles en el mensaje)",
        content = @Content)
  })
  public ResponseEntity<PointsAdjustmentResponse> ajustarPuntos(
      @RequestBody(required = false) PointsRequests.Adjustment peticion,
      @Parameter(in = ParameterIn.HEADER, required = true, description = "Una por ajuste.")
          @RequestHeader(value = IdempotencyKey.CABECERA, required = false)
          String clave) {
    return ajustado(ajustes.adjust(peticion, clave));
  }

  @PostMapping(value = "/points-adjustments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('movements:adjust-points')")
  @Operation(
      summary = "Ajustar los puntos de una persona, con el comprobante",
      description =
          """
          **La misma operación que el `POST` JSON**, con el comprobante adjunto en la misma
          petición (`RF-MV-057`, `RN-MV-077`): `multipart/form-data` con una parte
          `adjustment` —el JSON de siempre, con `Content-Type: application/json`— y una parte
          `file` —un **PDF, PNG o JPG de hasta 5 MB**, reconocido **por su contenido**, nunca
          por el nombre ni por el tipo de la parte—. **Todo o nada**: un archivo vacío
          (`VAL-002`), de otro tipo (`VAL-003`) o mayor de 5 MB (`VAL-004`) **no crea el
          ajuste**, y una resta que no alcanza no guarda el archivo. **La repetición compara
          también el archivo**: la misma clave con el mismo archivo responde `200` con el ajuste
          hecho; con otro, o con archivo donde no lo había, `409`. La respuesta trae
          `receipt`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Ajustado, con el comprobante."),
    @ApiResponse(responseCode = "200", description = "La misma petición repetida."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Los de la petición JSON, o el archivo vacío (`VAL-002`), de otro tipo (`VAL-003`)"
                + " o mayor de 5 MB (`VAL-004`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:adjust-points` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "La clave es de otra petición, también por el archivo (`EX-005`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "Los de la petición JSON (`EX-002`, `EX-006`)",
        content = @Content)
  })
  public ResponseEntity<PointsAdjustmentResponse> ajustarPuntosConComprobante(
      @RequestPart(value = "adjustment", required = false) PointsRequests.Adjustment peticion,
      @RequestPart(value = "file", required = false) MultipartFile file,
      @Parameter(in = ParameterIn.HEADER, required = true, description = "Una por ajuste.")
          @RequestHeader(value = IdempotencyKey.CABECERA, required = false)
          String clave) {
    // El archivo se valida antes de nada: inválido, ni se mira la base.
    PointsReceipt comprobante =
        file == null ? null : PointsReceipt.de(file.getOriginalFilename(), bytesDe(file));
    return ajustado(ajustes.adjust(peticion, clave, comprobante));
  }

  @PutMapping(
      value = "/points-adjustments/{id}/receipt",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('movements:attach-points-receipt')")
  @Operation(
      summary = "Adjuntar o reemplazar el comprobante de un ajuste",
      description =
          """
          Adjunta el comprobante a un ajuste de puntos ya hecho, **o reemplaza el que tenía**
          (`RF-MV-057`, `RN-MV-077`): `multipart/form-data` con una parte `file`, un **PDF,
          PNG o JPG de hasta 5 MB**, reconocido **por su contenido**. **El anterior no se
          conserva**; la auditoría guarda su nombre, tipo, tamaño y resumen. Responde `200` con
          los datos del comprobante, haya o no uno anterior. Sobre una compra de puntos o un
          movimiento que no existe, `404`. No mueve puntos.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El comprobante guardado."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Sin archivo (`VAL-001`), vacío (`VAL-002`), de otro tipo (`VAL-003`) o mayor de 5 MB"
                + " (`VAL-004`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:attach-points-receipt` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un ajuste de puntos con ese identificador (`EX-002`)",
        content = @Content)
  })
  public PointsReceiptInfo adjuntarComprobante(
      @PathVariable UUID id, @RequestPart(value = "file", required = false) MultipartFile file) {
    return comprobantes.attach(id, file == null ? null : file.getOriginalFilename(), bytesDe(file));
  }

  private static ResponseEntity<PointsAdjustmentResponse> ajustado(
      PointsAdjustmentService.AdjustmentResult hecho) {
    if (!hecho.created()) {
      return ResponseEntity.ok(hecho.adjustment());
    }
    return ResponseEntity.created(URI.create("/api/v1/movements/" + hecho.adjustment().id()))
        .body(hecho.adjustment());
  }

  private static byte[] bytesDe(MultipartFile file) {
    if (file == null) {
      return null;
    }
    try {
      return file.getBytes();
    } catch (IOException fallo) {
      throw new UncheckedIOException("No se pudo leer el comprobante de la petición.", fallo);
    }
  }

  @PostMapping("/points-rates")
  @PreAuthorize("hasAuthority('movements:set-points-rate')")
  @Operation(
      summary = "Fijar la tasa de puntos de una moneda",
      description =
          """
          Fija cuántos puntos da **una unidad** de una moneda —`100` es «1 = 100 puntos»—, con
          hasta cuatro decimales (`RF-MV-025`). **Rige desde ese instante**, y la anterior **no
          se toca**: queda en el histórico, porque explica las compras que se hicieron con ella
          (`RN-MV-050`). Fijar la que ya rige responde `200` con ella y no escribe nada. Solo en
          monedas activas.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tasa nueva, vigente desde ahora."),
    @ApiResponse(responseCode = "200", description = "Ya era la vigente: nada cambió."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Moneda ausente, o valor ausente, no positivo, con más de cuatro decimales o más de"
                + " ocho cifras enteras. Los errores salen juntos",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:set-points-rate` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "La moneda está inactiva (`EX-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La moneda no existe (`EX-002`)",
        content = @Content)
  })
  public ResponseEntity<PointsRateResponse> fijarTasa(
      @RequestBody(required = false) PointsRequests.SetRate peticion) {
    PointsRateService.SetResult hecho = tasas.set(peticion);
    return hecho.created()
        ? ResponseEntity.created(URI.create("/api/v1/movements/points-rates")).body(hecho.rate())
        : ResponseEntity.ok(hecho.rate());
  }

  @GetMapping("/points-rates")
  @PreAuthorize("hasAuthority('movements:read-points-rates')")
  @Operation(
      summary = "Consultar las tasas de puntos vigentes",
      description =
          """
          La tasa que rige hoy en cada moneda **activa** que venda puntos, ordenadas por código
          de moneda y sin paginar (`RF-MV-026`). El histórico no se publica. Con ella se calcula
          cuántos puntos da una compra y cuántos cuesta pagar con ellos; el cálculo definitivo
          lo hace la operación, con la tasa de ese momento.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Las vigentes; vacía si ninguna."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:read-points-rates` (`AUTH-002`)",
        content = @Content)
  })
  public List<PointsRateResponse> tasasVigentes() {
    return tasas.current();
  }

  @PostMapping("/mine/points-purchases")
  @PreAuthorize("hasAuthority('movements:buy-points')")
  @Operation(
      summary = "Comprar puntos",
      description =
          """
          Compra puntos para quien tiene la sesión, pagando un **importe** en una moneda con un
          método de pago (`RF-MV-027`). **La compra nace `PENDIENTE` y no abona nada**: los
          puntos llegan cuando administración confirme que el pago entró (`RF-MV-028`). Lo que
          sí queda fijado desde ya es **cuántos**: `amount × tasa vigente`, redondeado hacia
          abajo, a la tasa de este momento aunque cambie después (`RN-MV-051`). No se paga con
          `POINTS` ni con un método interno. La cabecera `Idempotency-Key` es obligatoria: la
          misma petición repetida responde `200` con la compra ya registrada. No comisiona.

          **Con tarjeta (`CREDIT_CARD`), desde el 01-10-2026, abre el cobro en la pasarela** en el mismo
          acto (`RF-MV-040`): la respuesta trae `cardCharge.clientSecret`, con el que la app pide la
          tarjeta con Stripe Elements. **Nada queda confirmado**: lo confirma la notificación de la
          pasarela. Si la pasarela no responde, `503` y no se registra nada; por debajo del mínimo
          (0,50 USD), `422`. Con la pasarela apagada, el pago queda pendiente sin cobro, como antes.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Compra registrada, pendiente."),
    @ApiResponse(responseCode = "200", description = "La misma petición repetida."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos ausentes o malformados, clave ausente o malformada, o un importe que no da"
                + " ningún punto",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:buy-points` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Moneda inactiva o sin tasa (`EX-003`); método desactivado, interno o `POINTS`"
                + " (`EX-005`); cuenta que todavía no opera (`EX-006`); o la clave es de otra"
                + " petición (`EX-008`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La moneda (`EX-002`) o el método (`EX-004`) no existen",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description =
            "La pasarela de pago no respondió; nada se escribió y se puede reintentar"
                + " (`RN-MV-057`)",
        content = @Content)
  })
  public ResponseEntity<PointsPurchaseResponse> comprarPuntos(
      @RequestBody(required = false) PointsRequests.Purchase peticion,
      @Parameter(in = ParameterIn.HEADER, required = true, description = "Una por compra.")
          @RequestHeader(value = IdempotencyKey.CABECERA, required = false)
          String clave) {
    PointsPurchaseService.BuyResult hecho = compras.buy(peticion, clave);
    if (!hecho.created()) {
      return ResponseEntity.ok(hecho.purchase());
    }
    return ResponseEntity.created(URI.create("/api/v1/movements/" + hecho.purchase().id()))
        .body(hecho.purchase());
  }
}
