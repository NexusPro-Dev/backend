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
   * <p><b>Lleva los DOS importes desde el 08-09-2026</b> (`RN-PM-024` reescrita): {@code price} es
   * el del sistema —el que se cobra— y {@code publicPrice} el anunciado, <b>nulo</b> cuando el
   * producto no lo declara. Hasta esa fecha viajaba <b>uno solo</b>, resuelto con un {@code
   * COALESCE} en la consulta, y el del sistema no salía por aquí a propósito.
   *
   * <p><b>Lo que eso publica está decidido y escrito</b> (`requirements/pm.md` §5.2.5): en una ruta
   * sin token, cualquiera resta un importe del otro y ve la diferencia entre lo anunciado y lo
   * cobrado. No es un descuido de esta clase — es lo pedido, y `CA-PM-169` lo deja comprobado para
   * que el día que se decida lo contrario haga falta decidirlo.
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
      BigDecimal publicPrice,
      CurrencyRef currency,
      ExchangeRef exchange) {}
}
