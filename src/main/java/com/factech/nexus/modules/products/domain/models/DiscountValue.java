package com.factech.nexus.modules.products.domain.models;

import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Forma y valor del descuento de un producto dentro de un paquete (`RN-PM-037`).
 *
 * <p><b>Un objeto de valor y no dos campos sueltos</b>, como {@code CommissionValue} en `CM`: es lo
 * que impide que un {@link DiscountType#PORCENTAJE} viaje con catorce dígitos o un {@link
 * DiscountType#FIJO} con tres decimales en una moneda de dos. La <b>forma</b> se valida aquí al
 * construirlo; la <b>cota</b> —que no deje al producto por debajo de cero— se comprueba contra un
 * precio con {@link #verificarCota}, porque el precio está en otra tabla y es el <b>de hoy</b>.
 *
 * <p><b>{@link #precioDentroDe} es la única resta del sistema</b>: {@link PackagePricing} la llama
 * por cada fila. La cuenta vive en un sitio y la cota en otro, pero las dos hablan este tipo.
 */
@Embeddable
public class DiscountValue {

  private static final BigDecimal CIEN = new BigDecimal("100");

  @Enumerated(EnumType.STRING)
  @Column(name = "discount_type", nullable = false, length = 20)
  private DiscountType type;

  @Column(name = "discount_value", nullable = false, precision = 14, scale = 4)
  private BigDecimal value;

  /** Exigido por JPA. */
  protected DiscountValue() {}

  /**
   * Construye el descuento validando su forma (`VAL-003`, `VAL-004` de `RF-PM-023`).
   *
   * @param decimalesDeLaMoneda los decimales de la moneda del paquete, que acotan el fijo
   */
  public static DiscountValue of(DiscountType type, BigDecimal value, int decimalesDeLaMoneda) {
    if (type == null || value == null) {
      String mensaje = "El producto, la forma del descuento y su valor son obligatorios.";
      throw new ValidationException(
          "VAL-002",
          mensaje,
          List.of(
              new FieldError(type == null ? "discountType" : "discountValue", "VAL-002", mensaje)));
    }
    if (value.compareTo(BigDecimal.ZERO) < 0) {
      String mensaje =
          "El valor del descuento no puede ser negativo ni tener más decimales que su moneda.";
      throw new ValidationException(
          "VAL-004", mensaje, List.of(new FieldError("discountValue", "VAL-004", mensaje)));
    }
    if (type == DiscountType.PORCENTAJE) {
      // Hasta dos decimales y hasta cien: `12.5 %` es un porcentaje y
      // `12.345 %` es una precisión que ninguna pantalla pinta. El techo de
      // cien es el único que también vive en el esquema.
      if (value.compareTo(CIEN) > 0 || value.stripTrailingZeros().scale() > 2) {
        String mensaje =
            "La forma del descuento debe ser PORCENTAJE o FIJO, y el porcentaje debe estar entre 0"
                + " y 100.";
        throw new ValidationException(
            "VAL-003", mensaje, List.of(new FieldError("discountValue", "VAL-003", mensaje)));
      }
    } else if (value.stripTrailingZeros().scale() > decimalesDeLaMoneda) {
      String mensaje =
          "El valor del descuento no puede ser negativo ni tener más decimales que su moneda.";
      throw new ValidationException(
          "VAL-004", mensaje, List.of(new FieldError("discountValue", "VAL-004", mensaje)));
    }
    DiscountValue descuento = new DiscountValue();
    descuento.type = type;
    descuento.value = value;
    return descuento;
  }

  /**
   * Lo leído de la base, <b>sin validar</b>: la forma la comprobó {@link #of} al escribir y el
   * esquema la sostiene. Es lo que usan las proyecciones para darle a {@link PackagePricing} el
   * mismo tipo que usa el caso de uso.
   */
  public static DiscountValue leido(DiscountType type, BigDecimal value) {
    DiscountValue descuento = new DiscountValue();
    descuento.type = type;
    descuento.value = value;
    return descuento;
  }

  /**
   * `RN-PM-037`: el descuento no deja al producto por debajo de cero, contra el precio <b>de
   * hoy</b> (`EX-006` de `RF-PM-023`, `EX-003` de `RF-PM-024`).
   *
   * <p>Un fijo mayor que el precio, o cualquier valor mayor que cero sobre un producto
   * <b>gratuito</b>. El porcentaje mayor que cien no llega aquí: es forma, no cota. <b>{@code
   * compareTo} y no {@code equals}</b>: {@code 49.00} y {@code 49.0000} son el mismo precio.
   *
   * @param codigoDeExcepcion el `EX-nnn` del caso de uso que llama, porque cada spec numera el suyo
   */
  public void verificarCota(
      BigDecimal precioDelProducto, String monedaCode, String codigoDeExcepcion) {
    boolean gratuito = precioDelProducto.compareTo(BigDecimal.ZERO) == 0;
    boolean excede =
        gratuito
            ? value.compareTo(BigDecimal.ZERO) > 0
            : type == DiscountType.FIJO && value.compareTo(precioDelProducto) > 0;
    if (excede) {
      String mensaje =
          "El descuento supera el precio del producto (%s %s)."
              .formatted(precioDelProducto.stripTrailingZeros().toPlainString(), monedaCode);
      throw new BusinessRuleException(
          codigoDeExcepcion,
          mensaje,
          List.of(new FieldError("discountValue", codigoDeExcepcion, mensaje)));
    }
  }

  /**
   * El precio del producto dentro del paquete, redondeado a los decimales de la moneda.
   *
   * <p>{@link DiscountType#FIJO}: {@code máx(0, precio − fijo)} — nunca negativo, que es el hueco
   * temporal de `RN-PM-037` cerrándose solo cuando el precio bajó después de asociar. {@link
   * DiscountType#PORCENTAJE}: {@code precio − redondear(precio × p ÷ 100)}, y <b>se redondea la
   * rebaja y no el resultado</b>, para que lo que el cliente ve como «10 % de 49.99» sea la misma
   * cifra que la que se le resta.
   */
  public BigDecimal precioDentroDe(BigDecimal precioDelProducto, int decimales) {
    BigDecimal rebaja =
        type == DiscountType.FIJO
            ? value
            : precioDelProducto.multiply(value).divide(CIEN, decimales, RoundingMode.HALF_UP);
    BigDecimal resultado = precioDelProducto.subtract(rebaja);
    if (resultado.compareTo(BigDecimal.ZERO) < 0) {
      resultado = BigDecimal.ZERO;
    }
    return resultado.setScale(decimales, RoundingMode.HALF_UP);
  }

  public boolean mismoValorQue(DiscountValue otro) {
    return otro != null && type == otro.type && value.compareTo(otro.value) == 0;
  }

  public DiscountType getType() {
    return type;
  }

  public BigDecimal getValue() {
    return value;
  }

  /** El valor con la escala de su forma: dos decimales el porcentaje, los de la moneda el fijo. */
  public BigDecimal valorEnEscala(int decimalesDeLaMoneda) {
    return value.setScale(
        type == DiscountType.PORCENTAJE ? 2 : decimalesDeLaMoneda, RoundingMode.HALF_UP);
  }
}
