package com.factech.nexus.shared.audit;

import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEnums.ErrorType;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que cada registro necesita <b>además</b> del núcleo común.
 *
 * <p>Ninguno de estos tipos lleva actor, correlación, IP ni agente de usuario: los toma el escritor
 * del contexto de la petición (`architecture.md` §6.6.1). Que quien emite el evento no pueda
 * proporcionarlos es intencional — es lo que impide que un caso de uso invente un actor o se olvide
 * de la IP, y lo que hace que una operación sin petición HTTP detrás escriba las tres columnas en
 * nulo sin tener que acordarse de hacerlo.
 */
public final class AuditEvents {

  private AuditEvents() {}

  /**
   * Alta o edición confirmada.
   *
   * @param module código del módulo que originó el evento
   * @param entity nombre lógico de la entidad ({@code roles}, {@code users})
   * @param entityId identificador del registro afectado
   * @param action {@code CREATE} o {@code UPDATE}
   * @param changes en {@code CREATE}, el estado inicial completo; en {@code UPDATE}, solo los
   *     campos modificados con su {@code before} y su {@code after}. Los campos enmascarados nunca
   *     entran (Art. IV.8)
   */
  public record ChangeEvent(
      String module,
      String entity,
      UUID entityId,
      ChangeAction action,
      Map<String, Object> changes) {}

  /**
   * Baja lógica, física o de una asociación.
   *
   * @param reason motivo declarado por el actor. Obligatorio salvo en {@code ASSOCIATION} (Art.
   *     V.13), y el esquema exige contenido, no solo presencia
   * @param snapshot estado completo al momento de eliminarse. Sin él, la fila dice que un
   *     identificador fue eliminado y ya nadie recuerda qué era
   */
  public record DeletionEvent(
      String module,
      String entity,
      UUID entityId,
      DeletionType deletionType,
      String reason,
      Map<String, Object> snapshot) {}

  /**
   * Fallo no controlado o rechazo por regla de negocio.
   *
   * <p>No todo error entra aquí: la validación de formato ({@code 400}), el {@code 401} y el {@code
   * 404} los cubre {@code request_log}, y la denegación de autorización ({@code 403}) va a {@link
   * SecurityEvent} porque no es un fallo del sistema sino el sistema funcionando. El esquema lo
   * impone con {@code ck_audit_error_log_status}.
   *
   * @param message mensaje ya saneado: sin trazas, SQL, rutas ni versiones (Art. VI.5)
   */
  public record ErrorEvent(
      String resource,
      UUID entityId,
      String operation,
      String errorCode,
      ErrorType errorType,
      int httpStatus,
      Severity severity,
      String message) {}

  /**
   * Evento del control de acceso (`security.md` §8).
   *
   * @param targetUserId usuario <b>objeto</b> del evento, distinto del actor. Nulo cuando el evento
   *     no recae sobre ninguna persona
   * @param detail contexto adicional, sujeto al enmascaramiento de `security.md` §7.3. Nunca
   *     contraseñas ni tokens, ni siquiera hasheados (Art. IV.8)
   */
  public record SecurityEvent(
      SecurityEventType eventType,
      Severity severity,
      Outcome outcome,
      UUID targetUserId,
      Map<String, Object> detail) {}
}
