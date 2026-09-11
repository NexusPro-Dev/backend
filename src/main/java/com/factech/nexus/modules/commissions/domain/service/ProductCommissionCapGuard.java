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

  /**
   * Lo que ocupa un valor fijo mayor que cero sobre un producto de <b>precio cero</b>.
   *
   * <p>No es un número arbitrario ni un centinela: es la respuesta correcta a la pregunta que hace
   * {@link #ocupado}. Un producto que no cobra nada no puede pagar ningún importe, de modo que
   * cualquier importe fijo es <b>más</b> del cien por cien de lo que cobra — y basta con que la
   * suma lo supere para que `RN-CM-019` lo rechace con su mensaje de siempre.
   *
   * <p>Se elige un valor que <b>por sí solo</b> pasa del tope para que no dependa de lo que sumen
   * las demás tasas: con `100` exacto, una fila así pasaría inadvertida si fuera la única.
   */
  private static final BigDecimal MAS_DE_CIEN = new BigDecimal("101");

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
   * El tope de <b>una sola</b> tasa contra el precio de su producto (`RN-CM-019`, 11-09-2026).
   *
   * <p>Lo usa la tasa <b>personalizada</b>, que desde esa fecha declara su producto y por tanto
   * conoce un precio contra el que compararse. Hasta entonces no lo conocía, y esa era la razón
   * literal por la que `RN-CM-018` la dejaba <b>sin tope por arriba</b>: atarla a un producto le
   * quitó la excusa.
   *
   * <h2>Es individual y NO una suma, y la diferencia es de negocio</h2>
   *
   * <p>{@link #verificar} suma <b>todas</b> las tasas de rol de un producto porque una venta las
   * paga <b>a la vez</b>: una por cada nivel de la cadena. Las personalizadas de personas distintas
   * sobre el mismo producto <b>no</b> son eso: son <b>alternativas entre sí</b> —cada una sustituye
   * a la del rol de su titular— y sumarlas rechazaría configuraciones perfectamente legítimas, como
   * dar el 60 % a un vendedor y el 60 % a otro que nunca aparecen en la misma cadena.
   *
   * <p><b>Y por eso no bloquea el producto.</b> {@link #verificar} necesita un bloqueo consultivo
   * porque lee filas ajenas y dos asociaciones simultáneas podrían sumar mal; aquí no se lee
   * ninguna fila ajena, de modo que no hay carrera que cerrar.
   *
   * <p>Lo que sí comparte es el cálculo: {@link #ocupado} y el mismo trato del <b>precio cero</b>
   * —cualquier importe fijo mayor que cero sobre un producto gratuito pasa del cien por cien de lo
   * que ese producto cobra—.
   */
  public void verificarIndividual(
      UUID productId, String productCode, CommissionValue valor, String errorCode) {

    if (valor.getRateType() != CommissionRateType.FIJO) {
      // Un porcentaje ya lo acota `RN-CM-007` de cero a cien por su cuenta, y
      // no divide por ningún precio: no hay nada que comprobar aquí.
      return;
    }

    BigDecimal ocupa =
        ocupado(
            valor.getRateType(),
            valor.getPercentage(),
            valor.getFixedAmount(),
            precioDe(productId));

    if (ocupa.compareTo(CIEN) > 0) {
      String mensaje = "La tasa pagaría más del 100 % del precio del producto " + productCode + ".";
      throw new BusinessRuleException(
          errorCode, mensaje, List.of(new FieldError("fixedAmount", errorCode, mensaje)));
    }
  }

  /**
   * El porcentaje que una fila ocupa.
   *
   * <h2>El precio SÍ puede ser cero desde el 08-09-2026, y esta clase decía por escrito que no</h2>
   *
   * <p>Decía que «el precio nunca es cero — {@code ck_products_price_positive} lo garantiza desde
   * `V39`», y confiaba en esa garantía en lugar de defenderse. **`V67` retiró esa restricción** y
   * la sustituyó por {@code ck_products_price_no_negativo} (`RN-PM-006`), para admitir la
   * renovación de una membresía gratuita: la división de abajo pasó a poder ser <b>entre cero</b>.
   *
   * <p><b>La resolución no necesita ninguna regla nueva</b>: es `RN-CM-019` llevada a su límite. Un
   * producto que <b>no cobra nada</b> no puede pagar ningún importe fijo, de modo que cualquier
   * valor fijo mayor que cero ocupa <b>más del cien por cien</b> de lo que ese producto cobra y lo
   * rechaza el mismo tope, con el mismo mensaje que cualquier otro exceso. Un valor fijo de
   * <b>cero</b> ocupa cero. Un <b>porcentaje</b> no se ve afectado: no divide por nada.
   *
   * <p>Lo que hay que leer de esto no es el arreglo: es que <b>una clase de `CM` dependía de una
   * restricción de `PM`</b>, lo decía en su Javadoc con el nombre de la restricción, y aun así el
   * cambio pudo llegar sin que nada fallara al compilar. Lo destapó leer el comentario.
   */
  private BigDecimal ocupado(
      CommissionRateType tipo, BigDecimal percentage, BigDecimal fixedAmount, BigDecimal precio) {
    if (tipo == CommissionRateType.PORCENTAJE) {
      return percentage;
    }
    if (precio.compareTo(BigDecimal.ZERO) == 0) {
      // `compareTo` y no `equals`: `0`, `0.00` y `0.0000` son el mismo cero con
      // distinta escala, y `equals` los daría por distintos — la fila leída de
      // la base llega con la escala de la columna, `numeric(14,4)`.
      return fixedAmount.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : MAS_DE_CIEN;
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
