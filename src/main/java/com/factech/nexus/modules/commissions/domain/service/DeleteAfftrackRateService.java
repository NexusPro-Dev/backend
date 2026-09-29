package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.DeleteAfftrackRateRequest;
import com.factech.nexus.modules.commissions.domain.models.AfftrackRate;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackRateRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-CM-018` — retirar un escalón de rol, con motivo.
 *
 * <p>La forma de `RF-CM-004`: el motivo primero, {@code 404} si no existe y {@code 409} si ya
 * estaba retirado —no es idempotente—. <b>El remanente de nadie se toca</b>: vive en las
 * liquidaciones (`requirements/cm.md` §7.11), y el siguiente cierre compara los FTD reunidos con lo
 * que quede de la escala (`RN-CM-041`).
 */
@Service
public class DeleteAfftrackRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "afftrack_rates";

  private final AfftrackRateRepository escalones;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteAfftrackRateService(AfftrackRateRepository escalones, AuditWriter auditoria) {
    this(escalones, auditoria, Clock.systemUTC());
  }

  DeleteAfftrackRateService(AfftrackRateRepository escalones, AuditWriter auditoria, Clock reloj) {
    this.escalones = escalones;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID id, DeleteAfftrackRateRequest peticion) {
    String motivo = AfftrackReasons.motivo(peticion, "VAL-001");

    AfftrackRate escalon =
        escalones
            .findAnyForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "La comisión afftrack indicada no existe."));
    if (escalon.estaRetirado()) {
      throw new BusinessRuleException("EX-002", "La comisión afftrack ya estaba retirada.");
    }

    // La instantánea ANTES de retirar: describe el escalón tal como estaba.
    var instantanea = escalon.instantanea();
    escalon.retirar(OffsetDateTime.now(reloj));
    escalones.flushChanges();

    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, ENTIDAD, escalon.getId(), DeletionType.LOGICAL, motivo, instantanea));
  }
}
