package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.Schedule;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSchedule;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionRepository.NewLiveSession;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.zoom.ZoomMeetings;
import com.factech.nexus.shared.zoom.ZoomMeetings.MeetingSpec;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Casos de uso `RF-AC-044` y `RF-AC-049`: programar una clase en vivo y crear su reunión.
 *
 * <p><b>Validar todo, crear la reunión y escribir</b> (`RN-AC-026`): la llamada a Zoom va
 * <b>fuera</b> de la transacción —no se espera a un tercero con una conexión de base abierta— y la
 * escritura <b>dentro</b>. Si Zoom falla, {@code 503} y nada guardado; si la escritura falla
 * después, <b>se borra la reunión</b> y el error sigue su camino.
 */
@Service
public class ScheduleLiveSessionService {

  private static final Logger LOG = LoggerFactory.getLogger(ScheduleLiveSessionService.class);

  private final LiveSessionSupport apoyo;
  private final LiveSessionRepository escrituras;
  private final LiveSessionQueryRepository consultas;
  private final ZoomMeetings zoom;
  private final AuditWriter auditoria;
  private final TransactionTemplate transaccion;

  public ScheduleLiveSessionService(
      LiveSessionSupport apoyo,
      LiveSessionRepository escrituras,
      LiveSessionQueryRepository consultas,
      ZoomMeetings zoom,
      AuditWriter auditoria,
      TransactionTemplate transaccion) {
    this.apoyo = apoyo;
    this.escrituras = escrituras;
    this.consultas = consultas;
    this.zoom = zoom;
    this.auditoria = auditoria;
    this.transaccion = transaccion;
  }

  /** `RF-AC-044`: cualquier clase, suelta o de cualquier curso. */
  public LiveSessionDetailResponse schedule(Schedule peticion) {
    return programar(peticion, null);
  }

  /** `RF-AC-049`: el curso es obligatorio y lo dicta quien pide (`RN-AC-028`). */
  public LiveSessionDetailResponse scheduleOwn(Schedule peticion) {
    UUID instructor = apoyo.actorId();
    if (peticion != null && peticion.courseId() == null) {
      throw LiveSessionSupport.sinCursoPropio();
    }
    return programar(peticion, instructor);
  }

  private LiveSessionDetailResponse programar(Schedule peticion, UUID instructor) {
    UUID autor = apoyo.actorId();
    List<FieldError> problemas = new ArrayList<>();
    String titulo = peticion == null || peticion.title() == null ? null : peticion.title().trim();
    if (titulo == null || titulo.isEmpty() || titulo.length() > 150) {
      problemas.add(
          new FieldError("title", "VAL-002", "El título es obligatorio, de 1 a 150 caracteres."));
    }
    OffsetDateTime inicio =
        LiveSessionSchedule.leer(
            peticion == null ? null : peticion.startsAt(),
            "startsAt",
            apoyo.zoom().zona(),
            problemas);
    OffsetDateTime fin =
        LiveSessionSchedule.leer(
            peticion == null ? null : peticion.endsAt(), "endsAt", apoyo.zoom().zona(), problemas);
    LiveSessionSchedule.comprobar(inicio, fin, apoyo.ahora(), problemas);
    LiveSessionSupport.lanzarSiHay(problemas);

    CourseRow curso = apoyo.curso(peticion.courseId(), instructor);
    Set<UUID> membresias = apoyo.membresias(peticion.membershipIds());
    Set<UUID> servicios = apoyo.servicios(peticion.productIds());
    String descripcion = vacioANulo(peticion.description());
    LiveSessionSchedule horario = new LiveSessionSchedule(inicio, fin);

    long reunion =
        LiveSessionSupport.conZoom(
            "EX-003",
            () -> zoom.create(new MeetingSpec(titulo, descripcion, inicio, horario.minutos())));

    UUID id = UUID.randomUUID();
    try {
      transaccion.executeWithoutResult(
          estado -> {
            escrituras.insert(
                new NewLiveSession(
                    id,
                    curso == null ? null : curso.id(),
                    titulo,
                    descripcion,
                    inicio,
                    fin,
                    reunion,
                    autor));
            escrituras.replaceMemberships(id, membresias);
            escrituras.replaceProducts(id, servicios);
            auditoria.recordChange(
                new ChangeEvent(
                    LiveSessionSupport.MODULO,
                    LiveSessionSupport.ENTIDAD,
                    id,
                    ChangeAction.CREATE,
                    LiveSessionSupport.instantanea(
                        consultas.find(id).orElseThrow(), membresias, servicios)));
          });
    } catch (RuntimeException fallo) {
      try {
        zoom.delete(reunion);
      } catch (RuntimeException tambien) {
        LOG.error(
            "La reunión {} de Zoom quedó huérfana: la clase no se guardó y no se pudo borrar",
            reunion,
            tambien);
      }
      throw fallo;
    }
    return transaccion.execute(estado -> apoyo.detalle(id));
  }

  static String vacioANulo(String texto) {
    return texto == null || texto.isBlank() ? null : texto.trim();
  }
}
