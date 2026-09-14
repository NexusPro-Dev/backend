package com.factech.nexus.modules.commissions.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Los productos sobre los que rige una tasa personalizada (`RF-CM-006`, 11-09-2026).
 *
 * <p><b>Es la respuesta de asociar y de desasociar</b>, y devuelve la lista <b>completa</b> después
 * de la operación, no solo lo que se acaba de tocar: quien asocia el tercer producto tiene que
 * poder ver los tres sin una segunda llamada. Mismo criterio que {@link ProductAssociationResponse}
 * para las tasas de rol.
 *
 * <p><b>La lista vacía es una respuesta legítima</b> y significa algo: esa tasa <b>no rige en
 * ninguna parte</b> (`RN-CM-012`). Por eso viaja siempre presente.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserRateProductsResponse(UUID rateId, List<ProductRef> products) {

  /** El producto, resuelto. Un identificador suelto obligaría a otra consulta y a otro permiso. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ProductRef(UUID id, String code, String name) {}
}
