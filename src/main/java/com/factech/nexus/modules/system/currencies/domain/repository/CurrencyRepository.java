package com.factech.nexus.modules.system.currencies.domain.repository;

import com.factech.nexus.modules.system.currencies.domain.models.Currency;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de escritura (`RF-SP-023`).
 *
 * <p>Un solo método, y no hay {@code save}: la moneda se carga gestionada por el contexto de
 * persistencia y el cambio de {@code isActive} se vuelca al confirmar. No hay {@code create} ni
 * {@code delete} porque `RN-SP-010` los prohíbe, y no tenerlos es la forma más barata de que nadie
 * los llame por descuido.
 */
public interface CurrencyRepository {

  /**
   * Carga la moneda <b>bloqueando su fila</b> hasta el final de la transacción.
   *
   * <p>Sin el bloqueo, dos peticiones simultáneas sobre la misma moneda leerían el mismo estado
   * inicial y ambas creerían haberlo cambiado, emitiendo dos eventos de auditoría para un solo
   * cambio real. `FA-001` —«no hubo cambio, no hay evento»— depende de leer el estado que de verdad
   * está vigente.
   */
  Optional<Currency> findByIdForUpdate(UUID id);
}
