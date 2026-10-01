package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.PayoutAccountResponse;
import com.factech.nexus.modules.movements.application.PayoutInstitutionResponse;
import com.factech.nexus.modules.movements.application.PayoutRequests;
import com.factech.nexus.modules.movements.domain.service.PayoutAccountService;
import com.factech.nexus.modules.movements.domain.service.PayoutInstitutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las cuentas de cobro (`requirements/mv.md` §4.5): <b>a dónde se paga un retiro</b>. El catálogo
 * de entidades, las cuentas propias y la consulta de administración.
 */
@Tag(
    name = "Cuentas de cobro",
    description =
        "Los bancos y billeteras móviles a los que se pagan los retiros, y las cuentas de cada"
            + " persona en ellos. Un retiro exige una cuenta y copia su destino.")
@RestController
@RequestMapping("/api/v1/movements")
public class PayoutController {

  private final PayoutInstitutionService entidades;
  private final PayoutAccountService cuentas;

  public PayoutController(PayoutInstitutionService entidades, PayoutAccountService cuentas) {
    this.entidades = entidades;
    this.cuentas = cuentas;
  }

  // ---------------------------------------------------------------------------
  // Entidades
  // ---------------------------------------------------------------------------

  @PostMapping("/payout-institutions")
  @PreAuthorize("hasAuthority('movements:create-payout-institution')")
  @Operation(
      summary = "Registrar una entidad de cobro",
      description =
          """
          Da de alta un **banco** o una **billetera móvil** de un país (`RF-MV-032`). El código
          —`BANCOLOMBIA`, `NEQUI`— es **único en todo el catálogo** y, como el tipo y el país,
          **no se puede cambiar**: es lo que queda copiado en cada retiro. Nace activa. El
          catálogo no se siembra: lo llena administración.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Registrada."),
    @ApiResponse(
        responseCode = "400",
        description = "Datos ausentes o malformados, todos juntos (`VAL-001` a `VAL-004`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:create-payout-institution` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "El país está inactivo (`EX-003`) o el código ya existe (`EX-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "El país no existe (`EX-002`)",
        content = @Content)
  })
  public ResponseEntity<PayoutInstitutionResponse> registrarEntidad(
      @RequestBody(required = false) PayoutRequests.RegisterInstitution peticion) {
    PayoutInstitutionResponse hecha = entidades.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/movements/payout-institutions/" + hecha.id()))
        .body(hecha);
  }

  @GetMapping("/payout-institutions")
  @PreAuthorize("hasAuthority('movements:read-payout-institutions')")
  @Operation(
      summary = "Consultar las entidades de cobro",
      description =
          """
          El catálogo de bancos y billeteras móviles (`RF-MV-033`), ordenado por país y por
          nombre, sin paginar. **Por omisión, solo las activas**, que son las que se ofrecen; las
          inactivas se piden con `status`. Para el formulario de una cuenta, filtre por el país
          de la persona: solo esas se admiten al registrarla.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La lista, quizá vacía."),
    @ApiResponse(
        responseCode = "400",
        description = "Un filtro malformado (`VAL-001` a `VAL-003`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:read-payout-institutions` (`AUTH-002`)",
        content = @Content)
  })
  public List<PayoutInstitutionResponse> consultarEntidades(
      @Parameter(description = "Solo las de ese país.") @RequestParam(required = false)
          String countryId,
      @Parameter(description = "BANCO o BILLETERA_MOVIL.") @RequestParam(required = false)
          String kind,
      @Parameter(description = "ACTIVA (por omisión), INACTIVA o TODAS.")
          @RequestParam(required = false)
          String status) {
    return entidades.list(countryId, kind, status);
  }

  @PatchMapping("/payout-institutions/{id}")
  @PreAuthorize("hasAuthority('movements:update-payout-institution')")
  @Operation(
      summary = "Editar una entidad de cobro",
      description =
          """
          Corrige el **nombre** o **activa y desactiva** una entidad (`RF-MV-034`). El código, el
          tipo y el país no se editan: enviarlos responde `400`. **Desactivarla** la saca del
          catálogo y le impide cuentas y retiros nuevos, **sin tocar las cuentas** de nadie; los
          retiros ya pedidos conservan su destino copiado. Enviar lo que ya tiene no escribe
          nada.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Editada, o sin cambios."),
    @ApiResponse(
        responseCode = "400",
        description = "Nada que cambiar, nombre inválido o una propiedad que no se edita",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:update-payout-institution` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La entidad no existe (`EX-002`)",
        content = @Content)
  })
  public PayoutInstitutionResponse editarEntidad(
      @PathVariable UUID id,
      @RequestBody(required = false) PayoutRequests.UpdateInstitution peticion) {
    return entidades.update(id, peticion);
  }

  // ---------------------------------------------------------------------------
  // Cuentas propias
  // ---------------------------------------------------------------------------

  @PostMapping("/mine/payout-accounts")
  @PreAuthorize("hasAuthority('movements:create-own-payout-account')")
  @Operation(
      summary = "Registrar una cuenta de cobro propia",
      description =
          """
          Registra **a dónde quiere que le paguen** quien tiene la sesión (`RF-MV-035`). **El
          titular es siempre esa persona**: el nombre y el documento se toman de su usuario, y
          sin documento no se puede registrar. La entidad tiene que estar activa y ser de su
          país. En un **banco**, `accountType` (AHORROS o CORRIENTE) y un número de 4 a 20
          dígitos; en una **billetera móvil**, solo el celular, de 7 a 15 dígitos y sin
          `accountType`. El número admite espacios y guiones y se guarda solo con dígitos. **La
          primera cuenta es la principal**; otra lo es si se pide, y la anterior deja de serlo.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Registrada."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos ausentes o malformados (`VAL-001` a `VAL-003`), o que no corresponden al tipo"
                + " de la entidad (`EX-004`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:create-own-payout-account` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Entidad inactiva o de otro país (`EX-003`), persona sin documento (`EX-005`) o"
                + " cuenta ya registrada (`EX-006`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La entidad no existe (`EX-002`)",
        content = @Content)
  })
  public ResponseEntity<PayoutAccountResponse> registrarCuenta(
      @RequestBody(required = false) PayoutRequests.RegisterAccount peticion) {
    return ResponseEntity.created(URI.create("/api/v1/movements/mine/payout-accounts"))
        .body(cuentas.register(peticion));
  }

  @GetMapping("/mine/payout-accounts")
  @PreAuthorize("hasAuthority('movements:list-own-payout-accounts')")
  @Operation(
      summary = "Consultar mis cuentas de cobro",
      description =
          """
          Las cuentas vivas de quien tiene la sesión (`RF-MV-036`): **la principal primero** y
          después de la más reciente a la más antigua. `usable` dice si sirve para pedir un
          retiro: es `false` cuando su entidad está desactivada, y hay que registrar otra.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La lista, quizá vacía."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:list-own-payout-accounts` (`AUTH-002`)",
        content = @Content)
  })
  public List<PayoutAccountResponse> misCuentas() {
    return cuentas.listMine();
  }

  @PatchMapping("/mine/payout-accounts/{id}")
  @PreAuthorize("hasAuthority('movements:update-own-payout-account')")
  @Operation(
      summary = "Editar una cuenta de cobro propia",
      description =
          """
          Corrige el tipo de cuenta o el número, o **la hace la principal** (`RF-MV-037`): la
          anterior deja de serlo. **La principal no se quita** —`principal: false` es un error—:
          se marca otra. La entidad no se cambia; para otra entidad, se registra otra cuenta.
          Una cuenta de una entidad desactivada no se edita: se da de baja. Los retiros ya
          pedidos hacia ella **conservan su destino**.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Editada, o sin cambios."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Nada que cambiar, datos malformados, `principal: false`, o datos que no"
                + " corresponden al tipo de la entidad (`EX-004`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:update-own-payout-account` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No es suya, no existe o está dada de baja (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Entidad desactivada (`EX-003`) o número repetido (`EX-005`)",
        content = @Content)
  })
  public PayoutAccountResponse editarCuenta(
      @PathVariable UUID id, @RequestBody(required = false) PayoutRequests.UpdateAccount peticion) {
    return cuentas.update(id, peticion);
  }

  @DeleteMapping("/mine/payout-accounts/{id}")
  @PreAuthorize("hasAuthority('movements:delete-own-payout-account')")
  @Operation(
      summary = "Dar de baja una cuenta de cobro propia",
      description =
          """
          La retira, sin vuelta atrás (`RF-MV-038`). Si era la principal, **la principal pasa a
          la más antigua** de las que quedan. Se admite con retiros pendientes hacia ella: su
          destino quedó copiado al pedirlos.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Dada de baja."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:delete-own-payout-account` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No es suya, no existe o ya está dada de baja (`EX-001`)",
        content = @Content)
  })
  public ResponseEntity<Void> darDeBajaCuenta(@PathVariable UUID id) {
    cuentas.delete(id);
    return ResponseEntity.noContent().build();
  }

  // ---------------------------------------------------------------------------
  // Administración
  // ---------------------------------------------------------------------------

  @GetMapping("/users/{userId}/payout-accounts")
  @PreAuthorize("hasAuthority('movements:read-user-payout-accounts')")
  @Operation(
      summary = "Consultar las cuentas de cobro de una persona",
      description =
          """
          Las cuentas de **cualquier** persona, para administración (`RF-MV-039`): las vivas en
          el orden de «mis cuentas» y, con `includeDeleted=true`, también las dadas de baja, al
          final y con su fecha. Alcance global: no sigue la estructura comercial.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La lista, quizá vacía."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:read-user-payout-accounts` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`EX-002`)",
        content = @Content)
  })
  public List<PayoutAccountResponse> cuentasDeUnaPersona(
      @PathVariable UUID userId,
      @Parameter(description = "true incluye las dadas de baja.")
          @RequestParam(defaultValue = "false")
          boolean includeDeleted) {
    return cuentas.listOf(userId, includeDeleted);
  }
}
