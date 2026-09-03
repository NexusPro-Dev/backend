package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.RateSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de {@link CommissionResolutionRepository}.
 *
 * <p><b>Una sola sentencia sobre dos tablas, unidas por {@code UNION ALL}</b>, y la precedencia es
 * la columna {@code prioridad} del {@code ORDER BY}. Dos consultas encadenadas en Java habrían dado
 * el mismo resultado hoy y habrían puesto la regla en un {@code if} — donde nada la protege de que
 * alguien invierta el orden mientras arregla otra cosa.
 */
@Repository
public class JpaCommissionResolutionRepository implements CommissionResolutionRepository {

  /**
   * `RN-CM-004`, escrita una vez.
   *
   * <p><b>La rama de la persona no filtra por rol ni por producto</b>, y las dos ausencias son la
   * regla: la tasa personalizada <b>gana venda lo que venda</b>, y desde el 01-09-2026 <b>ya no
   * lleva rol</b>, de modo que sigue rigiendo aunque su titular haya dejado de vender.
   *
   * <p><b>La rama del rol exige la asociación</b>, que es `RN-CM-012`: sin fila en {@code
   * product_commission_rates} no hay tarifa, por mucho que el catálogo tenga una tasa para ese rol.
   * Aquí no hay ningún {@code OR ... IS NULL} como en el modelo anterior, y esa es exactamente la
   * inversión de significado.
   *
   * <p><b>El {@code JOIN} entra por la clave compuesta</b> —{@code id} y {@code role_id}—, la misma
   * pareja que declara la clave foránea: así la consulta no puede leer el porcentaje de una tasa
   * cuyo rol no sea el copiado en la asociación.
   *
   * <p><b>Y se filtra {@code deleted_at IS NULL} en las dos ramas.</b> Una tasa retirada que
   * siguiera resolviendo pagaría por algo que alguien declaró que no debió existir.
   *
   * <h2>Las dos ramas proyectan las tres columnas del valor TAL CUAL</h2>
   *
   * <p>La tentación es fundirlas aquí con un {@code COALESCE(percentage, fixed_amount) AS value} y
   * ahorrarse un paso, porque la respuesta lleva <b>un</b> solo campo de valor. <b>Y con eso se
   * pierde de qué columna venía</b>: un {@code 10} de salida ya no diría si es un porcentaje o un
   * importe, y habría que reconstruirlo a partir de {@code rate_type} — lo mismo que hacer la
   * fusión fuera, pero con un sitio más donde equivocarse.
   *
   * <p>Peor: el {@code CHECK} de {@code V50} garantiza que <b>solo una está llena</b>, y esa
   * garantía se aprovecha <b>una sola vez</b>. Fundir aquí y volver a fundir al armar la respuesta
   * dejaría dos sitios que mantener de acuerdo el día que aparezca una tercera forma.
   *
   * <p><b>La fusión la hace quien arma la respuesta.</b> Es una decisión de proyección, no de
   * consulta.
   */
  private static final String SQL =
      """
      SELECT 0 AS prioridad,
             u.id           AS rate_id,
             u.rate_type    AS rate_type,
             u.percentage   AS percentage,
             u.fixed_amount AS fixed_amount,
             u.valid_from   AS valid_from,
             u.valid_to     AS valid_to
        FROM user_commission_rates u
       WHERE u.deleted_at IS NULL
         AND u.user_id = :persona
         AND u.valid_from <= CAST(:fecha AS date)
         AND (u.valid_to IS NULL OR u.valid_to >= CAST(:fecha AS date))

      UNION ALL

      SELECT 1 AS prioridad,
             c.id           AS rate_id,
             c.rate_type    AS rate_type,
             c.percentage   AS percentage,
             c.fixed_amount AS fixed_amount,
             NULL           AS valid_from,
             NULL           AS valid_to
        FROM product_commission_rates a
        JOIN commission_rates c
          ON c.id = a.commission_rate_id AND c.role_id = a.role_id
       WHERE c.deleted_at IS NULL
         AND a.role_id    = CAST(:rol AS uuid)
         AND a.product_id = :producto

       ORDER BY prioridad
       LIMIT 1
      """;

  private final EntityManager em;

  public JpaCommissionResolutionRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ResolvedRate> resolve(UUID roleId, UUID productId, UUID userId, LocalDate fecha) {

    List<Tuple> filas =
        em.createNativeQuery(SQL, Tuple.class)
            .setParameter("persona", userId)
            .setParameter("producto", productId)
            // Nulo: la persona no porta rol vendedor. La rama del rol no
            // devuelve nada y solo puede responder la personalizada.
            .setParameter("rol", roleId == null ? null : roleId.toString())
            .setParameter("fecha", fecha.toString())
            .getResultList();

    return filas.stream().findFirst().map(JpaCommissionResolutionRepository::comoTasa);
  }

  private static ResolvedRate comoTasa(Tuple fila) {
    int prioridad = ((Number) fila.get("prioridad")).intValue();
    CommissionRateType forma = CommissionRows.forma(fila.get("rate_type"));
    // AQUÍ, y no dentro de las ramas. La fila que ganó trae las dos columnas y
    // el `CHECK` garantiza que solo una está llena; se elige la que la forma
    // manda, en un solo sitio. Ver la nota de `SQL`.
    BigDecimal valor =
        forma == CommissionRateType.PORCENTAJE
            ? (BigDecimal) fila.get("percentage")
            : (BigDecimal) fila.get("fixed_amount");
    return new ResolvedRate(
        prioridad == 0 ? RateSource.PERSONALIZADA : RateSource.ROL,
        (UUID) fila.get("rate_id"),
        forma,
        valor,
        CommissionRows.fecha(fila.get("valid_from")),
        CommissionRows.fecha(fila.get("valid_to")));
  }
}
