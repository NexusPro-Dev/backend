package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductDetailResponse;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.shared.audit.DeletionReasonReader;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detalle de un producto (`RF-PM-003`).
 *
 * <p><b>Una sentencia, y una segunda solo si el producto está retirado.</b> El motivo del retiro no
 * está en {@code products} —el Art. V.13 lo manda al registro de eliminación— y se pide al puerto
 * de {@code shared/audit} <b>únicamente cuando hay retiro que explicar</b>. Preguntarlo siempre
 * costaría una consulta por cada consulta de un producto vivo, que son casi todas.
 *
 * <p><b>El producto retirado se devuelve, no se oculta</b> (`CA-PM-026`), al revés que un rol
 * eliminado. La asimetría es deliberada: el catálogo conserva lo retirado porque entender <b>por
 * qué</b> algo dejó de venderse es media razón de existir de este módulo, mientras que un rol
 * eliminado no debe dejar ni rastro de que existió.
 */
@Service
public class GetProductService {

  private static final String MODULO = "PM";
  private static final String ENTIDAD = "products";

  private final ProductQueryRepository consultas;
  private final DeletionReasonReader motivos;

  public GetProductService(ProductQueryRepository consultas, DeletionReasonReader motivos) {
    this.consultas = consultas;
    this.motivos = motivos;
  }

  @Transactional(readOnly = true)
  public ProductDetailResponse detail(UUID id) {
    ProductQueryRepository.ProductRow fila =
        consultas
            .findDetail(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un producto con ese identificador."));

    // El nulo aquí NO es lo mismo que un motivo vacío: significa que el
    // producto está vivo, y `ProductDetailResponse` lo omite del JSON en lugar
    // de enviarlo en nulo.
    String motivo =
        fila.deletedAt() == null
            ? null
            : motivos.reasonFor(MODULO, ENTIDAD, fila.id()).orElse(null);

    return ProductDetailResponse.from(fila, motivo);
  }
}
