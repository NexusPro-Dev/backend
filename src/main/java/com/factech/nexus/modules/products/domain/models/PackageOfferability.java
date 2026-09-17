package com.factech.nexus.modules.products.domain.models;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Si el paquete se puede ofrecer hoy, y si no, por qué (`RN-PM-039`, `RN-PM-040`, `RN-PM-047`).
 *
 * <p><b>Un objeto de dominio y no un {@code if} en el servicio</b>, porque cuatro lecturas lo
 * necesitan y tienen que decir lo mismo: el detalle lo publica con su motivo, la lista lo publica
 * como columna, la oferta filtra por él y el hotlink responde {@code 404}. Recibe el paquete y sus
 * filas y devuelve {@code (boolean, motivo)}; la oferta y el hotlink solo miran el booleano.
 *
 * <p><b>El orden de los motivos es fijo</b> (`CA-PM-281`, `CA-PM-377`): menos de dos productos →
 * sin descripción → paquete inactivo → paquete retirado → <b>fuera de su vigencia</b> (desde el
 * 16-09-2026, con la fecha) → un producto no ofrecible, <b>nombrado por su código</b>. Devuelve el
 * <b>primero</b> que se cumple. Va de lo que es del paquete a lo que es de sus productos, porque lo
 * primero se arregla desde el paquete y lo segundo no; y la vigencia va la última de las del
 * paquete porque es lo único suyo que cambia solo con el tiempo.
 *
 * <p><b>«Hoy» se lo pasa quien llama</b> —un {@code Clock} en UTC, el mismo con que `CM` resuelve
 * qué tasa rige—, y por eso este objeto sigue sin dependencias y se prueba con cualquier día. <b>El
 * día de fin cuenta entero</b>: un paquete que termina hoy se ofrece hoy.
 */
public record PackageOfferability(boolean offerable, String reason) {

  /** Lo que la decisión necesita de cada producto del paquete. */
  public record Producto(String code, boolean activo, boolean retirado) {

    public boolean ofrecible() {
      return activo && !retirado;
    }
  }

  private static final PackageOfferability OFRECIBLE = new PackageOfferability(true, null);

  /** La vigencia del paquete frente a un día: desde cuándo, hasta cuándo (nulo = sin fin) y hoy. */
  public record Vigencia(LocalDate hoy, LocalDate desde, LocalDate hasta) {

    public boolean todaviaNoEmpieza() {
      return hoy.isBefore(desde);
    }

    public boolean yaTermino() {
      return hasta != null && hoy.isAfter(hasta);
    }
  }

  public static PackageOfferability decidir(
      PackageStatus status,
      boolean retirado,
      boolean tieneDescripcion,
      Vigencia vigencia,
      List<Producto> productos) {
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
    if (vigencia.todaviaNoEmpieza()) {
      return new PackageOfferability(
          false, "El paquete todavía no está vigente: empieza el %s.".formatted(vigencia.desde()));
    }
    if (vigencia.yaTermino()) {
      return new PackageOfferability(
          false, "La vigencia del paquete terminó el %s.".formatted(vigencia.hasta()));
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

  /**
   * `RN-PM-044`: <b>a quién</b> se le ofrece el paquete — a quien tenga vigente la membresía de
   * origen de su upgrade; sin upgrade, a todos.
   *
   * <p>Es la otra pregunta, y por eso no es un motivo de {@link #decidir}: aquella dice si el
   * paquete se ofrece <b>a alguien</b> y esta si se le ofrece <b>a esta persona</b>. Vive aquí y no
   * en la oferta porque desde el 17-09-2026 la responden dos lecturas —la oferta (`RF-PM-007`) y la
   * venta del paquete (`RF-MV-012`), que distingue «no se ofrece» de «no se te ofrece a ti»— y
   * tienen que decir lo mismo.
   *
   * <p>Hay un upgrade como máximo —lo garantiza `RF-PM-023` al asociar, `RN-PM-046`—, y el {@code
   * allMatch} lo mira. Se deja el {@code allMatch} y no un {@code findFirst}: si algún día una fila
   * vieja o una carga a mano dejara dos, el paquete no se ofrecería a quien no puede comprarlo
   * entero.
   *
   * @param origenesDeSusUpgrades el origen de cada upgrade del paquete; vacía si no lleva ninguno
   * @param membresiaVigente la de quien mira, o nula si hoy no tiene nivel
   */
  public static boolean correspondeA(List<UUID> origenesDeSusUpgrades, UUID membresiaVigente) {
    return origenesDeSusUpgrades.stream()
        .allMatch(origen -> origen != null && origen.equals(membresiaVigente));
  }
}
