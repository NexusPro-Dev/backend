package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de consulta de las asociaciones.
 *
 * <p><b>Las dos direcciones son preguntas distintas y las dos se hacen.</b> «Sobre qué productos
 * rige esta tasa» la hace quien administra el catálogo; «qué paga este producto a cada rol» la hace
 * quien va a venderlo o quien revisa por qué una venta pagó lo que pagó.
 *
 * <p><b>No se pagina.</b> Una tasa se asocia a un puñado de productos y un producto tiene tantas
 * asociaciones como roles vendedores hay en el sistema — el orden de magnitud lo fija `SP`, y es
 * pequeño. Paginar aquí sería complejidad sin cliente.
 */
public interface ProductCommissionRateQueryRepository {

  /** Sobre qué productos rige esa tasa. */
  List<AssociationRow> findByRate(UUID commissionRateId);

  /** Qué paga ese producto, y a qué rol. */
  List<AssociationRow> findByProduct(UUID productId);

  /** Una asociación leída, con el producto, el rol y el porcentaje resueltos. */
  record AssociationRow(
      UUID productId,
      String productCode,
      String productName,
      UUID roleId,
      String roleCode,
      String roleName,
      UUID commissionRateId,
      CommissionRateType rateType,
      BigDecimal percentage,
      BigDecimal fixedAmount,
      OffsetDateTime createdAt) {}
}
