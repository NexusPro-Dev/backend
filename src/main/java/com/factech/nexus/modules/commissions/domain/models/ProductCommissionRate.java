package com.factech.nexus.modules.commissions.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La asociación entre una tasa de rol y un producto (`RF-CM-007`).
 *
 * <p><b>Es lo único que pone una tasa en vigor.</b> Sin ella, el catálogo entero no paga nada a
 * nadie (`RN-CM-012`): la ausencia dejó de significar «todos los productos» y pasó a significar
 * «ninguno».
 *
 * <p><b>La identidad es {@code (product_id, role_id)} y la regla vive ahí</b>: `RN-CM-013` —un solo
 * porcentaje por rol y producto— <b>no es una comprobación, es la forma de la tabla</b>. Dos tasas
 * del mismo rol sobre el mismo producto harían indeterminada la resolución y la elección quedaría a
 * criterio del plan de ejecución.
 *
 * <p><b>El rol va copiado, y no es la desnormalización que parece.</b> Existe justamente para que
 * esa clave primaria pueda formarse: sin él, la unicidad tendría que unir dos tablas y ningún
 * índice lo hace. Y <b>no puede divergir</b> del que la tasa declara, porque la clave foránea es
 * <b>compuesta</b> — {@code (commission_rate_id, role_id)} apunta a {@code commission_rates(id,
 * role_id)}. Copiar un rol distinto es imposible, no improbable.
 *
 * <p><b>No tiene retiro lógico</b>, a propósito: una asociación no es un hecho del pasado que haya
 * que conservar, es una configuración vigente. Lo que hay que conservar —con qué porcentaje se
 * pagó— es obligación de la liquidación (`RN-CM-008`). Desasociar borra la fila y deja registro de
 * eliminación <b>física</b> con motivo (Art. V.13), que es donde queda la huella.
 */
@Entity
@Table(name = "product_commission_rates")
@IdClass(ProductCommissionRate.Key.class)
public class ProductCommissionRate {

  @Id
  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Id
  @Column(name = "role_id", nullable = false, updatable = false)
  private UUID roleId;

  @Column(name = "commission_rate_id", nullable = false)
  private UUID commissionRateId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** Exigido por JPA. */
  protected ProductCommissionRate() {}

  /**
   * Asocia una tasa a un producto.
   *
   * <p><b>El rol no se recibe: se toma de la tasa.</b> Aceptarlo por parámetro permitiría enviar
   * uno distinto, y aunque la clave foránea compuesta lo rechazaría en el motor, el error llegaría
   * como una violación de integridad en lugar de como lo que es — un dato que nadie tenía que dar.
   */
  public static ProductCommissionRate create(
      UUID productId, CommissionRate tasa, OffsetDateTime ahora) {
    ProductCommissionRate asociacion = new ProductCommissionRate();
    asociacion.productId = productId;
    asociacion.roleId = tasa.getRoleId();
    asociacion.commissionRateId = tasa.getId();
    asociacion.createdAt = ahora;
    return asociacion;
  }

  /** El estado completo, para la auditoría. */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("product_id", productId.toString());
    estado.put("role_id", roleId.toString());
    estado.put("commission_rate_id", commissionRateId.toString());
    return estado;
  }

  public UUID getProductId() {
    return productId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public UUID getCommissionRateId() {
    return commissionRateId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * La clave compuesta, exigida por {@code @IdClass}.
   *
   * <p>Lleva {@code equals} y {@code hashCode} porque JPA los usa para identificar la entidad en el
   * contexto de persistencia: sin ellos, dos lecturas de la misma fila serían dos objetos distintos
   * y el {@code merge} escribiría una fila nueva.
   */
  public static class Key implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID productId;
    private UUID roleId;

    /** Exigido por JPA. */
    public Key() {}

    public Key(UUID productId, UUID roleId) {
      this.productId = productId;
      this.roleId = roleId;
    }

    @Override
    public boolean equals(Object otro) {
      if (this == otro) {
        return true;
      }
      return otro instanceof Key clave
          && Objects.equals(productId, clave.productId)
          && Objects.equals(roleId, clave.roleId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(productId, roleId);
    }
  }
}
