package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.CreateProductCommentRequest;
import com.factech.nexus.modules.products.application.ProductCommentResponse;
import com.factech.nexus.modules.products.domain.models.ProductComment;
import com.factech.nexus.modules.products.domain.repository.JpaProductCommentRepository;
import com.factech.nexus.modules.products.domain.repository.ProductCommentRepository;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.CurrentActor;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de `RF-PM-009`: reseñar un producto.
 *
 * <h2>El autor sale del token</h2>
 *
 * <p>No hay campo de persona en el cuerpo y no existe forma de reseñar en nombre de otro: como
 * `RF-PM-007` y `RF-SP-039`, la operación responde sobre quien llama.
 *
 * <h2>`RN-PM-026` vive dos veces, y las dos hacen falta</h2>
 *
 * <p>La consulta previa da un mensaje útil; el índice único parcial cierra la carrera entre dos
 * peticiones simultáneas del mismo actor, que la consulta no puede ver. Cuando muerde el índice, el
 * repositorio responde <b>la misma</b> {@code EX-002}: la carrera no es un {@code 500}.
 *
 * <h2>El {@code 404} del producto es uniforme</h2>
 *
 * <p>Inexistente, inactivo y retirado responden lo mismo. Quien porta {@code products:comment} es
 * un cliente, y un cliente ve la oferta, que tampoco distingue: distinguirlo aquí le diría lo que
 * la oferta le oculta — que hay un producto preparándose (`spec.md` `EX-001`).
 */
@Service
public class CreateProductCommentService {

  private static final String MODULO = "PM";
  private static final String ENTIDAD = "product_comments";

  static final String MENSAJE_PRODUCTO = "El producto no existe o no está a la venta.";

  private final ProductCommentRepository resenas;
  private final ProductQueryRepository productos;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public CreateProductCommentService(
      ProductCommentRepository resenas,
      ProductQueryRepository productos,
      CurrentActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(resenas, productos, actor, auditoria, ids, Clock.systemUTC());
  }

  CreateProductCommentService(
      ProductCommentRepository resenas,
      ProductQueryRepository productos,
      CurrentActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.resenas = resenas;
    this.productos = productos;
    this.actor = actor;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public ProductCommentResponse create(UUID productId, CreateProductCommentRequest peticion) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    // 1. La forma, antes de cualquier consulta: quien se equivocó en dos
    //    campos corrige una vez. Un cuerpo ausente es un cuerpo sin campos.
    Integer puntuacion = peticion == null ? null : peticion.rating();
    String texto = peticion == null ? null : peticion.comment();
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    ProductComment resena =
        ProductComment.create(ids.next(), productId, quien, puntuacion, texto, ahora);

    // 2. El producto, comprable (`RN-PM-028`). Sin bloqueo: no se escribe sobre él.
    if (!productos.isPurchasable(productId)) {
      throw noComprable();
    }

    // 3. `RN-PM-026`, el camino normal. El índice cubre la carrera.
    if (resenas.existsLiveByProductAndUser(productId, quien)) {
      throw yaResenado();
    }

    // 4. La inserción. Si el índice muerde, `save` traduce a la misma `EX-002`.
    resenas.save(resena);

    // 5. La auditoría, en la MISMA transacción (Art. V.14).
    auditoria.recordChange(
        new ChangeEvent(
            MODULO, ENTIDAD, resena.getId(), ChangeAction.CREATE, resena.instantanea()));

    return ProductCommentResponse.from(resena);
  }

  /** Un solo mensaje para los tres casos: ver el Javadoc de la clase. */
  static ResourceNotFoundException noComprable() {
    return new ResourceNotFoundException("EX-001", MENSAJE_PRODUCTO);
  }

  private static BusinessRuleException yaResenado() {
    String mensaje = JpaProductCommentRepository.MENSAJE_YA_RESENADO;
    return new BusinessRuleException(
        "EX-002", mensaje, List.of(new FieldError("productId", "EX-002", mensaje)));
  }
}
