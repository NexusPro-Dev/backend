package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductCommentResponse;
import com.factech.nexus.modules.products.application.UpdateProductCommentRequest;
import com.factech.nexus.modules.products.domain.models.ProductComment;
import com.factech.nexus.modules.products.domain.repository.ProductCommentRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ForbiddenException;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.security.CurrentActor;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de `RF-PM-010`: corregir la reseña propia.
 *
 * <h2>Primero «existe» y después «es tuya», y el orden no se invierte</h2>
 *
 * <p>Una reseña inexistente responde {@code 404} a todo el mundo; una existente y ajena responde
 * {@code 403}. Resolverla con {@code AND user_id = :actor} en la consulta haría que la ajena
 * respondiera {@code 404}, que es un cambio de contrato silencioso: por eso la propiedad se
 * comprueba <b>después</b> de encontrar la fila, en {@link ProductComment#esDe}. Que el {@code 403}
 * confirme que la reseña existe no publica nada — la lista pública ya la enseña con su
 * identificador (`plan.md` §5).
 *
 * <h2>El permiso habilita; esto autoriza</h2>
 *
 * <p>{@code products:comment} deja llegar hasta aquí. Un administrador con el permiso corrige
 * <b>las suyas</b> y recibe el mismo {@code 403} en las ajenas: no existe moderación (`RN-PM-027`,
 * `CA-PM-186`).
 */
@Service
public class UpdateProductCommentService {

  private static final String MODULO = "PM";
  private static final String ENTIDAD = "product_comments";

  static final String MENSAJE_NO_EXISTE = "La reseña no existe.";
  static final String MENSAJE_AJENA = "Solo el autor puede corregir su reseña.";

  private final ProductCommentRepository resenas;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public UpdateProductCommentService(
      ProductCommentRepository resenas, CurrentActor actor, AuditWriter auditoria) {
    this(resenas, actor, auditoria, Clock.systemUTC());
  }

  UpdateProductCommentService(
      ProductCommentRepository resenas, CurrentActor actor, AuditWriter auditoria, Clock reloj) {
    this.resenas = resenas;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public ProductCommentResponse update(
      UUID productId, UUID commentId, UpdateProductCommentRequest peticion) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    // Un cuerpo sin ningún campo es una corrección de nada, y se rechaza como
    // en `RF-PM-004`: el módulo entero corrige con «al menos uno».
    if (peticion == null || !peticion.informaAlgo()) {
      String mensaje = "Debe informar al menos uno de los campos corregibles.";
      throw new ValidationException(
          "VAL-005", mensaje, List.of(new FieldError(null, "VAL-005", mensaje)));
    }

    // 1. Existe, viva y de ese producto — bloqueada.
    ProductComment resena =
        resenas
            .findLiveByIdAndProductForUpdate(commentId, productId)
            .orElseThrow(UpdateProductCommentService::noExiste);

    // 2. Es tuya (`RN-PM-027`).
    if (!resena.esDe(quien)) {
      throw new ForbiddenException("EX-002", MENSAJE_AJENA);
    }

    // 3. Lo presente, y solo lo que cambia de valor.
    Map<String, Object> cambios =
        resena.corregir(peticion.rating(), peticion.comment(), OffsetDateTime.now(reloj));

    // 4. Sin cambio, sin escritura y sin auditoría (`FA-003`).
    if (!cambios.isEmpty()) {
      resenas.flush();
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, resena.getId(), ChangeAction.UPDATE, cambios));
    }

    return ProductCommentResponse.from(resena);
  }

  static ResourceNotFoundException noExiste() {
    return new ResourceNotFoundException("EX-001", MENSAJE_NO_EXISTE);
  }
}
