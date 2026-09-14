package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductCommentResponse;
import com.factech.nexus.modules.products.domain.repository.ProductCommentRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de `RF-PM-013`: la reseña propia sobre un producto.
 *
 * <p><b>Una consulta y sin mirar el producto.</b> La pregunta es «¿tengo reseña sobre este
 * identificador?», y la respuesta es la misma exista el producto o no, esté activo o no. Es lo que
 * hace que el {@code 404} <b>no diga nada del producto</b> y que esta lectura responda sobre
 * productos inactivos y retirados: la única que le da al autor el camino a la suya cuando la lista
 * pública ya no responde (`RN-PM-028`).
 *
 * <p>Aquí <b>sí</b> se filtra por actor en la consulta, al revés que en la corrección y el retiro:
 * no hay recurso ajeno que negar, la pregunta es sobre uno mismo.
 */
@Service
public class GetOwnProductCommentService {

  static final String MENSAJE_SIN_RESENA = "No has reseñado este producto.";

  private final ProductCommentRepository resenas;
  private final CurrentActor actor;

  public GetOwnProductCommentService(ProductCommentRepository resenas, CurrentActor actor) {
    this.resenas = resenas;
    this.actor = actor;
  }

  @Transactional(readOnly = true)
  public ProductCommentResponse mine(UUID productId) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    return resenas
        .findLiveByProductAndUser(productId, quien)
        .map(ProductCommentResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("EX-001", MENSAJE_SIN_RESENA));
  }
}
