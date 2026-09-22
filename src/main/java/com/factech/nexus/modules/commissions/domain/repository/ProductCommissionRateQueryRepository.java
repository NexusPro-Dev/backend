package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de la lectura «qué paga este producto, y a qué rol».
 *
 * <p>Hasta el 15-09-2026 leía la tabla de asociación en sus dos direcciones. Desde `RN-CM-021` la
 * tasa de rol nace con su producto y la tabla no existe: <b>la dirección «sobre qué productos rige
 * esta tasa» perdió sentido</b> —rige sobre uno, y el catálogo lo dice— y esta, la que hace quien
 * va a vender el producto o quien revisa por qué una venta pagó lo que pagó, se lee de {@code
 * commission_rates} sin más.
 *
 * <p><b>No se pagina.</b> Un producto tiene tantas tasas como roles vendedores hay en el sistema —
 * el orden de magnitud lo fija `SP`, y es pequeño. Paginar aquí sería complejidad sin cliente.
 */
public interface ProductCommissionRateQueryRepository {

  /** Qué paga ese producto, y a qué rol: sus tasas vivas. */
  List<AssociationRow> findByProduct(UUID productId);

  /** Una tasa viva leída desde el producto, con el producto, el rol y el valor resueltos. */
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
