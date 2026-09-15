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
 * <p><b>Nació gemela de {@code ProductCommissionRate}</b>, la asociación de la tasa de rol, para
 * que el módulo tuviera <b>una sola manera</b> de decir sobre qué rige una tasa. Esa gemela se
 * retiró el 15-09-2026 (`RN-CM-021`): la tasa de rol nace con su producto, en una columna de su
 * propia fila. La personalizada <b>sigue asociándose</b> por decisión del responsable del proyecto
 * —una excepción por persona rige sobre varios productos—, y esta es hoy la única asociación del
 * módulo.
 *
 * <p><b>La identidad es {@code (user_commission_rate_id, product_id)}</b>: la misma tasa no se
 * asocia dos veces al mismo producto, y eso no es algo que alguien comprueba.
 *
 * <p><b>Lo que NO copiaba de su gemela era el {@code role_id}.</b> Allí esa columna viajaba copiada
 * de la tasa para que `RN-CM-013` pudiera declararse en el esquema, con una clave foránea compuesta
 * que impedía que divergiera. Aquí <b>no hay nada equivalente que copiar</b>: la regla hermana
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
