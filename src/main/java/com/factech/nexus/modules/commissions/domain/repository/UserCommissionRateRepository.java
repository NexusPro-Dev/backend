package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de escritura de las tasas personalizadas.
 *
 * <p><b>Aquí vive lo que el catálogo por rol perdió</b>: el bloqueo y la traducción del
 * solapamiento. Es la <b>única</b> tabla del módulo con vigencia y, por tanto, la única cuya regla
 * dos peticiones simultáneas pueden burlar.
 */
public interface UserCommissionRateRepository {

  /**
   * Guarda la tasa.
   *
   * @throws com.factech.nexus.shared.error.BusinessRuleException si se solapa con otra viva de la
   *     misma persona — la violación de {@code uq_user_commission_rates_vigente}, traducida
   */
  UserCommissionRate save(UserCommissionRate tasa);

  /**
   * Serializa las operaciones sobre la <b>misma persona</b> dentro de la transacción.
   *
   * <p><b>No sustituye a la restricción del motor, la acompaña.</b> La autoridad sigue siendo
   * {@code uq_user_commission_rates_vigente}; esto solo hace que dos peticiones de la misma persona
   * se pongan en fila en lugar de chocar en el índice.
   *
   * <p><b>Existe por algo medido y no previsto en el plan</b> (28-08-2026, sobre la tabla
   * anterior): sin él, dos altas simultáneas con rangos que se solapan <b>se interbloquean</b>
   * —cada una espera a que la otra confirme su entrada en el índice— y PostgreSQL aborta una con
   * {@code 40P01}. El cliente recibía un {@code 500} en lugar del {@code 409} que le toca. Del
   * 11-09-2026 al 16-09-2026, sin el {@code EXCLUDE}, fue la única garantía de `RN-CM-006`; desde
   * `V10` vuelve a ser el acompañante.
   */
  void lockUser(UUID userId);

  /** La tasa viva, si existe. Una retirada se devuelve vacía: se trata como inexistente. */
  Optional<UserCommissionRate> findAlive(UUID id);

  /** La tasa exista o no esté viva, para distinguir el {@code 404} del {@code 409}. */
  Optional<UserCommissionRate> findAny(UUID id);

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
   * Con qué tasa viva de esa persona <b>sobre ese producto</b> se solapa el periodo declarado, si
   * con alguna (`RN-CM-006`, por persona y producto desde el 16-09-2026).
   *
   * <p><b>Se consulta ANTES de escribir para dar el mensaje, y no como garantía</b>: la carrera que
   * esta consulta no puede ver la cierra la restricción del motor, y el adaptador la traduce al
   * mismo {@link #solapamiento()}.
   */
  Optional<UserCommissionRate> findOverlapping(
      UUID userId, UUID productId, LocalDate validFrom, LocalDate validTo, UUID excluida);

  /**
   * El {@code 409} de `RN-CM-006`, escrito una sola vez: lo lanza el caso de uso cuando {@link
   * #findOverlapping} encuentra algo, y el adaptador cuando el motor rechaza la carrera que esa
   * consulta no pudo ver. Para el cliente es el mismo hecho, llegara por donde llegara.
   */
  static BusinessRuleException solapamiento() {
    String mensaje =
        "Esa persona ya tiene una tasa viva sobre ese producto en parte de ese periodo.";
    return new BusinessRuleException(
        "EX-006", mensaje, List.of(new FieldError("validFrom", "EX-006", mensaje)));
  }
}
