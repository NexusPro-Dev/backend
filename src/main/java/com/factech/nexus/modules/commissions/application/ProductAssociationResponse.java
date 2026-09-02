package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.ProductCommissionRateQueryRepository.AssociationRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Las asociaciones de una tasa o de un producto.
 *
 * <p><b>La colección va envuelta y no desnuda</b>, por lo mismo que la oferta de `PM`: hoy no se
 * pagina —una tasa se asocia a un puñado de productos—, y el día que haga falta, añadir los campos
 * de paginación junto a {@code content} no rompe a nadie. Devolver un array en la raíz obligaría a
 * un cambio incompatible.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductAssociationResponse(List<ProductAssociationItem> content) {

  public static ProductAssociationResponse de(List<AssociationRow> filas) {
    return new ProductAssociationResponse(
        filas.stream().map(ProductAssociationItem::from).toList());
  }

  /**
   * Una asociación, con el producto, el rol y el porcentaje resueltos.
   *
   * <p><b>El porcentaje viaja aquí aunque sea de la tasa y no de la asociación</b>, y es lo que
   * hace útil la lectura por producto: «qué paga este producto a cada rol» se responde de un
   * vistazo, sin cruzar con el catálogo.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ProductAssociationItem(
      ProductRef product,
      RoleRef role,
      UUID commissionRateId,
      BigDecimal percentage,
      OffsetDateTime createdAt) {

    public static ProductAssociationItem from(AssociationRow fila) {
      return new ProductAssociationItem(
          new ProductRef(fila.productId(), fila.productCode(), fila.productName()),
          new RoleRef(fila.roleId(), fila.roleCode(), fila.roleName()),
          fila.commissionRateId(),
          fila.percentage(),
          fila.createdAt());
    }
  }

  /** El producto, resuelto. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ProductRef(UUID id, String code, String name) {}

  /** El rol, resuelto. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name) {}
}
