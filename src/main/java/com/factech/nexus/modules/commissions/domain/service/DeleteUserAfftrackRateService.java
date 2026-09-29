package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.DeleteAfftrackRateRequest;
import com.factech.nexus.modules.commissions.domain.models.UserAfftrackRate;
import com.factech.nexus.modules.commissions.domain.repository.UserAfftrackRateRepository;
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
 * `RF-CM-019`, el retiro: con motivo, no idempotente, y libera sus días. Si era el último escalón
 * vigente de la persona sobre ese producto, <b>vuelve a aplicarse la escala de su rol</b>
 * (`RN-CM-039`).
 */
@Service
public class DeleteUserAfftrackRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_afftrack_rates";

  private final UserAfftrackRateRepository escalones;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteUserAfftrackRateService(
      UserAfftrackRateRepository escalones, AuditWriter auditoria) {
    this(escalones, auditoria, Clock.systemUTC());
  }

  DeleteUserAfftrackRateService(
      UserAfftrackRateRepository escalones, AuditWriter auditoria, Clock reloj) {
    this.escalones = escalones;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID id, DeleteAfftrackRateRequest peticion) {
    String motivo = AfftrackReasons.motivo(peticion, "VAL-007");
    UserAfftrackRate escalon =
        escalones
            .findAnyForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-006", "La comisión afftrack indicada no existe."));
    if (escalon.estaRetirado()) {
      throw new BusinessRuleException("EX-007", "La comisión afftrack ya estaba retirada.");
    }
    var instantanea = escalon.instantanea();
    escalon.retirar(OffsetDateTime.now(reloj));
    escalones.flushChanges();
    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, ENTIDAD, escalon.getId(), DeletionType.LOGICAL, motivo, instantanea));
  }
}
