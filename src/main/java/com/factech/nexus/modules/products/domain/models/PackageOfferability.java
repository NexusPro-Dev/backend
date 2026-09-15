package com.factech.nexus.modules.products.domain.models;

import java.util.List;

/**
 * Si el paquete se puede ofrecer hoy, y si no, por qué (`RN-PM-039`, `RN-PM-040`).
 *
 * <p><b>Un objeto de dominio y no un {@code if} en el servicio</b>, porque cuatro lecturas lo
 * necesitan y tienen que decir lo mismo: el detalle lo publica con su motivo, la lista lo publica
 * como columna, la oferta filtra por él y el hotlink responde {@code 404}. Recibe el paquete y sus
 * filas y devuelve {@code (boolean, motivo)}; la oferta y el hotlink solo miran el booleano.
 *
 * <p><b>El orden de los motivos es fijo</b> (`CA-PM-281`): menos de dos productos → sin descripción
 * → paquete inactivo → paquete retirado → un producto no ofrecible, <b>nombrado por su código</b>.
 * Devuelve el <b>primero</b> que se cumple. Va de lo que es del paquete a lo que es de sus
 * productos, porque lo primero se arregla desde el paquete y lo segundo no.
 */
public record PackageOfferability(boolean offerable, String reason) {

  /** Lo que la decisión necesita de cada producto del paquete. */
  public record Producto(String code, boolean activo, boolean retirado) {

    public boolean ofrecible() {
      return activo && !retirado;
    }
  }

  private static final PackageOfferability OFRECIBLE = new PackageOfferability(true, null);

  public static PackageOfferability decidir(
      PackageStatus status, boolean retirado, boolean tieneDescripcion, List<Producto> productos) {
    if (productos.size() < 2) {
      return new PackageOfferability(
          false, "El paquete tiene menos de dos productos y necesita al menos dos.");
    }
    if (!tieneDescripcion) {
      return new PackageOfferability(false, "El paquete no tiene descripción.");
    }
    if (status != PackageStatus.ACTIVO) {
      return new PackageOfferability(false, "El paquete está inactivo.");
    }
    if (retirado) {
      return new PackageOfferability(false, "El paquete está retirado.");
    }
    for (Producto producto : productos) {
      if (!producto.ofrecible()) {
        String estado = producto.retirado() ? "está retirado" : "está inactivo";
        String mensaje =
            "El producto %s %s y el paquete no se ofrece mientras alguno de los suyos no esté a la"
                + " venta.";
        return new PackageOfferability(false, mensaje.formatted(producto.code(), estado));
      }
    }
    return OFRECIBLE;
  }
}
