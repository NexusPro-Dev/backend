package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.DissociateProductRequest;
import com.factech.nexus.modules.commissions.domain.repository.UserRateProductRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retirar la asociación de una tasa personalizada con un producto (`RF-CM-006`, 11-09-2026).
 *
 * <p><b>Gemela de {@code DissociateProductService}</b>, y comparte hasta el cuerpo de la petición:
 * el motivo es obligatorio por lo mismo. La fila <b>se borra de verdad</b> —no hay {@code
 * deleted_at}— porque una asociación no es un hecho del pasado que haya que conservar sino
 * configuración vigente, de modo que <b>este texto es el único sitio donde quedará escrito por qué
 * esa persona dejó de tener su excepción en ese producto</b>.
 *
 * <p><b>Desasociar no retira la tasa</b>: sigue viva, y puede volver a asociarse aquí o en otro
 * producto. Retirarla es otra operación, y `RN-CM-015` exige desasociarla antes.
 *
 * <p><b>{@code 404} y no {@code 409} si ya no está</b>, mismo criterio que la gemela: con el
 * borrado físico no queda nada que distinga «nunca existió» de «ya se borró», y un {@code 409}
 * afirmaría algo que no se sabe.
 */
@Service
public class DissociateUserProductService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_commission_rate_products";
  private static final int MAX_MOTIVO = 500;

  private final UserRateProductRepository asociaciones;
  private final AuditWriter auditoria;

  public DissociateUserProductService(
      UserRateProductRepository asociaciones, AuditWriter auditoria) {
    this.asociaciones = asociaciones;
    this.auditoria = auditoria;
  }

  @Transactional
  public void dissociate(UUID rateId, UUID productId, DissociateProductRequest peticion) {
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

    if (!asociaciones.existe(rateId, productId)) {
      throw new ResourceNotFoundException("EX-404", "Esa tasa no está asociada a ese producto.");
    }

    // La instantánea se toma ANTES de borrar, y aquí no es una precaución: es la
    // copia. Después no queda fila de la que sacarla.
    Map<String, Object> instantanea =
        Map.of(
            "user_commission_rate_id", rateId.toString(),
            "product_id", productId.toString());

    asociaciones.borrar(rateId, productId);

    // `ASSOCIATION` y no `PHYSICAL`: lo que desaparece no es una entidad sino un
    // VÍNCULO entre dos que siguen vivas.
    auditoria.recordDeletion(
        new DeletionEvent(MODULO, ENTIDAD, rateId, DeletionType.ASSOCIATION, motivo, instantanea));
  }
}
