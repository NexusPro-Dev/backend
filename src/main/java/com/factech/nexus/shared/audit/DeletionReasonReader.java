package com.factech.nexus.shared.audit;

import java.util.Optional;
import java.util.UUID;

/**
 * Lectura <b>estrecha</b> del motivo con el que se eliminó una entidad (`RF-PM-003` · `T-01`).
 *
 * <h2>Por qué vive aquí y no detrás de un puerto de `SP`</h2>
 *
 * <p>La auditoría es <b>infraestructura compartida</b>: cada módulo <b>escribe</b> en ella a través
 * de {@link AuditWriter}, y lo que `SP` posee es <b>consultarla como producto</b> (`RF-SP-011` a
 * `RF-SP-014`), que es otra cosa. Leer el motivo de la eliminación de <b>una entidad propia</b> es
 * simétrico de escribirlo, y por eso vive junto al escritor.
 *
 * <p>La alternativa —que `PM` uniera {@code audit_deletion_log} desde su propia consulta— lo ataría
 * al esquema de un almacén que no gobierna. La otra —duplicar el motivo en una columna de {@code
 * products}— crearía dos verdades que divergen en cuanto una se corrija.
 *
 * <h2>Qué NO abre esta lectura</h2>
 *
 * <p>Está escrito porque es el riesgo del componente: <b>un puerto de lectura sobre la auditoría
 * puede convertirse en su puerta trasera</b>. Nace estrecho y se prueba que lo siga siendo —
 * ampliarlo exige decidirlo, no basta con añadir un método.
 *
 * <ul>
 *   <li><b>Solo el motivo</b>: ni el actor, ni la instantánea de lo eliminado, ni el instante.
 *       Quien quiera eso usa `RF-SP-012`, que exige {@code audit:read-deletions}.
 *   <li><b>Solo de una entidad concreta</b>: no hay filtros, ni paginación, ni rango de fechas. No
 *       se puede recorrer el registro con esto.
 *   <li><b>Cada módulo alcanza solo lo suyo</b>: el módulo y la entidad son parte de la pregunta, y
 *       preguntar por lo ajeno devuelve vacío. No es una comprobación de permisos —no la hay—: es
 *       que la clave por la que se busca incluye de quién es la fila.
 * </ul>
 */
public interface DeletionReasonReader {

  /**
   * El motivo con el que se eliminó esa entidad, si se eliminó.
   *
   * @param module módulo dueño de la entidad, tal como se escribió al registrar la eliminación
   * @param entity nombre de la tabla de la entidad
   * @param entityId identificador de la fila eliminada
   * @return el motivo literal, o vacío si no hay eliminación registrada para esa entidad
   */
  Optional<String> reasonFor(String module, String entity, UUID entityId);
}
