package com.factech.nexus.shared.error;

import java.util.List;
import java.util.Map;

/**
 * Identidad probada, <b>con</b> el permiso de la ruta, a la que lo pedido no se le abre: el
 * contenido de una lección cerrada (`RF-AC-035` `EX-002`). Sale como {@code 403}.
 *
 * <p><b>Se separa de {@link ForbiddenException} por la auditoría</b>: aquella registra una
 * denegación de severidad alta en {@code audit_security_log}, y esto no es un intento de intrusión
 * sino un alumno mirando lo que el catálogo le invita a mirar (`RF-AC-035` · plan §6). Lleva
 * miembros de extensión —la invitación— como cualquier {@link DomainException}.
 */
public class NotEntitledException extends ForbiddenException {

  private static final long serialVersionUID = 1L;

  public NotEntitledException(String errorCode, String message, Map<String, Object> extensions) {
    // El código viaja en `errors`, como en toda regla de negocio: es lo que separa
    // este 403 del de permiso (`AUTH-002`), que sale con la lista vacía.
    super(errorCode, message, List.of(new FieldError(null, errorCode, message)), extensions);
  }
}
