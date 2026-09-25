package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.ChangeLessonStatusRequest;
import com.factech.nexus.modules.academy.application.DeleteLessonRequest;
import com.factech.nexus.modules.academy.application.LessonResponse;
import com.factech.nexus.modules.academy.application.RegisterLessonRequest;
import com.factech.nexus.modules.academy.application.UpdateLessonRequest;
import com.factech.nexus.modules.academy.domain.service.ChangeLessonStatusService;
import com.factech.nexus.modules.academy.domain.service.DeleteLessonService;
import com.factech.nexus.modules.academy.domain.service.GetLessonService;
import com.factech.nexus.modules.academy.domain.service.RegisterLessonService;
import com.factech.nexus.modules.academy.domain.service.UpdateLessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las lecciones de un módulo (`AC`, `RF-AC-028` a `RF-AC-031` y `RF-AC-036`): lo que se estudia,
 * anidado bajo su módulo y su curso porque nace dentro y no se mueve (`RN-AC-019`). <b>La ruta
 * afirma la pertenencia en los tres niveles</b>: una lección de otro módulo, o un módulo de otro
 * curso, es {@code 404}. Cada escritura devuelve <b>la lección con su contenido</b>, y el {@code
 * GET} es la lectura de administración que lo trae sin editar.
 */
@RestController
@RequestMapping("/api/v1/courses/{courseId}/modules/{moduleId}/lessons")
@Tag(
    name = "Lecciones",
    description = "Lo que se estudia: un video o un texto dentro de un módulo.")
public class LessonController {

  private final RegisterLessonService alta;
  private final GetLessonService detalle;
  private final UpdateLessonService correccion;
  private final ChangeLessonStatusService estado;
  private final DeleteLessonService retiro;

  public LessonController(
      RegisterLessonService alta,
      GetLessonService detalle,
      UpdateLessonService correccion,
      ChangeLessonStatusService estado,
      DeleteLessonService retiro) {
    this.alta = alta;
    this.detalle = detalle;
    this.correccion = correccion;
    this.estado = estado;
    this.retiro = retiro;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Registrar una lección en un módulo",
      description =
          """
          Registra una lección con **tipo (`VIDEO` o `TEXTO`), título, duración en
          segundos enteros y orden**, obligatorios, y descripción, contenido y `open`
          opcionales. **`durationSeconds` es en segundos** desde el 25-09-2026 —un video
          de 12 min 34 s es `754`—, y las sumas del módulo (`durationSeconds`) y del
          curso (`totalDurationSeconds`) también; el formato «1 h 05 min» es del
          frontend. **El tipo manda sobre el contenido**: en un `VIDEO` es una URL
          absoluta http o https; en un `TEXTO` es Markdown que **el backend guarda y
          devuelve sin interpretar ni sanear** — quien lo pinta es el frontend, y tiene
          que hacerlo con un conversor que no ejecute lo que encuentre.

          **Nace `INACTIVA`**, con `open` falsa si no vino, y **el contenido es opcional al
          registrar y obligatorio para activar**. El título es único entre las lecciones
          vivas del mismo módulo. El módulo tiene que estar **vivo y ser del curso de la
          ruta**; enviar `status`, `moduleId` o `courseId` es `400`. Los errores de forma
          llegan **juntos**, incluido el del contenido de un video.

          **La auditoría lleva la longitud del contenido y no el texto.** La respuesta es
          la lección entera, contenido incluido. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Lección registrada, inactiva.",
        content = @Content(schema = @Schema(implementation = LessonResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, juntos (`VAL-001` a `VAL-007`), o un campo no admitido (`VAL-008`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El módulo no existe, está retirado o no es de ese curso (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Título ya en uso por una lección viva del módulo (`EX-002`)")
  })
  public ResponseEntity<LessonResponse> register(
      @PathVariable UUID courseId,
      @PathVariable UUID moduleId,
      @RequestBody RegisterLessonRequest peticion) {
    LessonResponse creada = alta.register(courseId, moduleId, peticion);
    return ResponseEntity.created(
            URI.create(
                "/api/v1/courses/" + courseId + "/modules/" + moduleId + "/lessons/" + creada.id()))
        .body(creada);
  }

  @GetMapping("/{lessonId}")
  @PreAuthorize("hasAuthority('courses:read')")
  @Operation(
      summary = "Consultar el detalle de una lección",
      description =
          """
          La lección entera **con su contenido**, para administración: es la lectura que
          permite ver lo que se escribió sin editarlo, porque el detalle del curso trae el
          árbol sin el contenido a propósito. **Se devuelve también una retirada**, con
          `deletedAt` y `deletionReason` —también la que arrastró el retiro de su módulo o
          de su curso—: es la lectura de administración y devuelve todo, incluida una
          lección activa a la que se le vació el contenido.

          La ruta afirma la pertenencia: una lección de otro módulo, o un módulo de otro
          curso, responde `404`. Es la misma forma que devuelven las escrituras. Exige
          `courses:read`; `courses:learn` no habilita — el aula la lee por otra ruta.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La lección con su contenido.",
        content = @Content(schema = @Schema(implementation = LessonResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:read` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "La lección no existe en esa ruta (`EX-001`)")
  })
  public LessonResponse detalle(
      @PathVariable UUID courseId, @PathVariable UUID moduleId, @PathVariable UUID lessonId) {
    return detalle.detail(courseId, moduleId, lessonId);
  }

  @PatchMapping("/{lessonId}")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Editar una lección",
      description =
          """
          Corrección **parcial** sobre `type`, `title`, `description`, `content`,
          `durationSeconds`, `displayOrder` y `open`; exige al menos uno. Solo la
          descripción y el contenido admiten `null` —**también en una lección activa, que
          sigue activa y deja de ofrecerse**—; el nulo en los otros cinco es `400`.

          **Cambiar el tipo exige que el contenido resultante case con él**: pasar a
          `VIDEO` con un texto guardado y sin URL en la petición responde `400` **sin
          aplicar nada**; pasar a `TEXTO` con una URL guardada se admite. **El contenido se
          audita como longitud y no como texto.** La lección no cambia de módulo: `moduleId`,
          `courseId` y `status` en el cuerpo son `400`. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La lección corregida, con su contenido.",
        content = @Content(schema = @Schema(implementation = LessonResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido, campos que no admiten vaciarse, la pareja tipo–contenido que no casa, cuerpo vacío o campo no admitido (`VAL-001` a `VAL-006`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description =
            "La lección no existe, está retirada o no es de ese módulo y curso (`EX-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Título ya en uso por otra lección viva del módulo (`EX-001`)")
  })
  public LessonResponse corregir(
      @PathVariable UUID courseId,
      @PathVariable UUID moduleId,
      @PathVariable UUID lessonId,
      @Valid @RequestBody UpdateLessonRequest peticion) {
    return correccion.update(courseId, moduleId, lessonId, peticion);
  }

  @PatchMapping("/{lessonId}/status")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Cambiar el estado de una lección",
      description =
          """
          `ACTIVO` publica; `INACTIVO` despublica. **Activar exige contenido**, de
          cualquiera de los dos tipos, y nada más. **Desactivar no exige nada**; desactivar
          la última activa de un módulo activo deja el módulo `ACTIVO` y no ofrecible, y la
          duración del módulo y del curso bajan en su siguiente lectura.

          Pedir el estado que ya tiene responde `200` sin escribir. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La lección con su estado.",
        content = @Content(schema = @Schema(implementation = LessonResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido o estado ausente o fuera de dominio (`VAL-001`, `VAL-002`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description =
            "La lección no existe, está retirada o no es de ese módulo y curso (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "No puede activarse: sin contenido (`EX-002`)")
  })
  public LessonResponse cambiarEstado(
      @PathVariable UUID courseId,
      @PathVariable UUID moduleId,
      @PathVariable UUID lessonId,
      @Valid @RequestBody ChangeLessonStatusRequest peticion) {
    return estado.change(courseId, moduleId, lessonId, peticion);
  }

  @PostMapping("/{lessonId}/deletion")
  @PreAuthorize("hasAuthority('courses:update')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retirar una lección",
      description =
          """
          Baja **lógica y con motivo**: la fila se queda con `deletedAt` puesto y nada
          más cambiado —ni el estado, ni el contenido, ni `open`—. **Es la única
          instantánea de auditoría que lleva el contenido entero**: el registro de
          eliminación es donde el texto queda para quien lo quiera recuperar.

          **No arrastra nada** y **el módulo y el curso no cambian**: retirar la última
          lección activa deja el módulo `ACTIVO` y no ofrecible. Su título queda libre en
          el módulo. Retirar una ya retirada responde `409`, distinto del `404` de la
          inexistente o de otro módulo. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Lección retirada."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido o motivo ausente, vacío o largo (`VAL-001` a `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "La lección no existe o no es de ese módulo y curso (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "La lección ya está retirada (`EX-002`)")
  })
  public void retirar(
      @PathVariable UUID courseId,
      @PathVariable UUID moduleId,
      @PathVariable UUID lessonId,
      @RequestBody(required = false) DeleteLessonRequest peticion) {
    retiro.delete(courseId, moduleId, lessonId, peticion);
  }
}
