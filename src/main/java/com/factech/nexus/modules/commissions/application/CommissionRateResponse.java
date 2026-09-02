package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository.RateRow;
import com.factech.nexus.modules.system.roles.application.RoleCatalog.RoleView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una tasa de rol tal como sale de la API.
 *
 * <p><b>{@code associatedProducts} es el campo que impide el malentendido del módulo.</b> Una tasa
 * recién creada llega con cero, y ese cero significa <b>no paga nada a nadie</b> (`RN-CM-012`). Sin
 * él, el cliente vería un rol y un porcentaje y concluiría que la tasa está configurada — que es
 * exactamente lo que era cierto en el modelo anterior y dejó de serlo.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommissionRateResponse(
    UUID id, RoleRef role, BigDecimal percentage, long associatedProducts) {

  /** El rol, resuelto. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name) {}

  /** Desde el agregado recién creado, que todavía no tiene ninguna asociación. */
  public static CommissionRateResponse from(CommissionRate tasa, RoleView rol) {
    return new CommissionRateResponse(
        tasa.getId(), new RoleRef(rol.id(), rol.code(), rol.name()), tasa.getPercentage(), 0L);
  }

  /** Desde una fila leída, con el rol ya resuelto por la misma sentencia. */
  public static CommissionRateResponse from(RateRow fila) {
    return new CommissionRateResponse(
        fila.id(),
        new RoleRef(fila.roleId(), fila.roleCode(), fila.roleName()),
        fila.percentage(),
        fila.associatedProducts());
  }
}
