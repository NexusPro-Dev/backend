package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.AssociateUserProductRequest;
import com.factech.nexus.modules.commissions.application.DeleteCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.DissociateProductRequest;
import com.factech.nexus.modules.commissions.application.ListUserCommissionRatesRequest;
import com.factech.nexus.modules.commissions.application.RegisterUserCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UpdateUserCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UserCommissionRatePageResponse;
import com.factech.nexus.modules.commissions.application.UserCommissionRateResponse;
import com.factech.nexus.modules.commissions.application.UserRateProductsResponse;
import com.factech.nexus.modules.commissions.domain.service.AssociateUserProductService;
import com.factech.nexus.modules.commissions.domain.service.DeleteUserCommissionRateService;
import com.factech.nexus.modules.commissions.domain.service.DissociateUserProductService;
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
 * tiene vigencia y persona, aquella tiene rol y asociaciones. Hasta el 01-09-2026 eran la misma
 * operación con campos opcionales, y esa fusión obligaba a un endpoint cuyas validaciones dependían
 * de qué campo había llegado.
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
  private final AssociateUserProductService asociacion;
  private final DissociateUserProductService desasociacion;

  public UserCommissionRateController(
      RegisterUserCommissionRateService alta,
      ListUserCommissionRatesService listado,
      UpdateUserCommissionRateService correccion,
      DeleteUserCommissionRateService retiro,
      AssociateUserProductService asociacion,
      DissociateUserProductService desasociacion) {
    this.alta = alta;
    this.listado = listado;
    this.correccion = correccion;
    this.retiro = retiro;
    this.asociacion = asociacion;
    this.desasociacion = desasociacion;
  }

  @Operation(
      summary = "Registrar la tasa personalizada de una persona",
      description =
          """
          Declara que **esta persona** gana este porcentaje, y **deja la tasa
          creada sin que rija todavía en ninguna parte**.

          **Para ponerla en vigor hay que asociarla a productos**, con
          `POST /api/v1/user-commission-rates/{id}/products`. Es exactamente el
          mismo mecanismo que las tasas de rol, y desde el 11-09-2026 sin
          excepción: `RN-CM-012` dice que **ninguna tasa rige hasta que se
          asocia**, y esta era la única que se saltaba la regla — pagaba desde el
          primer día sobre **todo el catálogo**.

          **Lo que eso cuesta conviene saberlo**: una tasa creada y no asociada
          **parece configurada y no paga nada**, y eso no falla — se descubre
          liquidando.

          **No lleva rol, y eso tiene una consecuencia que conviene conocer**: la
          tasa sigue rigiendo aunque su titular pase a un rol que no comisiona.
          Hasta el 01-09-2026 el rol era obligatorio precisamente para impedirlo.

          **Aquí NO se comprueba el solapamiento**, y es deliberado: sin producto
          no hay solapamiento posible. Dos tasas de la misma persona con fechas que
          se pisan son legítimas mientras rijan en productos distintos, y
          `RN-CM-006` se verifica **al asociar**.

          **Tampoco se acota el `fixedAmount`**: una tasa sin asociar no conoce el
          precio de nada (`RN-CM-018`). El tope lo pone `RN-CM-019` al asociarla.

          `validFrom` es obligatorio; sin `validTo`, rige indefinidamente.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tasa registrada"),
    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "422", description = "La persona no existe")
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
          Devuelve las tasas **tal como se declararon**, y no resuelve cuál se
          aplica: para eso está `GET /api/v1/commissions/effective`.

          **Incluye el historial**: las vencidas viajan junto a la vigente salvo que
          se filtre por fecha con `onDate`. Y es **el único historial que le queda al
          módulo** — las tasas de rol perdieron la vigencia.

          **Filtrar por persona devuelve las declaradas PARA esa persona**, no la que
          le aplica hoy sobre un producto.
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
          Corrige el **porcentaje** y el **fin de vigencia**. Se aplica lo que llega
          y se deja intacto lo que no.

          **Corregir no es cambiar**, y aquí la distinción sigue viva: corregir
          arregla un error y reescribe lo que esa tasa dice que rigió; cambiar lo que
          gana alguien a partir de una fecha es **cerrar la vigente y registrar
          otra**.

          `validTo: null` **vacía** el fin de vigencia y la tasa vuelve a regir
          indefinidamente; `percentage: null` se **rechaza**.

          **La persona y el inicio de vigencia no se corrigen**, y enviarlos devuelve
          `400`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tasa corregida"),
    @ApiResponse(responseCode = "400", description = "Datos inválidos o campos no corregibles"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "No existe, o está retirada"),
    @ApiResponse(responseCode = "409", description = "La vigencia resultante se solapa")
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
          Retira una tasa que **no debió existir**, con **motivo obligatorio**.

          **Retirar no es cerrar la vigencia**: se cierra lo que dejó de regir, se
          retira lo que fue un error. Por eso el retiro **no toca la vigencia** — el
          registro de eliminación debe poder decir qué periodo cubría lo retirado.

          Los días que ocupaba **quedan libres**, de modo que puede declararse otra
          tasa que los cubra.

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

  @Operation(
      summary = "Asociar una tasa personalizada a un producto",
      description =
          """
          **Es la operación que pone la tasa en vigor.** Hasta que se asocia no
          paga nada a nadie (`RN-CM-012`) — igual que una tasa de rol, y desde el
          11-09-2026 sin excepción: hasta esa fecha la personalizada pagaba desde
          el primer día sobre **todo el catálogo**.

          **Un producto por petición, y admite varios en llamadas sucesivas.**
          Donde esté asociada gana sobre la tasa del rol de esa persona; donde no,
          esa persona cobra por su rol como cualquier otra.

          **Devuelve la lista COMPLETA de productos** tras la operación, no solo el
          que se acaba de añadir: quien asocia el tercero tiene que poder ver los
          tres sin una segunda llamada.

          **`RN-CM-006` se comprueba aquí**: esa persona no puede tener dos tasas
          vivas que cubran el mismo día **sobre el mismo producto**. Sobre
          productos distintos sí, y es lo normal. La comprobación se hace **al
          asociar y no al registrar**, porque sin producto no hay solapamiento
          posible.

          **Un `fixedAmount` no puede superar el precio del producto**
          (`RN-CM-019`). Es el mismo tope que las de rol, y es **individual, no una
          suma**: las personalizadas de personas distintas sobre el mismo producto
          son alternativas entre sí, no cosas que se paguen a la vez.

          El producto debe existir y **no estar retirado**: configurar lo que nadie
          puede vender no falla nunca y no sirve nunca.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Asociada, con la lista completa"),
    @ApiResponse(responseCode = "400", description = "Producto ausente"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "La tasa no existe o está retirada"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Ya estaba asociada, se solapa con otra tasa viva de esa persona en ese producto, el"
                + " producto está retirado, o pagaría más del precio del producto"),
    @ApiResponse(responseCode = "422", description = "El producto no existe")
  })
  @PostMapping("/{id}/products")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('commissions:update')")
  public UserRateProductsResponse asociar(
      @PathVariable UUID id, @Valid @RequestBody AssociateUserProductRequest peticion) {
    return asociacion.associate(id, peticion);
  }

  @Operation(
      summary = "Retirar la asociación de una tasa personalizada con un producto",
      description =
          """
          **Deja de regir ahí, y la tasa sigue viva.** Puede volver a asociarse a
          ese producto o a otro; retirarla es otra operación.

          **El motivo es obligatorio.** La fila se borra de verdad —una asociación
          es configuración vigente, no un hecho del pasado— de modo que este texto
          es **el único sitio** donde quedará escrito por qué esa persona dejó de
          tener su excepción en ese producto.

          **`404` y no `409` si ya no estaba**: con el borrado físico no queda nada
          que distinga «nunca existió» de «ya se borró», y un `409` afirmaría algo
          que no se sabe.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Desasociada"),
    @ApiResponse(responseCode = "400", description = "Motivo ausente, en blanco o demasiado largo"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "Esa tasa no está asociada a ese producto")
  })
  @PostMapping("/{id}/products/{productId}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('commissions:update')")
  public void desasociar(
      @PathVariable UUID id,
      @PathVariable UUID productId,
      @RequestBody(required = false) DissociateProductRequest peticion) {
    desasociacion.dissociate(id, productId, peticion);
  }
}
