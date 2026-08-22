package com.factech.nexus.shared.audit;

import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditEvents.ErrorEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;

/**
 * Escritura de los cuatro registros de auditoría (`RF-SP-001` · `T-07`, `T-08`).
 *
 * <p><b>Vive en {@code shared} y no en un módulo</b> porque <b>todos</b> los módulos emiten eventos
 * y ninguno puede depender de la infraestructura de otro (`architecture.md` §5.1, §5.3). La tensión
 * con {@code requirements/sp.md} §10, que declara a `SP` dueño de las cuatro tablas, se resuelve
 * así: la <b>escritura</b> es transversal y vive aquí; la <b>propiedad</b> de `SP` se materializa
 * en que las migraciones y la API de lectura (`RF-SP-011` a `RF-SP-014`) le pertenecen. Ningún
 * módulo escribe en tablas de otro: todos escriben en las suyas a través de un componente
 * compartido.
 *
 * <p><b>Cada método declara su transacción, y no es un detalle.</b> Es la diferencia entre una
 * auditoría que dice la verdad y una que deja huecos o inventa hechos:
 *
 * <ul>
 *   <li>{@link #recordChange} y {@link #recordDeletion} exigen una transacción <b>ya abierta</b> y
 *       se unen a ella (Art. V.14). Si la operación se revierte, su evento también; si el evento
 *       falla, la operación falla.
 *   <li>{@link #recordError} y {@link #recordSecurity} abren la suya propia. Un rechazo se registra
 *       precisamente <b>mientras</b> la transacción de negocio se revierte: escrito dentro de ella,
 *       el {@code rollback} borraría justo el evento que hay que conservar.
 *   <li>{@link #recordSecurityAfterCommit} añade a lo anterior la espera al {@code commit}. Ver su
 *       documentación: es el caso que no es obvio.
 * </ul>
 */
public interface AuditWriter {

  /** Alta o edición confirmada. Exige transacción abierta y se une a ella. */
  void recordChange(ChangeEvent evento);

  /** Eliminación confirmada. Exige transacción abierta y se une a ella. */
  void recordDeletion(DeletionEvent evento);

  /** Fallo o rechazo. Transacción propia e independiente. */
  void recordError(ErrorEvent evento);

  /** Evento de seguridad que no depende de ninguna operación de negocio. Transacción propia. */
  void recordSecurity(SecurityEvent evento);

  /**
   * Evento de seguridad que solo es cierto si la operación de negocio se confirmó.
   *
   * <p><b>Por qué esperar al {@code commit}.</b> La transacción independiente es obligatoria, pero
   * por sí sola no basta: emitido antes del {@code commit}, una reversión posterior dejaría un
   * evento {@code SUCCESS} de una operación que nunca ocurrió, y ese evento <b>no se puede
   * retirar</b> porque su transacción ya cerró. Engancharlo al {@code commit} conserva la
   * independencia exigida y elimina el evento fantasma.
   *
   * <p><b>La contrapartida, que se acepta a conciencia:</b> si esta escritura falla después del
   * commit, la operación existe sin evento de seguridad. El fallo no se propaga —la respuesta ya se
   * decidió— pero se registra como {@code ERROR} en el log de aplicación con su correlación, y la
   * ausencia de eventos de seguridad se monitorea (`plan.md` §10).
   *
   * <p>Sin transacción activa escribe de inmediato: no hay {@code commit} que esperar.
   */
  void recordSecurityAfterCommit(SecurityEvent evento);
}
