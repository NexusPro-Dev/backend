package com.factech.nexus.modules.commissions.domain.models;

import com.factech.nexus.shared.persistence.MinorUnitsConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Un escalón de la escala afftrack <b>de un rol</b> sobre un producto FTD (`RF-CM-015`).
 *
 * <p>«Al reunir {@code threshold} FTD en un cierre, {@code amountPerFtd} por cada uno»: cada cierre
 * paga <b>el mayor límite alcanzado, una vez</b>, y guarda lo que sobra (`RN-CM-041`). Una escala
 * son varios escalones del mismo rol y producto con límites distintos.
 *
 * <p><b>Calca {@link CommissionRate} sin la forma</b>: un escalón siempre paga un importe por FTD
 * (`RN-CM-038`), y un tipo con un solo valor posible sería una columna que no dice nada. Nace con
 * su producto y su rol, que no se corrigen (`RN-CM-037`); el límite y el valor sí (`RF-CM-017`).
 */
@Entity
@Table(name = "afftrack_rates")
public class AfftrackRate {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /** El producto FTD. Identificador y no asociación: la entidad es de `PM` (D-25). */
  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "role_id", nullable = false, updatable = false)
  private UUID roleId;

  @Column(name = "threshold", nullable = false)
  private int threshold;

  @Column(name = "amount_per_ftd", nullable = false)
  @Convert(converter = MinorUnitsConverter.class)
  private BigDecimal amountPerFtd;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected AfftrackRate() {}

  /** Declara un escalón. La forma de los datos ya la validó la petición. */
  public static AfftrackRate create(
      UUID id,
      UUID productId,
      UUID roleId,
      int threshold,
      BigDecimal amountPerFtd,
      OffsetDateTime ahora) {
    AfftrackRate escalon = new AfftrackRate();
    escalon.id = id;
    escalon.productId = productId;
    escalon.roleId = roleId;
    escalon.threshold = threshold;
    escalon.amountPerFtd = amountPerFtd;
    escalon.createdAt = ahora;
    escalon.updatedAt = ahora;
    return escalon;
  }

  /**
   * Corrige el límite o el valor y <b>devuelve qué cambió de verdad</b> (`RF-CM-017`).
   *
   * <p>El valor se compara con {@code compareTo} y no con {@code equals}: {@code 8000} y {@code
   * 8000.0000} son el mismo valor, y registrarlo como cambio llenaría la auditoría de cambios que
   * no cambian nada (`FA-001`).
   *
   * @param nuevoLimite nulo si no se corrige
   * @param nuevoValor nulo si no se corrige
   * @return los campos que cambiaron, con {@code before} y {@code after}; vacío si ninguno
   */
  public Map<String, Object> corregir(
      Integer nuevoLimite, BigDecimal nuevoValor, OffsetDateTime ahora) {
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
    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /**
   * Retira el escalón (`RF-CM-018`, `RN-CM-005`). No es idempotente, como el retiro de una tasa.
   *
   * @return {@code true} si pasó de vivo a retirado
   */
  public boolean retirar(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    updatedAt = ahora;
    return true;
  }

  /** El estado completo, para la auditoría de alta y de retiro. */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("product_id", productId.toString());
    estado.put("role_id", roleId.toString());
    estado.put("threshold", threshold);
    estado.put("amount_per_ftd", amountPerFtd.toPlainString());
    return estado;
  }

  public boolean estaRetirado() {
    return deletedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return productId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public int getThreshold() {
    return threshold;
  }

  public BigDecimal getAmountPerFtd() {
    return amountPerFtd;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }
}
