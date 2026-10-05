package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.MyProductResponse;
import com.factech.nexus.modules.movements.domain.models.DeliveryStatus;
import com.factech.nexus.modules.movements.domain.models.Implementation;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.OwnLineRow;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
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
 * Activar un producto comprado de implementación manual (`RF-MV-010`): <b>lo hace quien lo compró,
 * y activarlo es entregarlo</b> (`RN-MV-048`).
 *
 * <p><b>El alcance va en la búsqueda</b>: la línea se busca entre las de las ventas del actor, de
 * modo que la de otra persona responde {@code 404} igual que una inexistente, y ningún permiso lo
 * ensancha. <b>La búsqueda bloquea la línea</b>, y es lo que hace que dos activaciones simultáneas
 * entreguen una vez: la segunda espera, lee la línea ya entregada y responde {@code 409}.
 *
 * <p><b>Las tres comprobaciones van antes de escribir nada</b>, y en este orden: la venta, la
 * implementación, la entrega. El orden decide qué se le dice a quien pregunta: de una venta anulada
 * interesa que está anulada, no que su línea sigue pendiente.
 */
@Service
public class ActivateMyProductService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movement_details";

  private final MovementRepository movimientos;
  private final LineDelivery entrega;
  private final ListMyProductsService comprado;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final ClientCatalog personas;
  private final Clock reloj;

  @Autowired
  public ActivateMyProductService(
      MovementRepository movimientos,
      LineDelivery entrega,
      ListMyProductsService comprado,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      ClientCatalog personas) {
    this(movimientos, entrega, comprado, actor, auditoria, personas, Clock.systemUTC());
  }

  ActivateMyProductService(
      MovementRepository movimientos,
      LineDelivery entrega,
      ListMyProductsService comprado,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      ClientCatalog personas,
      Clock reloj) {
    this.personas = personas;
    this.movimientos = movimientos;
    this.entrega = entrega;
    this.comprado = comprado;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public MyProductResponse activate(UUID lineId) {
    UUID sujeto = actor.id();

    // 1. LA LÍNEA, entre las suyas y bloqueada. Ajena e inexistente son la
    //    misma respuesta: un 403 confirmaría que el identificador existe.
    OwnLineRow linea =
        movimientos
            .findOwnLineForActivation(lineId, sujeto)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un producto comprado suyo con ese identificador."));

    // 2. `EX-006` (`RN-MV-075`, 05-10-2026): una cuenta que espera su primer
    //    depósito no activa nada a mano. Lo que compró al registrarse lo activa
    //    ese depósito (`RN-SP-057`), y activarlo antes adelantaría el FTD.
    personas
        .findClient(sujeto)
        .filter(p -> "FTD_PENDIENTE".equals(p.status()))
        .ifPresent(
            p ->
                conflicto(
                    "EX-006",
                    "status",
                    "Lo que compraste se activa al confirmarse tu primer depósito."));

    // 3. LO QUE TIENE QUE CUMPLIR, antes de escribir nada.
    if (!"CONFIRMADA".equals(linea.movementStatus())) {
      conflicto(
          "EX-002", "status", "La venta no está confirmada: está " + linea.movementStatus() + ".");
    }
    if (!Implementation.MANUAL.name().equals(linea.line().implementation())) {
      conflicto(
          "EX-003",
          "implementation",
          "El producto no es de implementación manual: se entrega solo al confirmar la venta.");
    }
    if (!DeliveryStatus.PENDIENTE.name().equals(linea.deliveryStatus())) {
      conflicto(
          "EX-004",
          "deliveryStatus",
          "El producto ya no está pendiente de activación: está " + linea.deliveryStatus() + ".");
    }

    // 4. ENTREGAR, con lo mismo que confirmar hace con una línea automática:
    //    la posesión, la vigencia desde AHORA y —si es un upgrade— el nivel,
    //    sin bajar a nadie (`RN-MV-029`).
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    Map<String, Object> resultado = entrega.deliver(linea.line(), sujeto, ahora);

    // 5. Auditoría: la línea, de pendiente a lo que quedó.
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("delivery_status", DeliveryStatus.PENDIENTE.name()));
    Map<String, Object> despues = new LinkedHashMap<>(resultado);
    despues.put("activated_at", ahora.toString());
    cambios.put("after", despues);
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, lineId, ChangeAction.UPDATE, cambios));

    // 6. El producto como queda, con la misma forma que el registro de lo comprado.
    return comprado.get(lineId);
  }

  private static void conflicto(String codigo, String campo, String mensaje) {
    throw new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }
}
