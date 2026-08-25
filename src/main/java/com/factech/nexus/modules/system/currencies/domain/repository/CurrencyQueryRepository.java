package com.factech.nexus.modules.system.currencies.domain.repository;

import com.factech.nexus.modules.system.currencies.application.CurrencyItem;
import java.util.List;

/** Puerto de consulta del catálogo (`RF-SP-019`). */
public interface CurrencyQueryRepository {

  /**
   * Catálogo ordenado por código.
   *
   * <p>El orden es fijo y el cliente no puede cambiarlo: sin {@code ORDER BY} explícito PostgreSQL
   * no garantiza orden alguno, y un catálogo que cambia de orden entre dos llamadas hace inútil
   * compararlo. No se ordena por «moneda por defecto primero», que produciría un orden que cambia
   * cuando cambia la moneda por defecto.
   *
   * @param includeInactive si {@code true}, añade las dadas de baja a las activas
   */
  List<CurrencyItem> findAll(boolean includeInactive);

  /** ¿Hay exactamente una moneda por defecto y activa? Lo usa la comprobación de arranque. */
  long countDefaultActive();
}
