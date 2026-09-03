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
 * Retiro de una tasa de rol (`RF-CM-004`).
 *
 * <p><b>Se retira lo que NO DEBIÓ EXISTIR</b> — de ahí el motivo obligatorio (Art. V.13).
 *
 * <p><b>Y aquí ya no hay nada que «cerrar» en lugar de retirar</b>, al revés que hasta el
 * 01-09-2026: sin vigencia, retirar es la única forma de sacar una tasa del catálogo. La otra forma
 * de dejar de pagar —sin tocar la tasa— es <b>desasociarla</b> del producto (`RF-CM-008`), que es
 * una operación distinta y no destruye nada.
 */
@Service
public class DeleteCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_rates";
  private static final int MAX_MOTIVO = 500;

  private final CommissionRateRepository tasas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteCommissionRateService(CommissionRateRepository tasas, AuditWriter auditoria) {
    this(tasas, auditoria, Clock.systemUTC());
  }

  DeleteCommissionRateService(CommissionRateRepository tasas, AuditWriter auditoria, Clock reloj) {
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

    CommissionRate tasa =
        tasas
            .findAny(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-404", "La tasa indicada no existe."));

    // 409 Y NO 404 SI YA ESTABA RETIRADA: la tasa existe, y decir que no existe
    // escondería que el retiro YA OCURRIÓ, que es lo que quien repite la
    // operación necesita saber. Además NO ES IDEMPOTENTE a propósito: retirar
    // dos veces con dos motivos distintos dejaría el segundo escrito sobre un
    // hecho anterior, y el registro pasaría a mentir sobre por qué se retiró.
    if (tasa.estaRetirada()) {
      throw new BusinessRuleException("EX-002", "La tasa ya estaba retirada.");
    }

    // -------------------------------------------------------------------------
    // UNA TASA ASOCIADA NO SE RETIRA, Y ESTA CONDICIÓN NO ESTÁ EN `cm.md`.
    //
    // Se añade al construir el módulo porque sin ella el retiro produce EL
    // FALLO QUE ESTE MÓDULO MÁS TEME: la asociación no tiene retiro lógico y
    // sobreviviría apuntando a una fila muerta, de modo que la resolución
    // —que filtra las retiradas— dejaría de encontrar tarifa y EL PRODUCTO
    // PASARÍA A NO PAGAR NADA sin que nada lo dijera. Es exactamente la
    // silenciosidad contra la que `RN-CM-012` avisa, pero llegando por la
    // puerta de atrás.
    //
    // Las otras dos salidas eran peores: borrar las asociaciones en cascada
    // destruiría configuración que nadie pidió destruir, y dejarlas apuntando
    // a la fila muerta es el defecto descrito arriba.
    //
    // El coste es dos operaciones donde antes había una — desasociar y luego
    // retirar—, y es un coste que se paga a la vista.
    // -------------------------------------------------------------------------
    if (tasas.tieneAsociaciones(tasa.getId())) {
      String mensaje =
          "La tasa está asociada a uno o más productos. Retire primero esas asociaciones: de otro"
              + " modo el producto dejaría de comisionar sin que nada lo indicara.";
      throw new BusinessRuleException(
          "EX-005", mensaje, List.of(new FieldError("id", "EX-005", mensaje)));
    }

    // La instantánea se toma ANTES de retirar: debe describir la tasa tal como
    // estaba, que es lo que el registro de eliminación existe para conservar.
    var instantanea = tasa.instantanea();

    tasa.delete(OffsetDateTime.now(reloj));
    tasas.flushChanges();

    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, ENTIDAD, tasa.getId(), DeletionType.LOGICAL, motivo, instantanea));
  }
}
