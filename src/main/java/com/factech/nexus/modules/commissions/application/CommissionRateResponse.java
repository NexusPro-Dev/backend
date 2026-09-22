package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository.RateRow;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.system.roles.application.RoleCatalog.RoleView;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una tasa de rol tal como sale de la API: <b>su producto, con su moneda</b>, su rol y su valor.
 *
 * <p><b>El precio y la moneda van dentro del producto</b> (15-09-2026, a petición del responsable
 * del proyecto), con la misma forma que los publica `PM` —el precio tal cual, y la moneda con
 * identificador, código y decimales—: un importe fijo es dinero en esa moneda, y un porcentaje es
 * una parte de ese precio. Sin los dos, «10.000» o «10 %» no dicen cuánto es. Viajan siempre, sea
 * cual sea la forma: el cliente no tiene que preguntar de qué forma es la tasa para saber si el
 * campo estará.
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

  /**
   * El producto, resuelto, con su precio y su moneda.
   *
   * <p><b>Con nombre propio en el contrato</b>: springdoc publica los esquemas por el nombre simple
   * de la clase, y `PM` y `CM` tienen varios {@code ProductRef} — sin esto, el contrato mostraba
   * aquí el del hotlink, con su portada y su membresía, que no es lo que esta respuesta devuelve.
   */
  @Schema(name = "CommissionRateProduct")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ProductRef(
      UUID id, String code, String name, BigDecimal price, CurrencyRef currency) {}

  /** La moneda del producto: la misma forma que `PM` publica en sus fichas. */
  @Schema(name = "CommissionRateProductCurrency")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CurrencyRef(UUID id, String code, int decimalPlaces) {}

  /** El rol, resuelto. */
  @Schema(name = "CommissionRateRole")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record RoleRef(UUID id, String code, String name) {}

  /**
   * Desde el agregado recién creado, con el rol que el alta ya resolvió y la vista de venta del
   * producto — la única lectura del puerto de `PM` que trae la moneda.
   */
  public static CommissionRateResponse from(CommissionRate tasa, RoleView rol, SaleView producto) {
    return new CommissionRateResponse(
        tasa.getId(),
        new ProductRef(
            producto.id(),
            producto.code(),
            producto.name(),
            producto.price(),
            new CurrencyRef(
                producto.currencyId(), producto.currencyCode(), producto.currencyDecimalPlaces())),
        new RoleRef(rol.id(), rol.code(), rol.name()),
        tasa.getValue().getRateType(),
        tasa.getPercentage(),
        tasa.getFixedAmount());
  }

  /** Desde una fila leída, con el producto y el rol ya resueltos por la misma sentencia. */
  public static CommissionRateResponse from(RateRow fila) {
    return new CommissionRateResponse(
        fila.id(),
        new ProductRef(
            fila.productId(),
            fila.productCode(),
            fila.productName(),
            fila.productPrice(),
            new CurrencyRef(fila.currencyId(), fila.currencyCode(), fila.currencyDecimalPlaces())),
        new RoleRef(fila.roleId(), fila.roleCode(), fila.roleName()),
        fila.rateType(),
        fila.percentage(),
        fila.fixedAmount());
  }
}
