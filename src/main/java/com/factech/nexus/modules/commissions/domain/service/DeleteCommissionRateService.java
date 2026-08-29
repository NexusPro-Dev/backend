package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.DeleteCommissionRateRequest;
import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateRepository;
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
 * Retiro de una tarifa (`RF-CM-004`).
 *
 * <p><b>Retirar no es cerrar la vigencia.</b> Se cierra lo que dejo de regir; se retira lo que NO
 * DEBIO EXISTIR — de ahi el motivo obligatorio (Art. V.13). Y por eso la vigencia no se toca: el
 * registro de eliminacion debe poder decir que periodo cubria lo retirado.
 */
@Service
public class DeleteCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_rates";
  private static final int MAX_MOTIVO = 500;

  private final CommissionRateRepository tarifas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteCommissionRateService(CommissionRateRepository tarifas, AuditWriter auditoria) {
    this(tarifas, auditoria, Clock.systemUTC());
  }

  DeleteCommissionRateService(
      CommissionRateRepository tarifas, AuditWriter auditoria, Clock reloj) {
    this.tarifas = tarifas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID id, DeleteCommissionRateRequest peticion) {
    // EL MOTIVO SE VERIFICA EL PRIMERO DE TODO: el Art. V.13 exige rechazar la
    // eliminacion sin motivo ANTES de ejecutarla, y hacerlo primero significa
    // ademas que un motivo vacio no cuesta ni una consulta.
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

    CommissionRate tarifa =
        tarifas
            .findAny(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-404", "La tarifa indicada no existe."));

    // 409 Y NO 404 SI YA ESTABA RETIRADA: la tarifa existe, y decir que no
    // existe esconderia que el retiro YA OCURRIO, que es lo que quien repite la
    // operacion necesita saber. Ademas NO ES IDEMPOTENTE a proposito: retirar
    // dos veces con dos motivos distintos dejaria el segundo escrito sobre un
    // hecho anterior, y el registro pasaria a mentir sobre por que se retiro.
    if (tarifa.estaRetirada()) {
      throw new BusinessRuleException("EX-002", "La tarifa ya estaba retirada.");
    }

    // La instantanea se toma ANTES de retirar: debe describir la tarifa tal como
    // estaba, CON SU VIGENCIA INTACTA, que es lo que el registro de eliminacion
    // existe para conservar.
    var instantanea = tarifa.instantanea();

    tarifa.delete(OffsetDateTime.now(reloj));
    tarifas.flushChanges();

    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, ENTIDAD, tarifa.getId(), DeletionType.LOGICAL, motivo, instantanea));
  }
}
