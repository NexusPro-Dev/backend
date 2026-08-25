package com.factech.nexus.modules.system.audit.interfaces;

import com.factech.nexus.modules.system.audit.application.ChangeAuditItem;
import com.factech.nexus.modules.system.audit.application.DeletionAuditItem;
import com.factech.nexus.modules.system.audit.application.ErrorAuditItem;
import com.factech.nexus.modules.system.audit.application.ListChangeAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListDeletionAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListErrorAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListSecurityAuditRequest;
import com.factech.nexus.modules.system.audit.application.SecurityAuditItem;
import com.factech.nexus.modules.system.audit.domain.service.AuditQueryService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los cuatro registros de auditoría (`RF-SP-011` a `RF-SP-014`).
 *
 * <p><b>Un controlador para los cuatro.</b> Las rutas son cuatro colecciones hermanas de un mismo
 * recurso raíz —{@code /api/v1/audit/…}—, y un controlador por registro multiplicaría por cuatro la
 * configuración sin separar nada que esté acoplado.
 *
 * <p><b>Compartir clase no comparte autorización.</b> Cada método declara <b>su</b> permiso, y son
 * cuatro distintos a propósito (`security.md` §4.4): quién editó un rol es información de operación
 * y quién intentó entrar y falló es información de seguridad. Un único {@code audit:read} obligaría
 * a conceder la segunda para poder dar la primera.
 *
 * <p><b>Ningún endpoint de escritura, y la ausencia es la implementación.</b> Estos registros son
 * append-only por diseño (Art. V.8): no se cumple con código que rechace, se cumple porque no hay a
 * qué llamar. Un {@code POST} o un {@code DELETE} sobre estas rutas obtiene {@code 405} de Spring
 * sin que nadie lo haya escrito.
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(
    name = "Auditoría",
    description =
        "Los cuatro registros de auditoría del sistema. Solo lectura, y cada uno con su permiso.")
public class AuditController {

  private final AuditQueryService auditoria;

  public AuditController(AuditQueryService auditoria) {
    this.auditoria = auditoria;
  }

  @GetMapping("/changes")
  @PreAuthorize("hasAuthority('audit:read-changes')")
  @Operation(
      summary = "Consultar la auditoría de cambios",
      description =
          """
          Altas y ediciones de todo el sistema, **del más reciente al más
          antiguo**. El orden no se puede cambiar: es parte del significado de un
          registro cronológico.

          **`changes` se devuelve tal como se escribió**, sin interpretar: en un
          `CREATE` es el estado inicial del registro y en un `UPDATE` solo los
          campos que cambiaron, cada uno con su `before` y su `after`.

          **`correlationId` enlaza con la petición** que produjo el evento, y con
          lo que esa misma petición dejó en los otros tres registros. Cuando son
          nulos, `correlationId` e `ipAddress` lo son **a la vez**: significa que
          el cambio no vino de la red —una migración, una tarea programada—, y
          nunca que se olvidara registrarlo. `actorId` nulo se lee igual: lo hizo
          el sistema.

          **Ningún filtro es obligatorio**, ni siquiera el rango de fechas: la
          consulta que más valor tiene es la línea de tiempo completa de un
          registro. `from` y `to` son **instantes** con zona horaria, no fechas
          sueltas, y el rango es **semiabierto** —incluye `from`, excluye `to`—
          para que dos rangos consecutivos no devuelvan dos veces el evento de la
          frontera.

          **`entityId` y `actorId` no se validan contra nada**: la auditoría
          conserva eventos de registros que ya no existen, que es su razón de ser.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de eventos de cambio.",
        content = @Content(schema = @Schema(implementation = PageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Rango de fechas incoherente (`VAL-001`), paginación fuera de límites (`VAL-002`) o"
                + " filtro fuera de su dominio (`VAL-003`). Se devuelven **juntos**",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `audit:read-changes` (`AUTH-002`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public PageResponse<ChangeAuditItem> cambios(
      @ParameterObject @ModelAttribute ListChangeAuditRequest filtros) {
    return auditoria.changes(filtros);
  }

  @GetMapping("/deletions")
  @PreAuthorize("hasAuthority('audit:read-deletions')")
  @Operation(
      summary = "Consultar la auditoría de eliminación",
      description =
          """
          Qué se eliminó, quién lo eliminó y **por qué**, del más reciente al más
          antiguo.

          **`snapshot` es lo que hace útil a este registro**: el estado completo
          del registro en el momento de borrarse. Sin él, la fila diría que un
          identificador fue eliminado y nadie recordaría qué era.

          **`reason` va vacío en las eliminaciones de asociación** —retirar un
          permiso de un rol, por ejemplo—, y es correcto: lo que desaparece es una
          asociación y no una entidad de negocio, de modo que no exige motivo.

          El filtro `reason` **busca por texto** dentro del motivo, sin distinguir
          acentos ni mayúsculas: es prosa que escribió una persona y nadie
          recuerda cómo la redactó. Por lo mismo, buscar por motivo deja fuera las
          eliminaciones de asociación, que no lo llevan.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de eventos de eliminación.",
        content = @Content(schema = @Schema(implementation = PageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Rango incoherente (`VAL-001`), paginación fuera de límites (`VAL-002`) o tipo de"
                + " eliminación fuera de su dominio (`VAL-003`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `audit:read-deletions` (`AUTH-002`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public PageResponse<DeletionAuditItem> eliminaciones(
      @ParameterObject @ModelAttribute ListDeletionAuditRequest filtros) {
    return auditoria.deletions(filtros);
  }

  @GetMapping("/errors")
  @PreAuthorize("hasAuthority('audit:read-errors')")
  @Operation(
      summary = "Consultar la auditoría de error",
      description =
          """
          Fallos no controlados y rechazos por regla de negocio, del más reciente
          al más antiguo.

          **El filtro para el que existe esta consulta es `correlationId`**:
          alguien reporta un error citando el identificador que la respuesta le
          devolvió, y con él se llega al fallo concreto y a la traza técnica que
          lo acompaña.

          **Qué NO aparece aquí, y es la frontera que más importa:** la denegación
          de autorización (`403`) vive en la auditoría de **seguridad**, porque no
          es un fallo del sistema sino el sistema funcionando. Tampoco la
          validación de formato, el `401` ni el `404` — esos los cubre el registro
          de peticiones.

          `message` viene **ya saneado** desde que se escribió: sin trazas, sin
          SQL, sin rutas y sin versiones.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de fallos registrados.",
        content = @Content(schema = @Schema(implementation = PageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Rango incoherente (`VAL-001`), paginación fuera de límites (`VAL-002`) o tipo o"
                + " severidad fuera de su dominio (`VAL-003`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `audit:read-errors` (`AUTH-002`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public PageResponse<ErrorAuditItem> errores(
      @ParameterObject @ModelAttribute ListErrorAuditRequest filtros) {
    return auditoria.errors(filtros);
  }

  @GetMapping("/security")
  @PreAuthorize("hasAuthority('audit:read-security')")
  @Operation(
      summary = "Consultar la auditoría de seguridad",
      description =
          """
          Entradas, bloqueos, cambios de privilegio y denegaciones de
          autorización, del más reciente al más antiguo.

          **Esta consulta se registra a sí misma.** Cada llamada deja un evento
          de seguridad con quién la hizo, cuándo y **con qué filtros**: en este
          registro, el acto de mirar es en sí mismo información de seguridad — y
          quién revisó los accesos de una cuenta ajena es exactamente la clase de
          hecho que conviene conservar. Es la diferencia con los otros tres, que
          se conforman con el registro de peticiones.

          **`targetUserId` es el filtro de la investigación de una cuenta**: qué
          le pasó a esta persona en estas fechas. Los **intentos de acceso
          fallidos no llevan usuario afectado** y por tanto no aparecen ahí: la
          presencia de ese campo delataría que la cuenta existe. Se localizan por
          tipo de evento y rango, leyendo el identificador intentado en `detail`.

          **`detail` no contiene credenciales en ninguna forma** —ni contraseñas,
          ni resúmenes, ni tokens—, y eso se garantiza al escribir.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de eventos de seguridad.",
        content = @Content(schema = @Schema(implementation = PageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Rango incoherente (`VAL-001`), paginación fuera de límites (`VAL-002`) o tipo de"
                + " evento, severidad o resultado fuera de su dominio (`VAL-003`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `audit:read-security` (`AUTH-002`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public PageResponse<SecurityAuditItem> seguridad(
      @ParameterObject @ModelAttribute ListSecurityAuditRequest filtros) {
    return auditoria.security(filtros);
  }
}
