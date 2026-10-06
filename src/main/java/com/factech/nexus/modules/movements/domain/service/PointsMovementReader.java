package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.ListPointsMovementsRequest;
import com.factech.nexus.modules.movements.application.PointsMovementDetail;
import com.factech.nexus.modules.movements.application.PointsMovementItem;
import com.factech.nexus.modules.movements.application.PointsMovementSortField;
import com.factech.nexus.modules.movements.application.PointsPurchaseResponse;
import com.factech.nexus.modules.movements.application.PointsReceiptFile;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.PointsMovementQuery;
import com.factech.nexus.modules.movements.domain.repository.PointsMovementQuery.PointsMovementFilter;
import com.factech.nexus.modules.movements.domain.repository.PointsMovementQuery.PointsMovementRow;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.BoundedCount;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los movimientos de puntos —compras y ajustes— en sus dos alcances (`RF-MV-055`, el propio;
 * `RF-MV-056`, el de administración): la lista, el detalle y el archivo del comprobante.
 *
 * <p><b>El alcance lo pone este servicio</b>: en el propio, la persona es el actor autenticado y
 * nunca un parámetro, y <b>ajeno es inexistente</b> —el detalle y la descarga buscan con la persona
 * en la condición, de modo que no hay una comprobación aparte que pudiera responder distinto
 * (`RF-MV-055` `EX-002`)—. <b>Quién hizo el ajuste no sale en el propio</b> (`spec.md` §2.1).
 */
@Service
public class PointsMovementReader {

  private static final List<String> TIPOS =
      List.of(PointsMovementQuery.COMPRA, PointsMovementQuery.AJUSTE);
  private static final List<String> ESTADOS = List.of("PENDIENTE", "CONFIRMADA", "RECHAZADA");
  private static final List<String> SENTIDOS = List.of("SUMA", "RESTA");

  private final PointsMovementQuery lecturas;
  private final MovementRepository movimientos;
  private final AuthenticatedActor actor;
  private final Pagination paginacion;

  public PointsMovementReader(
      PointsMovementQuery lecturas,
      MovementRepository movimientos,
      AuthenticatedActor actor,
      Pagination paginacion) {
    this.lecturas = lecturas;
    this.movimientos = movimientos;
    this.actor = actor;
    this.paginacion = paginacion;
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-055` — lo propio
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public PageResponse<PointsMovementItem> listMine(ListPointsMovementsRequest peticion) {
    return listar(peticion, actor.id(), false);
  }

  @Transactional(readOnly = true)
  public PointsMovementDetail detailMine(UUID movementId) {
    return detalle(movementId, actor.id(), false);
  }

  @Transactional(readOnly = true)
  public PointsReceiptFile receiptMine(UUID movementId) {
    return archivo(movementId, actor.id());
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-056` — administración
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  /** {@code userId} nulo: todas las personas. */
  public PageResponse<PointsMovementItem> list(ListPointsMovementsRequest peticion, UUID userId) {
    return listar(peticion, userId, true);
  }

  @Transactional(readOnly = true)
  public PointsMovementDetail detail(UUID movementId) {
    return detalle(movementId, null, true);
  }

  @Transactional(readOnly = true)
  public PointsReceiptFile receipt(UUID movementId) {
    return archivo(movementId, null);
  }

  // ---------------------------------------------------------------------------

  private PageResponse<PointsMovementItem> listar(
      ListPointsMovementsRequest peticion, UUID persona, boolean administracion) {
    // Todos los problemas juntos (`EX-001`), como los demás listados.
    List<FieldError> problemas = new ArrayList<>();
    Pagination.Slice pagina = null;
    try {
      pagina = paginacion.resolver(peticion.page(), peticion.size());
    } catch (ValidationException e) {
      problemas.addAll(e.errors());
    }
    String orden = null;
    try {
      orden = PointsMovementSortField.resolver(peticion.sort());
    } catch (ValidationException e) {
      problemas.addAll(e.errors());
    }
    if (peticion.type() != null && !TIPOS.contains(peticion.type())) {
      problemas.add(
          new FieldError(
              "type",
              "VAL-005",
              "El tipo '" + peticion.type() + "' no existe: COMPRA_PUNTOS o AJUSTE_PUNTOS."));
    }
    if (peticion.status() != null && !ESTADOS.contains(peticion.status())) {
      problemas.add(
          new FieldError(
              "status",
              "VAL-005",
              "El estado '"
                  + peticion.status()
                  + "' no existe: PENDIENTE, CONFIRMADA o RECHAZADA."));
    }
    if (peticion.sign() != null && !SENTIDOS.contains(peticion.sign())) {
      problemas.add(
          new FieldError(
              "sign", "VAL-003", "El sentido '" + peticion.sign() + "' no existe: SUMA o RESTA."));
    }
    if (peticion.from() != null
        && peticion.to() != null
        && peticion.from().isAfter(peticion.to())) {
      problemas.add(
          new FieldError("from", "VAL-004", "La fecha inicial no puede ser posterior a la final."));
    }
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }

    PointsMovementFilter filtro =
        new PointsMovementFilter(
            persona,
            administracion,
            peticion.type(),
            peticion.status(),
            peticion.currencyId(),
            peticion.from(),
            peticion.to(),
            peticion.sign(),
            peticion.q());
    List<PointsMovementItem> contenido =
        lecturas.find(filtro, orden, pagina.offset(), pagina.size()).stream()
            .map(f -> fila(f, administracion))
            .toList();
    BoundedCount total = lecturas.count(filtro, paginacion.techoDelConteo());
    return PageResponse.de(contenido, total, pagina.page(), pagina.size());
  }

  private PointsMovementDetail detalle(UUID movementId, UUID duenio, boolean administracion) {
    PointsMovementRow f =
        lecturas.findOne(movementId, duenio).orElseThrow(PointsMovementReader::noExiste);
    boolean compra = PointsMovementQuery.COMPRA.equals(f.type());
    return new PointsMovementDetail(
        fila(f, administracion),
        compra ? new PointsPurchaseResponse.Rate(f.pointsRateId(), f.pointsPerUnit()) : null,
        f.rejectionReason(),
        compra
            ? SaleDetailMapper.pagos(
                movimientos.findPaymentsOf(List.of(f.id())).getOrDefault(f.id(), List.of()))
            : List.of(),
        compra
            ? null
            : lecturas.findReceiptInfo(f.id()).map(AttachPointsReceiptService::info).orElse(null));
  }

  private PointsReceiptFile archivo(UUID movementId, UUID duenio) {
    // Primero el movimiento: «no existe» y «no tiene comprobante» son dos respuestas (`EX-002`,
    // `EX-003`), y la segunda solo se da sobre lo que el actor puede ver.
    lecturas.findOne(movementId, duenio).orElseThrow(PointsMovementReader::noExiste);
    return lecturas
        .findReceiptFile(movementId, duenio)
        .map(f -> new PointsReceiptFile(f.fileName(), f.contentType(), f.content()))
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-003", "Ese movimiento de puntos no tiene comprobante."));
  }

  private static PointsMovementItem fila(PointsMovementRow f, boolean administracion) {
    return new PointsMovementItem(
        f.id(),
        f.code(),
        f.type(),
        f.status(),
        new PointsMovementItem.Person(
            f.userId(), nombre(f.userFirstName(), f.userLastName()), f.username(), f.email()),
        new SaleResponse.Money(f.currencyId(), f.currencyCode()),
        f.points(),
        f.amount(),
        f.concept(),
        f.externalReference(),
        f.occurredAt(),
        f.confirmedAt(),
        f.rejectedAt(),
        f.hasReceipt(),
        !administracion || f.recordedBy() == null
            ? null
            : new PointsMovementItem.Actor(
                f.recordedBy(), nombre(f.recordedByFirstName(), f.recordedByLastName())));
  }

  private static String nombre(String nombres, String apellidos) {
    return (nombres + " " + apellidos).strip();
  }

  private static ResourceNotFoundException noExiste() {
    return new ResourceNotFoundException(
        "EX-002", "No existe un movimiento de puntos con ese identificador.");
  }
}
