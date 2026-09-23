package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.AssignSellersRequest;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.MovementStatus;
import com.factech.nexus.modules.movements.domain.models.SaleTypeStatus;
import com.factech.nexus.modules.movements.domain.models.TypeStatus;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.AssignmentHeader;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.AssignmentLine;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.modules.system.users.application.ClientCatalog.SellerView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asignar los vendedores de una venta (`RF-MV-016`, `RN-MV-035`).
 *
 * <h2>El orden es el de `spec.md` §8, y todo rechazo va antes de la primera escritura</h2>
 *
 * <ol>
 *   <li>La <b>forma</b> de la petición (`VAL-002` a `VAL-004`), sin tocar la base: una petición mal
 *       formada no espera a nadie.
 *   <li>La venta, <b>bloqueada</b> hasta el final de la transacción (`EX-001`). Es lo que hace
 *       verdadero el FA-004: confirmar espera a esta fila, o esta espera a confirmar.
 *   <li>El estado del pago (`EX-002`): en una rechazada o anulada no hay nada que comisionar.
 *   <li>Cada línea (`EX-004`, `EX-003`) y cada vendedor (`EX-005`).
 *   <li>La escritura y, si no queda ninguna línea sin vendedor, el paso a {@code VALIDADO}.
 * </ol>
 *
 * <p><b>Nada se escribe hasta que todo se ha comprobado</b>, de modo que un rechazo no depende de
 * revertir nada: ninguna asignación de la petición llega a la base.
 */
@Service
public class AssignSellersService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  private final MovementRepository movimientos;
  private final ClientCatalog clientes;
  private final AuditWriter auditoria;

  public AssignSellersService(
      MovementRepository movimientos, ClientCatalog clientes, AuditWriter auditoria) {
    this.movimientos = movimientos;
    this.clientes = clientes;
    this.auditoria = auditoria;
  }

  @Transactional
  public SaleResponse assign(UUID movementId, AssignSellersRequest peticion) {
    // 1. La forma, primero: no cuesta ni una consulta.
    List<AssignSellersRequest.Line> pedidas = verificarForma(peticion);

    // 2. La venta, en exclusiva.
    AssignmentHeader venta =
        movimientos
            .lockForAssignment(movementId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un movimiento con ese identificador."));

    // 3. El estado del pago.
    MovementStatus pago = MovementStatus.valueOf(venta.status());
    if (pago == MovementStatus.RECHAZADA || pago == MovementStatus.ANULADA) {
      String mensaje = "La venta está " + pago + ": no hay nada que atribuir.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("status", "EX-002", mensaje)));
    }

    // 4. Cada línea y cada vendedor.
    Map<UUID, AssignmentLine> lineas = new LinkedHashMap<>();
    for (AssignmentLine linea : movimientos.findLinesForAssignment(movementId)) {
      lineas.put(linea.productId(), linea);
    }
    Set<UUID> elegibles =
        clientes.sellersOf(venta.userId()).stream().map(SellerView::id).collect(Collectors.toSet());
    for (int i = 0; i < pedidas.size(); i++) {
      verificarLinea(pedidas.get(i), i, lineas, elegibles, pago);
    }

    // 5. La escritura: solo lo que cambia.
    Map<UUID, UUID> antes = new HashMap<>();
    for (AssignSellersRequest.Line pedida : pedidas) {
      AssignmentLine linea = lineas.get(pedida.productId());
      antes.put(linea.productId(), linea.sellerId());
      if (!pedida.sellerId().equals(linea.sellerId())) {
        movimientos.assignSeller(linea.lineId(), pedida.sellerId());
      }
    }

    Map<UUID, UUID> despues = new HashMap<>();
    for (AssignmentLine linea : lineas.values()) {
      despues.put(linea.productId(), linea.sellerId());
    }
    for (AssignSellersRequest.Line pedida : pedidas) {
      despues.put(pedida.productId(), pedida.sellerId());
    }

    // `RN-MV-035`: sin ninguna línea sin vendedor, la venta se valida SOLA, en
    // esta misma transacción. Y no vuelve atrás: asignar no admite quitar.
    String estadoAntes = venta.typeStatus();
    String estadoDespues = estadoAntes;
    if (despues.values().stream().noneMatch(Objects::isNull)
        && !SaleTypeStatus.VALIDADO.name().equals(estadoAntes)) {
      TypeStatus validado = validadoDe(venta.movementTypeId());
      movimientos.changeTypeStatus(movementId, validado.id());
      estadoDespues = validado.code();
    }

    // 6. Auditoría: el vendedor de cada línea tocada, y el estado, antes y después.
    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            movementId,
            ChangeAction.UPDATE,
            cambios(pedidas, antes, estadoAntes, estadoDespues)));

    // 7. La venta como queda.
    return SaleDetailMapper.de(
        movimientos
            .findById(movementId)
            .orElseThrow(() -> new IllegalStateException("La venta asignada desapareció.")));
  }

  /** `VAL-002` a `VAL-004`, juntos: quien escribió mal tres cosas no corrige tres veces. */
  private static List<AssignSellersRequest.Line> verificarForma(AssignSellersRequest peticion) {
    List<AssignSellersRequest.Line> pedidas =
        peticion == null || peticion.lines() == null ? List.of() : peticion.lines();
    if (pedidas.isEmpty()) {
      String mensaje = "Indique al menos una línea con su vendedor.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("lines", "VAL-002", mensaje)));
    }
    List<FieldError> problemas = new ArrayList<>();
    Set<UUID> vistos = new HashSet<>();
    for (int i = 0; i < pedidas.size(); i++) {
      AssignSellersRequest.Line pedida = pedidas.get(i);
      if (pedida == null || pedida.productId() == null) {
        problemas.add(
            new FieldError(
                "lines[" + i + "].productId", "VAL-003", "Cada línea nombra su producto."));
      }
      if (pedida == null || pedida.sellerId() == null) {
        problemas.add(
            new FieldError(
                "lines[" + i + "].sellerId", "VAL-003", "Cada línea nombra su vendedor."));
      }
      if (pedida != null && pedida.productId() != null && !vistos.add(pedida.productId())) {
        problemas.add(
            new FieldError(
                "lines[" + i + "].productId",
                "VAL-004",
                "El producto " + pedida.productId() + " se nombra dos veces."));
      }
    }
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La asignación solicitada no es válida.", problemas);
    }
    return pedidas;
  }

  /**
   * `EX-004`, `EX-003` y `EX-005`, en ese orden: primero si la línea existe, después si se puede
   * tocar, y por último si el vendedor vale — no tiene sentido decir que el vendedor no es del
   * cliente para una línea que no se podía cambiar.
   */
  private static void verificarLinea(
      AssignSellersRequest.Line pedida,
      int posicion,
      Map<UUID, AssignmentLine> lineas,
      Set<UUID> elegibles,
      MovementStatus pago) {
    String campo = "lines[" + posicion + "]";
    AssignmentLine linea = lineas.get(pedida.productId());
    if (linea == null) {
      String mensaje = "El producto " + pedida.productId() + " no es una línea de esta venta.";
      throw new UnprocessableEntityException(
          "EX-004", mensaje, List.of(new FieldError(campo + ".productId", "EX-004", mensaje)));
    }
    // Confirmada, lo atribuido queda congelado; lo que falta se sigue
    // asignando, porque confirmar no espera a la atribución. Reescribir el
    // mismo vendedor no corrige nada, y se admite.
    if (pago == MovementStatus.CONFIRMADA
        && linea.sellerId() != null
        && !linea.sellerId().equals(pedida.sellerId())) {
      String mensaje =
          "La venta está confirmada y la línea de "
              + pedida.productId()
              + " ya tiene vendedor: no se puede corregir.";
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError(campo + ".productId", "EX-003", mensaje)));
    }
    if (!elegibles.contains(pedida.sellerId())) {
      String mensaje = "El vendedor indicado no es uno de los vendedores de este cliente.";
      throw new UnprocessableEntityException(
          "EX-005", mensaje, List.of(new FieldError(campo + ".sellerId", "EX-005", mensaje)));
    }
  }

  private TypeStatus validadoDe(UUID movementTypeId) {
    return movimientos
        .findTypeStatus(movementTypeId, SaleTypeStatus.VALIDADO.name())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "El tipo de este movimiento no declara el estado VALIDADO."));
  }

  private static Map<String, Object> cambios(
      List<AssignSellersRequest.Line> pedidas,
      Map<UUID, UUID> antes,
      String estadoAntes,
      String estadoDespues) {
    List<Map<String, Object>> lineasAntes = new ArrayList<>();
    List<Map<String, Object>> lineasDespues = new ArrayList<>();
    for (AssignSellersRequest.Line pedida : pedidas) {
      UUID previo = antes.get(pedida.productId());
      Map<String, Object> a = new LinkedHashMap<>();
      a.put("product_id", pedida.productId().toString());
      // Nulo y PRESENTE: la línea no tenía vendedor.
      a.put("seller_id", previo == null ? null : previo.toString());
      lineasAntes.add(a);
      Map<String, Object> d = new LinkedHashMap<>();
      d.put("product_id", pedida.productId().toString());
      d.put("seller_id", pedida.sellerId().toString());
      lineasDespues.add(d);
    }
    Map<String, Object> antesDe = new LinkedHashMap<>();
    antesDe.put("type_status", estadoAntes);
    antesDe.put("lines", lineasAntes);
    Map<String, Object> despuesDe = new LinkedHashMap<>();
    despuesDe.put("type_status", estadoDespues);
    despuesDe.put("lines", lineasDespues);
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", antesDe);
    cambios.put("after", despuesDe);
    return cambios;
  }
}
