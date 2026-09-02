package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.ProductCommissionRate;
import java.util.Optional;
import java.util.UUID;

/** Puerto de escritura de las asociaciones entre tasas y productos. */
public interface ProductCommissionRateRepository {

  /**
   * Guarda la asociación.
   *
   * @throws com.factech.nexus.shared.error.BusinessRuleException si ese rol ya tiene una tasa
   *     asociada a ese producto — la violación de {@code pk_product_commission_rates}, traducida.
   *     Es `RN-CM-013`, y la comprueba la clave primaria y no el caso de uso: dos peticiones
   *     simultáneas burlarían cualquier {@code SELECT} previo
   */
  ProductCommissionRate save(ProductCommissionRate asociacion);

  /**
   * La asociación de esa tasa con ese producto, si existe.
   *
   * <p><b>Se busca por tasa y no por rol</b>, aunque la clave primaria sea {@code (product_id,
   * role_id)}: quien desasocia nombra la tasa, y si el producto estuviera asociado a <b>otra</b>
   * tasa del mismo rol, borrar por rol retiraría una asociación que nadie pidió retirar.
   */
  Optional<ProductCommissionRate> find(UUID commissionRateId, UUID productId);

  /** Borra la asociación. Es un borrado <b>físico</b>: la tabla no tiene retiro lógico. */
  void remove(ProductCommissionRate asociacion);
}
