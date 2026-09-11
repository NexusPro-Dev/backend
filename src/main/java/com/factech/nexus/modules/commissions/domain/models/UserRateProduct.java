package com.factech.nexus.modules.commissions.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Sobre qué productos rige una tasa personalizada (`RN-CM-014`, 11-09-2026).
 *
 * <p><b>Gemela de {@link ProductCommissionRate}</b>, y esa simetría es la decisión: el módulo pasa
 * a tener <b>una sola manera</b> de decir sobre qué rige una tasa. Se descartó darle a la
 * personalizada una columna {@code product_id} en su propia fila —resolvía lo mismo y obligaba a
 * aprender dos formas.
 *
 * <p><b>La identidad es {@code (user_commission_rate_id, product_id)}</b>: la misma tasa no se
 * asocia dos veces al mismo producto, y eso no es algo que alguien comprueba.
 *
 * <p><b>Lo que NO copia de su gemela es el {@code role_id}.</b> Allí esa columna viaja copiada de
 * la tasa para que `RN-CM-013` pudiera declararse en el esquema, con una clave foránea compuesta
 * que impide que diverja. Aquí <b>no hay nada equivalente que copiar</b>: la regla hermana
 * (`RN-CM-006`) habla de <b>persona y fechas</b>, y las fechas no caben en una clave primaria sin
 * volver a necesitar el {@code EXCLUDE} que `V85` retiró. Copiar {@code user_id} no compraría
 * ninguna restricción y solo añadiría un dato que puede mentir.
 *
 * <p><b>Sin retiro lógico</b>, como su gemela: una asociación no es un hecho del pasado que haya
 * que conservar, es configuración vigente. Desasociar deja registro de eliminación <b>física</b>
 * con motivo, y de ahí sale `RN-CM-015` — una tasa asociada no se retira.
 */
@Entity
@Table(name = "user_commission_rate_products")
@IdClass(UserRateProduct.Key.class)
public class UserRateProduct {

  @Id
  @Column(name = "user_commission_rate_id", nullable = false, updatable = false)
  private UUID userCommissionRateId;

  @Id
  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected UserRateProduct() {}

  public static UserRateProduct create(UUID rateId, UUID productId, OffsetDateTime ahora) {
    UserRateProduct asociacion = new UserRateProduct();
    asociacion.userCommissionRateId = rateId;
    asociacion.productId = productId;
    asociacion.createdAt = ahora;
    return asociacion;
  }

  public UUID getUserCommissionRateId() {
    return userCommissionRateId;
  }

  public UUID getProductId() {
    return productId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /** La clave compuesta, que JPA exige como clase propia. */
  public static class Key implements Serializable {

    private UUID userCommissionRateId;
    private UUID productId;

    public Key() {}

    public Key(UUID userCommissionRateId, UUID productId) {
      this.userCommissionRateId = userCommissionRateId;
      this.productId = productId;
    }

    @Override
    public boolean equals(Object otro) {
      if (this == otro) {
        return true;
      }
      if (!(otro instanceof Key clave)) {
        return false;
      }
      return Objects.equals(userCommissionRateId, clave.userCommissionRateId)
          && Objects.equals(productId, clave.productId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userCommissionRateId, productId);
    }
  }
}
