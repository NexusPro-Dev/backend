package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.AfftrackRate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de escritura de los escalones afftrack de rol (`RF-CM-015` a `RF-CM-018`).
 *
 * <p>«Un valor por límite» (`RN-CM-039`) lo cierra {@code uq_afftrack_rates_product_role_threshold}
 * en el esquema; {@link #existsAlive} es solo el mensaje del camino normal.
 */
public interface AfftrackRateRepository {

  /** Guarda el escalón; traduce el choque del índice único a {@code 409}. */
  AfftrackRate save(AfftrackRate escalon);

  /** El escalón vivo, leído con bloqueo de fila. Uno retirado se devuelve vacío. */
  Optional<AfftrackRate> findAliveForUpdate(UUID id);

  /**
   * El escalón, vivo o retirado, con bloqueo de fila: el retiro distingue {@code 404} de {@code
   * 409}.
   */
  Optional<AfftrackRate> findAnyForUpdate(UUID id);

  /**
   * ¿Hay otro escalón vivo del mismo producto, rol y límite?
   *
   * @param exceptoId el escalón que se corrige, para no chocar consigo mismo; nulo en el alta
   */
  boolean existsAlive(UUID productId, UUID roleId, int threshold, UUID exceptoId);

  /** Vuelca lo pendiente, traduciendo el choque del índice igual que {@link #save}. */
  void flushChanges();
}
