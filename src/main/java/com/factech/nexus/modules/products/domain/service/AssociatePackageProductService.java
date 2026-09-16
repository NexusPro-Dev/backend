package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.AssociatePackageProductRequest;
import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.domain.models.DiscountValue;
import com.factech.nexus.modules.products.domain.models.PackageItem;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.models.ProductPackage;
import com.factech.nexus.modules.products.domain.models.ProductStatus;
import com.factech.nexus.modules.products.domain.repository.PackageItemRepository;
import com.factech.nexus.modules.products.domain.repository.PackageItemRepository.Hermana;
import com.factech.nexus.modules.products.domain.repository.ProductPackageRepository;
import com.factech.nexus.modules.products.domain.repository.ProductRepository;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-023`: asociar un producto a un paquete con su descuento.
 *
 * <p><b>La operación que define al paquete</b>: siete verificaciones en fila, cada una con su
 * código. <b>El paquete se bloquea y el producto no</b>: dos asociaciones simultáneas al mismo
 * paquete se ordenan por el bloqueo, `RN-PM-046` se comprueba sobre el estado que dejó la primera,
 * y el producto no se escribe, de modo que no hay nada que proteger en él. Ese bloqueo es lo único
 * que sostiene «un upgrade por paquete»: el tipo vive en `products`, y ningún índice de las filas
 * puede mirarlo.
 *
 * <p><b>`EX-002` es `422` y `EX-003` es `409`, y el salto es deliberado</b>: el producto
 * inexistente es un dato del cuerpo que no resuelve —el trato de `RF-CM-007`—; el inactivo
 * <b>existe</b>, y quien llama tiene {@code packages:update}, ve el catálogo entero y merece saber
 * por qué no entra.
 */
@Service
public class AssociatePackageProductService {

  static final String ENTIDAD_FILA = "product_package_items";

  private final ProductPackageRepository paquetes;
  private final PackageItemRepository filas;
  private final ProductRepository productos;
  private final CurrencyCatalog monedas;
  private final AuditWriter auditoria;
  private final PackageDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public AssociatePackageProductService(
      ProductPackageRepository paquetes,
      PackageItemRepository filas,
      ProductRepository productos,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      PackageDetailReader detalle) {
    this(paquetes, filas, productos, monedas, auditoria, detalle, Clock.systemUTC());
  }

  AssociatePackageProductService(
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
  public PackageDetailResponse associate(UUID packageId, AssociatePackageProductRequest peticion) {
    ProductPackage paquete = paqueteVivo(paquetes, packageId);
    // La moneda del paquete, por el puerto de `SP`: da los decimales con los
    // que se valida la forma del fijo y el código con el que se nombra la cota.
    CurrencyView moneda = monedaDe(monedas, paquete);
    DiscountValue descuento =
        DiscountValue.of(peticion.discountType(), peticion.discountValue(), moneda.decimalPlaces());

    Product producto =
        productos
            .findById(peticion.productId())
            .orElseThrow(
                () -> {
                  String mensaje = "El producto indicado no existe.";
                  return new UnprocessableEntityException(
                      "EX-002", mensaje, List.of(new FieldError("productId", "EX-002", mensaje)));
                });
    if (producto.estaRetirado() || producto.getStatus() != ProductStatus.ACTIVO) {
      String mensaje = "El producto no está a la venta: solo se asocia lo activo y no retirado.";
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("productId", "EX-003", mensaje)));
    }
    if (!producto.getCurrencyId().equals(paquete.getCurrencyId())) {
      String mensaje =
          "El producto está en %s y el paquete en %s: un paquete solo reúne productos en su moneda."
              .formatted(codigoDeMoneda(producto.getCurrencyId()), moneda.code());
      throw new BusinessRuleException(
          "EX-004", mensaje, List.of(new FieldError("productId", "EX-004", mensaje)));
    }

    // UNA sola lectura de las hermanas para dos preguntas: si ya está (`EX-005`)
    // y si el paquete ya tiene su upgrade (`EX-007`).
    List<Hermana> hermanas = filas.findSiblings(paquete.getId());
    if (hermanas.stream().anyMatch(h -> h.productId().equals(producto.getId()))) {
      String mensaje =
          "Ese producto ya está en el paquete. Corrija su descuento en lugar de asociarlo de nuevo.";
      throw new BusinessRuleException(
          "EX-005", mensaje, List.of(new FieldError("productId", "EX-005", mensaje)));
    }

    // Contra el precio DE HOY (`RN-PM-037`). Nadie vuelve a comprobarlo después.
    descuento.verificarCota(producto.getPrice(), moneda.code(), "EX-006");

    if (producto.getType().exigeDestino()) {
      verificarUnicoUpgrade(hermanas);
    }

    PackageItem fila =
        filas.save(
            PackageItem.create(
                paquete.getId(), producto.getId(), descuento, OffsetDateTime.now(reloj)));

    // La instantánea lleva el precio del producto EN ESTE INSTANTE: es contra lo
    // que se validó el descuento, y lo que una revisión posterior necesitará
    // para entender por qué se admitió un fijo que hoy supera el precio. El
    // `entity_id` es el del PAQUETE: la fila es suya y no tiene identificador.
    auditoria.recordChange(
        new ChangeEvent(
            PackageDetailReader.MODULO,
            ENTIDAD_FILA,
            paquete.getId(),
            ChangeAction.CREATE,
            fila.instantanea(producto.getPrice())));

    return detalle.leer(paquete.getId());
  }

  /**
   * `RN-PM-046`: un paquete lleva UN upgrade como máximo. El que ya está ocupa el sitio, sea del
   * origen y el destino que sea —también si hoy está inactivo: el sitio lo ocupa la fila—, y los
   * bots no cuentan (`FA-003`, `FA-004`). Hasta el 16-09-2026 aquí se comparaban orígenes
   * (`RN-PM-044`); con un solo upgrade no hay con qué comparar, y `EX-007` cambió de letra.
   */
  private static void verificarUnicoUpgrade(List<Hermana> hermanas) {
    Hermana ocupante = hermanas.stream().filter(Hermana::esUpgrade).findFirst().orElse(null);
    if (ocupante == null) {
      return;
    }
    String mensaje =
        "Un paquete lleva un solo upgrade, y este ya tiene %s. Quítelo antes de asociar otro."
            .formatted(ocupante.productCode());
    throw new BusinessRuleException(
        "EX-007", mensaje, List.of(new FieldError("productId", "EX-007", mensaje)));
  }

  private String codigoDeMoneda(UUID id) {
    return monedas.find(id).map(CurrencyView::code).orElse(String.valueOf(id));
  }

  /** El paquete vivo, bloqueado, o el `404` que comparten las seis escrituras del paquete. */
  static ProductPackage paqueteVivo(ProductPackageRepository paquetes, UUID id) {
    return paquetes
        .findAliveByIdForUpdate(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-001", "No existe un paquete vivo con ese identificador."));
  }

  static CurrencyView monedaDe(CurrencyCatalog monedas, ProductPackage paquete) {
    return monedas
        .find(paquete.getCurrencyId())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "El paquete referencia una moneda que el catálogo no conoce: "
                        + paquete.getCurrencyId()));
  }
}
