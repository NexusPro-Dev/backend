package com.factech.nexus.modules.system.brokers.interfaces;

import com.factech.nexus.modules.system.brokers.application.BrokerCatalogResponse;
import com.factech.nexus.modules.system.brokers.application.BrokerItem;
import com.factech.nexus.modules.system.brokers.application.ListBrokersRequest;
import com.factech.nexus.modules.system.brokers.application.UpdateBrokerRequest;
import com.factech.nexus.modules.system.brokers.domain.service.ListBrokersService;
import com.factech.nexus.modules.system.brokers.domain.service.UpdateBrokerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de brokers (`RF-SP-052`). */
@RestController
@RequestMapping("/api/v1/brokers")
@Tag(
    name = "Brokers",
    description =
        "Catálogo de los brokers con los que opera la plataforma. Se puebla por migración; por"
            + " API solo se editan el enlace y el modelo (RN-SP-039, RF-SP-084).")
public class BrokerController {

  private final ListBrokersService catalogo;
  private final UpdateBrokerService edicion;

  public BrokerController(ListBrokersService catalogo, UpdateBrokerService edicion) {
    this.catalogo = catalogo;
    this.edicion = edicion;
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

          **Cada broker trae su enlace de registro** (`url`, 09-10-2026): la
          dirección del broker a la que se manda a la persona para abrir su
          cuenta. **Es nulo mientras no se haya cargado.**

          **Y su modelo** (`model`, 15-10-2026): texto libre, también nulo
          mientras no se haya cargado.

          **Un broker se identifica de momento por su nombre**, y eso tiene una
          consecuencia que conviene conocer antes de integrarse: **el nombre es la
          clave de negocio**, no un texto descriptivo. No hay un `code` estable
          frente a un cambio de nombre comercial — quien necesite referenciar un
          broker debe hacerlo por su **identificador**.

          **No se administra por API** (`RN-SP-039`): no hay alta, ni
          eliminación, ni cambio de estado ni de nombre. Se puebla por migración,
          igual que los catálogos de monedas y de tipos de documento. **Solo el
          enlace y el modelo se editan**, con `PATCH /api/v1/brokers/{id}`
          (`RF-SP-084`, 15-10-2026).

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
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public BrokerCatalogResponse listar(
      // `@ParameterObject` EXPLOTA el registro en sus parámetros de consulta. Sin
      // él, el contrato publica UNO solo llamado `filtros` y el frontend no ve
      // que existe `includeInactive`. Faltaba desde `RF-SP-052` (10-09-2026).
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListBrokersRequest filtros) {
    return catalogo.list(filtros);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('brokers:update')")
  @Operation(
      summary = "Editar el enlace y el modelo de un broker",
      description =
          """
          Cambia el **enlace de registro** (`url`) y el **modelo** (`model`) de un
          broker del catálogo (`RF-SP-084`, `RN-SP-039` enmendada). **Solo esos
          dos**: el nombre, el `advertiser` y el estado siguen cambiándose por
          migración, y enviarlos responde `400`.

          **Un campo ausente o `null` no se toca; `""` lo vacía.** Hay que
          enviar al menos uno. El enlace empieza por `http://` o `https://` y
          admite hasta 500 caracteres; el modelo, hasta 120. Los valores se
          recortan.

          Se edita también un broker **inactivo**. Enviar lo que ya tiene no
          escribe ni audita nada. Cada cambio queda **auditado** con el antes y el
          después.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Editado, o sin cambios."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Nada que cambiar, enlace o modelo inválidos, o una propiedad que no se edita"
                + " (`VAL-001`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `brokers:update` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "El broker no existe (`VAL-002`)",
        content = @Content)
  })
  public BrokerItem editar(
      @PathVariable UUID id, @RequestBody(required = false) UpdateBrokerRequest peticion) {
    return edicion.update(id, peticion);
  }
}
