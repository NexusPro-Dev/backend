package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.CommissionValue;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.system.users.application.LastLinkRoles;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * <b>Lo que se comprueba de la comisión por venta directa de una tasa de rol</b> (`RN-CM-050`), al
 * registrarla (`RF-CM-001`) y al corregirla (`RF-CM-003`), en este orden:
 *
 * <ol>
 *   <li><b>{@code EX-009}</b> ({@code 422}): el rol es el último eslabón, y la directa no pagaría
 *       nunca (`RN-CM-045`). Con el mismo puerto de `SP` que usa el devengo, para que las dos
 *       respuestas no puedan divergir.
 *   <li><b>{@code VAL-014}</b>: el fijo, con los decimales de la moneda del producto (`RN-CM-017`).
 *   <li>El gratuito solo admite fijo (`RN-CM-020`), con el código del caso de uso.
 *   <li><b>{@code EX-010}</b> ({@code 409}): el fijo no paga más que el precio. Es su tope
 *       individual; <b>no entra en la suma de `RN-CM-019`</b>, porque se paga en lugar de la tasa
 *       de rol y no junto a ella.
 * </ol>
 */
@Component
public class DirectCommissionGuard {

  private static final String CAMPO = "directCommission";

  private final LastLinkRoles ultimoEslabon;
  private final ProductCatalog productos;
  private final ProductCurrencyScale escala;

  public DirectCommissionGuard(
      LastLinkRoles ultimoEslabon, ProductCatalog productos, ProductCurrencyScale escala) {
    this.ultimoEslabon = ultimoEslabon;
    this.productos = productos;
    this.escala = escala;
  }

  /**
   * @param directa la que entra; {@code null} no se comprueba —es «sin directa»—
   * @param codigoGratuito {@code EX-006} al registrar y {@code EX-008} al corregir, los códigos que
   *     cada operación ya usa para el porcentaje sobre un gratuito
   */
  public void verificar(
      UUID productId,
      String productCode,
      UUID roleId,
      CommissionValue directa,
      String codigoGratuito) {
    if (directa == null) {
      return;
    }

    if (ultimoEslabon.ids().contains(roleId)) {
      String mensaje =
          "La comisión por venta directa solo la declaran los roles que no son el último eslabón.";
      throw new UnprocessableEntityException(
          "EX-009", mensaje, List.of(new FieldError(CAMPO, "EX-009", mensaje)));
    }

    boolean fija = directa.getRateType() == CommissionRateType.FIJO;
    if (fija) {
      escala.verificarImporte(
          productId,
          directa.getFixedAmount(),
          "VAL-014",
          CAMPO + ".fixedAmount",
          "El valor fijo de la directa");
    }

    BigDecimal precio = precioDe(productId);
    if (precio.signum() == 0) {
      if (!fija) {
        String mensaje =
            "El producto "
                + productCode
                + " es gratuito: solo admite comisiones de importe fijo, no de porcentaje.";
        throw new BusinessRuleException(
            codigoGratuito,
            mensaje,
            List.of(new FieldError(CAMPO + ".rateType", codigoGratuito, mensaje)));
      }
      return;
    }

    if (fija && directa.getFixedAmount().compareTo(precio) > 0) {
      String mensaje = "La comisión por venta directa no puede pagar más que el precio del producto.";
      throw new BusinessRuleException(
          "EX-010", mensaje, List.of(new FieldError(CAMPO + ".fixedAmount", "EX-010", mensaje)));
    }
  }

  private BigDecimal precioDe(UUID productId) {
    List<SaleView> vistas = productos.saleViewOf(List.of(productId));
    if (vistas.isEmpty()) {
      throw new IllegalStateException(
          "El producto " + productId + " no tiene vista de venta: no debería llegar aquí.");
    }
    return vistas.get(0).price();
  }
}
