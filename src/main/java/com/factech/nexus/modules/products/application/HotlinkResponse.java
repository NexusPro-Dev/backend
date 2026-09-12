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

  // La conversión ya NO se declara aquí: desde el 08-09-2026 la llevan las
  // cuatro lecturas del módulo y vive en `ExchangeRef`, que nació en este
  // fichero y salió de él el día que dejó de ser cosa del hotlink.

  /**
   * El producto que el enlace señala.
   *
   * <p><b>Lleva UN importe, y el precio de compra no tiene dónde ir</b> (`RN-PM-024`, 12-09-2026):
   * {@code price} es el que se cobra, y este registro <b>no tiene campo</b> para el costo de NEXUS
   * ni {@code findPublishedByCode} <b>selecciona la columna</b>. Es una ruta <b>sin token</b>: un
   * costo publicado aquí es el margen a la vista de quien reciba el enlace por mensajería, y no se
   * retira después. `CA-PM-163` prueba la ausencia con un producto que sí lo tiene declarado.
   *
   * <p>Entre el 08-09-2026 y el 12-09-2026 este registro tuvo {@code publicPrice} —lo que se
   * anunciaba— por decisión escrita en `requirements/pm.md` §5.2.5; convertido ese importe en el
   * costo, salió de aquí (§5.2.6). Ningún costo llegó a publicarse.
   */
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
