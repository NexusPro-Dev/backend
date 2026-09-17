package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.CorrectPackageDiscountRequest;
import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.domain.models.DiscountValue;
import com.factech.nexus.modules.products.domain.models.PackageItem;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.models.ProductPackage;
import com.factech.nexus.modules.products.domain.repository.PackageItemRepository;
import com.factech.nexus.modules.products.domain.repository.ProductPackageRepository;
import com.factech.nexus.modules.products.domain.repository.ProductRepository;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-024`: corregir el descuento de un producto dentro de un paquete.
 *
 * <p><b>La cota de `RF-PM-023` sobre una fila que ya existe.</b> El mismo {@link DiscountValue}, la
 * misma comprobación contra el precio <b>de hoy</b> —un fijo que entró cuando el producto valía 100
 * y hoy vale 50 no se puede corregir a 60 (`FA-002`)—, el mismo bloqueo del paquete; lo nuevo es
 * que la fila se busca por su pareja y se actualiza, con {@code type} y {@code value} en la
 * auditoría. <b>No mira el estado del producto</b>: la fila existe y el descuento es del paquete.
 */
@Service
public class CorrectPackageDiscountService {

  private final ProductPackageRepository paquetes;
  private final PackageItemRepository filas;
  private final ProductRepository productos;
  private final CurrencyCatalog monedas;
  private final AuditWriter auditoria;
  private final PackageDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public CorrectPackageDiscountService(
      ProductPackageRepository paquetes,
      PackageItemRepository filas,
      ProductRepository productos,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      PackageDetailReader detalle) {
    this(paquetes, filas, productos, monedas, auditoria, detalle, Clock.systemUTC());
  }

  CorrectPackageDiscountService(
      ProductPackageRepository paquetes,
      PackageItemRepository filas,
      ProductRepository productos,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      PackageDetailReader detalle,
      Clock reloj) {
    this.paquetes = paquetes;
    this.filas = filas;
    this.productos = productos;
    this.monedas = monedas;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public PackageDetailResponse correct(
      UUID packageId, UUID productId, CorrectPackageDiscountRequest peticion) {
    ProductPackage paquete = AssociatePackageProductService.paqueteVivo(paquetes, packageId);
    CurrencyView moneda = AssociatePackageProductService.monedaDe(monedas, paquete);
    DiscountValue nuevo =
        DiscountValue.of(peticion.discountType(), peticion.discountValue(), moneda.decimalPlaces());

    PackageItem fila = filaDe(filas, paquete.getId(), productId);
    // El producto se lee para el precio DE HOY, no para su estado: un producto
    // inactivo dentro del paquete se corrige igual (`CA-PM-320`).
    Product producto =
        productos
            .findById(productId)
            .orElseThrow(
                () -> new IllegalStateException("La fila referencia un producto que no existe."));
    nuevo.verificarCota(producto.getPrice(), moneda.code(), "EX-003");

    Map<String, Object> cambios = fila.corregir(nuevo, OffsetDateTime.now(reloj));
    if (!cambios.isEmpty()) {
      filas.flush();
      // `type` y `value` con su antes y su después (`FA-001`), y el producto
      // delante: el `entity_id` es el del paquete, y sin él el registro no diría
      // de cuál de sus filas habla.
      Map<String, Object> registro = new LinkedHashMap<>();
      registro.put("product_id", producto.getId().toString());
      registro.putAll(cambios);
      auditoria.recordChange(
          new ChangeEvent(
              PackageDetailReader.MODULO,
              AssociatePackageProductService.ENTIDAD_FILA,
              paquete.getId(),
              ChangeAction.UPDATE,
              registro));
    }
    return detalle.leer(paquete.getId());
  }

  /**
   * La pareja, o el `404` que `RF-PM-024` y `RF-PM-025` comparten: la fila que se toca no existe.
   */
  static PackageItem filaDe(PackageItemRepository filas, UUID packageId, UUID productId) {
    return filas
        .find(packageId, productId)
        .orElseThrow(
            () -> new ResourceNotFoundException("EX-002", "Ese producto no está en el paquete."));
  }
}
