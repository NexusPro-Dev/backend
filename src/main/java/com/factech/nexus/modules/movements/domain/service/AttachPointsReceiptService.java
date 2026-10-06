package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.PointsReceiptInfo;
import com.factech.nexus.modules.movements.domain.models.PointsReceipt;
import com.factech.nexus.modules.movements.domain.repository.PointsMovementQuery;
import com.factech.nexus.modules.movements.domain.repository.PointsMovementQuery.ReceiptInfoRow;
import com.factech.nexus.modules.movements.domain.repository.PointsReceiptRepository;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adjuntar o reemplazar el comprobante de un ajuste de puntos (`RF-MV-057`, `RN-MV-077`).
 *
 * <p>{@link #guardar} es el paso que comparten los dos caminos: el ajuste con archivo (`RF-MV-052`)
 * lo llama dentro de su transacción, y {@link #attach} después de bloquear la fila. <b>La auditoría
 * guarda nombre, tipo, tamaño y resumen —del anterior también al reemplazar—, nunca el
 * contenido</b>.
 */
@Service
public class AttachPointsReceiptService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "points_adjustment_receipts";

  private final PointsReceiptRepository comprobantes;
  private final PointsMovementQuery lecturas;
  private final AuditWriter auditoria;
  private final AuthenticatedActor actor;
  private final Clock reloj;

  @Autowired
  public AttachPointsReceiptService(
      PointsReceiptRepository comprobantes,
      PointsMovementQuery lecturas,
      AuditWriter auditoria,
      AuthenticatedActor actor) {
    this(comprobantes, lecturas, auditoria, actor, Clock.systemUTC());
  }

  AttachPointsReceiptService(
      PointsReceiptRepository comprobantes,
      PointsMovementQuery lecturas,
      AuditWriter auditoria,
      AuthenticatedActor actor,
      Clock reloj) {
    this.comprobantes = comprobantes;
    this.lecturas = lecturas;
    this.auditoria = auditoria;
    this.actor = actor;
    this.reloj = reloj;
  }

  /** `PUT /points-adjustments/{id}/receipt`: el archivo se valida antes de tocar la base. */
  @Transactional
  public PointsReceiptInfo attach(UUID movementId, String nombre, byte[] bytes) {
    if (bytes == null) {
      throw PointsReceipt.ausente();
    }
    PointsReceipt comprobante = PointsReceipt.de(nombre, bytes);
    if (!comprobantes.lockAdjustment(movementId)) {
      throw new ResourceNotFoundException(
          "EX-002", "No existe un ajuste de puntos con ese identificador.");
    }
    return guardar(movementId, comprobante);
  }

  /** Inserta o reemplaza, y audita. El movimiento ya es un ajuste y su fila ya está bloqueada. */
  PointsReceiptInfo guardar(UUID movementId, PointsReceipt comprobante) {
    Optional<ReceiptInfoRow> anterior = lecturas.findReceiptInfo(movementId);
    comprobantes.upsert(movementId, comprobante, actor.id(), OffsetDateTime.now(reloj));
    ReceiptInfoRow nuevo =
        lecturas
            .findReceiptInfo(movementId)
            .orElseThrow(() -> new IllegalStateException("El comprobante desapareció."));

    Map<String, Object> cambios = new LinkedHashMap<>();
    anterior.ifPresent(a -> cambios.put("before", huella(a)));
    cambios.put("after", huella(nuevo));
    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            movementId,
            anterior.isPresent() ? ChangeAction.UPDATE : ChangeAction.CREATE,
            cambios));
    return info(nuevo);
  }

  static PointsReceiptInfo info(ReceiptInfoRow fila) {
    return new PointsReceiptInfo(
        fila.fileName(), fila.contentType(), fila.sizeBytes(), fila.sha256(), fila.uploadedAt());
  }

  private static Map<String, Object> huella(ReceiptInfoRow fila) {
    Map<String, Object> huella = new LinkedHashMap<>();
    huella.put("fileName", fila.fileName());
    huella.put("contentType", fila.contentType());
    huella.put("sizeBytes", fila.sizeBytes());
    huella.put("sha256", fila.sha256());
    return huella;
  }
}
