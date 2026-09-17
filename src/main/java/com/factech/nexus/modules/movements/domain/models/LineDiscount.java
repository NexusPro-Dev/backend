package com.factech.nexus.modules.movements.domain.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Una rebaja de una línea, <b>como se pactó y como se cobró</b> (`RN-MV-027`).
 *
 * <p>{@code type} y {@code value} son la declaración —{@code PORCENTAJE 10}, o {@code FIJO 5.00}— y
 * {@code discountValue} es lo que valió <b>en dinero y por unidad</b> el día de la venta. Se
 * guardan las dos cosas porque responden preguntas distintas: «¿qué le prometieron?» y «¿cuánto le
 * rebajaron?». Un porcentaje solo no sobrevive a una corrección del precio del producto; un importe
 * solo no explica de dónde salió.
 *
 * <h2>El dinero se calcula aquí y no se recibe</h2>
 *
 * <p>No hay constructor que acepte {@code discountValue}: sale del tipo, del valor y del precio
 * unitario, con la regla de `PM` — <b>se redondea la rebaja y no el resultado</b>, a los decimales
 * de la moneda y a la mitad hacia arriba, para que lo que el cliente ve como «10 % de 49.99» sea la
 * misma cifra que la que se le resta. Es el mismo argumento con el que {@link Movement} suma su
 * total: si el importe llegara por parámetro, existiría una rebaja cuyo dinero no corresponde a su
 * declaración, y nada lo impediría.
 */
public final class LineDiscount {

  private static final BigDecimal CIEN = new BigDecimal("100");

  private final UUID id;
  private final MovementDiscountType type;
  private final BigDecimal value;
  private final BigDecimal discountValue;

  private LineDiscount(
      UUID id, MovementDiscountType type, BigDecimal value, BigDecimal discountValue) {
    this.id = id;
    this.type = type;
    this.value = value;
    this.discountValue = discountValue;
  }

  /**
   * Congela una rebaja sobre un precio unitario.
   *
   * @param value lo pactado: de {@code 0} a {@code 100} si es porcentaje; de {@code 0} al precio si
   *     es fijo
   * @param unitPrice el precio unitario <b>ya en la escala de la moneda</b>
   * @param decimales los de la moneda de la venta
   */
  public static LineDiscount de(
      MovementDiscountType type, BigDecimal value, BigDecimal unitPrice, int decimales) {
    if (type == null || value == null) {
      throw new IllegalArgumentException("Una rebaja declara su tipo y su valor.");
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException("Una rebaja no es negativa: eso es un recargo.");
    }
    if (type == MovementDiscountType.PORCENTAJE && value.compareTo(CIEN) > 0) {
      throw new IllegalArgumentException("Un porcentaje de rebaja no pasa de cien.");
    }
    BigDecimal dinero =
        type == MovementDiscountType.FIJO
            ? value.setScale(decimales, RoundingMode.HALF_UP)
            : unitPrice.multiply(value).divide(CIEN, decimales, RoundingMode.HALF_UP);
    // UN FIJO MAYOR QUE EL PRECIO SE COBRA COMO EL PRECIO, y no se rechaza: es
    // el hueco temporal de `RN-PM-037` —el precio bajó después de asociar— y
    // `PM` lo cierra publicando el producto a cero (`DiscountValue.precioDentroDe`).
    // Congelar aquí más de lo que vale dejaría la línea en negativo; congelar
    // menos que `PM` rompería `CA-MV-050`. Lo declarado (`value`) se guarda tal
    // cual: lo pactado fue eso, y lo cobrado es esto.
    if (dinero.compareTo(unitPrice) > 0) {
      dinero = unitPrice.setScale(decimales, RoundingMode.HALF_UP);
    }
    return new LineDiscount(UUID.randomUUID(), type, value, dinero);
  }

  /** Lo que de esta rebaja entra en la instantánea de auditoría. */
  Map<String, Object> instantanea() {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("type", type.name());
    datos.put("value", value.toPlainString());
    datos.put("discount_value", discountValue.toPlainString());
    return datos;
  }

  public UUID getId() {
    return id;
  }

  public MovementDiscountType getType() {
    return type;
  }

  public BigDecimal getValue() {
    return value;
  }

  public BigDecimal getDiscountValue() {
    return discountValue;
  }
}
