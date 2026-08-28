package com.factech.nexus.modules.commissions.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una tarifa de comisión (`RF-CM-001`).
 *
 * <p><b>Es a la vez agregado y modelo persistente</b>, como {@code Product} y {@code Role}:
 * `architecture.md` §5.1 sitúa el modelo persistente en {@code domain/models}.
 *
 * <p><b>La ausencia es la que da el alcance.</b> Sin persona la tarifa rige para todos los del rol;
 * sin producto, para todo el catálogo. No hay ningún campo que diga «para todos», porque podría
 * contradecir a los otros dos.
 *
 * <p><b>Lo que no se puede corregir vive aquí sin mutador</b>: el rol, el producto, la persona y el
 * inicio de vigencia. Cambiarlos no corrige la tarifa, crea otra — y reescribiría a quién se le
 * pagó.
 *
 * <p><b>`RN-CM-006` no se comprueba aquí</b>, y no es un olvido: el solapamiento mira a
 * <b>otras</b> filas y el agregado solo conoce la suya. Vive en {@code
 * ex_commission_rates_sin_solape}, en el motor, porque comprobarlo en el caso de uso sería una
 * carrera.
 */
@Entity
@Table(name = "commission_rates")
public class CommissionRate {

  private static final BigDecimal CIEN = new BigDecimal("100");

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "role_id", nullable = false, updatable = false)
  private UUID roleId;

  /** Nulo: rige para todo el catálogo. No es un dato que falte. */
  @Column(name = "product_id", updatable = false)
  private UUID productId;

  /** Nulo: rige para todos los del rol. Tampoco es un dato que falte. */
  @Column(name = "user_id", updatable = false)
  private UUID userId;

  @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal percentage;

  @Column(name = "valid_from", nullable = false, updatable = false)
  private LocalDate validFrom;

  /** Nulo: rige indefinidamente. No significa «se desconoce». */
  @Column(name = "valid_to")
  private LocalDate validTo;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected CommissionRate() {}

  /**
   * Declara una tarifa.
   *
   * @param ahora instante del alta, inyectado para que la prueba pueda fijarlo
   */
  public static CommissionRate create(
      UUID id,
      UUID roleId,
      UUID productId,
      UUID userId,
      BigDecimal percentage,
      LocalDate validFrom,
      LocalDate validTo,
      OffsetDateTime ahora) {

    verificarPorcentaje(percentage);
    verificarVigencia(validFrom, validTo);

    CommissionRate tarifa = new CommissionRate();
    tarifa.id = id;
    tarifa.roleId = roleId;
    tarifa.productId = productId;
    tarifa.userId = userId;
    tarifa.percentage = percentage;
    tarifa.validFrom = validFrom;
    tarifa.validTo = validTo;
    tarifa.createdAt = ahora;
    tarifa.updatedAt = ahora;
    return tarifa;
  }

  /**
   * Corrige lo corregible y <b>devuelve qué cambió de verdad</b> (`RF-CM-003`).
   *
   * <p><b>Los dos campos se tratan de forma opuesta ante el nulo explícito</b>: quitar el fin de
   * vigencia es una orden que se cumple —la tarifa vuelve a regir indefinidamente—, y quitar el
   * porcentaje se rechaza, porque una tarifa sin porcentaje no significa nada.
   *
   * <p><b>{@code updatedAt} solo se mueve si algo cambió</b>: una petición que no cambia nada no es
   * un cambio, y moverla haría creer que alguien tocó la tarifa.
   *
   * @return los campos que cambiaron, cada uno con {@code before} y {@code after}. Vacío si la
   *     petición no cambió nada
   */
  public Map<String, Object> update(
      Patchable<BigDecimal> nuevoPorcentaje, Patchable<LocalDate> nuevoFin, OffsetDateTime ahora) {

    Map<String, Object> cambios = new LinkedHashMap<>();

    if (nuevoPorcentaje.presente()) {
      BigDecimal valor = nuevoPorcentaje.valor();
      if (valor == null) {
        String mensaje = "El porcentaje no puede vaciarse.";
        throw new ValidationException(
            "VAL-002", mensaje, List.of(new FieldError("percentage", "VAL-002", mensaje)));
      }
      verificarPorcentaje(valor);
      // `compareTo` y no `equals`: 10.00 y 10.0000 son el mismo porcentaje con
      // distinta escala, y `equals` los daría por distintos — el registro se
      // llenaría de cambios que no cambian nada.
      if (percentage.compareTo(valor) != 0) {
        cambios.put(
            "percentage",
            Map.of("before", percentage.toPlainString(), "after", valor.toPlainString()));
        percentage = valor;
      }
    }

    if (nuevoFin.presente()) {
      LocalDate valor = nuevoFin.valor();
      verificarVigencia(validFrom, valor);
      if (!java.util.Objects.equals(valor, validTo)) {
        cambios.put("valid_to", Map.of("before", fecha(validTo), "after", fecha(valor)));
        validTo = valor;
      }
    }

    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /**
   * Retira la tarifa (`RF-CM-004`, `RN-CM-005`).
   *
   * <p><b>La vigencia NO se toca</b>, y no es un olvido: el registro de eliminación debe poder
   * decir <b>qué periodo cubría</b> lo que se retiró. Cerrarla «de paso» haría que todas las
   * instantáneas dijeran lo mismo y ese dato dejaría de significar nada — la salvaguarda habría
   * destruido la evidencia que protege. Es el criterio de `RF-PM-006` con el estado de un producto.
   *
   * <p><b>No es idempotente</b>: retirar dos veces con dos motivos distintos dejaría el segundo
   * escrito sobre un hecho anterior. Se devuelve si hubo cambio para que ese fallo no dependa de
   * acordarse de comprobarlo.
   *
   * @return {@code true} si la tarifa pasó de viva a retirada
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
   * El estado completo de la tarifa, para la auditoría.
   *
   * <p><b>La arma el agregado y la usan los dos registros</b> —creación y eliminación—, por lo
   * mismo que en `PM`: si cada caso de uso armara su mapa, los dos describirían la misma tarifa con
   * claves distintas y compararlos dejaría de ser posible.
   *
   * <p>El grado viaja calculado, porque es lo que hace legible la instantánea sin tener que deducir
   * de tres nulos qué alcance tenía.
   */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("role_id", roleId.toString());
    estado.put("product_id", productId == null ? null : productId.toString());
    estado.put("user_id", userId == null ? null : userId.toString());
    estado.put("percentage", percentage.toPlainString());
    estado.put("valid_from", validFrom.toString());
    estado.put("valid_to", validTo == null ? null : validTo.toString());
    estado.put("scope", scope().name());
    return estado;
  }

  /** El grado en que fue declarada. Se calcula, no se guarda. */
  public RateScope scope() {
    return RateScope.de(userId != null, productId != null);
  }

  public boolean estaRetirada() {
    return deletedAt != null;
  }

  /** `RN-CM-007`. El cero se admite: significa «no comisiona». */
  private static void verificarPorcentaje(BigDecimal valor) {
    if (valor == null) {
      String mensaje = "El porcentaje es obligatorio.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("percentage", "VAL-002", mensaje)));
    }
    if (valor.compareTo(BigDecimal.ZERO) < 0 || valor.compareTo(CIEN) > 0) {
      String mensaje = "El porcentaje debe estar entre cero y cien.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("percentage", "VAL-003", mensaje)));
    }
  }

  /**
   * `RN-CM-009`. El fin es opcional; su ausencia significa «indefinidamente».
   *
   * <p>Un fin <b>igual</b> al inicio se admite: una tarifa que rigió un solo día es válida.
   */
  private static void verificarVigencia(LocalDate desde, LocalDate hasta) {
    if (desde == null) {
      String mensaje = "El inicio de vigencia es obligatorio.";
      throw new ValidationException(
          "VAL-004", mensaje, List.of(new FieldError("validFrom", "VAL-004", mensaje)));
    }
    if (hasta != null && hasta.isBefore(desde)) {
      String mensaje = "El fin de vigencia no puede ser anterior a su inicio.";
      throw new ValidationException(
          "VAL-005", mensaje, List.of(new FieldError("validTo", "VAL-005", mensaje)));
    }
  }

  /**
   * La fecha en el registro de auditoría va como texto y no como ausencia.
   *
   * <p>{@code Map.of} rechaza los nulos, y aunque los admitiera, una clave que desaparece del JSON
   * haría indistinguible «se quitó el fin de vigencia» de «no se tocó» — que es justo la distinción
   * que `RF-CM-003` existe para conservar.
   */
  private static String fecha(LocalDate valor) {
    return valor == null ? "" : valor.toString();
  }

  public UUID getId() {
    return id;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public UUID getProductId() {
    return productId;
  }

  public UUID getUserId() {
    return userId;
  }

  public BigDecimal getPercentage() {
    return percentage;
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public LocalDate getValidTo() {
    return validTo;
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
