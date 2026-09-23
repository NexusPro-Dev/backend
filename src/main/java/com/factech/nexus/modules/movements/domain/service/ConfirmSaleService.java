package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.DeliveryStatus;
import com.factech.nexus.modules.movements.domain.models.Implementation;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.DeliveryLineRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementDetailView;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup.CurrentMembershipView;
import com.factech.nexus.modules.system.users.application.MembershipGrant;
import com.factech.nexus.modules.system.users.application.MembershipGrant.GrantOrder;
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
import java.util.Optional;
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
 * <p><b>Este servicio decide SI se concede; `SP` decide CÓMO.</b> Que el producto sea automático
 * (`RN-MV-021`), que sea un upgrade (`RN-MV-020`) y que no baje de nivel (`RN-MV-029`) se resuelven
 * aquí, antes de llamar a {@link MembershipGrant}; cerrar la vigente, abrir la comprada y auditar
 * lo hace `SP` con sus reglas (`architecture.md` §15.2.1).
 */
@Service
public class ConfirmSaleService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  private final MovementRepository movimientos;
  private final CurrentMembershipLookup membresias;
  private final MembershipGrant concesion;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public ConfirmSaleService(
      MovementRepository movimientos,
      CurrentMembershipLookup membresias,
      MembershipGrant concesion,
      AuditWriter auditoria) {
    this(movimientos, membresias, concesion, auditoria, Clock.systemUTC());
  }

  ConfirmSaleService(
      MovementRepository movimientos,
      CurrentMembershipLookup membresias,
      MembershipGrant concesion,
      AuditWriter auditoria,
      Clock reloj) {
    this.movimientos = movimientos;
    this.membresias = membresias;
    this.concesion = concesion;
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
   * Una línea: manual → sigue pendiente; automática sin upgrade → entregada; automática con upgrade
   * → concede salvo que baje de nivel (`RN-MV-029`), y entonces se retiene.
   *
   * @return lo que se decidió, para el asiento de auditoría
   */
  private Map<String, Object> entregar(DeliveryLineRow linea, UUID sujeto, OffsetDateTime ahora) {
    Map<String, Object> asiento = new LinkedHashMap<>();
    asiento.put("product_code", linea.productCode());

    if (Implementation.MANUAL.name().equals(linea.implementation())) {
      // `RN-MV-021`: lo manual espera a que alguien lo autorice (`RF-MV-010`).
      // Lo que queda pendiente es la entrega, no el cobro.
      asiento.put("delivery_status", DeliveryStatus.PENDIENTE.name());
      return asiento;
    }

    if (linea.upgrade()) {
      Optional<String> motivo = motivoParaRetener(linea, sujeto);
      if (motivo.isPresent()) {
        movimientos.markRetained(linea.lineId(), motivo.get());
        asiento.put("delivery_status", DeliveryStatus.RETENIDA.name());
        asiento.put("delivery_note", motivo.get());
        return asiento;
      }
      asiento.put("membership_code", linea.targetMembershipCode());
    }

    // TODA LÍNEA QUE SE ENTREGA DEJA ESCRITO LO QUE LA PERSONA PASA A TENER
    // (`RN-MV-036`), y no solo las de upgrade: hasta el 23-09-2026 un bot
    // entregado no dejaba constancia de posesión en ninguna parte, de modo que el
    // sistema sabía qué se le había vendido a alguien y no qué tenía.
    //
    // La membresía va SOLO si el producto la concede; con ella nula, la escritura
    // publicada anota la posesión y no toca el nivel de nadie. Y la vigencia es la
    // copiada en la línea, contada DESDE LA CONFIRMACIÓN (`RN-MV-020`): quien pagó
    // treinta días recibe treinta días de uso.
    //
    // La línea viaja dentro de la orden y va ÚNICA en el esquema: es lo que hace
    // idempotente la entrega, sin que este servicio tenga que comprobar antes si
    // ya la entregó — comprobarlo sería una carrera.
    concesion.grant(
        new GrantOrder(
            sujeto,
            linea.productId(),
            linea.upgrade() ? linea.targetMembershipId() : null,
            linea.lineId(),
            linea.validityDays(),
            ahora));

    movimientos.markDelivered(linea.lineId(), ahora);
    asiento.put("delivery_status", DeliveryStatus.ENTREGADA.name());
    return asiento;
  }

  /**
   * `RN-MV-029`: <b>confirmar no baja de nivel a nadie</b>. Si la membresía comprada es inferior a
   * la vigente en este instante, la línea se retiene y el motivo se escribe para una persona.
   *
   * <p>Renovar el <b>mismo</b> nivel concede. Sin membresía vigente —la suya venció— no hay nada
   * por debajo de lo que bajar, y concede. La cadena crece hacia abajo: {@code 1} es la cima, de
   * modo que <b>inferior es número mayor</b> (`requirements/sp.md` §10.4), igual que en {@code
   * SaleRules.verificarQueSube}.
   */
  private Optional<String> motivoParaRetener(DeliveryLineRow linea, UUID sujeto) {
    if (linea.targetMembershipId() == null || linea.targetMembershipLevel() == null) {
      // Un upgrade sin destino no debería existir; si llega aquí, algo se rompió
      // antes, y entregarlo a ciegas sería peor que retenerlo con constancia.
      return Optional.of("El producto ya no declara la membresía que concede.");
    }
    Optional<CurrentMembershipView> vigente = membresias.currentMembershipOf(sujeto);
    if (vigente.isEmpty() || linea.targetMembershipLevel() <= vigente.get().level()) {
      return Optional.empty();
    }
    return Optional.of(
        "La membresía comprada (%s) es inferior a la vigente (%s)."
            .formatted(linea.targetMembershipCode(), vigente.get().code()));
  }
}
