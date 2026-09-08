package com.factech.nexus.modules.system.brokers.application;

import java.util.List;

/**
 * El catálogo entero, envuelto (`RF-SP-052`).
 *
 * <p><b>Envuelto en {@code content} y no un arreglo desnudo</b>, como los otros tres catálogos del
 * módulo: deja sitio a paginar el día que haga falta sin romper a ningún cliente. <b>Sin
 * totales</b> —no se pagina, y {@code totalElements} sería el tamaño del arreglo dicho dos veces—.
 */
public record BrokerCatalogResponse(List<BrokerItem> content) {

  public static BrokerCatalogResponse de(List<BrokerItem> brokers) {
    return new BrokerCatalogResponse(brokers);
  }
}
