package com.factech.nexus.modules.system.currencies.application;

import com.factech.nexus.modules.system.currencies.domain.models.Currency;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Una moneda en el contrato de la API.
 *
 * <p><b>Un solo tipo para los dos endpoints.</b> `RF-SP-019` lo devuelve dentro de la colección y
 * `RF-SP-023` lo devuelve suelto con el estado ya actualizado. Que el cambio de estado responda con
 * el mismo cuerpo permite comprobar en la misma respuesta lo que `CA-SP-188` exige: que el código,
 * el nombre, el símbolo y los decimales <b>no</b> cambiaron.
 *
 * <p>{@code @JsonInclude(ALWAYS)} porque {@code application.yml} declara {@code non_null} para todo
 * el sistema: sin él, una moneda sin símbolo llegaría sin la clave en lugar de con {@code null}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CurrencyResponse(
    UUID id,
    String code,
    String name,
    String symbol,
    int decimalPlaces,
    boolean isDefault,
    boolean isActive) {

  public static CurrencyResponse from(CurrencyItem moneda) {
    return new CurrencyResponse(
        moneda.id(),
        moneda.code(),
        moneda.name(),
        moneda.symbol(),
        moneda.decimalPlaces(),
        moneda.isDefault(),
        moneda.isActive());
  }

  public static CurrencyResponse from(Currency moneda) {
    return new CurrencyResponse(
        moneda.getId(),
        // `char(3)` rellena con espacios al leerse: se recortan aquí para que el
        // contrato publique `USD` y no `USD ` según por dónde salga el dato.
        moneda.getCode() == null ? null : moneda.getCode().trim(),
        moneda.getName(),
        moneda.getSymbol(),
        moneda.getDecimalPlaces(),
        moneda.isDefault(),
        moneda.isActive());
  }
}
