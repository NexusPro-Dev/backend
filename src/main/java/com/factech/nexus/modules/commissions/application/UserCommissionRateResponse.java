package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateQueryRepository.UserRateRow;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una tasa personalizada tal como sale de la API.
 *
 * <p><b>{@code JsonInclude.ALWAYS} no es decorativo</b>: sin él, el fin de vigencia de una tasa
 * indefinida llegaría <b>ausente</b> en lugar de {@code null}, y un campo que falta es
 * indistinguible de uno que el cliente no conoce. Aquí además significa algo: su ausencia es «rige
 * indefinidamente».
 *
 * <p><b>No lleva rol, y no es un olvido</b>: la tasa es de la persona (`cm.md` §7.2). Tampoco lleva
 * producto — no se acota a ninguno (`RN-CM-014`).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserCommissionRateResponse(
    UUID id, UserRef user, BigDecimal percentage, LocalDate validFrom, LocalDate validTo) {

  /** La persona, resuelta. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record UserRef(UUID id, String username, String fullName) {}

  public static UserCommissionRateResponse from(UserCommissionRate tasa, UserView persona) {
    return new UserCommissionRateResponse(
        tasa.getId(),
        new UserRef(persona.id(), persona.username(), persona.fullName()),
        tasa.getPercentage(),
        tasa.getValidFrom(),
        tasa.getValidTo());
  }

  /** Desde una fila leída, con la persona ya resuelta por la misma sentencia. */
  public static UserCommissionRateResponse from(UserRateRow fila) {
    return new UserCommissionRateResponse(
        fila.id(),
        new UserRef(fila.userId(), fila.username(), fila.userFullName()),
        fila.percentage(),
        fila.validFrom(),
        fila.validTo());
  }
}
