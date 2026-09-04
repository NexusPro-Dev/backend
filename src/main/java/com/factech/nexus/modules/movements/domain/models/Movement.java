package com.factech.nexus.modules.movements.domain.models;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un hecho económico ya sucedido (`RF-MV-001`).
 *
 * <p><b>Nace {@link MovementStatus#PENDIENTE} y no existe forma de construirlo en otro estado</b>
 * (`RN-MV-004`). Que el estado no se pueda pasar por argumento es lo que hace verificable que
 * registrar una venta <b>no concede ningún nivel, no habilita ninguna cuenta y no comisiona</b>: no
 * es una promesa del caso de uso, es que la clase no ofrece el camino.
 *
 * <h2>El total lo suma el agregado, y por eso `RN-MV-013` no se puede incumplir</h2>
 *
 * <p>Si lo sumara el caso de uso, la venta podría construirse con un total que no corresponde a sus
 * líneas y nada lo impediría: la regla solo sería cierta mientras nadie se equivocara. Construido
 * aquí a partir de las líneas, <b>no hay ningún instante en que el total no sea la suma</b>. Es el
 * mismo argumento con el que `CM` metió la forma y el valor en un solo objeto.
 *
 * <h2>NO es una entidad JPA, y es la primera del sistema que no lo es</h2>
 *
 * <p>`architecture.md` §5.1 sitúa el modelo persistente en {@code domain/models}, y {@code
 * Product}, {@code Role} y {@code Membership} son a la vez agregado y entidad. Aquí se separan, por
 * dos motivos que se suman:
 *
 * <ol>
 *   <li><b>El reintento acotado del código lo exige</b> (`plan.md` §10, riesgo 2). Con {@code
 *       persist}, la violación de {@code uq_movements_code} marca la transacción para deshacerse:
 *       el segundo intento ya no puede ocurrir dentro de ella, y «tres intentos y falla» pasaría a
 *       necesitar una transacción por intento. Con {@code INSERT … ON CONFLICT DO NOTHING} —el
 *       mismo recurso que {@code UserRepository.addRoles} usa por lo mismo— el rechazo es una
 *       cuenta de filas afectadas y no una excepción, y el reintento es un bucle.
 *   <li><b>Esta tabla no se actualiza nunca</b> (`RN-MV-001`), de modo que el seguimiento de
 *       cambios de JPA —que es lo que se paga por mapearla— no tiene aquí nada que seguir. Es
 *       además la única tabla del sistema sin {@code updated_at} ni {@code deleted_at}, que es lo
 *       que el riesgo 4 del plan advertía que el gestor de auditoría no sabe tratar.
 * </ol>
 *
 * <p>La consecuencia está declarada: las lecturas de `RF-MV-006` y `RF-MV-007` usarán un
 * repositorio de consulta con registros planos, como {@code ProductQueryRepository} y su {@code
 * ProductRow}, que es el patrón dominante del proyecto para leer.
 */
public final class Movement {

  private final UUID id;
  private final UUID movementTypeId;
  private final UUID clientId;
  private final UUID sellerId;
  private final UUID paymentMethodId;
  private final UUID currencyId;
  private final MovementStatus status;
  private final BigDecimal totalAmount;
  private final BigDecimal discountAmount;
  private final BigDecimal payableAmount;
  private final OffsetDateTime occurredAt;
  private final OffsetDateTime createdAt;
  private final List<MovementLine> lines;

  /**
   * El código es lo único mutable del agregado, y solo hacia otro código.
   *
   * <p>No es una concesión: es el reintento. Al chocar contra {@code uq_movements_code} la venta no
   * cambia —los mismos productos, el mismo importe, el mismo cliente—, y lo único que hace falta es
   * otro comprobante. Reconstruir el agregado entero para eso obligaría a rehacer la suma, que es
   * justo lo que este diseño evita.
   */
  private String code;

  private Movement(
      UUID id,
      UUID movementTypeId,
      UUID clientId,
      UUID sellerId,
      UUID paymentMethodId,
      UUID currencyId,
      String code,
      List<MovementLine> lines,
      int decimales,
      OffsetDateTime occurredAt,
      OffsetDateTime createdAt) {
    this.id = id;
    this.movementTypeId = movementTypeId;
    this.clientId = clientId;
    this.sellerId = sellerId;
    this.paymentMethodId = paymentMethodId;
    this.currencyId = currencyId;
    this.code = code;
    this.lines = List.copyOf(lines);
    this.status = MovementStatus.PENDIENTE;
    this.occurredAt = occurredAt;
    this.createdAt = createdAt;

    // `RN-MV-013`: EL TOTAL ES LA SUMA DE LAS LINEAS, aquí y en ningún otro
    // sitio. Se lleva a la escala de la moneda una sola vez, sobre la suma:
    // redondear línea a línea y sumar después da un número distinto del que
    // aparece impreso al pie del comprobante.
    BigDecimal suma =
        lines.stream()
            .map(MovementLine::getLineAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(decimales, RoundingMode.UNNECESSARY);

    this.totalAmount = suma;
    // Hoy siempre cero, por decisión del responsable del proyecto del
    // 02-09-2026: no hay descuentos. No se recibe por parámetro porque un
    // descuento que se pudiera pasar sería un descuento sin autorización y sin
    // rastro, que es exactamente lo que `spec.md` §2 descarta.
    this.discountAmount = BigDecimal.ZERO.setScale(decimales, RoundingMode.UNNECESSARY);
    this.payableAmount = this.totalAmount.subtract(this.discountAmount);
  }

  /**
   * Registra la venta, <b>siempre pendiente</b>.
   *
   * <p><b>El estado y el importe a pagar no se reciben.</b> El primero porque no hay ningún camino
   * por el que esta operación produzca otra cosa; el segundo porque {@code ck_movements_payable} lo
   * ata al total y al descuento, y recibirlo permitiría escribir una fila que el esquema rechaza —
   * un error que aparecería en el {@code commit} y no aquí.
   *
   * @param lines al menos una (`RN-MV-009`). La comprobación vive aquí y no en el esquema porque un
   *     {@code CHECK} no puede contar filas de otra tabla
   * @param decimales los de la moneda de la venta (`RN-MV-014`)
   */
  public static Movement registrar(
      UUID movementTypeId,
      UUID clientId,
      UUID sellerId,
      UUID paymentMethodId,
      UUID currencyId,
      String code,
      List<MovementLine> lines,
      int decimales,
      OffsetDateTime occurredAt,
      OffsetDateTime ahora) {
    if (lines == null || lines.isEmpty()) {
      // No es una validación de entrada duplicada: `VAL-003` rechaza la
      // PETICION sin líneas, y esto rechaza el AGREGADO sin líneas. Lo segundo
      // protege de que un camino futuro —una venta armada desde otro sitio—
      // produzca una cabecera con total cero y nada que la explique.
      throw new IllegalArgumentException("Una venta no existe sin al menos una línea.");
    }
    return new Movement(
        UUID.randomUUID(),
        movementTypeId,
        clientId,
        sellerId,
        paymentMethodId,
        currencyId,
        code,
        lines,
        decimales,
        occurredAt,
        ahora);
  }

  /**
   * Otro comprobante para la misma venta, tras chocar contra {@code uq_movements_code}.
   *
   * <p>Ver el Javadoc de {@link #code}.
   */
  public void reemplazarCodigo(String nuevo) {
    this.code = nuevo;
  }

  /**
   * La instantánea completa que exige `RF-MV-001` · §6, armada <b>por el agregado</b>.
   *
   * <p>Si cada caso de uso armara su mapa, dos registros describirían la misma venta con claves
   * distintas y compararlos dejaría de ser posible. Es lo mismo que `PM` decidió.
   *
   * <p><b>El vendedor tiene que estar aquí</b>, y es lo único de esta instantánea que no es rutina:
   * es un dato que el actor no envió y que determina <b>a quién se le va a pagar</b>. Sin él, la
   * pregunta «¿por qué esta venta se le atribuyó a esta persona?» solo se puede responder
   * reconstruyendo cómo estaba la estructura comercial ese día — y `user_supervisors` conserva los
   * tramos cerrados precisamente porque esa reconstrucción es cara.
   */
  public Map<String, Object> instantanea() {
    Map<String, Object> datos = new LinkedHashMap<>();
    datos.put("code", code);
    datos.put("status", status.name());
    datos.put("client_id", clientId.toString());
    datos.put("seller_id", sellerId.toString());
    datos.put("payment_method_id", paymentMethodId.toString());
    datos.put("currency_id", currencyId.toString());
    datos.put("total_amount", totalAmount.toPlainString());
    datos.put("discount_amount", discountAmount.toPlainString());
    datos.put("payable_amount", payableAmount.toPlainString());
    datos.put("occurred_at", occurredAt.toString());

    List<Map<String, Object>> detalle = new ArrayList<>(lines.size());
    for (MovementLine linea : lines) {
      detalle.add(linea.instantanea());
    }
    datos.put("lines", detalle);
    return datos;
  }

  public UUID getId() {
    return id;
  }

  public UUID getMovementTypeId() {
    return movementTypeId;
  }

  public UUID getClientId() {
    return clientId;
  }

  public UUID getSellerId() {
    return sellerId;
  }

  public UUID getPaymentMethodId() {
    return paymentMethodId;
  }

  public UUID getCurrencyId() {
    return currencyId;
  }

  public String getCode() {
    return code;
  }

  public MovementStatus getStatus() {
    return status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public BigDecimal getDiscountAmount() {
    return discountAmount;
  }

  public BigDecimal getPayableAmount() {
    return payableAmount;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public List<MovementLine> getLines() {
    return lines;
  }
}
