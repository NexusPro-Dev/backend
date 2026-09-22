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
   * <p><b>Las dos ramas exigen el PRODUCTO, y las dos lo leen de su propia tabla</b>, con lo que
   * `RN-CM-012` no tiene excepción: ninguna tasa rige donde no se la puso. La de la persona lee
   * {@code user_commission_rates.product_id} (`RN-CM-021`, 16-09-2026); del 11-09-2026 al
   * 16-09-2026 entraba por {@code user_commission_rate_products}, y hasta el 11-09-2026 no miraba
   * el producto —la personalizada ganaba vendiera lo que vendiera—, de modo que tapaba el catálogo
   * entero de su titular y la rama del rol no llegaba a mirarse nunca.
   *
   * <p><b>Lo que la rama de la persona sigue sin filtrar es el rol</b>, y esa ausencia sí es la
   * regla: desde el 01-09-2026 estas tasas no llevan rol, de modo que siguen rigiendo aunque su
   * titular haya dejado de vender.
   *
   * <p><b>La rama del rol lee {@code commission_rates.product_id}</b> (`RN-CM-021`, 15-09-2026): la
   * tasa nace con su producto, y sin tasa viva para ese producto y ese rol no hay tarifa. Hasta esa
   * fecha entraba por {@code product_commission_rates}, la tabla de asociación que `V94` retiró, y
   * el {@code JOIN} por clave compuesta que protegía la copia del rol ya no tiene nada que
   * proteger.
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
         AND u.product_id = :producto
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
        FROM commission_rates c
       WHERE c.deleted_at IS NULL
         AND c.role_id    = CAST(:rol AS uuid)
         AND c.product_id = :producto

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
