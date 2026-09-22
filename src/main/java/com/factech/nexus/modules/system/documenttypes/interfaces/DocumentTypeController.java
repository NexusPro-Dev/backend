package com.factech.nexus.modules.system.documenttypes.interfaces;

import com.factech.nexus.modules.system.documenttypes.application.DocumentTypeCatalogResponse;
import com.factech.nexus.modules.system.documenttypes.application.ListDocumentTypesRequest;
import com.factech.nexus.modules.system.documenttypes.domain.service.ListDocumentTypesService;
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

/**
 * Catálogo de tipos de documento (`RF-SP-051`).
 *
 * <p><b>Un solo método, y la ausencia de los demás es la implementación</b> (`CA-SP-585`). Es el
 * mismo criterio con el que `CurrencyController` no expone alta ni eliminación, llevado un paso más
 * allá: allí quedaba el cambio de estado, y aquí <b>no queda nada</b>.
 *
 * <p><b>Y el motivo no es simetría con `RN-SP-010`, es una necesidad.</b> Las monedas quedan fuera
 * de la API porque son un catálogo estable que nadie edita; este queda fuera porque <b>su contenido
 * es la validación de mayoría de edad</b> (`RN-SP-035`, `RN-SP-036`). El catálogo solo lleva
 * documentos de adulto, de modo que un {@code POST} aquí dejaría que cualquiera con el permiso
 * añadiera «Tarjeta de Identidad» y <b>la validación desaparecería sin cambiar ninguna regla, sin
 * migración y sin que nadie lo notara</b>.
 *
 * <p>Lo que deja de admitirse se retira con {@code is_active} en una migración revisada. Que eso
 * cueste un despliegue es el precio correcto para una decisión que cambia quién puede entrar al
 * sistema.
 *
 * <p><b>Una prueba de arquitectura comprueba que esta clase no declara ningún verbo de
 * escritura</b>, porque «no existe la operación» no se demuestra llamándola —no hay a qué llamar—
 * sino comprobando que no está.
 */
@RestController
@RequestMapping("/api/v1/document-types")
@Tag(
    name = "Tipos de documento",
    description =
        "Catálogo de documentos de identidad admitidos. Solo lectura: su contenido es la"
            + " validación de mayoría de edad (RN-SP-036).")
public class DocumentTypeController {

  private final ListDocumentTypesService catalogo;

  public DocumentTypeController(ListDocumentTypesService catalogo) {
    this.catalogo = catalogo;
  }

  @GetMapping
  // SIN @PreAuthorize desde el 08-09-2026: el catálogo es PÚBLICO (ver
  // `SecurityConfig.CATALOGOS_PUBLICOS`).
  @Operation(
      summary = "Consultar el catálogo de tipos de documento",
      description =
          """
          Devuelve los documentos de identidad con los que una persona puede
          registrarse, con su **nombre** y su **abreviación**.

          **El catálogo contiene solo documentos de persona mayor de edad**, y esa
          es la validación entera: no hay ninguna marca que distinga cuáles sí y
          cuáles no, porque los que no están **no están**. Registrar a un menor no
          se rechaza — no se puede expresar.

          **No se administra por API** (`RN-SP-036`): no hay alta, ni edición, ni
          eliminación, ni cambio de estado. Se puebla por migración, y lo que deja
          de admitirse se retira también por migración. Es la asimetría deliberada
          con los catálogos de países y monedas, que sí tienen operación para
          cambiar su estado.

          **No se pagina**: son unos pocos elementos, y partirlos obligaría a dos
          llamadas para pintar un desplegable.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Catálogo completo, ordenado por nombre",
        content = @Content(schema = @Schema(implementation = DocumentTypeCatalogResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public DocumentTypeCatalogResponse listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute
          ListDocumentTypesRequest filtros) {
    return catalogo.list(filtros);
  }
}
