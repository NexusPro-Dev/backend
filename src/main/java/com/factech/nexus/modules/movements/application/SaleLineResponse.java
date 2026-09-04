package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.domain.models.MovementLine;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una línea de la venta, tal como quedó (`RF-MV-001` · §6.2).
 *
 * <p><b>{@code unitPrice} y {@code validityDays} son lo que se copió</b>, no lo que el catálogo
 * dice hoy (`RN-MV-002`). Es el campo que hace observable la diferencia: corregir el precio del
 * producto mañana no cambia este número, y esa es la razón de ser de la copia.
 *
 * <p><b>{@code validityDays} nulo significa que lo adquirido no caduca</b> (`RN-PM-015`), no «sin
 * dato». Viaja siempre, también cuando es nulo: omitir la clave se leería como que esta versión no
 * la registraba.
 *
 * <p><b>La membresía destino no viaja</b>, y su ausencia es coherente con que no se copie: quien
 * necesite saber a qué nivel lleva un upgrade lo pregunta al catálogo, donde `RF-PM-004` garantiza
 * que no ha cambiado.
 */
public record SaleLineResponse(
    UUID productId,
    String productCode,
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineAmount,
    Integer validityDays) {

  static SaleLineResponse de(MovementLine linea) {
    return new SaleLineResponse(
        linea.getProductId(),
        linea.getProductCode(),
        linea.getProductName(),
        linea.getQuantity(),
        linea.getUnitPrice(),
        linea.getLineAmount(),
        linea.getValidityDays());
  }
}
