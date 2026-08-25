package com.factech.nexus.modules.system.audit.domain.repository;

import com.factech.nexus.modules.system.audit.application.ChangeAuditItem;
import com.factech.nexus.modules.system.audit.application.DeletionAuditItem;
import com.factech.nexus.modules.system.audit.application.ErrorAuditItem;
import com.factech.nexus.modules.system.audit.application.ListChangeAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListDeletionAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListErrorAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListSecurityAuditRequest;
import com.factech.nexus.modules.system.audit.application.SecurityAuditItem;
import com.factech.nexus.shared.pagination.BoundedCount;
import java.util.List;

/**
 * Lectura de los cuatro registros de auditoría (`RF-SP-011` a `RF-SP-014`).
 *
 * <p><b>Un solo puerto para los cuatro, y no cuatro.</b> Los cuatro planes describían uno por
 * registro; se unifican porque son la misma consulta cuatro veces —predicado opcional, orden fijo
 * por {@code occurred_at DESC, id DESC}, página y conteo acotado— sobre cuatro tablas que comparten
 * el núcleo común de columnas de `architecture.md` §6.6.1. Cuatro interfaces con la misma forma no
 * separan nada que esté acoplado: garantizan cuatro copias del mismo predicado de rango y cuatro
 * ocasiones de que una divergiera de las otras.
 *
 * <p><b>Solo lectura, y no por convención.</b> Estos registros son <b>append-only</b> por diseño
 * (Art. V.8): no existe aquí ningún método que escriba, ni debe existir. Quien escribe es {@code
 * AuditWriter}, en {@code shared}, dentro de la transacción de la operación que lo produce.
 *
 * <p><b>Cada método devuelve la página; el total va aparte</b> y acotado ({@link BoundedCount}),
 * porque estas tablas crecen sin purga y un {@code COUNT(*)} exacto por página es un recorrido
 * completo en cada petición.
 */
public interface AuditQueryRepository {

  /** Una página de eventos de creación y edición, del más reciente al más antiguo. */
  List<ChangeAuditItem> changes(ListChangeAuditRequest filtros, int offset, int limit);

  /** El total de esos eventos, exacto hasta {@code techo}. Mismo predicado que la página. */
  BoundedCount countChanges(ListChangeAuditRequest filtros, int techo);

  List<DeletionAuditItem> deletions(ListDeletionAuditRequest filtros, int offset, int limit);

  BoundedCount countDeletions(ListDeletionAuditRequest filtros, int techo);

  List<ErrorAuditItem> errors(ListErrorAuditRequest filtros, int offset, int limit);

  BoundedCount countErrors(ListErrorAuditRequest filtros, int techo);

  List<SecurityAuditItem> security(ListSecurityAuditRequest filtros, int offset, int limit);

  BoundedCount countSecurity(ListSecurityAuditRequest filtros, int techo);
}
