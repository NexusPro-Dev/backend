package com.factech.nexus.modules.system.teams.interfaces;

import com.factech.nexus.modules.system.teams.application.ListTeamsRequest;
import com.factech.nexus.modules.system.teams.application.RegisterTeamRequest;
import com.factech.nexus.modules.system.teams.application.TeamDetailResponse;
import com.factech.nexus.modules.system.teams.application.TeamItem;
import com.factech.nexus.modules.system.teams.domain.service.ListTeamsService;
import com.factech.nexus.modules.system.teams.domain.service.RegisterTeamService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los equipos de la fuerza comercial (`SP`, `RF-SP-063` a `RF-SP-070`).
 *
 * <p><b>Un equipo agrupa a la cúspide y no manda.</b> Reúne a quienes portan el rol comercial de
 * mayor rango —los que no tienen superior—, y con cada uno entra, por su cadena de mando, toda la
 * red que cuelga de él. Quién está a cargo de quién lo administra `PATCH /users/{id}/supervisor`;
 * esto solo dice en qué cajón está cada manager.
 *
 * <p><b>Pertenecer a un equipo no concede acceso a ningún dato.</b> El alcance de las lecturas se
 * resuelve por la estructura comercial, no por el equipo.
 *
 * <p><b>Un recurso propio con sus ocho permisos</b> (`teams:`), uno por operación: listar y abrir
 * son dos, y asignar y retirar miembros también.
 */
@RestController
@RequestMapping("/api/v1/teams")
@Tag(
    name = "Equipos",
    description = "Los equipos en que se organiza la cúspide de la fuerza comercial.")
public class TeamController {

  private final RegisterTeamService alta;
  private final ListTeamsService listado;

  public TeamController(RegisterTeamService alta, ListTeamsService listado) {
    this.alta = alta;
    this.listado = listado;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('teams:create')")
  @Operation(
      summary = "Registrar un equipo",
      description =
          """
          Registra un equipo con **nombre** obligatorio y descripción opcional. **Nace
          vacío y `ACTIVO`**: los miembros se asignan después, con su propia petición,
          porque asignar comprueba que cada persona sea de la cúspide y cierra su
          pertenencia anterior.

          **No tiene código**, al contrario que un rol o una membresía: nada del
          sistema referencia a un equipo por un nombre estable, y se llega a él por su
          identificador. Lo que identifica es el nombre, **único entre los equipos no
          eliminados, sin distinguir mayúsculas ni acentos**; un equipo eliminado
          libera el suyo, y el equipo que lo reutilice no hereda nada —ni miembros, ni
          historial—.

          **Tampoco tiene país ni responsable**: un equipo no se organiza por territorio
          y lo que hay por encima de un manager es administración, no otro manager.
          Enviar `status`, `members` o `code` responde `400`.

          La respuesta es la misma forma que el detalle: `memberCount` en cero,
          `members` vacío y `description` presente y nula si no vino. **Pertenecer a un
          equipo no concede acceso a ningún dato.** Exige `teams:create`; ningún otro
          `teams:` habilita esta operación.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Equipo registrado, activo y sin miembros.",
        content = @Content(schema = @Schema(implementation = TeamDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, juntos (`VAL-001`, `VAL-002`), o un cuerpo con `status`, `members`"
                + " o `code` (`VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `teams:create` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Nombre ya en uso por un equipo no eliminado (`EX-001`)")
  })
  public ResponseEntity<TeamDetailResponse> register(
      @Valid @RequestBody RegisterTeamRequest peticion) {
    TeamDetailResponse creado = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/teams/" + creado.id())).body(creado);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('teams:list')")
  @Operation(
      summary = "Consultar los equipos",
      description =
          """
          El catálogo de administración de equipos, paginado y **en orden alfabético
          por nombre**, con el más antiguo primero entre dos que empaten. Se ordena
          así, y no por fecha de alta, porque el nombre es lo único que identifica a un
          equipo y la lista es corta; `createdAt` también se admite, descendente por
          omisión.

          Cada fila trae **`memberCount`, cuántos miembros VIGENTES tiene hoy**: las
          pertenencias cerradas son historial y no cuentan, de modo que un equipo con
          dos cerradas y una abierta dice uno. **No depende del estado**: un equipo
          `INACTIVO` conserva a su gente y la sigue contando. Un equipo eliminado dice
          cero, porque no se elimina un equipo con miembros vigentes.

          **La fila no trae la descripción ni quiénes son los miembros**: eso es el
          detalle (`GET /teams/{id}`). Publicar los miembros por fila haría que la
          respuesta creciera con el producto de dos cardinalidades.

          Busca por `q` —nombre, **por contenido** y sin distinguir mayúsculas ni
          acentos: «norte» encuentra «Equipo Norte» y «Región NORTE»—. Filtra por
          `status` (`ACTIVO` o `INACTIVO`, en cualquier caja). **Excluye los
          eliminados** salvo `includeDeleted=true`, y entonces los trae con su
          `deletedAt`; los dos filtros deciden por separado. «Vacío» no es filtro: el
          recuento se publica por fila y la pantalla separa.

          Los parámetros inválidos se devuelven **juntos** con `400`. Exige
          `teams:list`; `teams:read` **no** habilita esta operación, porque listar y
          abrir son dos permisos.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página de equipos."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación, `status`, `sort` o `includeDeleted` inválidos, juntos (`VAL-001` a"
                + " `VAL-004`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `teams:list` (`AUTH-002`)")
  })
  public PageResponse<TeamItem> listar(@ParameterObject @ModelAttribute ListTeamsRequest filtros) {
    return listado.list(filtros);
  }
}
