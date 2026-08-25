package com.factech.nexus.modules.system.currencies.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * El catálogo completo (`RF-SP-019`).
 *
 * <p><b>Envuelto en {@code content} y sin metadatos de paginación</b>, por lo dicho en `RF-SP-010`
 * §4: un arreglo desnudo en la raíz cierra la puerta a añadir después cualquier metadato sin romper
 * a todos los clientes, y rellenar {@code totalPages: 1} diría que hay paginación donde no la hay.
 *
 * <p><b>Sigue siendo una colección aunque tenga un solo elemento.</b> No se devuelve un objeto
 * suelto ni «la moneda por defecto» como recurso propio: el día que haya dos, el contrato no
 * cambia.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CurrencyCatalogResponse(List<CurrencyResponse> content) {

  public static CurrencyCatalogResponse from(List<CurrencyItem> monedas) {
    return new CurrencyCatalogResponse(monedas.stream().map(CurrencyResponse::from).toList());
  }
}
