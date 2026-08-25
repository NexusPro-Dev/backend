package com.factech.nexus.modules.system.currencies.interfaces;

import com.factech.nexus.modules.system.currencies.application.ChangeCurrencyStatusRequest;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalogResponse;
import com.factech.nexus.modules.system.currencies.application.CurrencyResponse;
import com.factech.nexus.modules.system.currencies.application.ListCurrenciesRequest;
import com.factech.nexus.modules.system.currencies.domain.service.ChangeCurrencyStatusService;
import com.factech.nexus.modules.system.currencies.domain.service.ListCurrenciesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de monedas (`RF-SP-019`, `RF-SP-023`).
 *
 * <p><b>Sin alta, sin edición y sin eliminación, y la ausencia es la implementación</b>
 * (`CA-SP-131`). `RN-SP-010` hace el catálogo inmutable por API: no se cumple con código que
 * rechace, se cumple porque no hay a qué llamar. En particular, un {@code PATCH} sobre el recurso
 * completo <b>no existe y debe seguir sin existir</b> — el estado se cambia sobre el subrecurso
 * {@code /status}, de modo que la ruta del recurso completo no está mapeada para ningún método.
 *
 * <p><b>El permiso de modificación está reservado a `SUPERADMIN`</b>: {@code
 * V7__seed_system_roles.sql} lo excluye del catálogo que recibe `ADMIN`, junto con la lectura de la
 * auditoría de seguridad. El estado de una moneda condiciona todo cálculo financiero, y `spec.md`
 * de `RF-SP-023` declara un único actor.
 */
@RestController
@RequestMapping("/api/v1/currencies")
@Tag(name = "Monedas", description = "Catálogo de monedas. Inmutable por API salvo su estado.")
public class CurrencyController {

  private final ListCurrenciesService catalogo;
  private final ChangeCurrencyStatusService cambioDeEstado;

  public CurrencyController(
      ListCurrenciesService catalogo, ChangeCurrencyStatusService cambioDeEstado) {
    this.catalogo = catalogo;
    this.cambioDeEstado = cambioDeEstado;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('currencies:read')")
  @Operation(
      summary = "Consultar el catálogo de monedas",
      description =
          """
          Devuelve el catálogo completo, sin paginar, ordenado por código.

          `decimalPlaces` es el campo del que depende el redondeo de todo cálculo
          financiero: cero es un valor legítimo y el campo nunca es nulo.

          Las monedas inactivas no aparecen salvo que se pidan con
          `includeInactive=true`, que **añade** a las activas en lugar de
          sustituirlas.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Catálogo devuelto. Siempre es una colección, aunque tenga un solo elemento.",
        content = @Content(schema = @Schema(implementation = CurrencyCatalogResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "El parámetro de inclusión no es booleano",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso de lectura de monedas (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public CurrencyCatalogResponse consultar(@ModelAttribute ListCurrenciesRequest peticion) {
    return catalogo.list(peticion);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('currencies:update')")
  @Operation(
      summary = "Activar o desactivar una moneda",
      description =
          """
          Cambia el estado de una moneda del catálogo. Es lo **único** que la API
          puede modificar de una moneda.

          Se envía el estado destino, no una acción: pedir dos veces lo mismo deja
          el mismo estado y **no** registra un segundo evento.

          La moneda por defecto **no puede desactivarse**: los importes del sistema
          quedarían sin referencia válida. Cambiar cuál es la moneda por defecto es
          una operación de migración, no de API.

          El cuerpo no admite ningún otro campo ni motivo alguno.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "Estado aplicado. Devuelve la moneda completa, con su definición sin cambios.",
        content = @Content(schema = @Schema(implementation = CurrencyResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador o cuerpo inválidos, o campo no admitido (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso de modificación de monedas (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe moneda con ese identificador (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Se intenta desactivar la moneda por defecto (`RN-SP-010`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public CurrencyResponse cambiarEstado(
      @PathVariable UUID id, @Valid @RequestBody ChangeCurrencyStatusRequest peticion) {
    return cambioDeEstado.changeStatus(id, peticion.isActive());
  }
}
