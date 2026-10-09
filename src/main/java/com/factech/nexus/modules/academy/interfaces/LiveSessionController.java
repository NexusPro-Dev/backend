package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.Cancel;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.ListFilter;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.Schedule;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.Update;
import com.factech.nexus.modules.academy.application.LiveSessionResponses.HostLink;
import com.factech.nexus.modules.academy.application.LiveSessionResponses.Item;
import com.factech.nexus.modules.academy.domain.service.CancelLiveSessionService;
import com.factech.nexus.modules.academy.domain.service.HostLiveSessionService;
import com.factech.nexus.modules.academy.domain.service.ListLiveSessionsService;
import com.factech.nexus.modules.academy.domain.service.ScheduleLiveSessionService;
import com.factech.nexus.modules.academy.domain.service.UpdateLiveSessionService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Las clases en vivo por Zoom para quien las gobierna (`RF-AC-042` a `RF-AC-052`): administración,
 * todas; el instructor, las de los cursos que dicta, bajo {@code /mine} (`RN-AC-028`).
 *
 * <p>{@code /mine} y {@code /available} son segmentos literales y Spring los prefiere a {@code
 * /{id}}. <b>Una ruta por operación y por alcance</b> (`RN-SEG-014`): las de {@code /mine} tienen
 * sus propios permisos y responden {@code 404} sobre una clase ajena.
 */
@RestController
@RequestMapping("/api/v1/live-sessions")
@Tag(
    name = "Clases en vivo",
    description =
        "Programar, corregir, cancelar e iniciar las clases en vivo por Zoom: administración"
            + " todas, el instructor las de sus cursos.")
public class LiveSessionController {

  private static final String ZOOM =
      """

      **La reunión la crea la plataforma en Zoom**, con registro obligatorio: solo entra quien
      recibe su enlace personal al pulsar «Entrar» (`POST /live-sessions/available/{id}/registration`).
      **De Zoom solo se guarda el identificador de la reunión**; el enlace general y la contraseña
      no salen nunca. Si Zoom no está configurado, falla o no responde, `503` y nada cambia.
      """;

  private final ListLiveSessionsService lecturas;
  private final ScheduleLiveSessionService programar;
  private final UpdateLiveSessionService corregir;
  private final CancelLiveSessionService cancelar;
  private final HostLiveSessionService anfitrion;

  public LiveSessionController(
      ListLiveSessionsService lecturas,
      ScheduleLiveSessionService programar,
      UpdateLiveSessionService corregir,
      CancelLiveSessionService cancelar,
      HostLiveSessionService anfitrion) {
    this.lecturas = lecturas;
    this.programar = programar;
    this.corregir = corregir;
    this.cancelar = cancelar;
    this.anfitrion = anfitrion;
  }

  // ---------------------------------------------------------------------------
  // Administración
  // ---------------------------------------------------------------------------

  @GetMapping
  @PreAuthorize("hasAuthority('live-sessions:list')")
  @Operation(
      summary = "Consultar las clases en vivo",
      description =
          """
          **Todas las clases**, también las canceladas y las terminadas, por inicio
          descendente y paginadas. Filtros: `courseId`, `status` (`PROGRAMADA` o
          `CANCELADA`), `ended` —calculado con la hora de fin— y el periodo de inicio
          `from`/`to` (semiabierto). Cada fila trae cuántas membresías y servicios la abren y
          **cuántos se registraron**.

          Exige `live-sessions:list`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página."),
    @ApiResponse(
        responseCode = "400",
        description = "Paginación o filtro mal formado (`VAL-001`, `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)")
  })
  public PageResponse<Item> listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListFilter filtros) {
    return lecturas.list(filtros);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('live-sessions:read')")
  @Operation(
      summary = "Consultar el detalle de una clase en vivo",
      description =
          """
          La clase con su curso, **sus membresías y servicios**, el identificador de la
          reunión de Zoom —**sin enlace ni contraseña**—, la cancelación si la hay y **quién se
          registró para entrar**, con su fecha. `ended` dice si su hora de fin ya pasó.

          Exige `live-sessions:read`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La clase."),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)")
  })
  public LiveSessionDetailResponse detalle(@PathVariable UUID id) {
    return lecturas.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('live-sessions:create')")
  @Operation(
      summary = "Programar una clase en vivo",
      description =
          """
          **Día, hora de inicio y hora de fin** (`startsAt`, `endsAt`), con su zona o sin ella
          —y entonces en `America/Bogota`—: el inicio no en el pasado, el fin de 15 minutos a
          10 horas después. **`membershipIds` y `productIds` dicen quién puede entrar**, como en
          un curso: lista explícita, servicios `BOT`, y **sin ninguna de las dos, la clase es
          de todos los alumnos con sesión**. `courseId` es opcional: agrupa la clase y decide
          qué instructor la administra, no quién entra.
          """
              + ZOOM
              + """

          Exige `live-sessions:create`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "La clase programada, con el detalle."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Campos ausentes o fuera de forma, horario inválido o listas repetidas (`VAL-001` a `VAL-004`)"),
    @ApiResponse(
        responseCode = "422",
        description = "Curso, membresía o producto que no valen (`EX-001`, `EX-002`)"),
    @ApiResponse(responseCode = "503", description = "Zoom no disponible (`EX-003`)")
  })
  public LiveSessionDetailResponse programar(@RequestBody(required = false) Schedule peticion) {
    return programar.schedule(peticion);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('live-sessions:update')")
  @Operation(
      summary = "Corregir una clase en vivo",
      description =
          """
          **Solo lo que viene.** Las listas, si vienen, **reemplazan enteras** (una lista
          vacía deja la clase de todos); `removeCourse: true` la deja suelta. Lo que Zoom
          conoce —título, inicio y fin— **se corrige allí primero**. Quitar a alguien de la
          lista no lo des-registra en Zoom: no recibe un enlace nuevo. **Una clase cancelada o
          terminada no se corrige** (`409`).
          """
              + ZOOM
              + """

          Exige `live-sessions:update`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La clase corregida."),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "Cancelada o terminada (`EX-003`)"),
    @ApiResponse(responseCode = "422", description = "Curso, membresía o producto que no valen"),
    @ApiResponse(responseCode = "503", description = "Zoom no disponible (`EX-004`)")
  })
  public LiveSessionDetailResponse corregir(
      @PathVariable UUID id, @RequestBody(required = false) Update peticion) {
    return corregir.update(id, peticion);
  }

  @PostMapping("/{id}/cancellation")
  @PreAuthorize("hasAuthority('live-sessions:cancel')")
  @Operation(
      summary = "Cancelar una clase en vivo",
      description =
          """
          Con `reason` obligatorio: **borra la reunión en Zoom** —así nadie entra aunque guarde
          su enlace— y **conserva la clase** como `CANCELADA` con fecha y motivo. No se
          deshace. Una reunión que ya no existía en Zoom no lo impide. `409` si ya estaba
          cancelada o terminó.

          Exige `live-sessions:cancel`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La clase cancelada."),
    @ApiResponse(responseCode = "400", description = "Sin motivo (`VAL-002`)"),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "Cancelada o terminada (`EX-003`)"),
    @ApiResponse(responseCode = "503", description = "Zoom no disponible (`EX-004`)")
  })
  public LiveSessionDetailResponse cancelar(
      @PathVariable UUID id, @RequestBody(required = false) Cancel peticion) {
    return cancelar.cancel(id, peticion);
  }

  @PostMapping("/{id}/host-link")
  @PreAuthorize("hasAuthority('live-sessions:host')")
  @Operation(
      summary = "Iniciar una clase en vivo como anfitrión",
      description =
          """
          **El enlace de inicio de Zoom**, pedido en el momento: abre la reunión **como
          anfitrión** con la cuenta del negocio. **Caduca a las pocas horas y no se guarda**;
          cada entrega queda en la auditoría de seguridad. `409` si la clase está cancelada o
          terminó.

          Exige `live-sessions:host`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El enlace de anfitrión."),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "Cancelada o terminada (`EX-003`)"),
    @ApiResponse(responseCode = "503", description = "Zoom no disponible (`EX-004`)")
  })
  public HostLink anfitrion(@PathVariable UUID id) {
    return anfitrion.host(id);
  }

  // ---------------------------------------------------------------------------
  // El instructor, sobre las clases de sus cursos (RN-AC-028)
  // ---------------------------------------------------------------------------

  @GetMapping("/mine")
  @PreAuthorize("hasAuthority('live-sessions:list-own')")
  @Operation(
      summary = "Consultar mis clases en vivo como instructor",
      description =
          """
          El listado de administración **acotado a las clases de los cursos que dicta** quien
          pregunta, con los mismos filtros. Las clases sueltas no salen.

          Exige `live-sessions:list-own`.
          """)
  public PageResponse<Item> listarPropias(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListFilter filtros) {
    return lecturas.listOwn(filtros);
  }

  @PostMapping("/mine")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('live-sessions:create-own')")
  @Operation(
      summary = "Programar una clase en vivo de mi curso",
      description =
          """
          Como programar una clase, con **`courseId` obligatorio** y de un curso que dicta
          quien pide; si no, `422` (`EX-001`).
          """
              + ZOOM
              + """

          Exige `live-sessions:create-own`.
          """)
  public LiveSessionDetailResponse programarPropia(
      @RequestBody(required = false) Schedule peticion) {
    return programar.scheduleOwn(peticion);
  }

  @PatchMapping("/mine/{id}")
  @PreAuthorize("hasAuthority('live-sessions:update-own')")
  @Operation(
      summary = "Corregir una clase en vivo de mi curso",
      description =
          """
          Como corregir una clase, sobre las de los cursos que dicta: **una ajena responde
          `404`**, y no se puede mover a un curso que no dicta ni dejarla suelta (`422`).

          Exige `live-sessions:update-own`.
          """)
  public LiveSessionDetailResponse corregirPropia(
      @PathVariable UUID id, @RequestBody(required = false) Update peticion) {
    return corregir.updateOwn(id, peticion);
  }

  @PostMapping("/mine/{id}/cancellation")
  @PreAuthorize("hasAuthority('live-sessions:cancel-own')")
  @Operation(
      summary = "Cancelar una clase en vivo de mi curso",
      description =
          """
          Como cancelar una clase, sobre las de los cursos que dicta; una ajena responde `404`.

          Exige `live-sessions:cancel-own`.
          """)
  public LiveSessionDetailResponse cancelarPropia(
      @PathVariable UUID id, @RequestBody(required = false) Cancel peticion) {
    return cancelar.cancelOwn(id, peticion);
  }

  @PostMapping("/mine/{id}/host-link")
  @PreAuthorize("hasAuthority('live-sessions:host-own')")
  @Operation(
      summary = "Iniciar como anfitrión una clase en vivo de mi curso",
      description =
          """
          El enlace de anfitrión de una clase de un curso que dicta quien pide; una ajena
          responde `404`. Cada entrega se audita.

          Exige `live-sessions:host-own`.
          """)
  public HostLink anfitrionPropia(@PathVariable UUID id) {
    return anfitrion.hostOwn(id);
  }
}
