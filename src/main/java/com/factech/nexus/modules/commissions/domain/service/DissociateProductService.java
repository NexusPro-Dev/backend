package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.DissociateProductRequest;
import com.factech.nexus.modules.commissions.domain.models.ProductCommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.ProductCommissionRateRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retirar la asociación de una tasa con un producto (`RF-CM-008`).
 *
 * <p><b>Es la única forma de dejar de pagar sin retirar la tasa</b>, y por eso existe como
 * operación propia: la tasa sigue en el catálogo, disponible para otros productos, y este deja de
 * comisionar a ese rol.
 *
 * <p><b>El borrado es FÍSICO</b>, y es la única eliminación así del módulo. La tabla no tiene
 * {@code deleted_at} a propósito: una asociación no es un hecho del pasado que haya que conservar,
 * es una configuración vigente. <b>Lo que hay que conservar —con qué porcentaje se pagó— es
 * obligación de la liquidación</b> (`RN-CM-008`).
 *
 * <p><b>De modo que el registro de eliminación es lo ÚNICO que queda</b> de que esa tasa rigió
 * alguna vez sobre ese producto. No es un complemento de la fila: la sustituye.
 */
@Service
public class DissociateProductService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "product_commission_rates";
  private static final int MAX_MOTIVO = 500;

  private final ProductCommissionRateRepository asociaciones;
  private final AuditWriter auditoria;

  public DissociateProductService(
      ProductCommissionRateRepository asociaciones, AuditWriter auditoria) {
    this.asociaciones = asociaciones;
    this.auditoria = auditoria;
  }

  @Transactional
  public void dissociate(UUID rateId, UUID productId, DissociateProductRequest peticion) {
    // EL MOTIVO SE VERIFICA EL PRIMERO DE TODO (Art. V.13), y aquí pesa más que
    // en cualquier otro retiro del módulo: como la fila desaparece, este texto
    // es el único sitio donde quedará escrito por qué dejó de pagarse.
    String motivo = peticion == null || peticion.reason() == null ? null : peticion.reason().trim();
    if (motivo == null || motivo.isEmpty()) {
      String mensaje = "El motivo de la desasociación es obligatorio.";
      throw new ValidationException(
          "VAL-007", mensaje, List.of(new FieldError("reason", "VAL-007", mensaje)));
    }
    if (motivo.length() > MAX_MOTIVO) {
      String mensaje = "El motivo no puede exceder %d caracteres.".formatted(MAX_MOTIVO);
      throw new ValidationException(
          "VAL-008", mensaje, List.of(new FieldError("reason", "VAL-008", mensaje)));
    }

    // 404 Y NO 409 SI YA NO ESTÁ, al revés que en los retiros de tasa: allí la
    // fila permanece y puede decirse «ya estaba retirada»; aquí el borrado es
    // físico y no queda nada que distinga «nunca existió» de «ya se borró». Sin
    // ese dato, inventar un 409 sería afirmar algo que no se sabe.
    ProductCommissionRate asociacion =
        asociaciones
            .find(rateId, productId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-404", "Esa tasa no está asociada a ese producto."));

    // La instantánea se toma ANTES de borrar, y aquí no es una precaución: es
    // la copia.
    var instantanea = asociacion.instantanea();

    asociaciones.remove(asociacion);

    // `ASSOCIATION` y no `PHYSICAL`, aunque la fila se borre de verdad: lo que
    // desaparece no es una entidad sino un VÍNCULO entre dos que siguen vivas.
    // El catálogo de `AuditEnums` distingue los dos casos precisamente para que
    // quien lea el registro sepa si perdió un objeto o una relación.
    auditoria.recordDeletion(
        new DeletionEvent(MODULO, ENTIDAD, rateId, DeletionType.ASSOCIATION, motivo, instantanea));
  }
}
