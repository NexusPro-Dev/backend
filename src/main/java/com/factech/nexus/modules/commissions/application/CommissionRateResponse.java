package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository.RateRow;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.modules.system.roles.application.RoleCatalog.RoleView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una tasa de rol tal como sale de la API: <b>su producto</b>, su rol y su valor.
 *
 * <p>Hasta el 15-09-2026 llevaba {@code associatedProducts}, y ese número era lo que impedía el
 * malentendido del módulo: una tasa con cero <b>no pagaba nada a nadie</b> (`RN-CM-012`). Desde que
 * la tasa nace con su producto (`RN-CM-021`) no hay nada que contar: toda tasa viva rige, y lo que
 * el cliente necesita saber es <b>sobre cuál</b>.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommissionRateResponse(
    UUID id,
    ProductRef product,
    RoleRef role,
    CommissionRateType rateType,
    BigDecimal percentage,
    BigDecimal fixedAmount) {

  /** El producto, resuelto. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ProductRef(UUID id, String code, String name) {}

  /** El rol, resuelto. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name) {}

  /** Desde el agregado recién creado, con el producto y el rol que el alta ya resolvió. */
  public static CommissionRateResponse from(
      CommissionRate tasa, RoleView rol, ProductView producto) {
    return new CommissionRateResponse(
        tasa.getId(),
        new ProductRef(producto.id(), producto.code(), producto.name()),
        new RoleRef(rol.id(), rol.code(), rol.name()),
        tasa.getValue().getRateType(),
        tasa.getPercentage(),
        tasa.getFixedAmount());
  }

  /** Desde una fila leída, con el producto y el rol ya resueltos por la misma sentencia. */
  public static CommissionRateResponse from(RateRow fila) {
    return new CommissionRateResponse(
        fila.id(),
        new ProductRef(fila.productId(), fila.productCode(), fila.productName()),
        new RoleRef(fila.roleId(), fila.roleCode(), fila.roleName()),
        fila.rateType(),
        fila.percentage(),
        fila.fixedAmount());
  }
}
