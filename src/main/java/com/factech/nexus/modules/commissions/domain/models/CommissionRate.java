package com.factech.nexus.modules.commissions.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una tasa de comisión <b>de rol</b>: qué paga <b>un producto</b> a un rol vendedor (`RF-CM-001`).
 *
 * <p><b>Nace con su producto y rige solo sobre él, desde que existe</b> (`RN-CM-021`, 15-09-2026).
 * Hasta esa fecha era catálogo —sin producto— y regía únicamente donde se la asociaba ({@code
 * product_commission_rates}, retirada en {@code V94}); hoy <b>registrarla es ponerla en vigor</b>,
 * y el error posible cambió de signo: ya no es «configuré y no paga» sino «registré y paga desde
 * ya» (`requirements/cm.md` §5.4).
 *
 * <p><b>Sin persona ni vigencia</b>, que es lo que la distingue de la versión del 28-08-2026: la
 * persona vive en {@link UserCommissionRate}, con su propia vigencia y su propia asociación.
 *
 * <p><b>Y por no llevar vigencia, esta tabla ya no es un historial.</b> No hay dos filas contando
 * cada una su parte: hay una que ahora dice otra cosa. <b>Corregir un porcentaje reescribe lo que
 * rigió siempre</b>, y lo único que puede preservar el pasado es que la liquidación copie el
 * porcentaje que aplicó (`RN-CM-008`) — liquidación que todavía no existe.
 *
 * <p><b>Varias tasas por rol son legítimas, una por producto</b>: «`AGENTE` 10 %» en un producto y
 * «`AGENTE` 15 %» en otro. Lo que no puede repetirse es un rol sobre el <b>mismo</b> producto entre
 * las vivas, y eso lo cierra {@code uq_commission_rates_product_role} (`RN-CM-013`).
 */
@Entity
@Table(name = "commission_rates")
public class CommissionRate {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /**
   * El producto que paga esta tasa (`RN-CM-021`, 15-09-2026).
   *
   * <p><b>Obligatorio e inmutable</b>: la tasa de rol nace con su producto y rige solo sobre él.
   * Cambiar de producto es retirar la tasa y registrar otra, porque lo que se pagó por el primero
   * tiene que seguir resolviendo la misma fila. Identificador y no asociación, por lo mismo que en
   * {@code Product}: la entidad vive en otro módulo (D-25).
   */
  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  /**
   * El rol al que la tasa paga. <b>Inmutable</b>: cambiarlo no corrige la tasa, crea otra sobre el
   * mismo producto con un rol que nadie eligió.
   */
  @Column(name = "role_id", nullable = false, updatable = false)
  private UUID roleId;

  /**
   * <b>Lo que paga: la forma y la cifra, como una sola cosa.</b>
   *
   * <p>Desde el 02-09-2026 puede ser un porcentaje o un importe fijo (`RN-CM-016`). Está incrustado
   * y no suelto porque «una forma y solo una» no se puede evaluar mirando un campo — ver {@link
   * CommissionValue}.
   */
  @Embedded private CommissionValue value;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected CommissionRate() {}

  /**
   * Declara una tasa de rol.
   *
   * @param ahora instante del alta, inyectado para que la prueba pueda fijarlo
   */
  public static CommissionRate create(
      UUID id, UUID productId, UUID roleId, CommissionValue value, OffsetDateTime ahora) {
    if (productId == null) {
      String mensaje = "El producto de la tasa es obligatorio.";
      throw new ValidationException(
          "VAL-013", mensaje, List.of(new FieldError("productId", "VAL-013", mensaje)));
    }

    if (value == null) {
      String mensaje = "La forma de la comisión es obligatoria: porcentaje o valor fijo.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("rateType", "VAL-002", mensaje)));
    }

    CommissionRate tasa = new CommissionRate();
    tasa.id = id;
    tasa.productId = productId;
    tasa.roleId = roleId;
    tasa.value = value;
    tasa.createdAt = ahora;
    tasa.updatedAt = ahora;
    return tasa;
  }

  /**
   * Corrige el porcentaje y <b>devuelve qué cambió de verdad</b> (`RF-CM-003`).
   *
   * <p><b>Aquí ya no hay «corregir» frente a «cambiar».</b> En el modelo anterior eran dos
   * operaciones distintas —corregir reescribía, cambiar cerraba una vigencia y abría otra— porque
   * la tarifa tenía fechas. Sin ellas <b>solo queda reescribir</b>, y con ello la certeza de que
   * esta llamada <b>borra lo que la tasa dijo hasta ahora</b> sin dejar rastro en ningún sitio.
   *
   * <p><b>{@code updatedAt} solo se mueve si algo cambió</b>: una petición que no cambia nada no es
   * un cambio, y moverla haría creer que alguien tocó la tasa.
   *
   * @return los campos que cambiaron, cada uno con {@code before} y {@code after}. Vacío si la
   *     petición no cambió nada
   */
  public Map<String, Object> update(Patchable<CommissionValue> nuevoValor, OffsetDateTime ahora) {

    Map<String, Object> cambios = new LinkedHashMap<>();

    if (nuevoValor.presente()) {
      CommissionValue valor = nuevoValor.valor();
      if (valor == null) {
        String mensaje = "La forma de la comisión no puede vaciarse.";
        throw new ValidationException(
            "VAL-002", mensaje, List.of(new FieldError("rateType", "VAL-002", mensaje)));
      }
      // NO es `compareTo` sobre la cifra, y ese detalle es el defecto silencioso
      // de esta operación. `10 %` y `10` de importe fijo dan `compareTo == 0` y
      // NO son ni remotamente el mismo valor: si se comparara así, esta
      // corrección devolvería éxito sin escribir, sin auditar y sin mover la
      // marca de modificación, y la tasa seguiría pagando el 10 %.
      // Tampoco es `equals`, que daría 10.00 y 10.0000 por distintos y llenaría
      // el registro de cambios que no cambian nada. Ver `CommissionValue`.
      if (!value.mismoValorQue(valor)) {
        cambios.put(
            "value", Map.of("before", value.paraAuditoria(), "after", valor.paraAuditoria()));
        value = valor;
      }
    }

    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /**
   * Retira la tasa (`RF-CM-004`, `RN-CM-005`).
   *
   * <p><b>No es idempotente</b>: retirar dos veces con dos motivos distintos dejaría el segundo
   * escrito sobre un hecho anterior. Se devuelve si hubo cambio para que ese fallo no dependa de
   * acordarse de comprobarlo.
   *
   * @return {@code true} si la tasa pasó de viva a retirada
   */
  public boolean delete(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    updatedAt = ahora;
    return true;
  }

  /**
   * El estado completo de la tasa, para la auditoría.
   *
   * <p><b>La arma el agregado y la usan los dos registros</b> —creación y eliminación—, por lo
   * mismo que en `PM`: si cada caso de uso armara su mapa, los dos describirían la misma tasa con
   * claves distintas y compararlos dejaría de ser posible.
   */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    // El producto va delante del rol: es lo que la fila configura (`RN-CM-021`).
    estado.put("product_id", productId.toString());
    estado.put("role_id", roleId.toString());
    // La forma va SIEMPRE, y no solo el número. Sin ella, un `10` guardado aquí
    // no dice si esa tasa pagaba una décima parte de la venta o diez unidades
    // de dinero — y como esta tabla no tiene vigencia, este registro es la
    // única copia que queda de lo que la tasa decía antes.
    estado.put("rate_type", value.getRateType().name());
    estado.put("value", value.cifra().toPlainString());
    return estado;
  }

  public boolean estaRetirada() {
    return deletedAt != null;
  }

  public UUID getId() {
    return id;
  }

  /** El producto que paga la tasa (`RN-CM-021`). Nunca nulo. */
  public UUID getProductId() {
    return productId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public CommissionValue getValue() {
    return value;
  }

  /**
   * El porcentaje, o <b>nulo si esta tasa paga un importe fijo</b>.
   *
   * <p>Se conserva por comodidad de quien arma respuestas, y hay que leerlo con {@link
   * CommissionValue#getRateType()} delante: un nulo aquí <b>no significa que falte</b>.
   */
  public BigDecimal getPercentage() {
    return value.getPercentage();
  }

  /** El importe fijo, o nulo si esta tasa paga un porcentaje. Ver {@link #getPercentage()}. */
  public BigDecimal getFixedAmount() {
    return value.getFixedAmount();
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }
}
