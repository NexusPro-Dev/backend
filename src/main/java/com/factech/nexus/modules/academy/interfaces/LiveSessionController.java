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
import com.factech.nexus.modules.academy.domain.service.LiveSessionCoverService;
import com.factech.nexus.modules.academy.domain.service.ScheduleLiveSessionService;
import com.factech.nexus.modules.academy.domain.service.UpdateLiveSessionService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Las clases en vivo por Zoom para quien las gobierna (`RF-AC-042` a `RF-AC-061`): administración,
 * todas; el instructor, aquellas de las que es instructor, bajo {@code /mine} (`RN-AC-028`,
 * `RN-AC-032`).
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
        "Programar, corregir, cancelar, iniciar y poner portada a las clases en vivo por Zoom:"
            + " administración todas, el instructor las suyas.")
public class LiveSessionController {

  private static final String ZOOM =
      """

      **La reunión la crea la plataforma en Zoom**, con registro obligatorio: solo entra quien
      recibe su enlace personal al pulsar «Entrar» (`POST /live-sessions/available/{id}/registration`).
      **De Zoom solo se guarda el identificador de la reunión**; el enlace general y la contraseña
      no salen nunca. Si Zoom no está configurado, falla o no responde, `503` y nada cambia.

      **Se repite** con `recurrence` —`DAILY`; `WEEKLY`, el día de la semana del inicio; o
      `MONTHLY`, el día del mes del inicio, no después del 28— y `repeatUntil`, el último día,
      obligatorio con ella: **una sola clase y una sola reunión recurrente de Zoom** a la misma
      hora, de 2 a 60 fechas (`RN-AC-031`). Al corregir, `NONE` quita la repetición y deja la
      primera fecha. La clase termina con **su última fecha**.
      """;

  private final ListLiveSessionsService lecturas;
  private final ScheduleLiveSessionService programar;
  private final UpdateLiveSessionService corregir;
  private final CancelLiveSessionService cancelar;
  private final HostLiveSessionService anfitrion;
  private final LiveSessionCoverService portadas;

  public LiveSessionController(
      ListLiveSessionsService lecturas,
      ScheduleLiveSessionService programar,
      UpdateLiveSessionService corregir,
      CancelLiveSessionService cancelar,
      HostLiveSessionService anfitrion,
      LiveSessionCoverService portadas) {
    this.portadas = portadas;
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
          descendente y paginadas. Filtros: `instructorId` —las de las que es instructor—,
          `categoryId` —las de esa categoría (`RN-AC-034`)—,
          `status` (`PROGRAMADA` o
          `CANCELADA`), `ended` —calculado con la hora de fin de la última fecha— y el periodo
          de inicio `from`/`to` (semiabierto); **una clase que se repite** entra si alguna de
          sus fechas puede caer en él: empieza antes de `to` y termina después de `from`. Cada fila trae cuántas membresías y servicios la abren y
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
          La clase con **sus instructores**, su portada (`coverImageUrl`), **sus membresías y
          servicios**, el identificador de la
          reunión de Zoom —**sin enlace ni contraseña**—, la cancelación si la hay y **quién se
          registró para entrar**, con su fecha. `ended` dice si terminó su última fecha;
          `recurrence` y `repeatUntil`, cómo se repite; y `nextStartsAt`/`nextEndsAt`, la fecha
          en curso o la siguiente.

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
          de todos los alumnos con sesión**. **No cuelga de ningún curso** (`RN-AC-032`):
          `instructorIds` nombra a sus instructores —personas con `courses:teach`, sin
          repetir—, que la gobiernan bajo `/mine`; sin ninguno, solo administración. La
          portada se pone aparte, con `PUT /{id}/cover`. **`categoryId` es obligatorio**: una
          categoría viva de `GET /live-session-categories` (`RN-AC-034`); sin ella, `400`
          `VAL-005`; inexistente o retirada, `422` `EX-002`, sin llamar a Zoom.
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
            "Campos ausentes o fuera de forma, horario inválido, listas repetidas o sin categoría (`VAL-001` a `VAL-005`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "Categoría, instructor, membresía o producto que no valen (`EX-002`, `EX-003`)"),
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
          vacía deja la clase de todos), **también `instructorIds`**. `categoryId`, si viene,
          la cambia por otra viva (`422` `EX-002` si no lo está). Lo que Zoom
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
    @ApiResponse(
        responseCode = "422",
        description = "Instructor, membresía o producto que no valen"),
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
          cada entrega queda en la auditoría de seguridad. `startsAt` y `endsAt` son los de la
          fecha en curso o la siguiente. `409` si la clase está cancelada o terminó.

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
  // El instructor, sobre las clases de las que es instructor (RN-AC-028, RN-AC-032)
  // ---------------------------------------------------------------------------

  @GetMapping("/mine")
  @PreAuthorize("hasAuthority('live-sessions:list-own')")
  @Operation(
      summary = "Consultar mis clases en vivo como instructor",
      description =
          """
          El listado de administración **acotado a las clases de las que es instructor** quien
          pregunta, con los mismos filtros. Las clases sueltas no salen.

          Exige `live-sessions:list-own`.
          """)
  public PageResponse<Item> listarPropias(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListFilter filtros) {
    return lecturas.listOwn(filtros);
  }

  @GetMapping("/mine/{id}")
  @PreAuthorize("hasAuthority('live-sessions:read-own')")
  @Operation(
      summary = "Consultar el detalle de una clase en vivo mía",
      description =
          """
          El detalle de administración —descripción, membresías, servicios, repetición,
          registrados, sin enlace ni contraseña— **de una clase de la que es instructor** quien
          pregunta, también cancelada o terminada. **Una ajena, suelta o inexistente responde
          `404`**, como si no existiera.

          Exige `live-sessions:read-own`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La clase."),
    @ApiResponse(responseCode = "404", description = "No existe o no es suya (`EX-001`)")
  })
  public LiveSessionDetailResponse detallePropio(@PathVariable UUID id) {
    return lecturas.getOwn(id);
  }

  @PostMapping("/mine")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('live-sessions:create-own')")
  @Operation(
      summary = "Programar una clase en vivo mía",
      description =
          """
          Como programar una clase, y **quien pide queda entre sus instructores**, con los que
          nombre en `instructorIds`.
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
      summary = "Corregir una clase en vivo mía",
      description =
          """
          Como corregir una clase, sobre las de las que es instructor: **una ajena responde
          `404`**, y `instructorIds` **no puede dejarlo fuera** (`422`, `EX-004`).

          Exige `live-sessions:update-own`.
          """)
  public LiveSessionDetailResponse corregirPropia(
      @PathVariable UUID id, @RequestBody(required = false) Update peticion) {
    return corregir.updateOwn(id, peticion);
  }

  @PostMapping("/mine/{id}/cancellation")
  @PreAuthorize("hasAuthority('live-sessions:cancel-own')")
  @Operation(
      summary = "Cancelar una clase en vivo mía",
      description =
          """
          Como cancelar una clase, sobre las de las que es instructor; una ajena responde `404`.

          Exige `live-sessions:cancel-own`.
          """)
  public LiveSessionDetailResponse cancelarPropia(
      @PathVariable UUID id, @RequestBody(required = false) Cancel peticion) {
    return cancelar.cancelOwn(id, peticion);
  }

  @PostMapping("/mine/{id}/host-link")
  @PreAuthorize("hasAuthority('live-sessions:host-own')")
  @Operation(
      summary = "Iniciar como anfitrión una clase en vivo mía",
      description =
          """
          El enlace de anfitrión de una clase de la que es instructor quien pide; una ajena
          responde `404`. Cada entrega se audita.

          Exige `live-sessions:host-own`.
          """)
  public HostLink anfitrionPropia(@PathVariable UUID id) {
    return anfitrion.hostOwn(id);
  }

  // ---------------------------------------------------------------------------
  // La portada (RF-AC-058 a RF-AC-061, RN-AC-032)
  // ---------------------------------------------------------------------------

  @PutMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('live-sessions:set-cover')")
  @Operation(
      summary = "Poner la portada de una clase en vivo",
      description =
          """
          Recibe **un archivo** —`multipart/form-data`, una sola parte llamada `file`— y lo
          convierte en la portada de la clase, **tal cual**, como la de un curso. **El tipo lo
          deciden los bytes**: `JPEG`, `PNG` o `WebP`, **hasta 5 MB** (`VAL-004`); sin
          archivo o vacío, `VAL-002`; otro formato, `VAL-003`. **Si ya tenía portada, la
          reemplaza** y la anterior se borra; `coverImageUrl` pasa a
          `/api/v1/academy-images/{imageId}`, pública.  **Cancelada o terminada, `409`**.

          Exige `live-sessions:set-cover`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La clase, con su portada."),
    @ApiResponse(
        responseCode = "400",
        description = "Sin archivo, de otro formato o de más de 5 MB (`VAL-002` a `VAL-004`)"),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "Cancelada o terminada (`EX-003`)")
  })
  public LiveSessionDetailResponse ponerPortada(
      @PathVariable UUID id, @RequestPart(value = "file", required = false) MultipartFile file) {
    return portadas.upload(id, CoverPart.bytesDe(file));
  }

  @DeleteMapping("/{id}/cover")
  @PreAuthorize("hasAuthority('live-sessions:remove-cover')")
  @Operation(
      summary = "Quitar la portada de una clase en vivo",
      description =
          """
          **Quita la portada y la borra**: `coverImageUrl` vuelve a nulo. Sin portada, `200`
          sin cambio. **Cancelada o terminada, `409`**.

          Exige `live-sessions:remove-cover`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La clase, sin portada."),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "Cancelada o terminada (`EX-003`)")
  })
  public LiveSessionDetailResponse quitarPortada(@PathVariable UUID id) {
    return portadas.remove(id);
  }

  @PutMapping(value = "/mine/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('live-sessions:set-cover-own')")
  @Operation(
      summary = "Poner la portada de una clase en vivo mía",
      description =
          """
          Recibe **un archivo** —`multipart/form-data`, una sola parte llamada `file`— y lo
          convierte en la portada de la clase, **tal cual**, como la de un curso. **El tipo lo
          deciden los bytes**: `JPEG`, `PNG` o `WebP`, **hasta 5 MB** (`VAL-004`); sin
          archivo o vacío, `VAL-002`; otro formato, `VAL-003`. **Si ya tenía portada, la
          reemplaza** y la anterior se borra; `coverImageUrl` pasa a
          `/api/v1/academy-images/{imageId}`, pública. Sobre una clase de la que no es instructor, `404`. **Cancelada o terminada, `409`**.

          Exige `live-sessions:set-cover-own`.
          """)
  public LiveSessionDetailResponse ponerPortadaPropia(
      @PathVariable UUID id, @RequestPart(value = "file", required = false) MultipartFile file) {
    return portadas.uploadOwn(id, CoverPart.bytesDe(file));
  }

  @DeleteMapping("/mine/{id}/cover")
  @PreAuthorize("hasAuthority('live-sessions:remove-cover-own')")
  @Operation(
      summary = "Quitar la portada de una clase en vivo mía",
      description =
          """
          Como quitar la portada, sobre una clase de la que es instructor; una ajena responde
          `404`.

          Exige `live-sessions:remove-cover-own`.
          """)
  public LiveSessionDetailResponse quitarPortadaPropia(@PathVariable UUID id) {
    return portadas.removeOwn(id);
  }
}
