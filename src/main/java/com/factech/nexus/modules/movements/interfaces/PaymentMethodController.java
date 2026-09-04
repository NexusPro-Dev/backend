package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.PaymentMethodCatalogResponse;
import com.factech.nexus.modules.movements.domain.service.ListPaymentMethodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El catálogo de métodos de pago (`RF-MV-009`).
 *
 * <p><b>Recurso propio y no un método más en {@link MovementController}</b>: los métodos de pago no
 * cuelgan de una venta y no se leen desde una. Colgarlos de {@code /movements} obligaría a inventar
 * {@code /movements/payment-methods}, que dice que un método de pago es parte de un movimiento — y
 * es al revés. Es además lo que `requirements/mv.md` §2 ya decía al separar «Medios de pago» como
 * submódulo propio.
 */
@Tag(
    name = "Métodos de pago",
    description = "Con qué se puede pagar, y en qué países no vale cada medio.")
@RestController
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {

  private final ListPaymentMethodsService catalogo;

  public PaymentMethodController(ListPaymentMethodsService catalogo) {
    this.catalogo = catalogo;
  }

  @Operation(
      summary = "Consultar los métodos de pago",
      description =
          """
          Devuelve los métodos de pago **activos**, sin paginar y ordenados por código.

          **`excludedCountries` dice dónde NO vale cada medio.** Una lista vacía
          significa que vale en todos los países, y el campo **viaja siempre**:
          no hay que distinguir «sin exclusiones» de «no vino el campo».

          **Esta restricción es informativa y el servidor NO la aplica.** Registrar
          una venta con un método excluido en algún país **se registra con
          normalidad**: quien decide qué ofrecer es el cliente que consume esta
          respuesta. El servidor no sabe de qué país es quien compra — ninguna
          persona del sistema tiene país todavía.

          **No admite parámetros**, y en particular no admite un país: filtrar aquí
          haría creer que el servidor sabe cuál corresponde.

          **No exige permiso**, solo estar autenticado. `movements:read` gobierna ver
          ventas y hoy está reservado al superadministrador; exigirlo aquí dejaría sin
          métodos de pago a la pantalla de compra propia, que no pide ningún permiso.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El catálogo de métodos de pago activos."),
    @ApiResponse(
        responseCode = "401",
        description = "Sin token.",
        content = @io.swagger.v3.oas.annotations.media.Content())
  })
  @GetMapping
  public PaymentMethodCatalogResponse consultar() {
    return catalogo.list();
  }
}
