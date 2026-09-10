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

  // SIN @PreAuthorize, y desde el 09-09-2026 tampoco hace falta token: la ruta
  // es PÚBLICA (`RN-MV-024`, ver `SecurityConfig.CATALOGOS_PUBLICOS`). Lo que
  // sostiene que abrirla no publique nada es el predicado de la consulta —los
  // dos ejes, `is_active` y `visibility`—, no este filtro.
  @Operation(
      summary = "Consultar los métodos de pago",
      description =
          """
          Devuelve los métodos de pago **activos y de visibilidad `PUBLICO`**, sin
          paginar y ordenados por código.

          **Es una consulta PÚBLICA** (`RN-MV-024`): responde **sin token**, porque
          el formulario de registro por enlace elige con qué se paga antes de que
          exista la cuenta. **Con sesión devuelve exactamente lo mismo** — no hay
          un catálogo para el anónimo y otro para quien ha entrado, y no existe
          parámetro que cambie lo que sale.

          **Lo que NO devuelve, y no hay forma de pedirlo**: ni los métodos
          desactivados —un método retirado no se puede usar en una venta nueva— ni
          los de visibilidad `INTERNO` (`RN-MV-023`). Estos últimos **sirven para
          pagar y no los elige ninguna persona**: los pone el sistema. Es el caso
          de `GRATIS`, con el que se anotan las ventas de importe cero
          (`RN-MV-022`): quien depure una de esas verá un `paymentMethodId` que
          **este catálogo no resuelve**, y eso es deliberado.

          **La respuesta no publica el campo de visibilidad.** Si todo lo que sale
          es `PUBLICO`, declararlo sugeriría que puede salir otra cosa.

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

          **Está acotada por origen** —120 peticiones por minuto, con cubo propio—,
          como los demás catálogos públicos: superar la cota responde `429`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "El catálogo de métodos de pago activos y públicos. Se responde igual con token y"
                + " sin él."),
    @ApiResponse(
        responseCode = "429",
        description =
            "Se superó la cota de peticiones por origen (120 por minuto). La cabecera"
                + " `Retry-After` dice cuánto esperar.",
        content = @io.swagger.v3.oas.annotations.media.Content())
  })
  @GetMapping
  public PaymentMethodCatalogResponse consultar() {
    return catalogo.list();
  }
}
