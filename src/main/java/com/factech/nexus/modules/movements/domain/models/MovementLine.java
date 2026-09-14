package com.factech.nexus.modules.movements.domain.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
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
 * <p>No es una entidad JPA, por el mismo motivo que {@link Movement}: ver su Javadoc.
 *
 * <h2>{@code productCode} y {@code productName} NO se guardan, y no son copias</h2>
 *
 * <p>{@code movement_details} no tiene esas columnas (`requirements/mv.md` §7.3) y no es un olvido:
 * el nombre de un producto <b>no</b> es de lo que se congela, porque corregir una errata no
 * reescribe lo que alguien compró. Viajan aquí porque la respuesta de la venta los devuelve
 * (`RF-MV-001` · §6.2) y la lectura que resolvió el catálogo ya los tenía: volver a pedirlos sería
 * una consulta más para un dato que está en la mano.
 *
 * <p>La distinción importa al leer este código: <b>lo que se persiste desde aquí es lo copiado</b>
 * —precio unitario, importe y vigencia—, y lo demás es presentación.
 */
public final class MovementLine {

  private final UUID id;
  private final UUID productId;
  private final String productCode;
  private final String productName;
  private final int quantity;
  private final BigDecimal unitPrice;
  private final BigDecimal lineAmount;
  private final Integer validityDays;

  private MovementLine(
      UUID id,
      UUID productId,
      String productCode,
      String productName,
      int quantity,
      BigDecimal unitPrice,
      Integer validityDays) {
    this.id = id;
    this.productId = productId;
    this.productCode = productCode;
    this.productName = productName;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.validityDays = validityDays;
    this.lineAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
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
   * @param validityDays nulo significa que lo adquirido <b>no caduca</b> (`RN-PM-015`)
   */
  public static MovementLine copiarDe(
      UUID productId,
      String productCode,
      String productName,
      int quantity,
      BigDecimal precio,
      Integer validityDays) {
    return new MovementLine(
        UUID.randomUUID(), productId, productCode, productName, quantity, precio, validityDays);
  }

  /** Lo que de esta línea entra en la instantánea de auditoría. */
  Map<String, Object> instantanea() {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("product_id", productId.toString());
    datos.put("product_code", productCode);
    datos.put("quantity", quantity);
    datos.put("unit_price", unitPrice.toPlainString());
    datos.put("line_amount", lineAmount.toPlainString());
    // Se escribe la clave con nulo y no se omite: la ausencia de la clave se
    // leería como «esta versión no lo registraba», y el nulo dice «no caduca».
    datos.put("validity_days", validityDays);
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
