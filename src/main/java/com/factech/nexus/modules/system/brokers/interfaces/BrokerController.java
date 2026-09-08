package com.factech.nexus.modules.system.brokers.interfaces;

import com.factech.nexus.modules.system.brokers.application.BrokerCatalogResponse;
import com.factech.nexus.modules.system.brokers.application.ListBrokersRequest;
import com.factech.nexus.modules.system.brokers.domain.service.ListBrokersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de brokers (`RF-SP-052`). */
@RestController
@RequestMapping("/api/v1/brokers")
@Tag(
    name = "Brokers",
    description =
        "Catálogo de los brokers con los que opera la plataforma. Solo lectura: se puebla por"
            + " migración (RN-SP-039).")
public class BrokerController {

  private final ListBrokersService catalogo;

  public BrokerController(ListBrokersService catalogo) {
    this.catalogo = catalogo;
  }

  @GetMapping
  // SIN @PreAuthorize desde el 08-09-2026: el catálogo es PÚBLICO (ver
  // `SecurityConfig.CATALOGOS_PUBLICOS`).
  @Operation(
      summary = "Consultar el catálogo de brokers",
      description =
          """
          Devuelve los brokers con los que opera la plataforma, **ordenados por
          nombre**.

          **Un broker guarda de momento solo su nombre**, y eso tiene una
          consecuencia que conviene conocer antes de integrarse: **el nombre es la
          clave de negocio**, no un texto descriptivo. No hay un `code` estable
          frente a un cambio de nombre comercial — quien necesite referenciar un
          broker debe hacerlo por su **identificador**.

          **No se administra por API** (`RN-SP-039`): no hay alta, ni edición, ni
          eliminación, ni cambio de estado. Se puebla por migración, igual que los
          catálogos de monedas y de tipos de documento, y cada alta queda en el
          historial del repositorio en lugar de en una fila que alguien insertó.

          **Puede responder la colección vacía**, y eso no es un fallo: es el
          estado de un catálogo que todavía no se ha sembrado.

          **No se pagina**: son unos pocos elementos y partirlos obligaría a dos
          llamadas para pintar un desplegable.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Catálogo, ordenado por nombre",
        content = @Content(schema = @Schema(implementation = BrokerCatalogResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "El actor no posee `brokers:read`",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public BrokerCatalogResponse listar(@ModelAttribute ListBrokersRequest filtros) {
    return catalogo.list(filtros);
  }
}
