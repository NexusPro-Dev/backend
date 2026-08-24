package com.factech.nexus.modules.system.memberships.interfaces;

import com.factech.nexus.modules.system.memberships.application.ListMembershipsRequest;
import com.factech.nexus.modules.system.memberships.application.MembershipChainResponse;
import com.factech.nexus.modules.system.memberships.application.MembershipDetailResponse;
import com.factech.nexus.modules.system.memberships.application.MembershipResponse;
import com.factech.nexus.modules.system.memberships.application.RegisterMembershipRequest;
import com.factech.nexus.modules.system.memberships.domain.service.GetMembershipService;
import com.factech.nexus.modules.system.memberships.domain.service.ListMembershipsService;
import com.factech.nexus.modules.system.memberships.domain.service.RegisterMembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadena de membresías (`RF-SP-016`, `RF-SP-017`, `RF-SP-018`).
 *
 * <p><b>Sin manejadores de edición ni de eliminación, y la ausencia es la implementación.</b>
 * `RN-SP-008` hace la membresía inmutable: no se cumple con código que rechace, se cumple porque no
 * hay a qué llamar. Un {@code PUT} sobre este recurso obtiene {@code 405} de Spring sin que nadie
 * lo haya escrito. Lo único que cambia una fila ya escrita es el reordenamiento que provoca un
 * alta.
 *
 * <p><b>{@code memberships:create} no se comparte con {@code memberships:read}.</b> Consultar la
 * cadena es una operación de apoyo que cualquier administrador necesita; insertar un nivel
 * intermedio cambia el alcance de todos los consumidores que ya tenían membresía y es irreversible.
 */
@RestController
@RequestMapping("/api/v1/memberships")
@Tag(
    name = "Membresías",
    description = "Cadena lineal de niveles de consumidor. Inmutable salvo el reordenamiento.")
public class MembershipController {

  private final RegisterMembershipService alta;
  private final ListMembershipsService listado;
  private final GetMembershipService detalle;

  public MembershipController(
      RegisterMembershipService alta,
      ListMembershipsService listado,
      GetMembershipService detalle) {
    this.alta = alta;
    this.listado = listado;
    this.detalle = detalle;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('memberships:create')")
  @Operation(
      summary = "Registrar una membresía",
      description =
          """
          Inserta una membresía en la cadena, por encima de la hija indicada.

          Si no se indica hija, la membresía queda en el extremo inferior y no se
          reordena nada. Si se indica, la nueva ocupa su nivel, hereda su superior
          y todo lo que estaba en ese nivel o por debajo baja uno.

          El nivel y la superior **no se envían**: los calcula el sistema. Enviar
          `level` o `parentMembershipId` devuelve `400`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Membresía registrada, con la posición que ocupó.",
        content = @Content(schema = @Schema(implementation = MembershipResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Formato u obligatoriedad incumplidos, o campo no admitido en el cuerpo",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `memberships:create` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Código o nombre ya en uso (`EX-001`), o la cadena cambió durante la operación"
                + " (`EX-003`) y debe reintentarse",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La membresía hija indicada no existe (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ResponseEntity<MembershipResponse> registrar(
      @Valid @RequestBody RegisterMembershipRequest peticion) {
    MembershipResponse creada = alta.register(peticion.toCommand());
    return ResponseEntity.created(URI.create("/api/v1/memberships/" + creada.id())).body(creada);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('memberships:read')")
  @Operation(
      summary = "Consultar la cadena de membresías",
      description =
          """
          Devuelve la cadena completa, de la superior al extremo inferior, sin paginar.

          `level` es la distancia hasta la cima: `1` es la superior. El orden de la
          colección lo refleja.

          No se admite paginación ni ordenamiento: el orden **es** la información.
          Los parámetros de paginación se ignoran y devuelven la cadena entera.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Cadena devuelta. Puede venir vacía si aún no hay membresías.",
        content = @Content(schema = @Schema(implementation = MembershipChainResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `memberships:read` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public MembershipChainResponse consultar(@ModelAttribute ListMembershipsRequest peticion) {
    return listado.list(peticion);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('memberships:read')")
  @Operation(
      summary = "Consultar el detalle de una membresía",
      description =
          """
          Devuelve la membresía con sus dos vecinos inmediatos, expandidos.

          Los vecinos llegan solo hasta el primer grado y no traen los suyos: la
          cadena entera se obtiene con el listado.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Detalle devuelto.",
        content = @Content(schema = @Schema(implementation = MembershipDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "El identificador no es un UUID (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `memberships:read` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe membresía con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public MembershipDetailResponse consultarDetalle(@PathVariable UUID id) {
    return detalle.byId(id);
  }
}
