package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.Cancel;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.zoom.ZoomMeetings;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso `RF-AC-046` y `RF-AC-051`: cancelar una clase en vivo.
 *
 * <p><b>Borra la reunión en Zoom</b> —así nadie entra aunque guarde su enlace— y <b>conserva la
 * fila</b> con fecha y motivo (`RN-AC-029`). Una reunión que ya no existe en Zoom no lo impide; el
 * puerto lo tolera. Se audita como {@code UPDATE}: la fila no se retira.
 */
@Service
public class CancelLiveSessionService {

  private final LiveSessionSupport apoyo;
  private final LiveSessionRepository escrituras;
  private final ZoomMeetings zoom;
  private final AuditWriter auditoria;

  public CancelLiveSessionService(
      LiveSessionSupport apoyo,
      LiveSessionRepository escrituras,
      ZoomMeetings zoom,
      AuditWriter auditoria) {
    this.apoyo = apoyo;
    this.escrituras = escrituras;
    this.zoom = zoom;
    this.auditoria = auditoria;
  }

  @Transactional
  public LiveSessionDetailResponse cancel(UUID id, Cancel peticion) {
    return cancelar(id, peticion, null);
  }

  @Transactional
  public LiveSessionDetailResponse cancelOwn(UUID id, Cancel peticion) {
    return cancelar(id, peticion, apoyo.actorId());
  }

  private LiveSessionDetailResponse cancelar(UUID id, Cancel peticion, UUID instructor) {
    String motivo = peticion == null || peticion.reason() == null ? "" : peticion.reason().trim();
    if (motivo.isEmpty() || motivo.length() > 500) {
      String mensaje = "El motivo es obligatorio, de 1 a 500 caracteres.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("reason", "VAL-002", mensaje)));
    }
    LiveSessionRow clase = escrituras.findForUpdate(id).orElseThrow(LiveSessionSupport::noExiste);
    if (instructor != null) {
      apoyo.exigirPropia(clase, instructor);
    }
    apoyo.exigirModificable(clase, "EX-003");

    LiveSessionSupport.conZoom("EX-004", () -> zoom.delete(clase.zoomMeetingId()));
    OffsetDateTime ahora = apoyo.ahora().atOffset(ZoneOffset.UTC);
    escrituras.cancel(id, motivo, ahora);

    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("status", Map.of("before", "PROGRAMADA", "after", "CANCELADA"));
    cambios.put("cancellationReason", Map.of("after", motivo));
    auditoria.recordChange(
        new ChangeEvent(
            LiveSessionSupport.MODULO,
            LiveSessionSupport.ENTIDAD,
            id,
            ChangeAction.UPDATE,
            cambios));
    return apoyo.detalle(id);
  }
}
