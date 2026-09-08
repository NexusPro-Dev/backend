package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lo que un hotlink devuelve, sin autenticación (`RF-PM-008`).
 *
 * <p><b>Publica MENOS que las otras cuatro respuestas del módulo, y es deliberado.</b> Del
 * vendedor, dos campos; de la membresía, tres. En una ruta pública recortar es la decisión por
 * omisión.
 *
 * <p>{@code JsonInclude.ALWAYS}: la membresía de un bot y la conversión que no existe llegan
 * <b>presentes y nulas</b>, no ausentes. Un campo que falta es indistinguible de uno que el cliente
 * no conoce.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HotlinkResponse(SellerRef seller, ProductRef product) {

  /** Nombre y apellido. Nada más (`RN-PM-022`). */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record SellerRef(String firstName, String lastName) {}

  /**
   * La membresía destino, <b>recortada</b>: sin identificador y sin nivel.
   *
   * <p>El {@code id} no le sirve a quien no puede llamar a nada más, y el {@code level} publicaría
   * <b>la forma de la cadena comercial</b> sin token. No son dos formas del mismo dato: es la misma
   * <b>recortada</b>.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MembershipBadge(String code, String name, String color) {}

  /** La moneda, con los decimales que declara. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CurrencyRef(String code, int decimalPlaces) {}

  /**
   * La conversión a la moneda de casa.
   *
   * <p><b>{@code rate} viaja como CADENA</b>, y es el único campo del sistema que lo hace: tiene
   * ocho decimales, y un número JSON pasa por coma flotante de doble precisión en cualquier cliente
   * JavaScript. Como cadena, la tasa que se muestra es la que se declaró.
   *
   * <p><b>{@code amount} es informativo.</b> Lo que se cobra no es este número: una venta va en una
   * sola moneda (`RN-MV-012`) y congela su importe al registrarse.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ExchangeRef(CurrencyRef currency, String rate, BigDecimal amount) {}

  /** El producto que el enlace señala. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ProductRef(
      UUID id,
      String code,
      ProductType type,
      String name,
      String description,
      String icon,
      Integer validityDays,
      MembershipBadge membership,
      BigDecimal price,
      CurrencyRef currency,
      ExchangeRef exchange) {}
}
