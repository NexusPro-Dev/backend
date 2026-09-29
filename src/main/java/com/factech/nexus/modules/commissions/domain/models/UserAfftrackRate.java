package com.factech.nexus.modules.commissions.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Un escalón de la escala afftrack <b>de una persona</b> sobre un producto FTD, con vigencia
 * (`RF-CM-019`).
 *
 * <p>Es a {@link AfftrackRate} lo que {@link UserCommissionRate} es a {@link CommissionRate}. <b>Si
 * la persona tiene al menos uno vigente el día del cierre, su escala sustituye entera la de su
 * rol</b> (`RN-CM-039`): sus escalones y los del rol no se mezclan.
 *
 * <p>Se corrigen el límite, el valor y el fin de vigencia; la persona, el producto y el inicio son
 * lo que el escalón es y no se corrigen (`RN-CM-037`).
 */
@Entity
@Table(name = "user_afftrack_rates")
public class UserAfftrackRate {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "threshold", nullable = false)
  private int threshold;

  @Column(name = "amount_per_ftd", nullable = false)
  private BigDecimal amountPerFtd;

  @Column(name = "valid_from", nullable = false, updatable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected UserAfftrackRate() {}

  public static UserAfftrackRate create(
      UUID id,
      UUID userId,
      UUID productId,
      int threshold,
      BigDecimal amountPerFtd,
      LocalDate validFrom,
      LocalDate validTo,
      OffsetDateTime ahora) {
    UserAfftrackRate escalon = new UserAfftrackRate();
    escalon.id = id;
    escalon.userId = userId;
    escalon.productId = productId;
    escalon.threshold = threshold;
    escalon.amountPerFtd = amountPerFtd;
    escalon.validFrom = validFrom;
    escalon.validTo = validTo;
    escalon.createdAt = ahora;
    escalon.updatedAt = ahora;
    return escalon;
  }

  /**
   * Corrige lo que llegó y devuelve qué cambió de verdad.
   *
   * @param nuevoLimite nulo si no se corrige
   * @param nuevoValor nulo si no se corrige
   * @param corrigeFin si {@code validTo} llegó en la petición —también vacío, que <b>quita el
   *     fin</b> (`RF-CM-006` `FA-003`)—
   */
  public Map<String, Object> corregir(
      Integer nuevoLimite,
      BigDecimal nuevoValor,
      boolean corrigeFin,
      LocalDate nuevoFin,
      OffsetDateTime ahora) {
    Map<String, Object> cambios = new LinkedHashMap<>();
    if (nuevoLimite != null && nuevoLimite != threshold) {
      cambios.put("threshold", Map.of("before", threshold, "after", nuevoLimite));
      threshold = nuevoLimite;
    }
    if (nuevoValor != null && nuevoValor.compareTo(amountPerFtd) != 0) {
      cambios.put(
          "amount_per_ftd",
          Map.of("before", amountPerFtd.toPlainString(), "after", nuevoValor.toPlainString()));
      amountPerFtd = nuevoValor;
    }
    if (corrigeFin && !java.util.Objects.equals(nuevoFin, validTo)) {
      // `Map.of` no admite nulos, y el fin vacío es justo el valor que significa algo.
      Map<String, Object> cambio = new HashMap<>();
      cambio.put("before", validTo == null ? null : validTo.toString());
      cambio.put("after", nuevoFin == null ? null : nuevoFin.toString());
      cambios.put("valid_to", cambio);
      validTo = nuevoFin;
    }
    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /** Retira el escalón; no es idempotente. */
  public boolean retirar(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    updatedAt = ahora;
    return true;
  }

  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("user_id", userId.toString());
    estado.put("product_id", productId.toString());
    estado.put("threshold", threshold);
    estado.put("amount_per_ftd", amountPerFtd.toPlainString());
    estado.put("valid_from", validFrom.toString());
    estado.put("valid_to", validTo == null ? null : validTo.toString());
    return estado;
  }

  public boolean estaRetirado() {
    return deletedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getProductId() {
    return productId;
  }

  public int getThreshold() {
    return threshold;
  }

  public BigDecimal getAmountPerFtd() {
    return amountPerFtd;
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public LocalDate getValidTo() {
    return validTo;
  }
}
