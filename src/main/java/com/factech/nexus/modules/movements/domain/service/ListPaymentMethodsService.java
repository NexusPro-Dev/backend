package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.PaymentMethodCatalogResponse;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Con qué se puede pagar, y dónde cada medio no sirve (`RF-MV-009`).
 *
 * <p><b>No hay nada que decidir aquí, y es correcto que así sea.</b> El servicio existe porque el
 * controlador no habla con el repositorio —`architecture.md` §5.2—, no porque haya una regla que
 * aplicar: la única de este requerimiento, `RN-MV-019`, se cumple <b>devolviendo</b> las
 * exclusiones, y el filtro por activo vive en la consulta.
 *
 * <p><b>Lo que este caso de uso NO hace es lo que lo define.</b> No comprueba el país de nadie, no
 * lo recibe y no lo podría averiguar: `users` no guarda país. La restricción se publica y quien la
 * aplica es el cliente — de modo que registrar una venta con un método excluido <b>se registra con
 * normalidad</b>, y `CA-MV-034` lo prueba a propósito para que nadie lo «arregle» sin decidirlo.
 */
@Service
public class ListPaymentMethodsService {

  private final MovementRepository movimientos;

  public ListPaymentMethodsService(MovementRepository movimientos) {
    this.movimientos = movimientos;
  }

  @Transactional(readOnly = true)
  public PaymentMethodCatalogResponse list() {
    return PaymentMethodCatalogResponse.de(movimientos.findActivePaymentMethods());
  }
}
