package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.UserAfftrackRate;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de escritura de los escalones afftrack de persona (`RF-CM-019`).
 *
 * <p>«Un escalón vigente por persona, producto y límite cada día» (`RN-CM-039`) lo cierra {@code
 * ex_user_afftrack_rates_vigente}; {@link #overlaps} es solo el mensaje del camino normal.
 */
public interface UserAfftrackRateRepository {

  /** Guarda el escalón; traduce la violación del {@code EXCLUDE} a {@code 409}. */
  UserAfftrackRate save(UserAfftrackRate escalon);

  /** El escalón, vivo o retirado, leído con bloqueo de fila. */
  Optional<UserAfftrackRate> findAnyForUpdate(UUID id);

  /**
   * ¿Otro escalón vivo de la misma persona, producto y límite cubre algún día de ese intervalo?
   *
   * @param excluido el que se corrige; nulo en el alta
   */
  boolean overlaps(
      UUID userId,
      UUID productId,
      int threshold,
      LocalDate validFrom,
      LocalDate validTo,
      UUID excluido);

  /** Vuelca lo pendiente, traduciendo el {@code EXCLUDE} igual que {@link #save}. */
  void flushChanges();

  /** El mismo {@code 409} para la comprobación previa y para la carrera. */
  static BusinessRuleException solapamiento() {
    String mensaje =
        "Esa persona ya tiene una comisión afftrack viva sobre ese producto con ese límite en parte"
            + " de ese periodo.";
    return new BusinessRuleException(
        "EX-002", mensaje, List.of(new FieldError("validFrom", "EX-002", mensaje)));
  }
}
