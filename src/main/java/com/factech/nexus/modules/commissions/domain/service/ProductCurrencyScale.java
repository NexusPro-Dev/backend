package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.CommissionValue;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.products.application.ProductPrice;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * `RN-CM-017`, cerrada para la tasa de rol: <b>el importe fijo cabe en los decimales de la moneda
 * de su producto</b> (15-09-2026).
 *
 * <p>Hasta el 15-09-2026 una tasa de rol no conocía ningún producto al declararse, y por eso su
 * importe fijo no podía validarse contra moneda alguna — «la misma fila paga cosas distintas según
 * a qué producto se aplique». Desde que nace con su producto (`RN-CM-021`), conoce una sola moneda,
 * y un importe con más decimales de los que esa moneda admite se rechaza igual que `RN-PM-007`
 * rechaza un precio: es dinero en esa moneda, y no se puede pagar medio centavo.
 *
 * <p>Vive aparte de {@link ProductCommissionCapGuard} porque no es un tope sino una forma, y porque
 * la personalizada —que sigue sin producto hasta asociarse— no pasa por aquí.
 */
@Service
public class ProductCurrencyScale {

  private final ProductCatalog productos;

  public ProductCurrencyScale(ProductCatalog productos) {
    this.productos = productos;
  }

  /**
   * @param errorCode el `VAL` de la operación que llama: el alta y la corrección numeran distinto
   */
  public void verificar(UUID productId, CommissionValue valor, String errorCode) {
    if (valor == null || valor.getRateType() != CommissionRateType.FIJO) {
      return;
    }
    verificarImporte(productId, valor.getFixedAmount(), errorCode, "fixedAmount", "El valor fijo");
  }

  /**
   * El mismo límite para un importe suelto: el valor por FTD de un escalón afftrack (`RN-CM-038`),
   * que no es un {@link CommissionValue} porque no tiene forma que elegir.
   *
   * @param campo el campo del cuerpo que se señala en el error
   * @param nombre cómo se llama el importe en el mensaje
   */
  public void verificarImporte(
      UUID productId, java.math.BigDecimal importe, String errorCode, String campo, String nombre) {
    if (importe == null) {
      return;
    }
    List<SaleView> vistas = productos.saleViewOf(List.of(productId));
    if (vistas.isEmpty()) {
      // El caso de uso ya comprobó que el producto existe; llegar aquí sin él
      // sería un defecto, no un dato inválido.
      throw new IllegalStateException(
          "El producto " + productId + " no tiene moneda: no debería llegar aquí.");
    }
    int decimales = vistas.get(0).currencyDecimalPlaces();
    if (!ProductPrice.cabeEn(importe, decimales)) {
      String mensaje =
          nombre
              + " no admite más decimales que los de la moneda del producto ("
              + decimales
              + ").";
      throw new ValidationException(
          errorCode, mensaje, List.of(new FieldError(campo, errorCode, mensaje)));
    }
  }
}
