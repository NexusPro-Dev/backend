package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.DeleteCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.ListUserCommissionRatesRequest;
import com.factech.nexus.modules.commissions.application.RegisterUserCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UpdateUserCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UserCommissionRatePageResponse;
import com.factech.nexus.modules.commissions.application.UserCommissionRateResponse;
import com.factech.nexus.modules.commissions.domain.service.DeleteUserCommissionRateService;
import com.factech.nexus.modules.commissions.domain.service.ListUserCommissionRatesService;
import com.factech.nexus.modules.commissions.domain.service.RegisterUserCommissionRateService;
import com.factech.nexus.modules.commissions.domain.service.UpdateUserCommissionRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Las tasas de comisión personalizadas (`RF-CM-006`).
 *
 * <p><b>Recurso aparte y no un filtro del catálogo</b>, porque las dos piezas no se parecen: esta
 * tiene vigencia y persona, aquella tiene rol. Hasta el 01-09-2026 eran la misma operación con
 * campos opcionales, y esa fusión obligaba a un endpoint cuyas validaciones dependían de qué campo
 * había llegado.
 *
 * <p><b>Desde el 16-09-2026 la tasa nace con su producto</b> (`RN-CM-021`), como la de rol. Las
 * rutas de asociación —{@code /{id}/products}— existieron del 11-09-2026 al 16-09-2026 y ya no
 * están.
 */
@Tag(
    name = "Comisiones",
    description = "Tasas de comisión personalizadas: la excepción por persona.")
@RestController
@RequestMapping("/api/v1/user-commission-rates")
public class UserCommissionRateController {

  private final RegisterUserCommissionRateService alta;
  private final ListUserCommissionRatesService listado;
  private final UpdateUserCommissionRateService correccion;
  private final DeleteUserCommissionRateService retiro;

  public UserCommissionRateController(
      RegisterUserCommissionRateService alta,
      ListUserCommissionRatesService listado,
      UpdateUserCommissionRateService correccion,
      DeleteUserCommissionRateService retiro) {
    this.alta = alta;
    this.listado = listado;
    this.correccion = correccion;
    this.retiro = retiro;
  }

  @Operation(
      summary = "Registrar la tasa personalizada de una persona sobre un producto",
      description =
          """
          Declara que **esta persona** gana esto —un porcentaje o un importe—
          **por este producto**, desde una fecha y, opcionalmente, hasta otra.
          **`productId` es obligatorio** (`RN-CM-021`, 16-09-2026): la tasa nace
          con su producto, **rige sobre él desde su inicio de vigencia** y no
          cambia de producto — cambiar es retirarla y registrar otra. Una
          excepción que abarque varios productos son varias tasas.

          **Registrar es poner en vigor.** No hay paso de asociación ni
          borrador. Del 11-09-2026 al 16-09-2026 la tasa se creaba sin producto y
          no pagaba nada hasta asociarse; las de aquel modelo se borraron (`V10`)
          y hay que registrarlas de nuevo, una por producto.

          **Una sola vigente por persona, producto y día** (`RN-CM-006`): un
          periodo que pise a otra tasa viva de esa persona sobre ese producto
          responde `409` — también cuando las dos llegan a la vez, porque la
          regla vive en el motor. Sobre **otro** producto no hay conflicto, y las
          consecutivas son el historial.

          Y todo lo que antes se comprobaba al asociar se comprueba aquí:
          producto **vivo y no retirado**; el **tope individual** —un importe
          fijo no pasa del precio del producto, `RN-CM-019`—; la regla del
          **gratuito** —solo importe fijo, `RN-CM-020`—; y, para un importe
          fijo, **los decimales de la moneda del producto** (`RN-CM-017`).

          **No lleva rol, y eso tiene una consecuencia que conviene conocer**: la
          tasa sigue rigiendo aunque su titular pase a un rol que no comisiona.
          Hasta el 01-09-2026 el rol era obligatorio precisamente para impedirlo.

          `validFrom` es obligatorio; sin `validTo`, rige indefinidamente.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tasa registrada, y rigiendo desde su inicio"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, o el importe fijo no cabe en los decimales de la moneda del producto"
                + " (`VAL-014`)"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Esa persona ya tiene una tasa viva sobre ese producto en parte de ese periodo"
                + " (`EX-006`), el importe pasa del precio del producto (`EX-007`), o el producto"
                + " es gratuito y la tasa es de porcentaje (`EX-008`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "La persona no existe (`EX-002`), o el producto no existe (`EX-003`) o está retirado"
                + " (`EX-004`)")
  })
  @PostMapping
  @PreAuthorize("hasAuthority('commissions:create')")
  public ResponseEntity<UserCommissionRateResponse> registrar(
      @Valid @RequestBody RegisterUserCommissionRateRequest peticion) {
    UserCommissionRateResponse creada = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/user-commission-rates/" + creada.id()))
        .body(creada);
  }

  @Operation(
      summary = "Consultar las tasas personalizadas",
      description =
          """
          Devuelve las tasas **tal como se declararon**, cada una **con su
          producto** (`id`, `code`, `name`, `price` y `currency`), y no resuelve
          cuál se aplica: para eso está `GET /api/v1/commissions/effective`.

          **Incluye el historial**: las vencidas viajan junto a la vigente salvo que
          se filtre por fecha con `onDate`. Y es **el único historial que le queda al
          módulo** — las tasas de rol perdieron la vigencia.

          **Filtrar por persona devuelve las declaradas PARA esa persona**, no la que
          le aplica hoy sobre un producto.

          **Filtrar por producto (`productId`) devuelve las de ese producto**, de
          cualquier persona: es la respuesta a «quién tiene excepción aquí». Se
          combina con los demás filtros — persona y producto juntos responden
          «¿tiene esta persona excepción en este producto?», con su historial. Un
          producto donde nadie tiene excepción devuelve la página vacía, y **no
          significa que no comisione**: significa que todos cobran por su rol.

          Hasta el 16-09-2026 cada fila traía `associatedProducts`, cuántos
          productos hacían regir la excepción; hoy trae **cuál**, porque es uno.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de tasas personalizadas"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin permiso")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('commissions:read')")
  public UserCommissionRatePageResponse listar(
      @ModelAttribute ListUserCommissionRatesRequest filtros) {
    return listado.list(filtros);
  }

  @Operation(
      summary = "Corregir una tasa personalizada",
      description =
          """
          Corrige el **valor** —la forma y la cifra— y el **fin de vigencia**. Se
          aplica lo que llega y se deja intacto lo que no.

          **Corregir no es cambiar**, y aquí la distinción sigue viva: corregir
          arregla un error y reescribe lo que esa tasa dice que rigió; cambiar lo que
          gana alguien a partir de una fecha es **cerrar la vigente y registrar
          otra**.

          `validTo: null` **vacía** el fin de vigencia y la tasa vuelve a regir
          indefinidamente; `rateType: null` se **rechaza**.

          **La persona, el producto y el inicio de vigencia no se corrigen**:
          `userId` y `validFrom` devuelven `400` con `VAL-009`, y `productId` es un
          campo desconocido que también responde `400`.

          **Todo se revisa contra el producto de la tasa** (`RN-CM-021`): alargar
          `validTo` no puede pisar a otra tasa viva de la misma persona sobre él
          (`409`, `EX-006`); un `fixedAmount` que supere su precio se rechaza con
          `409` (`EX-007`); sobre un producto **gratuito** solo cabe el importe
          fijo (`RN-CM-020`): dejarla de porcentaje se rechaza con `409`
          (`EX-008`), y el fijo entra del importe que sea; y un importe fijo tiene
          que **caber en los decimales de la moneda del producto** (`VAL-014`).
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tasa corregida"),
    @ApiResponse(responseCode = "400", description = "Datos inválidos o campos no corregibles"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "No existe, o está retirada"),
    @ApiResponse(
        responseCode = "409",
        description =
            "La vigencia resultante se solapa (`EX-006`), el valor pasaría del precio del producto"
                + " (`EX-007`), o dejaría de porcentaje la tasa de un producto gratuito (`EX-008`)")
  })
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('commissions:update')")
  public UserCommissionRateResponse corregir(
      @PathVariable UUID id, @RequestBody UpdateUserCommissionRateRequest peticion) {
    return correccion.update(id, peticion);
  }

  @Operation(
      summary = "Retirar una tasa personalizada",
      description =
          """
          Retira una tasa que **no debió existir**, con **motivo obligatorio**, y
          desde ese instante **esa persona deja de cobrar por ese producto** por
          esta tasa.

          **Retirar no es cerrar la vigencia**: se cierra lo que dejó de regir, se
          retira lo que fue un error. Por eso el retiro **no toca la vigencia** — el
          registro de eliminación debe poder decir qué periodo cubría lo retirado.

          Los días que ocupaba **quedan libres**, de modo que puede declararse otra
          tasa que los cubra. Del 11-09-2026 al 16-09-2026 una tasa asociada no
          se retiraba; sin asociación no hay condición.

          **No es idempotente**: retirar dos veces devuelve `409`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Tasa retirada"),
    @ApiResponse(responseCode = "400", description = "Motivo ausente, en blanco o demasiado largo"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "La tasa no existe"),
    @ApiResponse(responseCode = "409", description = "Ya estaba retirada")
  })
  @PostMapping("/{id}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('commissions:delete')")
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteCommissionRateRequest peticion) {
    retiro.delete(id, peticion);
  }
}
