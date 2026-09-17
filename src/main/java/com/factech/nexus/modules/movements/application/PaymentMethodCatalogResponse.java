package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodCatalogView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * El catálogo de métodos de pago (`RF-MV-009`).
 *
 * <p><b>Envuelto en {@code content} y sin metadatos de paginación</b>, copiando literalmente lo que
 * `RF-SP-019` fijó para las monedas: un arreglo desnudo en la raíz cierra la puerta a añadir
 * cualquier metadato después sin romper a todos los clientes, y rellenar {@code totalPages: 1}
 * diría que hay paginación donde no la hay. Tres filas no se paginan.
 *
 * <p><b>Lo que este catálogo publica y no comprueba</b> es dónde <b>no</b> vale cada método
 * (`RN-MV-019`). Quien filtra es el cliente: registrar una venta con un método excluido se registra
 * con normalidad, y eso está probado a propósito (`CA-MV-034`).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PaymentMethodCatalogResponse(List<PaymentMethodResponse> content) {

  public static PaymentMethodCatalogResponse de(List<PaymentMethodCatalogView> metodos) {
    return new PaymentMethodCatalogResponse(
        metodos.stream().map(PaymentMethodResponse::de).toList());
  }
}
