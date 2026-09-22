package com.factech.nexus.modules.commissions.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que paga una comisión: <b>su forma y su valor, juntos</b> (`RN-CM-016`).
 *
 * <p><b>Es un objeto y no tres atributos sueltos, y el motivo no es ahorrar código.</b> La regla
 * dice «<b>exactamente uno</b> de los dos, <b>y el que corresponda al tipo</b>», y esa frase no se
 * puede evaluar mirando un campo: hacen falta los tres a la vez. Repartidos por el agregado, cada
 * uno se asigna por su cuenta y <b>la regla solo es cierta entre asignaciones</b>; dentro de un
 * objeto que se construye entero o no se construye, <b>no hay ningún instante en que sea falsa</b>.
 *
 * <p><b>Lo incrustan las dos piezas del módulo</b> —{@link CommissionRate} y {@link
 * UserCommissionRate}—, y que sea <b>el mismo</b> objeto y no dos gemelos tiene consecuencias:
 * `RN-CM-016` se decide una vez, corregir hereda la regla sin escribirla (`RF-CM-003`), y {@code
 * RF-CM-005} puede devolver la comisión resuelta <b>sin saber de cuál de las dos tablas salió</b>.
 *
 * <h2>La igualdad está escrita a mano, y tiene que estarlo</h2>
 *
 * <p>Un {@code record} la generaría con {@code equals}, y sería <b>incorrecta</b>. Hacen falta dos
 * cosas a la vez que ni {@code equals} ni {@code compareTo} dan por separado:
 *
 * <ul>
 *   <li><b>Misma forma, {@code 10.00} frente a {@code 10.0000}: NO hay cambio.</b> La escala no es
 *       información, y {@code equals} los daría por distintos — cada corrección inocua escribiría
 *       un evento de auditoría.
 *   <li><b>Distinta forma, {@code 10} frente a {@code 10}: SÍ hay cambio.</b> Y de los grandes: la
 *       tasa pasa de pagar una décima parte de la venta a pagar diez unidades de dinero. {@code
 *       compareTo} sobre la cifra los daría por iguales, y la corrección <b>devolvería éxito sin
 *       cambiar, sin auditar y sin mover la marca de modificación</b>.
 * </ul>
 *
 * <p>De modo que es <b>el tipo por identidad y la cifra por {@code compareTo}</b>. Las dos pruebas
 * que la vigilan usan los mismos números y esperan lo contrario, porque <b>cualquiera de las dos se
 * satisface rompiendo la otra</b>.
 *
 * <h2>Por qué lanza `ValidationException` y no `BusinessRuleException`</h2>
 *
 * <p>Vive en el dominio y aun así el rechazo es <b>400</b>, no 409. No es una excepción a la
 * arquitectura: es el precedente del propio módulo — `VAL-005`, el orden de la vigencia, también es
 * una comprobación entre campos, también vive en un agregado y también lanza esto. Lo que el actor
 * envió está <b>mal formado</b>, no en conflicto con el estado del sistema.
 */
@Embeddable
public class CommissionValue {

  private static final BigDecimal CIEN = new BigDecimal("100");

  @Enumerated(EnumType.STRING)
  @Column(name = "rate_type", nullable = false, length = 20)
  private CommissionRateType rateType;

  /** Presente solo si la forma es {@link CommissionRateType#PORCENTAJE}. */
  @Column(name = "percentage", precision = 5, scale = 2)
  private BigDecimal percentage;

  /**
   * Presente solo si la forma es {@link CommissionRateType#FIJO}.
   *
   * <p>{@code numeric(14,4)}, la misma forma que {@code products.price}, porque la escala real la
   * decide la moneda ({@code currencies.decimal_places}, de 0 a 4). Con menos decimales, una
   * comisión en una moneda de cuatro no se podría expresar.
   */
  @Column(name = "fixed_amount", precision = 14, scale = 4)
  private BigDecimal fixedAmount;

  /** Exigido por JPA. */
  protected CommissionValue() {}

  /**
   * Construye el valor de una comisión, comprobando `RN-CM-016` y el rango de su forma.
   *
   * <p><b>Es el único constructor.</b> No hay forma de obtener un valor inconsistente, ni desde un
   * alta ni desde una corrección.
   *
   * @param rateType la forma declarada. Obligatoria: no se deduce del campo que venga lleno
   * @param percentage el porcentaje, solo si la forma es {@code PORCENTAJE}
   * @param fixedAmount el importe, solo si la forma es {@code FIJO}
   */
  public static CommissionValue of(
      CommissionRateType rateType, BigDecimal percentage, BigDecimal fixedAmount) {

    if (rateType == null) {
      String mensaje = "La forma de la comisión es obligatoria: porcentaje o valor fijo.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("rateType", "VAL-002", mensaje)));
    }

    // `VAL-011` es UN solo mensaje para lo que parecen tres errores —las dos
    // formas llenas, ninguna llena, y la equivocada llena— porque son el mismo:
    // lo enviado no concuerda con lo declarado. Separarlos obligaría a redactar
    // tres frases que dicen lo mismo con distinta cara.
    boolean esPorcentaje = rateType == CommissionRateType.PORCENTAJE;
    boolean llegaElQueToca = esPorcentaje ? percentage != null : fixedAmount != null;
    boolean llegaElOtro = esPorcentaje ? fixedAmount != null : percentage != null;

    if (!llegaElQueToca || llegaElOtro) {
      String mensaje =
          "Una comisión por porcentaje lleva porcentaje y no valor fijo; "
              + "una comisión por valor fijo, al revés.";
      String campo = esPorcentaje ? "percentage" : "fixedAmount";
      throw new ValidationException(
          "VAL-011", mensaje, List.of(new FieldError(campo, "VAL-011", mensaje)));
    }

    if (esPorcentaje) {
      verificarPorcentaje(percentage);
    } else {
      verificarImporte(fixedAmount);
    }

    CommissionValue valor = new CommissionValue();
    valor.rateType = rateType;
    valor.percentage = percentage;
    valor.fixedAmount = fixedAmount;
    return valor;
  }

  /** Atajo de lectura para las pruebas y para quien solo necesita un porcentaje. */
  public static CommissionValue porcentaje(BigDecimal valor) {
    return of(CommissionRateType.PORCENTAJE, valor, null);
  }

  /** Atajo de lectura. Ver {@link CommissionRateType#FIJO} para lo que este importe NO lleva. */
  public static CommissionValue fijo(BigDecimal valor) {
    return of(CommissionRateType.FIJO, null, valor);
  }

  /**
   * <b>La comparación que decide si una corrección cambió algo.</b>
   *
   * <p>Ver la nota de la clase: el tipo por identidad y la cifra por {@code compareTo}. Ni {@code
   * equals} ni {@code compareTo} solos sirven, y las dos maneras de equivocarse son las dos maneras
   * naturales de escribirla.
   */
  public boolean mismoValorQue(CommissionValue otro) {
    if (otro == null || rateType != otro.rateType) {
      return false;
    }
    return cifra().compareTo(otro.cifra()) == 0;
  }

  /**
   * La cifra declarada, sea cual sea la forma.
   *
   * <p><b>Solo tiene sentido junto a {@link #getRateType()}</b>: «10» es el diez por ciento o son
   * diez unidades de dinero, y son cosas de órdenes de magnitud distintos.
   */
  public BigDecimal cifra() {
    return rateType == CommissionRateType.PORCENTAJE ? percentage : fixedAmount;
  }

  /**
   * El valor, para la auditoría.
   *
   * <p><b>Lleva la forma y no solo el número</b>, y ahí está lo que salva. Un {@code before} que
   * dijera {@code 10} sin decir que era un porcentaje <b>no conserva nada</b>: quien lo lea dentro
   * de un año no podrá saber si esa tasa pagaba una décima parte de la venta o diez unidades de
   * dinero. Y como esta tabla no tiene vigencia, ese registro es la única copia que queda.
   */
  public String paraAuditoria() {
    return rateType.name() + " " + cifra().toPlainString();
  }

  /** `RN-CM-007`. El cero se admite: significa «no comisiona». */
  private static void verificarPorcentaje(BigDecimal valor) {
    if (valor.compareTo(BigDecimal.ZERO) < 0 || valor.compareTo(CIEN) > 0) {
      String mensaje = "El porcentaje debe estar entre cero y cien.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("percentage", "VAL-003", mensaje)));
    }
  }

  /**
   * `RN-CM-018`. <b>Solo se acota por abajo, y esa asimetría con el porcentaje es la regla.</b>
   *
   * <p>Cien es un límite que el negocio conoce sin mirar nada; para el importe <b>no existe ese
   * número</b>. La tasa no conoce el precio del producto —al registrarla no hay ninguno— y la
   * personalizada ni siquiera sabe sobre cuáles rige. Ponerlo aquí sería inventarlo. El tope lo
   * hereda la liquidación, y su forma será <b>rechazar y no recortar</b>.
   */
  private static void verificarImporte(BigDecimal valor) {
    if (valor.compareTo(BigDecimal.ZERO) < 0) {
      String mensaje = "El valor fijo no puede ser negativo.";
      throw new ValidationException(
          "VAL-012", mensaje, List.of(new FieldError("fixedAmount", "VAL-012", mensaje)));
    }
  }

  public CommissionRateType getRateType() {
    return rateType;
  }

  public BigDecimal getPercentage() {
    return percentage;
  }

  public BigDecimal getFixedAmount() {
    return fixedAmount;
  }
}
