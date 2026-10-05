package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.CommissionableLinesEvent;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.DeliveryLineRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MyMovementRow;
import com.factech.nexus.modules.system.users.application.FirstDepositActivation;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La activación por el primer depósito, <b>implementada por `MV` para `SP`</b> (`RN-SP-057`,
 * `RN-MV-075`, 05-10-2026).
 *
 * <p>La venta del alta gratuita nace confirmada y <b>sin entregar</b> ({@link
 * ConfirmSaleService#confirmarSinEntregar}). Aquí llega su entrega: cada línea pendiente pasa por
 * {@link LineDelivery} —la misma que confirmar y que activar a mano—, <b>sea automática o
 * manual</b>, porque lo que esperaba no era a quien la compró sino al depósito. Con ella se concede
 * la membresía del producto y se escribe {@code delivered_at}, que es el momento del FTD para `CM`
 * (`RN-CM-036`).
 *
 * <p><b>Y solo entonces sale el aviso a `CM`</b> (`RN-MV-049`): la comisión espera al depósito,
 * como la entrega. Hoy la línea del alta es un FTD, que el devengo por venta descarta; si alguna
 * vez no lo fuera, tampoco se habría comisionado antes.
 *
 * <p>{@code MANDATORY}: solo tiene sentido dentro de la transacción que saca la cuenta de {@code
 * FTD_PENDIENTE}. Si algo falla aquí, esa transición tampoco ocurre.
 */
@Service
public class PublishedFirstDepositActivation implements FirstDepositActivation {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  private final MovementRepository movimientos;
  private final LineDelivery entrega;
  private final AuditWriter auditoria;
  private final ApplicationEventPublisher avisos;
  private final Clock reloj;

  @Autowired
  public PublishedFirstDepositActivation(
      MovementRepository movimientos,
      LineDelivery entrega,
      AuditWriter auditoria,
      ApplicationEventPublisher avisos) {
    this(movimientos, entrega, auditoria, avisos, Clock.systemUTC());
  }

  PublishedFirstDepositActivation(
      MovementRepository movimientos,
      LineDelivery entrega,
      AuditWriter auditoria,
      ApplicationEventPublisher avisos,
      Clock reloj) {
    this.movimientos = movimientos;
    this.entrega = entrega;
    this.auditoria = auditoria;
    this.avisos = avisos;
    this.reloj = reloj;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void activate(UUID userId, UUID movementId) {
    MyMovementRow venta =
        movimientos
            .findById(movementId)
            .map(MovementRepository.MovementDetailView::header)
            .orElseThrow(
                () -> new IllegalStateException("La venta del alta " + movementId + " no existe."));
    // Lo que SP afirma al llamar, comprobado y no supuesto: una venta ajena, o
    // una que no esté confirmada, es un defecto de integridad y no un caso de
    // negocio. Que salte revierte también el cambio de estado.
    if (!userId.equals(venta.userId())
        || !"VENTA".equals(venta.type())
        || !"CONFIRMADA".equals(venta.status())) {
      throw new IllegalStateException(
          "La venta del alta "
              + movementId
              + " no es una VENTA CONFIRMADA de "
              + userId
              + ": es "
              + venta.type()
              + " "
              + venta.status()
              + ".");
    }

    List<DeliveryLineRow> pendientes = movimientos.findPendingLinesForDelivery(movementId);
    if (pendientes.isEmpty()) {
      return;
    }
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    List<Map<String, Object>> resultado = new ArrayList<>();
    List<UUID> lineas = new ArrayList<>();
    for (DeliveryLineRow linea : pendientes) {
      resultado.add(entrega.deliver(linea, userId, ahora));
      lineas.add(linea.lineId());
    }

    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("lines", "PENDIENTE"));
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("activated_by", "FIRST_DEPOSIT");
    despues.put("activated_at", ahora.toString());
    despues.put("lines", resultado);
    cambios.put("after", despues);
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, movementId, ChangeAction.UPDATE, cambios));

    // Dentro de la transacción: se entrega después del commit, o no se entrega.
    avisos.publishEvent(new CommissionableLinesEvent(movementId, lineas));
  }
}
