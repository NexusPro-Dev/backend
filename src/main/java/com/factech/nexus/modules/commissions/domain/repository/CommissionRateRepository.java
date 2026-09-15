package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de escritura del catálogo de tasas por rol.
 *
 * <p><b>Perdió el bloqueo y la consulta de solapamiento</b> que tenía hasta el 01-09-2026, y no es
 * una simplificación gratuita: <b>sin vigencia no hay nada que pueda solaparse</b>.
 *
 * <p>Lo que dos peticiones simultáneas <b>sí</b> pueden burlar es «un rol por producto»
 * (`RN-CM-013`), y desde el 15-09-2026 eso lo cierra {@code uq_commission_rates_product_role} en
 * esta misma tabla: la tasa nace con su producto (`RN-CM-021`), y {@link #existsAlive} es solo el
 * mensaje del camino normal.
 */
public interface CommissionRateRepository {

  /** Guarda la tasa. */
  CommissionRate save(CommissionRate tasa);

  /** La tasa viva, si existe. Una retirada se devuelve vacía: se trata como inexistente. */
  Optional<CommissionRate> findAlive(UUID id);

  /**
   * La tasa exista o no esté viva.
   *
   * <p>Es lo que permite distinguir «no existe» —{@code 404}— de «ya estaba retirada» —{@code
   * 409}—. Decir que no existe escondería que el retiro <b>ya ocurrió</b>, que es lo que quien
   * repite la operación necesita saber.
   */
  Optional<CommissionRate> findAny(UUID id);

  /**
   * `RN-CM-013`: ¿hay ya una tasa viva de ese rol sobre ese producto? Es la comprobación con
   * mensaje del camino normal; la carrera la cierra {@code uq_commission_rates_product_role}.
   */
  boolean existsAlive(UUID productId, UUID roleId);

  /**
   * ¿La tasa está asociada a algún producto?
   *
   * <p>Lo necesita el retiro: retirar una tasa asociada <b>dejaría de pagar sin que nada lo
   * dijera</b>, porque la asociación seguiría ahí apuntando a una fila muerta.
   */

  /**
   * Fuerza el volcado de lo pendiente dentro de la transacción.
   *
   * <p>La corrección no llama a {@code save}: la entidad está gestionada y el {@code UPDATE} sale
   * en el {@code commit}, <b>fuera de todo</b> {@code try}. Aquí ya no hay ninguna restricción que
   * traducir, pero el volcado explícito se conserva para que el orden de las escrituras respecto a
   * la auditoría siga siendo el declarado y no el que decida Hibernate.
   */
  void flushChanges();
}
