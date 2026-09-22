package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.PackageDetailResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-019`: el detalle de un paquete, con la cuenta hecha.
 *
 * <p>Es {@link PackageDetailReader} bajo una transacción de solo lectura, y nada más: la escalera
 * —detalle, precio, conversión, ofrecibilidad, motivo de retiro— vive allí porque la comparten las
 * ocho operaciones que devuelven el paquete. <b>El retirado no es un {@code 404}</b>: se devuelve
 * con su fecha y su motivo, como el producto en `RF-PM-003`.
 */
@Service
public class GetPackageService {

  private final PackageDetailReader detalle;

  public GetPackageService(PackageDetailReader detalle) {
    this.detalle = detalle;
  }

  @Transactional(readOnly = true)
  public PackageDetailResponse detail(UUID id) {
    return detalle.leer(id);
  }
}
