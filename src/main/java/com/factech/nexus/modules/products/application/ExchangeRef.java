package com.factech.nexus.modules.products.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * La conversión de un importe a la moneda por omisión del sistema (`RN-PM-024`, 08-09-2026).
 *
 * <p><b>La comparten las CUATRO lecturas del módulo</b> —listado, detalle, oferta y hotlink—, y esa
 * es la decisión que la trae aquí en vez de dejarla dentro de {@code HotlinkResponse}, donde nació.
 * Declararla cuatro veces daría cuatro formas del mismo dato, y la cuarta acabaría distinta: es lo
 * que los javadoc de {@code ProductItem} y {@code OfferItem} llevan seis requerimientos evitando.
 *
 * <p><b>{@code rate} viaja como CADENA</b>, y es el único campo del sistema que lo hace: tiene ocho
 * decimales, y un número JSON pasa por coma flotante de doble precisión en cualquier cliente
 * JavaScript. Como cadena, la tasa que se muestra es la que se declaró.
 *
 * <p><b>{@code amount} es informativo, y esto tiene que llegar hasta el frontend.</b> Lo que se
 * cobra no es este número: una venta va en <b>una sola moneda</b> (`RN-MV-012`) y congela su
 * importe al registrarse. Esta conversión se calcula al vuelo, cambia el día que cambie la tasa y
 * <b>no reserva nada</b>.
 *
 * <p><b>Y se calcula sobre el importe que SE MUESTRA</b> —el público si el producto lo declara y el
 * del sistema si no—, no sobre los dos. Desde que las cuatro lecturas publican los dos importes,
 * dar dos convertidos obligaría a decir en la respuesta cuál corresponde a cuál, que es justo la
 * ambigüedad que publicar los dos vino a quitar.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExchangeRef(TargetCurrency currency, String rate, BigDecimal amount) {

  /**
   * La moneda de destino de la conversión, que es <b>siempre la de por omisión</b>.
   *
   * <p><b>No lleva identificador</b>, al revés que {@link ProductResponse.CurrencyRef}: hay
   * exactamente una moneda por omisión en el sistema y nadie llama a nada con su identificador. En
   * el hotlink, además, esta proyección viaja <b>sin token</b>.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record TargetCurrency(String code, int decimalPlaces) {}
}
