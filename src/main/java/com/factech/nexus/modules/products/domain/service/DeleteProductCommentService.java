package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.domain.models.ProductComment;
import com.factech.nexus.modules.products.domain.repository.ProductCommentRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ForbiddenException;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de `RF-PM-011`: retirar la reseña propia.
 *
 * <h2>La primera baja de una entidad de negocio SIN motivo declarado</h2>
 *
 * <p>Art. V.13, tercera excepción —el <b>contenido propio</b>, 14-09-2026—: el único «por qué»
 * posible ya está en el evento cuando quien retira y quien escribió son la misma persona. El
 * registro de eliminación se escribe igual, con la instantánea y {@link #MOTIVO_CONTENIDO_PROPIO}
 * en {@code reason}: {@code ck_deletion_reason} sigue exigiendo contenido en toda baja {@code
 * LOGICAL}, y lo tiene (`architecture.md` §6.6.3). <b>La constante es una decisión de la
 * especificación</b> (`spec.md` §14.2): cambiarla es enmendarla.
 *
 * <h2>Retirar dos veces responde {@code 404}, y ahí se aparta de `RF-PM-006`</h2>
 *
 * <p>La reseña retirada no la devuelve nadie, de modo que para el autor «retirada» y «no existe»
 * son lo mismo; distinguirlo se lo confirmaría a un tercero.
 *
 * <h2>Sin excepción para nadie</h2>
 *
 * <p>El superadministrador recibe el mismo {@code 403} sobre una ajena (`CA-PM-196`). La
 * moderación, si llega, es otra operación <b>con motivo</b> — no una rama aquí.
 */
@Service
public class DeleteProductCommentService {

  private static final String MODULO = "PM";
  private static final String ENTIDAD = "product_comments";

  /** El motivo fijo que el Art. V.13 admite para el contenido propio (`spec.md` §14.2). */
  public static final String MOTIVO_CONTENIDO_PROPIO = "Retirada por su autor";

  static final String MENSAJE_AJENA = "Solo el autor puede retirar su reseña.";

  private final ProductCommentRepository resenas;
  private final CurrentActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteProductCommentService(
      ProductCommentRepository resenas, CurrentActor actor, AuditWriter auditoria) {
    this(resenas, actor, auditoria, Clock.systemUTC());
  }

  DeleteProductCommentService(
      ProductCommentRepository resenas, CurrentActor actor, AuditWriter auditoria, Clock reloj) {
    this.resenas = resenas;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID productId, UUID commentId) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    // 1. Existe, viva y de ese producto — bloqueada. La retirada NO se
    //    encuentra: segundo DELETE → 404 (ver el Javadoc).
    ProductComment resena =
        resenas
            .findLiveByIdAndProductForUpdate(commentId, productId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", UpdateProductCommentService.MENSAJE_NO_EXISTE));

    // 2. Es tuya (`RN-PM-027`).
    if (!resena.esDe(quien)) {
      throw new ForbiddenException("EX-002", MENSAJE_AJENA);
    }

    // 3. LA INSTANTÁNEA, ANTES DE TOCAR NADA: después de marcar diría que la
    //    reseña ya estaba retirada.
    Map<String, Object> instantanea = resena.instantanea();

    // 4. La marca. Puntuación y texto se quedan: la instantánea y la fila
    //    tienen que decir lo mismo.
    resena.retirar(OffsetDateTime.now(reloj));
    resenas.flush();

    // 5. El registro, en la MISMA transacción (Art. V.14), con el motivo fijo.
    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO,
            ENTIDAD,
            resena.getId(),
            DeletionType.LOGICAL,
            MOTIVO_CONTENIDO_PROPIO,
            instantanea));
  }
}
