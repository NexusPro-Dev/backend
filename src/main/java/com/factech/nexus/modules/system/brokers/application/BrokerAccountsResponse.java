package com.factech.nexus.modules.system.brokers.application;

import java.util.List;

/**
 * Las cuentas de una persona, envueltas (`RF-SP-055`).
 *
 * <p><b>Envuelto en {@code content} y no un arreglo desnudo</b>, como el catálogo de brokers y los
 * otros tres del módulo: deja sitio a paginar el día que haga falta sin romper a ningún cliente.
 *
 * <p><b>Sin totales</b>: una persona tiene unas pocas cuentas y esto no se pagina, de modo que
 * {@code totalElements} sería el tamaño del arreglo dicho dos veces. El listado que sí pagina —y
 * que sí lleva total— es el del equipo (`RF-SP-056`).
 */
public record BrokerAccountsResponse(List<BrokerAccountItem> content) {

  public static BrokerAccountsResponse de(List<BrokerAccountItem> cuentas) {
    return new BrokerAccountsResponse(cuentas);
  }
}
