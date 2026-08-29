package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.models.RateScope;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository.RateRow;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.modules.system.roles.application.RoleCatalog.RoleView;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * La tarifa tal como sale de la API.
 *
 * <p><b>{@code JsonInclude.ALWAYS} no es decorativo</b>: sin el, el producto y la persona de una
 * tarifa por omision llegarian <b>ausentes</b> en lugar de {@code null}, y un campo que falta es
 * indistinguible de uno que el cliente no conoce. Aqui ademas significan algo — su ausencia es la
 * que da el alcance.
 *
 * <p><b>El grado viaja calculado</b>, para que el cliente no tenga que deducirlo de tres nulos.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommissionRateResponse(
    UUID id,
    RoleRef role,
    ProductRef product,
    UserRef user,
    RateScope scope,
    BigDecimal percentage,
    LocalDate validFrom,
    LocalDate validTo) {

  /** El rol, resuelto. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name) {}

  /** El producto, resuelto. Nulo y presente cuando la tarifa rige para todo el catalogo. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ProductRef(UUID id, String code, String name) {}

  /** La persona, resuelta. Nula y presente cuando la tarifa rige para todos los del rol. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record UserRef(UUID id, String username, String fullName) {}

  public static CommissionRateResponse from(
      CommissionRate tarifa, RoleView rol, ProductView producto, UserView persona) {
    return new CommissionRateResponse(
        tarifa.getId(),
        new RoleRef(rol.id(), rol.code(), rol.name()),
        producto == null ? null : new ProductRef(producto.id(), producto.code(), producto.name()),
        persona == null ? null : new UserRef(persona.id(), persona.username(), persona.fullName()),
        tarifa.scope(),
        tarifa.getPercentage(),
        tarifa.getValidFrom(),
        tarifa.getValidTo());
  }

  /** Desde una fila leida, con lo de otros modulos ya resuelto por la misma sentencia. */
  public static CommissionRateResponse from(RateRow fila) {
    return new CommissionRateResponse(
        fila.id(),
        new RoleRef(fila.roleId(), fila.roleCode(), fila.roleName()),
        fila.productId() == null
            ? null
            : new ProductRef(fila.productId(), fila.productCode(), fila.productName()),
        fila.userId() == null
            ? null
            : new UserRef(fila.userId(), fila.username(), fila.userFullName()),
        RateScope.de(fila.userId() != null, fila.productId() != null),
        fila.percentage(),
        fila.validFrom(),
        fila.validTo());
  }
}
