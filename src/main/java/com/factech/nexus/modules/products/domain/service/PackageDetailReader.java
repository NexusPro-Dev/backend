package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.domain.models.PackageOfferability;
import com.factech.nexus.modules.products.domain.models.PackagePricing;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageDetail;
import com.factech.nexus.shared.audit.DeletionReasonReader;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Arma la respuesta de administración del paquete: detalle → precio → conversión → ofrecibilidad →
 * motivo de retiro (`RF-PM-019` §8).
 *
 * <p><b>Un solo sitio</b>, porque lo devuelven ocho operaciones —el alta, el detalle y las seis
 * escrituras— y cada una que lo armara por su cuenta sería una copia de la escalera que podría
 * quedarse atrás. <b>Tres sentencias</b> —el paquete con sus filas, la moneda de casa, la tasa solo
 * si hay algo que convertir—, <b>cuatro</b> con conversión y <b>cinco</b> cuando está retirado y
 * hay que leer el motivo (`CA-PM-284`).
 */
@Component
public class PackageDetailReader {

  static final String MODULO = "PM";
  static final String ENTIDAD = "product_packages";

  private final ProductPackageQueryRepository consultas;
  private final DeletionReasonReader motivos;
  private final ProductExchangeResolver conversiones;

  public PackageDetailReader(
      ProductPackageQueryRepository consultas,
      DeletionReasonReader motivos,
      ProductExchangeResolver conversiones) {
    this.consultas = consultas;
    this.motivos = motivos;
    this.conversiones = conversiones;
  }

  /** El detalle, o el `404` de `RF-PM-019` `EX-001`. Un retirado <b>no</b> es un `404`. */
  public PackageDetailResponse leer(UUID id) {
    PackageDetail detalle =
        consultas
            .findDetail(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un paquete con ese identificador."));
    return armar(detalle);
  }

  public PackageDetailResponse armar(PackageDetail detalle) {
    PackagePricing cuenta = detalle.precio();
    PackageOfferability ofrecibilidad = detalle.ofrecibilidad();
    UUID moneda = detalle.paquete().currencyId();
    // El nulo aquí significa que el paquete está vivo, y la respuesta lo omite
    // del JSON en lugar de enviarlo en nulo — como el producto (`RF-PM-003`).
    String motivo =
        detalle.paquete().retirado()
            ? motivos.reasonFor(MODULO, ENTIDAD, detalle.paquete().id()).orElse(null)
            : null;
    return PackageDetailResponse.from(
        detalle,
        cuenta,
        ofrecibilidad,
        // Sobre `price` y no sobre las líneas: es el importe que se cobra. Sobre
        // cero —el paquete vacío— nula y SIN pedir nada: no hay nada que
        // convertir (`FA-001` de `RF-PM-019`).
        cuenta.price().signum() == 0
            ? null
            : conversiones.para(List.of(moneda)).de(moneda, cuenta.price()),
        motivo);
  }
}
