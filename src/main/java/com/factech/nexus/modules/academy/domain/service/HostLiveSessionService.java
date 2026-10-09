package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionResponses.HostLink;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionRow;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.zoom.ZoomMeetings;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Casos de uso `RF-AC-047` y `RF-AC-052`: el enlace de anfitrión de una clase (`RN-AC-030`).
 *
 * <p>Se pide a Zoom <b>en el momento</b> —caduca a las pocas horas— y <b>no se guarda</b>. Da el
 * control de la reunión, y por eso <b>cada entrega se audita</b> como evento de seguridad, sin el
 * enlace.
 */
@Service
public class HostLiveSessionService {

  private final LiveSessionSupport apoyo;
  private final LiveSessionQueryRepository consultas;
  private final ZoomMeetings zoom;
  private final AuditWriter auditoria;

  public HostLiveSessionService(
      LiveSessionSupport apoyo,
      LiveSessionQueryRepository consultas,
      ZoomMeetings zoom,
      AuditWriter auditoria) {
    this.apoyo = apoyo;
    this.consultas = consultas;
    this.zoom = zoom;
    this.auditoria = auditoria;
  }

  public HostLink host(UUID id) {
    return entregar(id, null);
  }

  public HostLink hostOwn(UUID id) {
    return entregar(id, apoyo.actorId());
  }

  private HostLink entregar(UUID id, UUID instructor) {
    LiveSessionRow clase = consultas.find(id).orElseThrow(LiveSessionSupport::noExiste);
    if (instructor != null) {
      apoyo.exigirPropia(clase, instructor);
    }
    apoyo.exigirModificable(clase, "EX-003");
    String enlace =
        LiveSessionSupport.conZoom("EX-004", () -> zoom.hostLink(clase.zoomMeetingId()));

    Map<String, Object> detalle = new LinkedHashMap<>();
    detalle.put("liveSessionId", clase.id().toString());
    detalle.put("zoomMeetingId", clase.zoomMeetingId());
    detalle.put("asInstructor", instructor != null);
    auditoria.recordSecurity(
        new SecurityEvent(
            SecurityEventType.LIVE_SESSION_HOST_LINK_ISSUED,
            Severity.MEDIA,
            Outcome.SUCCESS,
            null,
            detalle));
    return new HostLink(enlace, clase.startsAt(), clase.endsAt());
  }
}
