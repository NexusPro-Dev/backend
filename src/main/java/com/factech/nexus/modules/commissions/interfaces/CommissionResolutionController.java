package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.EffectiveCommissionResponse;
import com.factech.nexus.modules.commissions.domain.service.ResolveCommissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La resolución de la comisión efectiva (`RF-CM-005`).
 *
 * <p><b>Controlador aparte y no un método más del de tarifas</b>, porque es otro recurso: no
 * devuelve una tarifa del catálogo sino una respuesta calculada. Es el mismo corte que separó la
 * oferta propia del catálogo en `PM`.
 */
@Tag(
    name = "Comisiones",
    description = "Tarifas de comisión: cuánto gana un rol vendedor, por producto y por persona.")
@RestController
@RequestMapping("/api/v1/commissions")
public class CommissionResolutionController {

  private final ResolveCommissionService resolucion;

  public CommissionResolutionController(ResolveCommissionService resolucion) {
    this.resolucion = resolucion;
  }

  @Operation(
      summary = "Consultar la comisión efectiva",
      description =
          """
          Responde **cuánto le corresponde a una persona por vender un producto un
          día concreto**, y **por qué**: devuelve la tarifa que ganó y en qué grado
          estaba declarada.

          **Tres desenlaces, y ninguno es un error:**

          - `RESUELTA`: hay tarifa. El porcentaje puede ser **cero**, y eso significa
            «no comisiona» — es una decisión declarada.
          - `SIN_TARIFA`: la persona vende y **nadie declaró** tarifa aplicable. El
            porcentaje llega **nulo y presente**, nunca cero.
          - `NO_COMISIONA`: la persona **no porta ningún rol vendedor**.

          **Nulo y cero no son lo mismo**, y quien consuma esto va a pagar con esa
          cifra.

          Sin `onDate`, se resuelve con la fecha de hoy. Un producto **retirado** se
          resuelve con normalidad: preguntar qué se pagaba por algo que ya no se
          vende es legítimo.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Cualquiera de los tres desenlaces"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "422", description = "La persona o el producto no existen")
  })
  @GetMapping("/effective")
  @PreAuthorize("hasAuthority('commissions:read')")
  public EffectiveCommissionResponse efectiva(
      @RequestParam UUID userId,
      @RequestParam UUID productId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate onDate) {
    return resolucion.resolve(userId, productId, onDate);
  }
}
