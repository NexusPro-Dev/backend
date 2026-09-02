package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.DeleteCommissionRateRequest;
import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retiro de una tasa personalizada.
 *
 * <p><b>Retirar no es cerrar la vigencia.</b> Se cierra lo que dejó de regir; se retira lo que NO
 * DEBIÓ EXISTIR — de ahí el motivo obligatorio (Art. V.13). Y por eso la vigencia no se toca: el
 * registro de eliminación debe poder decir qué periodo cubría lo retirado.
 *
 * <p><b>Los días que ocupaba quedan libres</b>, porque la restricción del motor es parcial sobre
 * las vivas: puede declararse otra tasa que los cubra.
 */
@Service
public class DeleteUserCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "user_commission_rates";
  private static final int MAX_MOTIVO = 500;

  private final UserCommissionRateRepository tasas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteUserCommissionRateService(
      UserCommissionRateRepository tasas, AuditWriter auditoria) {
    this(tasas, auditoria, Clock.systemUTC());
  }

  DeleteUserCommissionRateService(
      UserCommissionRateRepository tasas, AuditWriter auditoria, Clock reloj) {
    this.tasas = tasas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID id, DeleteCommissionRateRequest peticion) {
    // EL MOTIVO SE VERIFICA EL PRIMERO DE TODO: el Art. V.13 exige rechazar la
    // eliminación sin motivo ANTES de ejecutarla, y hacerlo primero significa
    // además que un motivo vacío no cuesta ni una consulta.
    String motivo = peticion == null || peticion.reason() == null ? null : peticion.reason().trim();
    if (motivo == null || motivo.isEmpty()) {
      String mensaje = "El motivo del retiro es obligatorio.";
      throw new ValidationException(
          "VAL-007", mensaje, List.of(new FieldError("reason", "VAL-007", mensaje)));
    }
    if (motivo.length() > MAX_MOTIVO) {
      String mensaje = "El motivo no puede exceder %d caracteres.".formatted(MAX_MOTIVO);
      throw new ValidationException(
          "VAL-008", mensaje, List.of(new FieldError("reason", "VAL-008", mensaje)));
    }

    UserCommissionRate tasa =
        tasas
            .findAny(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-404", "La tasa indicada no existe."));

    // 409 Y NO 404 SI YA ESTABA RETIRADA: la tasa existe, y decir que no existe
    // escondería que el retiro YA OCURRIÓ. Además NO ES IDEMPOTENTE a
    // propósito: retirar dos veces con dos motivos distintos dejaría el segundo
    // escrito sobre un hecho anterior.
    if (tasa.estaRetirada()) {
      throw new BusinessRuleException("EX-002", "La tasa ya estaba retirada.");
    }

    // La instantánea se toma ANTES de retirar: debe describir la tasa tal como
    // estaba, CON SU VIGENCIA INTACTA, que es lo que el registro de eliminación
    // existe para conservar.
    var instantanea = tasa.instantanea();

    tasa.delete(OffsetDateTime.now(reloj));
    tasas.flushChanges();

    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, ENTIDAD, tasa.getId(), DeletionType.LOGICAL, motivo, instantanea));
  }
}
