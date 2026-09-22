package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.VoidReason;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
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
 * Anular una venta pendiente que no debía existir (`RF-MV-005`).
 *
 * <p><b>Es {@link ConfirmSaleService} sin la entrega</b>: la misma transición condicionada al
 * estado anterior —la cuenta de filas decide, y la segunda anulación afecta cero sin haber leído
 * nada— y ningún recorrido de líneas, porque una pendiente no concedió nada (`RN-MV-004`) y no hay
 * nada que deshacer.
 *
 * <p><b>El motivo se valida antes de tocar la venta</b>, como el de una eliminación: un motivo
 * vacío no cuesta ni una consulta (Art. V.13). Y <b>anular no es borrar</b>: la fila se queda, con
 * el motivo escrito en ella, que es lo que separa «anulada» de «desaparecida».
 */
@Service
public class VoidSaleService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  private final MovementRepository movimientos;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public VoidSaleService(MovementRepository movimientos, AuditWriter auditoria) {
    this(movimientos, auditoria, Clock.systemUTC());
  }

  VoidSaleService(MovementRepository movimientos, AuditWriter auditoria, Clock reloj) {
    this.movimientos = movimientos;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public SaleResponse voidSale(UUID movementId, String reason) {
    // 1. El motivo, PRIMERO: sin él no se mira nada.
    VoidReason motivo = new VoidReason(reason);
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    // 2. La transición condicionada. Cero filas: no pendiente, o no existe.
    if (!movimientos.voidIfPending(movementId, ahora, motivo.value())) {
      String estado =
          movimientos
              .findStatus(movementId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "EX-001", "No existe un movimiento con ese identificador."));
      String mensaje = "La venta no está pendiente: está " + estado + ".";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("status", "EX-002", mensaje)));
    }

    // 3. Auditoría, con el motivo: es un cambio de estado, NO una eliminación.
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("status", "ANULADA");
    despues.put("voided_at", ahora.toString());
    despues.put("void_reason", motivo.value());
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("status", "PENDIENTE"));
    cambios.put("after", despues);
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, movementId, ChangeAction.UPDATE, cambios));

    // 4. La venta como queda.
    return SaleDetailMapper.de(
        movimientos
            .findById(movementId)
            .orElseThrow(() -> new IllegalStateException("La venta anulada desapareció.")));
  }
}
