package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * El producto de un escalón afftrack: existe, no está retirado <b>y es FTD</b> (`RN-CM-002`,
 * `RN-CM-010`, `RN-CM-036`, `RN-CM-037`).
 *
 * <p>Las dos altas —de rol (`RF-CM-015`) y de persona (`RF-CM-019`)— responden con los mismos tres
 * códigos, y los tres son {@code 422} distintos: quien envía un producto que existe, vivo y no FTD
 * se ha equivocado de producto, no de catálogo. Por eso vive aquí una vez y no en cada alta.
 *
 * <p>Y sirve también a las tasas por venta, <b>al revés</b>: {@link #esFtd} es lo que `RN-CM-037`
 * usa para rechazar una tasa sobre un producto FTD.
 */
@Service
public class AfftrackProductCheck {

  private final ProductCatalog productos;

  public AfftrackProductCheck(ProductCatalog productos) {
    this.productos = productos;
  }

  /** Comprueba el producto de un escalón y lo devuelve. */
  public ProductView verificar(UUID productId) {
    ProductView producto =
        productos
            .find(productId)
            .orElseThrow(() -> rechazo("EX-003", "El producto indicado no existe."));
    if (producto.retired()) {
      throw rechazo("EX-004", "No se configuran comisiones sobre un producto retirado.");
    }
    if (!esFtd(producto.id())) {
      throw rechazo(
          "EX-005",
          "Las comisiones afftrack solo se declaran sobre la membresía BECA → BECA (un FTD).");
    }
    return producto;
  }

  /** ¿Es un producto FTD? La definición es de `PM` (`ProductCatalog.ftdProductIds`). */
  public boolean esFtd(UUID productId) {
    return productos.ftdProductIds().contains(productId);
  }

  private static UnprocessableEntityException rechazo(String codigo, String mensaje) {
    return new UnprocessableEntityException(
        codigo, mensaje, List.of(new FieldError("productId", codigo, mensaje)));
  }
}
