package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.Schedule;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSchedule;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries.Recurrence;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionRepository.NewLiveSession;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.zoom.ZoomMeetings;
import java.time.LocalDate;
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
 * Casos de uso `RF-AC-044` y `RF-AC-049`: programar una clase en vivo y crear su reunión, que se
 * repite si trae `recurrence` (`RF-AC-055`, `RN-AC-031`).
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

  /** `RF-AC-044`: cualquier clase, con los instructores que nombre, o ninguno. */
  public LiveSessionDetailResponse schedule(Schedule peticion) {
    return programar(peticion, null);
  }

  /** `RF-AC-049`: quien programa queda entre los instructores (`RN-AC-028`, `RN-AC-032`). */
  public LiveSessionDetailResponse scheduleOwn(Schedule peticion) {
    return programar(peticion, apoyo.actorId());
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
    // `RN-AC-034`: toda clase declara su categoría.
    if (peticion == null || peticion.categoryId() == null) {
      problemas.add(
          new FieldError("categoryId", "VAL-005", "La categoría de la clase es obligatoria."));
    }
    int antesDeLaFrecuencia = problemas.size();
    Recurrence frecuencia =
        LiveSessionSeries.leerFrecuencia(
                peticion == null ? null : peticion.recurrence(), false, problemas)
            .orElse(null);
    boolean frecuenciaIlegible = problemas.size() > antesDeLaFrecuencia;
    String hastaTexto = peticion == null ? null : peticion.repeatUntil();
    LocalDate hasta = LiveSessionSeries.leerHasta(hastaTexto, problemas);
    boolean hastaIlegible = hasta == null && hastaTexto != null && !hastaTexto.isBlank();
    if (!frecuenciaIlegible) {
      LiveSessionSeries.comprobar(
          inicio,
          fin,
          frecuencia,
          hasta,
          hastaIlegible,
          apoyo.zoom().zona(),
          apoyo.ahora(),
          problemas);
    }
    LiveSessionSupport.lanzarSiHay(problemas);

    List<UUID> nombrados = new ArrayList<>();
    if (instructor != null) {
      nombrados.add(instructor);
    }
    if (peticion.instructorIds() != null) {
      peticion.instructorIds().stream()
          .filter(i -> instructor == null || !instructor.equals(i))
          .forEach(nombrados::add);
    }
    UUID categoria = apoyo.categoria(peticion.categoryId());
    List<UUID> instructores = apoyo.instructores(nombrados);
    Set<UUID> membresias = apoyo.membresias(peticion.membershipIds());
    Set<UUID> servicios = apoyo.servicios(peticion.productIds());
    String descripcion = vacioANulo(peticion.description());
    LiveSessionSeries serie = new LiveSessionSeries(inicio, fin, frecuencia, hasta);

    long reunion =
        LiveSessionSupport.conZoom(
            "EX-003", () -> zoom.create(apoyo.reunion(titulo, descripcion, serie)));

    UUID id = UUID.randomUUID();
    try {
      transaccion.executeWithoutResult(
          estado -> {
            escrituras.insert(
                new NewLiveSession(
                    id,
                    titulo,
                    descripcion,
                    inicio,
                    fin,
                    frecuencia == null ? null : frecuencia.name(),
                    frecuencia == null ? null : hasta,
                    serie.finDeLaSerie(apoyo.zoom().zona()),
                    reunion,
                    categoria,
                    autor));
            escrituras.replaceMemberships(id, membresias);
            escrituras.replaceProducts(id, servicios);
            escrituras.replaceInstructors(id, instructores);
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
