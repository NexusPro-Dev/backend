package com.factech.nexus.modules.system.audit.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que los cuatro listados de auditoría tienen en común (`RF-SP-011` a `RF-SP-014`).
 *
 * <p>Los cuatro paginan igual, acotan por rango de fechas igual y ordenan igual —del más reciente
 * al más antiguo—, de modo que la parte común se declara una vez. Lo que cambia entre ellos son los
 * filtros propios de cada registro, que cada petición añade.
 *
 * <p><b>{@code from} y {@code to} son instantes, no fechas.</b> Si se admitiera {@code 2026-08-01},
 * el servidor tendría que elegir una zona horaria para interpretarla y elegiría la suya, que casi
 * nunca es la de quien consulta: alguien en Bogotá pediría «el día 1» y recibiría desde las 19:00
 * del día anterior. Los eventos se guardan en tiempo universal y la conversión es del cliente
 * (`spec.md` §13); esta es la forma de que pueda hacerla.
 *
 * <p><b>El rango es semiabierto</b> —{@code occurred_at >= from AND occurred_at < to}—. Con ambos
 * extremos inclusivos, dos rangos consecutivos —agosto y septiembre— devolverían dos veces el
 * evento que cayera exactamente en la medianoche del 1 de septiembre, y quien recorra la línea de
 * tiempo mes a mes contaría de más.
 */
public interface AuditFilters {

  Integer page();

  Integer size();

  OffsetDateTime from();

  OffsetDateTime to();

  /**
   * Identificador de la petición que produjo el evento; nulo en los registros que no lo filtran.
   */
  UUID correlationId();
}
