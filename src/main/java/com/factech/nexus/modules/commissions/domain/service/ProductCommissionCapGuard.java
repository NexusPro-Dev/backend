package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.CommissionValue;
import com.factech.nexus.modules.commissions.domain.repository.ProductCommissionRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.ProductCommissionRateQueryRepository.AssociationRow;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * El tope de cien de un producto (`RN-CM-019`), compartido por `RF-CM-007` (asociar) y `RF-CM-003`
 * (corregir).
 *
 * <p><b>Suma el porcentaje ocupado de cada tasa de rol asociada a un producto</b> —el de una tasa
 * `PORCENTAJE` tal cual, el de una `FIJO` convertido a {@code fixed_amount ÷ precio × 100} contra
 * el precio que `PM` publica <b>hoy</b>— y rechaza si, con el valor que entra, la suma pasa de
 * cien.
 *
 * <p><b>No cierra `RN-CM-011` entero.</b> Una tasa <b>personalizada</b> en la cadena comercial no
 * se asocia a ningún producto (`RN-CM-004`) y queda fuera de esta suma. Y el tope se calcula contra
 * el precio de <b>hoy</b>: si el producto cambia de precio después (`RF-PM-004`), nadie repite la
 * cuenta — aceptado a conciencia, igual que el resto de huecos del módulo (`cm.md` §5.3).
 *
 * <p><b>La suma no se puede leer y comprobar con seguridad frente a otra petición sobre el mismo
 * producto sin bloquear</b>: ninguna restricción de Postgres expresa «la suma de estas filas no
 * puede superar cien» — un {@code CHECK} evalúa una fila sola, y un {@code EXCLUDE} compara pares
 * por solape, no acumula un total. Se cierra con un <b>bloqueo consultivo</b> por producto, tomado
 * antes de leer la suma, que serializa cualquier par de transacciones que toquen el mismo producto
 * sin bloquear las que tocan productos distintos.
 */
@Service
public class ProductCommissionCapGuard {

  private static final BigDecimal CIEN = new BigDecimal("100");
  private static final MathContext PRECISION = new MathContext(20, RoundingMode.HALF_UP);

  /**
   * Espacio de nombres del bloqueo consultivo, para no chocar con uno futuro de otro módulo.
   * Hexadecimal de «RN19» en ASCII: sin significado numérico, solo trazable a simple vista.
   */
  private static final int ADVISORY_LOCK_NAMESPACE = 0x524E3139;

  private final ProductCommissionRateQueryRepository consultas;
  private final ProductCatalog productos;
  private final EntityManager em;

  @Autowired
  public ProductCommissionCapGuard(
      ProductCommissionRateQueryRepository consultas, ProductCatalog productos, EntityManager em) {
    this.consultas = consultas;
    this.productos = productos;
    this.em = em;
  }

  /**
   * Comprueba que {@code productId} no quede pagando más de cien al sumar sus asociaciones vivas
   * con {@code valorEntrante}, y rechaza si se pasa.
   *
   * @param productId el producto sobre el que se comprueba la suma
   * @param productCode su código, solo para el mensaje de rechazo
   * @param rateIdExcluido la fila que no cuenta en la suma existente porque es la que {@code
   *     valorEntrante} sustituye — {@code null} cuando no hay ninguna que excluir (asociar una tasa
   *     nueva; corregir una que todavía no tenía ese producto no llega a llamar aquí)
   * @param valorEntrante la forma y la cifra que va a regir
   * @param errorCode el código de la excepción que declara cada operación —`EX-005` en `RF-CM-007`,
   *     `EX-006` en `RF-CM-003`— para que el mensaje llegue con la causa de <b>esa</b> operación
   */
  public void verificar(
      UUID productId,
      String productCode,
      UUID rateIdExcluido,
      CommissionValue valorEntrante,
      String errorCode) {

    bloquear(productId);

    List<AssociationRow> asociaciones = consultas.findByProduct(productId);

    boolean necesitaPrecio =
        valorEntrante.getRateType() == CommissionRateType.FIJO
            || asociaciones.stream()
                .anyMatch(
                    fila ->
                        !Objects.equals(fila.commissionRateId(), rateIdExcluido)
                            && fila.rateType() == CommissionRateType.FIJO);

    BigDecimal precio = necesitaPrecio ? precioDe(productId) : null;

    BigDecimal suma = BigDecimal.ZERO;
    for (AssociationRow fila : asociaciones) {
      if (Objects.equals(fila.commissionRateId(), rateIdExcluido)) {
        continue;
      }
      suma = suma.add(ocupado(fila.rateType(), fila.percentage(), fila.fixedAmount(), precio));
    }
    suma =
        suma.add(
            ocupado(
                valorEntrante.getRateType(),
                valorEntrante.getPercentage(),
                valorEntrante.getFixedAmount(),
                precio));

    if (suma.compareTo(CIEN) > 0) {
      String mensaje =
          "El producto "
              + productCode
              + " quedaría pagando más del 100 % de sí mismo entre sus tasas de rol asociadas.";
      throw new BusinessRuleException(
          errorCode, mensaje, List.of(new FieldError("productId", errorCode, mensaje)));
    }
  }

  /**
   * El porcentaje que una fila ocupa. El precio nunca es cero —{@code ck_products_price_positive}
   * lo garantiza desde `V39`, y esta clase confía en esa garantía en lugar de defenderse de un
   * estado que el propio esquema hace imposible.
   */
  private BigDecimal ocupado(
      CommissionRateType tipo, BigDecimal percentage, BigDecimal fixedAmount, BigDecimal precio) {
    if (tipo == CommissionRateType.PORCENTAJE) {
      return percentage;
    }
    return fixedAmount.divide(precio, PRECISION).multiply(CIEN);
  }

  private BigDecimal precioDe(UUID productId) {
    return productos
        .findPrice(productId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "El producto " + productId + " no tiene precio: no debería llegar aquí."));
  }

  /**
   * Serializa cualquier par de transacciones que comprueben el tope del mismo producto a la vez.
   *
   * <p>No es {@code PESSIMISTIC_WRITE} sobre las filas existentes porque ese bloqueo solo alcanza
   * filas que <b>ya existen</b>: el primer asociado a un producto no tiene ninguna que bloquear, y
   * la carrera que hay que cerrar incluye justo ese caso.
   */
  private void bloquear(UUID productId) {
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:ns, :clave)")
        .setParameter("ns", ADVISORY_LOCK_NAMESPACE)
        .setParameter("clave", productId.hashCode())
        .getSingleResult();
  }
}
