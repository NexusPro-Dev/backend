package com.factech.nexus.shared.audit;

/**
 * Dominios cerrados de los cuatro registros de auditoría.
 *
 * <p>Se declaran como enumerados y no como cadenas sueltas porque **cada uno tiene su {@code CHECK}
 * en el esquema** (`V4__create_audit_logs.sql`). Un literal mal escrito desde Java no produciría un
 * dato raro que nadie nota: produciría una violación de integridad dentro de la transacción de
 * auditoría. Tenerlos aquí hace que ese error no llegue a compilar.
 *
 * <p>Los valores son <b>exactamente</b> los que se persisten, sin traducir: el ejemplo {@code
 * RoleStatus.ACTIVE} de `development-guide.md` §4.2 ilustra el uso de mayúsculas, no el idioma, y
 * traducirlos obligaría a una tabla de conversión entre el enum y el {@code CHECK}.
 */
public final class AuditEnums {

  private AuditEnums() {}

  /** Acción registrada en {@code audit_change_log} (`architecture.md` §6.6.2). */
  public enum ChangeAction {
    CREATE,
    UPDATE
  }

  /** Clase de eliminación registrada en {@code audit_deletion_log} (`architecture.md` §6.6.3). */
  public enum DeletionType {
    LOGICAL,
    PHYSICAL,
    ASSOCIATION
  }

  /** Naturaleza del fallo registrado en {@code audit_error_log} (`architecture.md` §6.6.4). */
  public enum ErrorType {
    BUSINESS_RULE,
    INTEGRATION,
    UNHANDLED
  }

  /**
   * Severidad. {@code audit_error_log} solo admite {@code MEDIA} y {@code ALTA}; {@code
   * audit_security_log} admite además {@code INFORMATIVA} (`security.md` §8.2).
   */
  public enum Severity {
    INFORMATIVA,
    MEDIA,
    ALTA
  }

  /** Resultado de un evento de seguridad (`security.md` §8.2). */
  public enum Outcome {
    SUCCESS,
    FAILURE
  }

  /**
   * Catálogo cerrado de eventos de seguridad: los <b>veinte</b> códigos que fija `RF-SP-014` §2 y
   * que replica el {@code CHECK} de {@code ck_audit_security_log_event_type}.
   *
   * <p>Están todos, incluidos los de requerimientos que aún no tienen plan, y es deliberado: un
   * valor que nadie escribe es inerte, mientras que una migración de alteración por requerimiento
   * no lo es.
   *
   * <p><b>La severidad no está ligada al tipo.</b> {@link #AUTHORIZATION_DENIED} tiene dos emisores
   * y dos severidades: la capa de seguridad lo emite con {@code MEDIA} cuando alguien tropieza con
   * un permiso que no tiene, y los casos de uso de `RF-SP-004` a `RF-SP-009` lo emiten con {@code
   * ALTA} cuando salta `RN-SEG-011`.
   */
  public enum SecurityEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCOUNT_LOCKED,
    REFRESH_TOKEN_REUSE,
    LOGOUT,
    AUTHORIZATION_DENIED,
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED,
    ROLE_PERMISSIONS_CHANGED,
    USER_CREATED,
    EMAIL_CHANGED,
    USER_ROLES_ASSIGNED,
    USER_ROLES_REVOKED,
    USER_STATUS_CHANGED,
    USER_DELETED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    SECURITY_AUDIT_READ,

    /**
     * Una ráfaga topó con el límite de tasa (`security.md` §5.5).
     *
     * <p><b>No es un intento de acceso fallido</b>, y por eso no reutiliza {@link #LOGIN_FAILURE}:
     * la credencial ni siquiera llega a comprobarse. Mezclarlos corrompería las dos lecturas que
     * ese registro sirve —el contador de intentos de una cuenta y la investigación de un acceso—,
     * porque una ráfaga bloqueada inflaría el primero sin que nadie haya fallado una contraseña.
     *
     * <p>Añadido el 25-08-2026 con {@code V34}; el catálogo pasa de diecinueve a <b>veinte</b>.
     */
    RATE_LIMIT_EXCEEDED,

    /**
     * La purga retiró familias de sesión ya caducadas (`security.md` §5.5, issue #25).
     *
     * <p><b>Una purga que borra evidencia sin dejar constancia de cuánta borró no es auditable</b>,
     * y esa es toda la razón de este código. La fila que se elimina es la que sostenía la detección
     * de robo por reutilización de `RF-SP-035`: el evento deja escrito cuántas se retiraron, con
     * qué plazo de retención y hasta qué fecha de corte, que es lo que permite responder después
     * «por qué esta sesión ya no está».
     *
     * <p>Su actor es <b>nulo</b>, y no es un dato que falte: no lo hizo nadie, lo hizo el sistema.
     * Su severidad es {@code INFORMATIVA} porque una purga que ocurre a su hora es rutina; lo que
     * merece atención es que <b>deje</b> de ocurrir, y eso no lo cuenta un evento sino su ausencia.
     *
     * <p>Añadido el 25-08-2026 con {@code V36}; el catálogo pasa de veinte a <b>veintiuno</b>.
     */
    SESSION_TOKENS_PURGED
  }
}
