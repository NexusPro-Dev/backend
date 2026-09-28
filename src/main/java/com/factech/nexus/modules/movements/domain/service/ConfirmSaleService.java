package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.DeliveryStatus;
import com.factech.nexus.modules.movements.domain.models.Implementation;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.DeliveryLineRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementDetailView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmar el pago de una venta pendiente y <b>entregar lo que se pueda entregar</b> en el mismo
 * acto (`RF-MV-003`).
 *
 * <p><b>Tres cosas en una transacción</b>: la transición condicionada, el recorrido de las líneas y
 * —para un upgrade automático— la escritura que `SP` publica. Si cualquiera falla, no queda nada:
 * una venta confirmada cuya membresía no se concedió sería la avería que nadie reporta.
 *
 * <p><b>La transición va PRIMERO y las líneas se leen DESPUÉS</b>, y el orden no es cosmético: si
 * las líneas se leyeran antes, dos confirmaciones simultáneas las tendrían las dos en la mano; con
 * la transición delante, solo quien la ganó recorre las líneas, y la otra responde `EX-002` sin
 * haber leído nada (`RN-MV-005`).
 *
 * <p><b>Este servicio decide SI se entrega; {@link LineDelivery} decide CÓMO.</b> Que el producto
 * sea automático (`RN-MV-021`) se resuelve aquí; que no baje de nivel (`RN-MV-029`) y la escritura
 * de `SP` están en {@link LineDelivery}, que comparte con activar lo manual (`RF-MV-010`).
 */
@Service
public class ConfirmSaleService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  private final MovementRepository movimientos;
  private final LineDelivery entrega;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public ConfirmSaleService(
      MovementRepository movimientos, LineDelivery entrega, AuditWriter auditoria) {
    this(movimientos, entrega, auditoria, Clock.systemUTC());
  }

  ConfirmSaleService(
      MovementRepository movimientos, LineDelivery entrega, AuditWriter auditoria, Clock reloj) {
    this.movimientos = movimientos;
    this.entrega = entrega;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public SaleResponse confirm(UUID movementId) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    // 1. LA TRANSICIÓN, condicionada al estado anterior. Cero filas significa
    //    «no estaba pendiente» o «no existe», y solo entonces se lee para
    //    distinguirlos: `EX-001` frente a `EX-002`.
    if (!movimientos.confirmIfPending(movementId, ahora)) {
      String estado =
          movimientos
              .findStatus(movementId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "EX-001", "No existe un movimiento con ese identificador."));
      // `CA-MV-219` (26-09-2026): pendiente y sin pago pendiente —el último se
      // rechazó y nadie ha vuelto a pagar—. No hay cobro que dar por entrado.
      if ("PENDIENTE".equals(estado) && !movimientos.hasPendingPayment(movementId)) {
        String sinPago =
            "La venta no tiene un pago pendiente: su último pago se rechazó y hay que volver a"
                + " pagarla.";
        throw new BusinessRuleException(
            "EX-006", sinPago, List.of(new FieldError("payments", "EX-006", sinPago)));
      }
      // EL ESTADO VA EN EL MENSAJE, y es lo que una pasarela que reentrega
      // necesita: saber que ese pago ya se procesó, no solo que algo chocó.
      String mensaje = "La venta no está pendiente: está " + estado + ".";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("status", "EX-002", mensaje)));
    }

    // 2. LAS LÍNEAS, después: solo quien ganó la transición llega aquí.
    MovementDetailView antes =
        movimientos
            .findById(movementId)
            .orElseThrow(() -> new IllegalStateException("La venta confirmada desapareció."));
    UUID sujeto = antes.header().userId();

    List<Map<String, Object>> resultado = new ArrayList<>();
    for (DeliveryLineRow linea : movimientos.findLinesForDelivery(movementId)) {
      resultado.add(entregar(linea, sujeto, ahora));
    }

    // 3. Auditoría: el cambio de estado y lo que se decidió de cada línea.
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("status", "PENDIENTE"));
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("status", "CONFIRMADA");
    despues.put("confirmed_at", ahora.toString());
    despues.put("lines", resultado);
    cambios.put("after", despues);
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, movementId, ChangeAction.UPDATE, cambios));

    // 4. La venta como queda, con la misma forma que registrar y que el detalle.
    return SaleDetailMapper.de(
        movimientos
            .findById(movementId)
            .orElseThrow(() -> new IllegalStateException("La venta confirmada desapareció.")));
  }

  /**
   * Una línea: manual → sigue pendiente, a la espera de quien la compró (`RF-MV-010`); automática →
   * la entrega {@link LineDelivery}, que retiene el upgrade que bajaría de nivel.
   *
   * @return lo que se decidió, para el asiento de auditoría
   */
  private Map<String, Object> entregar(DeliveryLineRow linea, UUID sujeto, OffsetDateTime ahora) {
    if (Implementation.MANUAL.name().equals(linea.implementation())) {
      // `RN-MV-021`: lo manual espera a que quien lo compró lo active
      // (`RF-MV-010`, `RN-MV-048`). Lo que queda pendiente es la entrega, no el cobro.
      Map<String, Object> asiento = new LinkedHashMap<>();
      asiento.put("product_code", linea.productCode());
      asiento.put("delivery_status", DeliveryStatus.PENDIENTE.name());
      return asiento;
    }
    return entrega.deliver(linea, sujeto, ahora);
  }
}
