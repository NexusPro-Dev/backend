package com.factech.nexus.modules.movements.domain.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una línea de la venta, con <b>lo que se le copió</b> al catálogo (`RN-MV-002`).
 *
 * <p><b>El precio unitario y la vigencia son copias, y ahí está toda su razón de ser.</b>
 * `RF-PM-004` corrige el precio de un producto y `RN-PM-015` declara su vigencia en días: leerlas
 * del catálogo al mostrar una venta de hace un año <b>reescribiría lo que alguien pagó y lo que
 * compró</b>.
 *
 * <p><b>Lo que NO se copia es la membresía destino.</b> `RF-PM-004` rechaza cambiarla y `RN-PM-010`
 * garantiza que el producto no desaparece nunca, de modo que leerla dentro de tres años da el mismo
 * valor. Copiarla solo añadiría un sitio donde el dato pudiera discrepar de sí mismo. Es el
 * criterio del módulo: <b>se copia lo que puede cambiar; lo inmutable se referencia</b>.
 *
 * <p><b>{@code lineAmount} se guarda aunque sea {@code quantity × unitPrice}</b>, por lo mismo que
 * el total en la cabecera: es el número que se imprimió. Recalcularlo al leer hace que un cambio de
 * redondeo reescriba comprobantes ya entregados.
 *
 * <h2>El descuento es de la línea</h2>
 *
 * <p>Desde el 16-09-2026 (`RN-MV-027`) cada rebaja es un {@link LineDiscount}, y de la lista salen
 * las dos cifras congeladas: {@code lineDiscount = quantity × Σ discountValue} y {@code lineAmount
 * = quantity × unitPrice − lineDiscount}. <b>No hay constructor que las reciba</b>, por lo mismo
 * que {@link Movement} suma su total: si llegaran por parámetro, existiría una línea cuyo importe
 * no corresponde a sus rebajas. Ninguna rebaja deja la línea por debajo de cero. Hoy la lista va
 * siempre vacía, porque ninguna entrada aplica descuentos.
 *
 * <p><b>El paquete no está aquí: está en la cabecera</b> (`RN-MV-028`). Una venta lleva un paquete
 * y nada más, y {@code movements.package_id} con el {@code productId} de cada línea forma la pareja
 * que identifica su asociación en {@code product_package_items} (`RN-PM-038`) — que no tiene
 * identificador propio, y cuya fila se borra al desasociar (`RN-PM-042`).
 *
 * <h2>El vendedor es de la línea, y en una venta siempre lo hay</h2>
 *
 * <p>Desde el 16-09-2026 (`RN-MV-003`) {@code sellerId} vive aquí y no en la cabecera: la comisión
 * se devenga <b>por línea</b>, cada línea puede tener el suyo, y los tipos de movimiento que vienen
 * —depósito, comisión— no venden nada. La columna admite nulo <b>solo</b> por ellos: una línea de
 * venta sin vendedor no existe, y {@link #copiarDe} no ofrece la forma de construirla — quien
 * compra sin colgar de nadie <b>es su propio vendedor</b>. Es lo que el esquema no puede sostener,
 * porque «obligatorio en {@code VENTA}» exige mirar {@code movement_types}.
 *
 * <p>No es una entidad JPA, por el mismo motivo que {@link Movement}: ver su Javadoc.
 *
 * <h2>El nombre y la descripción SÍ se copian; el código NO</h2>
 *
 * <p>Desde el 16-09-2026 {@code productName} y {@code productDescription} se congelan en la línea,
 * y es `RN-MV-002` aplicada sin excepción: `RF-PM-004` los corrige, de modo que leerlos del
 * catálogo al mostrar una venta de hace un año <b>reescribiría lo que alguien compró</b>. Hasta ese
 * día viajaban por aquí sin guardarse, con el argumento de que corregir una errata no cambia lo
 * vendido — cierto para la errata y falso para el caso que importa: un producto renombrado de
 * verdad.
 *
 * <p><b>{@code productCode} no se copia</b>, y esa asimetría es el mismo criterio y no una
 * excepción: `RN-PM-013` declara el código <b>inmutable</b>, y lo inmutable se referencia. Se lee
 * de {@code products} al mostrar la venta y da siempre el mismo valor; copiarlo solo añadiría un
 * sitio donde el dato pudiera discrepar de sí mismo. Viaja por aquí porque la lectura que resolvió
 * el catálogo ya lo tenía.
 */
public final class MovementLine {

  private final UUID id;
  private final UUID productId;
  private final UUID sellerId;
  private final String productCode;
  private final String productName;
  private final String productDescription;
  private final int quantity;
  private final BigDecimal unitPrice;
  private final BigDecimal lineDiscount;
  private final BigDecimal lineAmount;
  private final Integer validityDays;
  private final Implementation implementation;
  private final List<LineDiscount> discounts;

  private MovementLine(
      UUID id,
      UUID productId,
      UUID sellerId,
      String productCode,
      String productName,
      String productDescription,
      int quantity,
      BigDecimal unitPrice,
      Integer validityDays,
      Implementation implementation,
      List<LineDiscount> discounts) {
    this.id = id;
    this.productId = productId;
    this.sellerId = sellerId;
    this.productCode = productCode;
    this.productName = productName;
    this.productDescription = productDescription;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.validityDays = validityDays;
    this.implementation = implementation;
    this.discounts = List.copyOf(discounts);
    // `RN-MV-027`: LAS DOS CIFRAS SALEN DE LAS REBAJAS, aquí y en ningún otro
    // sitio. Cada rebaja ya viene en dinero por unidad y en la escala de la
    // moneda; la línea multiplica por la cantidad y resta.
    BigDecimal porUnidad =
        discounts.stream()
            .map(LineDiscount::getDiscountValue)
            // La identidad lleva la escala del precio: una línea sin rebajas
            // dice «0.00» y no «0», igual que el descuento de la cabecera.
            .reduce(BigDecimal.ZERO.setScale(unitPrice.scale()), BigDecimal::add);
    BigDecimal cantidad = BigDecimal.valueOf(quantity);
    BigDecimal bruto = unitPrice.multiply(cantidad);
    this.lineDiscount = porUnidad.multiply(cantidad);
    if (this.lineDiscount.compareTo(bruto) > 0) {
      // Es lo que `ck_movement_details_discount` rechazaría en el commit; se
      // rechaza aquí para que el error aparezca donde se cometió.
      throw new IllegalArgumentException("Ningún descuento deja la línea por debajo de cero.");
    }
    this.lineAmount = bruto.subtract(this.lineDiscount);
  }

  /**
   * Copia el producto en una línea.
   *
   * <p><b>El importe no se recibe: se calcula aquí</b>, y no hay ningún constructor que lo acepte.
   * Es el mismo argumento con el que {@link Movement} suma su total: si el importe llegara por
   * parámetro, existiría una línea cuyo importe no corresponde a su precio y a su cantidad, y nada
   * lo impediría.
   *
   * @param precio el precio del catálogo <b>ya llevado a la escala de su moneda</b>. Llega con la
   *     escala de la columna de `PM` —{@code numeric(14,4)}—, y ajustarlo es responsabilidad de
   *     quien resuelve la venta, que es quien conoce la moneda
   * @param sellerId quien vendió <b>esta</b> línea (`RN-MV-003`), <b>obligatorio</b>: no hay línea
   *     de venta sin vendedor, y quien no cuelga de nadie es el suyo
   * @param productName y {@code productDescription} <b>se copian</b> (`RN-MV-002`): son lo que el
   *     catálogo decía el día de la venta, y `RF-PM-004` puede corregirlos mañana
   * @param validityDays nulo significa que lo adquirido <b>no caduca</b> (`RN-PM-015`)
   */
  public static MovementLine copiarDe(
      UUID productId,
      UUID sellerId,
      String productCode,
      String productName,
      String productDescription,
      int quantity,
      BigDecimal precio,
      Integer validityDays,
      String implementation) {
    return copiarDe(
        productId,
        sellerId,
        productCode,
        productName,
        productDescription,
        quantity,
        precio,
        validityDays,
        implementation,
        List.of());
  }

  /**
   * Copia el producto en una línea <b>con las rebajas que se le aplican</b> (`RN-MV-027`). Hoy no
   * la usa ninguna entrada con rebajas: es la forma que la compra de paquetes necesitará, escrita
   * junto a la otra para que las dos cuenten igual.
   */
  public static MovementLine copiarDe(
      UUID productId,
      UUID sellerId,
      String productCode,
      String productName,
      String productDescription,
      int quantity,
      BigDecimal precio,
      Integer validityDays,
      String implementation,
      List<LineDiscount> rebajas) {
    if (sellerId == null) {
      // No es una validación de entrada: el vendedor no viene de la petición.
      // Protege de que un camino futuro arme una línea de venta sin atribución,
      // que es justo el estado que la enmienda del 16-09-2026 retiró.
      throw new IllegalArgumentException("Una línea de venta no existe sin vendedor.");
    }
    if (productName == null || productName.isBlank()) {
      // Se copia, y una copia vacía no es una copia: sin esto, una línea podría
      // quedar sin decir qué se vendió y el nulo solo aparecería al leerla.
      throw new IllegalArgumentException("Una línea congela el nombre de lo que se vendió.");
    }
    if (implementation == null) {
      // Sin la copia, la línea no se puede entregar (`RN-MV-030`): confirmar
      // no sabría si concede o espera. Se rechaza al construirla y no al
      // confirmar, que es donde el hueco aparecería con el cobro ya hecho.
      throw new IllegalArgumentException("Una línea congela cómo se entrega lo que se vendió.");
    }
    return new MovementLine(
        UUID.randomUUID(),
        productId,
        sellerId,
        productCode,
        productName,
        productDescription,
        quantity,
        precio,
        validityDays,
        Implementation.valueOf(implementation),
        rebajas == null ? List.of() : rebajas);
  }

  /** Lo que de esta línea entra en la instantánea de auditoría. */
  Map<String, Object> instantanea() {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("product_id", productId.toString());
    // La clave decide A QUIÉN SE LE PAGA por esta línea, y por eso se escribe
    // aquí y no en la cabecera desde el 16-09-2026 (`RN-MV-003`).
    datos.put("seller_id", sellerId.toString());
    datos.put("product_code", productCode);
    // Copias, y por eso están en la instantánea: lo que el catálogo decía ese
    // día, no lo que diga cuando alguien lea este registro (`RN-MV-002`).
    datos.put("product_name", productName);
    datos.put("product_description", productDescription);
    datos.put("quantity", quantity);
    datos.put("unit_price", unitPrice.toPlainString());
    datos.put("line_discount", lineDiscount.toPlainString());
    datos.put("line_amount", lineAmount.toPlainString());
    List<Map<String, Object>> rebajas = new ArrayList<>(discounts.size());
    for (LineDiscount rebaja : discounts) {
      rebajas.add(rebaja.instantanea());
    }
    datos.put("discounts", rebajas);
    // Se escribe la clave con nulo y no se omite: la ausencia de la clave se
    // leería como «esta versión no lo registraba», y el nulo dice «no caduca».
    datos.put("validity_days", validityDays);
    // Copia, como el precio (`RN-MV-030`): lo vendido se entrega como se vendió.
    datos.put("implementation", implementation.name());
    return datos;
  }

  /** ¿Cabe este importe en una moneda de tantos decimales? Lo comprueba `RN-MV-014`. */
  public boolean importeCabeEn(int decimales) {
    return lineAmount.stripTrailingZeros().scale() <= decimales;
  }

  /** El importe llevado a la escala en que lo guarda el libro. */
  public BigDecimal importeEnEscala(int decimales) {
    return lineAmount.setScale(decimales, RoundingMode.UNNECESSARY);
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return productId;
  }

  public UUID getSellerId() {
    return sellerId;
  }

  public String getProductDescription() {
    return productDescription;
  }

  public Implementation getImplementation() {
    return implementation;
  }

  public BigDecimal getLineDiscount() {
    return lineDiscount;
  }

  public List<LineDiscount> getDiscounts() {
    return discounts;
  }

  public String getProductCode() {
    return productCode;
  }

  public String getProductName() {
    return productName;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getLineAmount() {
    return lineAmount;
  }

  public Integer getValidityDays() {
    return validityDays;
  }
}
