package com.factech.nexus.modules.system.countries.interfaces;

import com.factech.nexus.modules.system.countries.application.ChangeCountryStatusRequest;
import com.factech.nexus.modules.system.countries.application.CountryCatalogResponse;
import com.factech.nexus.modules.system.countries.application.CountryResponse;
import com.factech.nexus.modules.system.countries.application.ListCountriesRequest;
import com.factech.nexus.modules.system.countries.application.RegisterCountryRequest;
import com.factech.nexus.modules.system.countries.domain.service.ChangeCountryStatusService;
import com.factech.nexus.modules.system.countries.domain.service.ListCountriesService;
import com.factech.nexus.modules.system.countries.domain.service.RegisterCountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de países (`RF-SP-020`, `RF-SP-021`, `RF-SP-022`).
 *
 * <p><b>Sin edición y sin eliminación</b> (`CA-SP-137`). `RN-SP-009` hace el país inmutable salvo
 * su estado, y esa regla <b>no tiene código que la implemente</b>: se cumple porque los manejadores
 * no existen. La verificación tiene que ser precisa, y por eso conviene dejar escrito qué responde
 * cada cosa:
 *
 * <ul>
 *   <li>{@code PUT} / {@code PATCH} / {@code DELETE} sobre <b>un país concreto</b> → {@code 404},
 *       porque esa ruta no está mapeada para ningún método: no existe endpoint de detalle de país
 *       en ningún requerimiento del módulo.
 *   <li>Los mismos verbos sobre <b>la colección</b> → {@code 405}, porque la colección sí está
 *       mapeada, por el alta y por el listado.
 *   <li>{@code PATCH} sobre el subrecurso de estado → es la <b>única</b> excepción, y la introduce
 *       `RF-SP-022`.
 * </ul>
 *
 * <p>La distinción no es una sutileza: es la diferencia entre «este recurso no admite ese método» y
 * «esa ruta no existe», y un criterio que verifica lo que la API <b>no</b> expone tiene que afirmar
 * exactamente lo que un cliente recibe.
 */
@RestController
@RequestMapping("/api/v1/countries")
@Tag(name = "Países", description = "Catálogo de países. Inmutable salvo su estado.")
public class CountryController {

  private final RegisterCountryService alta;
  private final ListCountriesService catalogo;
  private final ChangeCountryStatusService cambioDeEstado;

  public CountryController(
      RegisterCountryService alta,
      ListCountriesService catalogo,
      ChangeCountryStatusService cambioDeEstado) {
    this.alta = alta;
    this.catalogo = catalogo;
    this.cambioDeEstado = cambioDeEstado;
  }

  /**
   * Registra un país.
   *
   * <p><b>Sin cabecera {@code Location}, y es una decisión y no un olvido.</b> Esa cabecera existe
   * para llevar al cliente al recurso creado, y aquí apuntaría a una URL que <b>no resuelve</b>: no
   * hay endpoint de detalle de país en ningún requerimiento del módulo, de modo que seguirla
   * devolvería {@code 404}. Una cabecera que lleva a la nada es peor que no ponerla. El
   * identificador lo devuelve el cuerpo, que es de donde el cliente lo toma de todos modos.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('countries:create')")
  @Operation(
      summary = "Registrar un país",
      description =
          """
          Registra un país en el catálogo. Queda **activo** siempre: el estado no
          se envía y enviarlo devuelve `400`.

          El código se normaliza a mayúsculas y se recorta, porque lo fija
          ISO 3166-1 y no lo inventa nadie: `co`, ` CO` y `CO` son el mismo país.

          El nombre admite acentos y caracteres no latinos sin transformación
          alguna, pero **no puede coincidir** con otro ya registrado ignorando
          mayúsculas y acentos: el catálogo no se edita, y el duplicado sería
          permanente.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "País registrado, con su código ya normalizado.",
        content = @Content(schema = @Schema(implementation = CountryResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Código o nombre inválidos, o campo no admitido en el cuerpo",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso de creación de países (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Código o nombre ya presentes en el catálogo (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public CountryResponse registrar(@Valid @RequestBody RegisterCountryRequest peticion) {
    return alta.register(peticion.toCommand());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('countries:read')")
  @Operation(
      summary = "Consultar el catálogo de países",
      description =
          """
          Devuelve el catálogo completo, sin paginar, **ordenado alfabéticamente
          por nombre** según la intercalación del español — no por orden de bytes.

          La búsqueda actúa sobre código y nombre, ignorando mayúsculas y acentos.
          Sin coincidencias devuelve una colección vacía, no un error.

          Los países inactivos no aparecen salvo que se pidan, y entonces se
          **añaden** a los activos. La búsqueda y el estado son independientes.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Catálogo devuelto. Puede venir vacío.",
        content = @Content(schema = @Schema(implementation = CountryCatalogResponse.class))),
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
        description = "Autenticado sin el permiso de lectura de países (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public CountryCatalogResponse consultar(@ModelAttribute ListCountriesRequest peticion) {
    return catalogo.list(peticion);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('countries:update')")
  @Operation(
      summary = "Activar o desactivar un país",
      description =
          """
          Cambia el estado de un país. Es lo **único** que la API puede modificar
          de un país.

          Se envía el estado destino, no una acción: pedir dos veces lo mismo deja
          el mismo estado y **no** registra un segundo evento.

          **Desactivar no es corregir**: el código y el nombre permanecen, y los
          datos que ya los referencian siguen resolviéndolos. Evita que el error se
          propague desde ese momento, no lo repara.

          El cuerpo no admite ningún otro campo ni motivo alguno.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Estado aplicado. Devuelve el país tal como quedó.",
        content = @Content(schema = @Schema(implementation = CountryResponse.class))),
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
        description = "Autenticado sin el permiso de modificación de países (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe país con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public CountryResponse cambiarEstado(
      @PathVariable UUID id, @Valid @RequestBody ChangeCountryStatusRequest peticion) {
    return cambioDeEstado.changeStatus(id, peticion.isActive());
  }
}
