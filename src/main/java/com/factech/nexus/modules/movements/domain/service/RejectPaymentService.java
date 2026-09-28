package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.RejectionReason;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-MV-004`: rechazar el pago pendiente de una venta. La venta sigue pendiente, y el comprador
 * puede volver a pagarla (`RF-MV-018`).
 *
 * <p>Es `RF-MV-005` sobre el pago: el motivo primero, la transición condicionada, y la lectura
 * <b>después</b> y solo para explicar el fallo (`plan.md` §1).
 */
@Service
public class RejectPaymentService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "payments";

  private final MovementRepository movimientos;
  private final PaymentRepository pagos;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public RejectPaymentService(
      MovementRepository movimientos, PaymentRepository pagos, AuditWriter auditoria) {
    this(movimientos, pagos, auditoria, Clock.systemUTC());
  }

  RejectPaymentService(
      MovementRepository movimientos, PaymentRepository pagos, AuditWriter auditoria, Clock reloj) {
    this.movimientos = movimientos;
    this.pagos = pagos;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public SaleResponse reject(UUID movementId, String reason) {
    RejectionReason motivo = new RejectionReason(reason);
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    if (!pagos.rejectPendingOfSale(movementId, ahora, motivo.value())) {
      String estado =
          movimientos
              .findStatus(movementId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "EX-001", "No existe una venta con ese identificador."));
      if (!"PENDIENTE".equals(estado)) {
        String mensaje = "La venta no está pendiente: está " + estado + ".";
        throw new BusinessRuleException(
            "EX-002", mensaje, List.of(new FieldError("status", "EX-002", mensaje)));
      }
      String mensaje = "La venta no tiene un pago pendiente que rechazar.";
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("payments", "EX-003", mensaje)));
    }

    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("status", "RECHAZADO");
    despues.put("rejected_at", ahora.toString());
    despues.put("rejection_reason", motivo.value());
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("status", "PENDIENTE"));
    cambios.put("after", despues);
    cambios.put("movement_id", movementId.toString());
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, movementId, ChangeAction.UPDATE, cambios));

    return SaleDetailMapper.de(
        movimientos
            .findById(movementId)
            .orElseThrow(() -> new IllegalStateException("La venta desapareció.")));
  }
}
