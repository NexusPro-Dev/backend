package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import java.util.Optional;
import java.util.UUID;

/** Puerto de escritura de las tarifas de comisión. */
public interface CommissionRateRepository {

  /**
   * Guarda la tarifa.
   *
   * @throws com.factech.nexus.shared.error.BusinessRuleException si se solapa con otra viva del
   *     mismo caso — la violación de {@code ex_commission_rates_sin_solape}, traducida
   */
  CommissionRate save(CommissionRate tarifa);

  /**
   * Serializa las operaciones sobre el MISMO caso —rol, producto y persona— dentro de la
   * transacción.
   *
   * <p><b>No sustituye a la restricción del motor, la acompaña.</b> La autoridad sigue siendo
   * {@code ex_commission_rates_sin_solape}; esto solo hace que dos peticiones del mismo caso se
   * pongan en fila en lugar de chocar en el índice.
   *
   * <p><b>Existe por algo medido y no previsto en el plan</b> (28-08-2026): sin él, dos altas
   * simultáneas con rangos que se solapan <b>se interbloquean</b> —cada una espera a que la otra
   * confirme su entrada en el índice— y PostgreSQL aborta una con {@code 40P01}. El cliente recibía
   * un {@code 500} en lugar del {@code 409} que le toca. Con el bloqueo, la segunda espera, ve la
   * fila de la primera y recibe su conflicto traducido.
   */
  void lockCase(CommissionRate tarifa);

  /** La tarifa viva, si existe. Una retirada se devuelve vacía: se trata como inexistente. */
  Optional<CommissionRate> findAlive(UUID id);

  /**
   * La tarifa exista o no esté viva.
   *
   * <p>Es lo que permite distinguir «no existe» —{@code 404}— de «ya estaba retirada» —{@code
   * 409}—. Decir que no existe escondería que el retiro <b>ya ocurrió</b>, que es lo que quien
   * repite la operación necesita saber.
   */
  Optional<CommissionRate> findAny(UUID id);

  /**
   * Fuerza el volcado de lo pendiente dentro de la transacción.
   *
   * <p><b>Existe por un defecto vivido, no por gusto.</b> La corrección no llama a {@code save}: la
   * entidad está gestionada y el {@code UPDATE} sale en el {@code commit}, <b>fuera de todo</b>
   * {@code try}, de modo que la violación del solapamiento se escaparía como {@code 500} en lugar
   * del {@code 409} que le toca. Es exactamente lo que le ocurrió a `RF-SP-027` con el correo
   * duplicado.
   *
   * @throws com.factech.nexus.shared.error.BusinessRuleException con la misma traducción que {@link
   *     #save}
   */
  void flushChanges();

  /**
   * Con qué tarifa viva se solapa el periodo declarado, si con alguna.
   *
   * <p><b>Se consulta DESPUÉS de capturar la violación</b>, y no antes: hacerlo antes sería la
   * carrera que la restricción existe para cerrar. En ese punto la otra tarifa existe con certeza,
   * y esto solo sirve para poder decir <b>cuál</b> es en el mensaje.
   */
  Optional<CommissionRate> findOverlapping(
      UUID roleId,
      UUID productId,
      UUID userId,
      java.time.LocalDate validFrom,
      java.time.LocalDate validTo,
      UUID excluida);
}
