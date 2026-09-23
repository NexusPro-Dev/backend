package com.factech.nexus.modules.system.teams.interfaces;

import com.factech.nexus.modules.system.teams.application.ChangeTeamStatusRequest;
import com.factech.nexus.modules.system.teams.application.ListTeamsRequest;
import com.factech.nexus.modules.system.teams.application.RegisterTeamRequest;
import com.factech.nexus.modules.system.teams.application.TeamDetailResponse;
import com.factech.nexus.modules.system.teams.application.TeamItem;
import com.factech.nexus.modules.system.teams.application.UpdateTeamRequest;
import com.factech.nexus.modules.system.teams.domain.service.ChangeTeamStatusService;
import com.factech.nexus.modules.system.teams.domain.service.GetTeamService;
import com.factech.nexus.modules.system.teams.domain.service.ListTeamsService;
import com.factech.nexus.modules.system.teams.domain.service.RegisterTeamService;
import com.factech.nexus.modules.system.teams.domain.service.UpdateTeamService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  private final GetTeamService ficha;
  private final UpdateTeamService correccion;
  private final ChangeTeamStatusService estado;

  public TeamController(
      RegisterTeamService alta,
      ListTeamsService listado,
      GetTeamService ficha,
      UpdateTeamService correccion,
      ChangeTeamStatusService estado) {
    this.alta = alta;
    this.listado = listado;
    this.ficha = ficha;
    this.correccion = correccion;
    this.estado = estado;
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

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('teams:read')")
  @Operation(
      summary = "Consultar el detalle de un equipo",
      description =
          """
          El equipo entero: lo suyo —incluida la `description`, que el listado no
          trae— y **sus miembros vigentes**, cada uno con `id`, `username`,
          `firstName`, `lastName`, `status` y `joinedAt`, **desde cuándo pertenece a
          este equipo**. La lista va **por antigüedad**, con el nombre de usuario de
          desempate para que el orden sea determinista.

          **El `status` de cada miembro es el de la PERSONA**, no el de su pertenencia
          —una pertenencia vigente no tiene estados—, y se publica porque un manager
          desactivado **sigue en su equipo**: cambiar el estado de una persona no la
          saca. Quien administra necesita verlo sin abrir cada ficha. A un eliminado
          sí se le cierra la pertenencia en la misma transacción, de modo que deja de
          ser vigente y no aparece.

          **`members` son solo los vigentes**: quien tuvo una pertenencia cerrada aquí
          no sale, y `memberCount` coincide siempre con el tamaño de la lista y con el
          número que da el listado para este mismo equipo.

          **Un equipo eliminado NO es un `404`**: se devuelve con `deletedAt` y
          `deletionReason` —leído de la auditoría de eliminación— y con `members`
          vacío, no por ocultarlos sino porque no se elimina un equipo que tenga
          vigentes. Si el registro de auditoría no aparece, `deletionReason` sale
          **presente y nulo**: el detalle no depende de la auditoría para responder.
          Un equipo `INACTIVO` se devuelve igual, **con su gente**: desactivar no
          vacía.

          Exige `teams:read`; `teams:list` **no** habilita esta operación, porque
          listar y abrir son dos.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El equipo con sus miembros vigentes.",
        content = @Content(schema = @Schema(implementation = TeamDetailResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador mal formado (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `teams:read` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "El equipo no existe (`EX-001`)")
  })
  public TeamDetailResponse detalle(@PathVariable UUID id) {
    return ficha.detail(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('teams:update')")
  @Operation(
      summary = "Corregir un equipo",
      description =
          """
          Corrige el **nombre**, la **descripción** o los dos. Y nada más: el estado
          se cambia con `PATCH /teams/{id}/status` y los miembros entran y salen por
          sus propias rutas, cada una con su permiso. **Enviar `status` o `members`
          responde `400`** por campo desconocido.

          **Omitir un campo no es borrarlo.** `description` ausente deja la que
          había; **`description: null` la borra** y la respuesta la devuelve presente
          y nula. El **nombre no se puede vaciar**: un equipo sin nombre no existe, de
          modo que `name: null` es `400`, no un borrado.

          **Un cuerpo sin ningún campo es `400`**: un `PATCH` vacío no es una
          corrección, y responderle `200` haría creer que algo cambió.

          El nombre sigue siendo **único entre los equipos no eliminados, sin
          distinguir mayúsculas ni acentos**, y el choque es `409`; **renombrarse al
          nombre que ya se tiene se admite** —no se compite contra uno mismo— y el
          nombre de un equipo eliminado **está libre**. Dos renombrados simultáneos al
          mismo nombre salen por el mismo `409`, nunca por un `500`.

          **Un equipo eliminado responde `404`**, igual que uno inexistente: no es
          editable en ningún caso. **Uno `INACTIVO` se edita con normalidad**, porque
          inactivo significa que no recibe miembros, no que sea inmutable — corregir
          una errata antes de reactivarlo es justo lo que se hace.

          La respuesta es la **forma del detalle**, ya con los valores nuevos y sus
          miembros vigentes; **el estado, la fecha de eliminación y las pertenencias no
          se tocan**. Exige `teams:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Equipo corregido, en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = TeamDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Nombre vacío o largo, descripción larga, cuerpo sin campos (`VAL-003`) o con campos"
                + " no admitidos (`VAL-004`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `teams:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El equipo no existe o está eliminado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description = "El nombre ya lo usa otro equipo no eliminado (`EX-002`)")
  })
  public TeamDetailResponse corregir(
      @PathVariable UUID id, @RequestBody UpdateTeamRequest peticion) {
    return correccion.update(id, peticion);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('teams:change-status')")
  @Operation(
      summary = "Suspender o reactivar un equipo",
      description =
          """
          Pasa el equipo a `INACTIVO` o lo devuelve a `ACTIVO`. Se declara **el
          estado destino** y no una acción, de modo que **repetir la petición deja
          el mismo resultado**: pedir el estado que ya se tiene responde `200` sin
          escribir nada y **sin registrar auditoría**.

          **`INACTIVO` significa «no recibe», no «está vacío».** El equipo
          suspendido **conserva a todos sus miembros**: el detalle los sigue
          devolviendo y el listado los sigue contando. Cerrar las pertenencias al
          suspender movería la atribución de toda una red sin que nadie lo hubiera
          decidido; para vaciarlo hay que retirar a cada miembro, con motivo.

          **La restricción es sobre el equipo que RECIBE.** Un equipo `INACTIVO` no
          admite miembros nuevos, pero **sí se puede retirar** a los que tiene —es
          la única forma de vaciarlo para poder eliminarlo— y **sí se puede
          reasignar a alguien desde él hacia un equipo activo**.

          **Desactivar no le quita nada a nadie.** Un manager de un equipo
          suspendido sigue activo, sigue siendo manager y sigue teniendo su red;
          pertenecer a un equipo no concede acceso a ningún dato, y por eso esta
          operación **no registra ningún evento de seguridad** — al contrario que
          desactivar un rol, que retira permisos de inmediato.

          **El cuerpo no admite motivo**: el motivo es la barrera de lo
          irreversible, y esto se deshace con una petición. Enviar `reason`, `name`
          o `members` responde `400`.

          Suspender **no es eliminar**: «ya no organizo con este equipo pero quiero
          ver quién estaba» es esto; `DELETE /teams/{id}` es «no debería existir», y
          exige vaciarlo antes. Un equipo eliminado responde `404`: sobre él no hay
          estado que cambiar.

          La respuesta es la **forma del detalle**, con el estado nuevo y los mismos
          miembros. Exige `teams:change-status`; **`teams:update` no habilita esta
          operación**, porque corregir el nombre de un equipo y suspenderlo son dos
          decisiones de negocio distintas.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Equipo con su estado nuevo, en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = TeamDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Estado ausente o fuera del dominio (`VAL-001`), identificador mal formado"
                + " (`VAL-002`) o cuerpo con campos no admitidos (`VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `teams:change-status` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El equipo no existe o está eliminado (`EX-001`)")
  })
  public TeamDetailResponse cambiarEstado(
      @PathVariable UUID id, @RequestBody ChangeTeamStatusRequest peticion) {
    return estado.change(id, peticion);
  }
}
