package com.factech.nexus.modules.products.domain.models;

/**
 * Quién aplica lo que el producto otorga (`RN-PM-020`).
 *
 * <p><b>Es lo primero de este catálogo que gobierna a otro módulo.</b> `RN-MV-020` concedía la
 * membresía comprada en <b>toda</b> venta confirmada; desde el 07-09-2026 la concede <b>solo</b> si
 * el producto es {@link #AUTOMATICA}. Con {@link #MANUAL}, lo comprado queda esperando a que un
 * funcionario lo autorice (`RN-MV-021`, `RF-MV-010`).
 *
 * <p><b>Lo que queda pendiente es la entrega y no el cobro</b>: la venta se confirma con normalidad
 * cuando el dinero entra, de modo que `RN-MV-005` —de `CONFIRMADA` no se sale— sigue intacta.
 *
 * <p><b>El dominio se declara como enumerado y no como {@code boolean}</b> aunque hoy tenga dos
 * valores: el día que aparezca una tercera forma —diferida, automática con tope— el nombre de un
 * campo booleano ya habría mentido, y el coste de haberlo elegido se paga entero en ese momento.
 */
public enum ProductImplementation {

  /** El sistema aplica lo comprado sin que intervenga nadie. */
  AUTOMATICA,

  /**
   * Lo comprado espera a que un funcionario lo autorice. Confirmar el pago <b>no</b> lo entrega.
   */
  MANUAL
}
