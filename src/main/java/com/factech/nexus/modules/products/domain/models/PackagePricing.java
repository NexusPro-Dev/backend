package com.factech.nexus.modules.products.domain.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La cuenta del paquete (`RN-PM-036`, `RN-PM-037`), en un solo sitio.
 *
 * <p>Recibe los decimales de la moneda del paquete y una línea por producto —su identificador, su
 * precio <b>de hoy</b> y su descuento— y devuelve el precio de cada uno dentro del paquete y los
 * tres totales: {@code listPrice} (la suma sin descuentos), {@code price} (la suma con ellos) y
 * {@code savings} (la diferencia).
 *
 * <p><b>Se redondea por producto y el total es la suma</b>, no al revés: el total tiene que cuadrar
 * con las líneas que el front pinta (`CA-PM-277`). <b>Y no hay columna donde guardarlo</b>: si un
 * producto cambia de precio, todos los paquetes que lo contienen cambian solos (`CA-PM-278`).
 *
 * <p>Es un objeto de dominio sin dependencias: lo consumen el detalle, la lista, la oferta y el
 * hotlink, y <b>es el único sitio del sistema que sabe restar un descuento</b> — a través de {@link
 * DiscountValue#precioDentroDe}. Si la cuenta cambia, cambia ahí.
 */
public final class PackagePricing {

  /** Una línea del paquete: el producto, su precio de catálogo hoy y su descuento. */
  public record Linea(UUID productId, BigDecimal precio, DiscountValue descuento) {}

  private final int decimales;
  private final Map<UUID, BigDecimal> porProducto;
  private final BigDecimal listPrice;
  private final BigDecimal price;

  private PackagePricing(
      int decimales, Map<UUID, BigDecimal> porProducto, BigDecimal listPrice, BigDecimal price) {
    this.decimales = decimales;
    this.porProducto = porProducto;
    this.listPrice = listPrice;
    this.price = price;
  }

  /**
   * Sobre una lista vacía devuelve tres ceros en la escala de la moneda (`FA-001` de `RF-PM-019`).
   */
  public static PackagePricing calcular(int decimales, List<Linea> lineas) {
    Map<UUID, BigDecimal> porProducto = new LinkedHashMap<>();
    BigDecimal lista = BigDecimal.ZERO.setScale(decimales, RoundingMode.HALF_UP);
    BigDecimal total = BigDecimal.ZERO.setScale(decimales, RoundingMode.HALF_UP);
    for (Linea linea : lineas) {
      BigDecimal enPaquete = linea.descuento().precioDentroDe(linea.precio(), decimales);
      porProducto.put(linea.productId(), enPaquete);
      lista = lista.add(linea.precio().setScale(decimales, RoundingMode.HALF_UP));
      total = total.add(enPaquete);
    }
    return new PackagePricing(decimales, Map.copyOf(porProducto), lista, total);
  }

  /** El precio de un producto dentro del paquete; nulo si el producto no está en la cuenta. */
  public BigDecimal precioDe(UUID productId) {
    return porProducto.get(productId);
  }

  /** La suma de los precios de catálogo, sin descuentos. */
  public BigDecimal listPrice() {
    return listPrice;
  }

  /** La suma de los precios dentro del paquete: lo que vale el paquete hoy. */
  public BigDecimal price() {
    return price;
  }

  /** Lo que se ahorra quien compra el paquete frente a comprar cada producto suelto. */
  public BigDecimal savings() {
    return listPrice.subtract(price).setScale(decimales, RoundingMode.HALF_UP);
  }
}
