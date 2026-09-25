package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.ChangeCourseModuleStatusRequest;
import com.factech.nexus.modules.academy.application.CourseModuleDetailResponse;
import com.factech.nexus.modules.academy.application.DeleteCourseModuleRequest;
import com.factech.nexus.modules.academy.application.RegisterCourseModuleRequest;
import com.factech.nexus.modules.academy.application.UpdateCourseModuleRequest;
import com.factech.nexus.modules.academy.domain.service.ChangeCourseModuleStatusService;
import com.factech.nexus.modules.academy.domain.service.DeleteCourseModuleService;
import com.factech.nexus.modules.academy.domain.service.RegisterCourseModuleService;
import com.factech.nexus.modules.academy.domain.service.UpdateCourseModuleService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los módulos de un curso (`AC`, `RF-AC-022` a `RF-AC-025`): las partes del curso, anidadas bajo él
 * porque nacen dentro y no se mueven (`RN-AC-019`). <b>Sin listado ni detalle propios</b>: se leen
 * dentro del curso, y cada escritura devuelve <b>el módulo</b> —no el curso—, que es la fila del
 * árbol que cambió. Todo bajo {@code courses:update} (`ac.md` §7): quien puede corregir el curso
 * puede armarlo. La portada del módulo llega con `RF-AC-026` y `RF-AC-027`.
 */
@RestController
@RequestMapping("/api/v1/courses/{courseId}/modules")
@Tag(
    name = "Módulos de curso",
    description = "Las partes de un curso: agrupan lecciones y se ordenan dentro de él.")
public class CourseModuleController {

  private final RegisterCourseModuleService alta;
  private final UpdateCourseModuleService correccion;
  private final ChangeCourseModuleStatusService estado;
  private final DeleteCourseModuleService retiro;

  public CourseModuleController(
      RegisterCourseModuleService alta,
      UpdateCourseModuleService correccion,
      ChangeCourseModuleStatusService estado,
      DeleteCourseModuleService retiro) {
    this.alta = alta;
    this.correccion = correccion;
    this.estado = estado;
    this.retiro = retiro;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Registrar un módulo en un curso",
      description =
          """
          Registra una parte del curso con **título y orden**, obligatorios, y descripción
          corta, descripción larga y video de presentación —**de YouTube o de Vimeo**—, opcionales. **Nace `INACTIVO`,
          vacío y dentro del curso de la ruta**, del que no se mueve: enviar `courseId`,
          `status`, `lessons` o `coverImageUrl` responde `400`.

          **El título es único entre los módulos vivos del mismo curso**, sin mayúsculas
          ni acentos; el mismo título en otro curso es legítimo, y un retirado libera el
          suyo. El curso tiene que estar **vivo**, en cualquier estado — un curso activo
          admite el módulo igual, que no cambia lo que el aula enseña hasta activarse.

          La respuesta es **el módulo**, no el curso: `courseId`, `coverImageUrl`
          presente y nulo, `lessons` vacío, cero segundos y `offerable: false` con
          `offerableReason` «inactivo». Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Módulo registrado, inactivo y vacío.",
        content = @Content(schema = @Schema(implementation = CourseModuleDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, juntos (`VAL-001` a `VAL-005`), o un campo no admitido (`VAL-006`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o está retirado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Título ya en uso por un módulo vivo del curso (`EX-002`)")
  })
  public ResponseEntity<CourseModuleDetailResponse> register(
      @PathVariable UUID courseId, @Valid @RequestBody RegisterCourseModuleRequest peticion) {
    CourseModuleDetailResponse creado = alta.register(courseId, peticion);
    return ResponseEntity.created(
            URI.create("/api/v1/courses/" + courseId + "/modules/" + creado.id()))
        .body(creado);
  }

  @PatchMapping("/{moduleId}")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Editar un módulo",
      description =
          """
          Corrección **parcial**: solo lo que viene cambia. Admite `title`,
          `shortDescription`, `longDescription`, `presentationVideoUrl` y `displayOrder`,
          y exige al menos uno. **Ausente y nulo no significan lo mismo**: solo las
          descripciones y el video admiten `null` —también en un módulo activo, que sigue
          activo—; el nulo en título y orden responde `400`, junto con los demás errores.

          El título nuevo no puede ser el de **otro** módulo vivo del mismo curso. **El
          módulo no cambia de curso**: `courseId` en el cuerpo es `400`, y un módulo de
          otro curso responde `404`, como el retirado. Un cuerpo sin cambios de valor
          responde `200` sin auditar. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El módulo corregido.",
        content = @Content(schema = @Schema(implementation = CourseModuleDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido, campos que no admiten vaciarse, cuerpo vacío o campo no admitido (`VAL-001` a `VAL-006`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El módulo no existe, está retirado o no es de ese curso (`EX-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Título ya en uso por otro módulo vivo del curso (`EX-001`)")
  })
  public CourseModuleDetailResponse corregir(
      @PathVariable UUID courseId,
      @PathVariable UUID moduleId,
      @Valid @RequestBody UpdateCourseModuleRequest peticion) {
    return correccion.update(courseId, moduleId, peticion);
  }

  @PatchMapping("/{moduleId}/status")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Cambiar el estado de un módulo",
      description =
          """
          `ACTIVO` publica; `INACTIVO` despublica. **Activar exige una lección activa** y
          nada más —ni descripciones ni portada—. **Desactivar no exige nada**, y
          desactivar el último módulo activo de un curso activo **no toca el curso**: queda
          `ACTIVO` y deja de ofrecerse, y su detalle lo dice.

          Con un módulo activo, **el curso puede activarse** (`PATCH /courses/{id}/status`).
          Pedir el estado que ya tiene responde `200` sin escribir. Un módulo retirado, o
          de otro curso, responde `404`. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El módulo con su estado.",
        content = @Content(schema = @Schema(implementation = CourseModuleDetailResponse.class))),
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
        description = "El módulo no existe, está retirado o no es de ese curso (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description = "No puede activarse: sin lección activa (`EX-002`)")
  })
  public CourseModuleDetailResponse cambiarEstado(
      @PathVariable UUID courseId,
      @PathVariable UUID moduleId,
      @Valid @RequestBody ChangeCourseModuleStatusRequest peticion) {
    return estado.change(courseId, moduleId, peticion);
  }

  @PostMapping("/{moduleId}/deletion")
  @PreAuthorize("hasAuthority('courses:update')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retirar un módulo",
      description =
          """
          Baja **lógica y con motivo**: la fila se queda con `deletedAt` puesto y nada
          más cambiado —ni el estado—, y **arrastra sus lecciones vivas** con el mismo
          instante, el mismo motivo y una fila de auditoría por cada una. **`POST` y no
          `DELETE`** porque el cuerpo lleva el motivo, obligatorio.

          **El curso no cambia**: retirar su último módulo activo lo deja `ACTIVO` y
          no ofrecible. El módulo retirado y sus lecciones siguen en el detalle del curso,
          marcados; su título queda libre en el curso. Retirar uno ya retirado responde
          `409`, distinto del `404` del inexistente o del de otro curso. Exige
          `courses:update` — el módulo es parte del curso, y no hace falta `courses:delete`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Módulo retirado, con sus lecciones."),
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
        description = "El módulo no existe o no es de ese curso (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "El módulo ya está retirado (`EX-002`)")
  })
  public void retirar(
      @PathVariable UUID courseId,
      @PathVariable UUID moduleId,
      @RequestBody(required = false) DeleteCourseModuleRequest peticion) {
    retiro.delete(courseId, moduleId, peticion);
  }
}
