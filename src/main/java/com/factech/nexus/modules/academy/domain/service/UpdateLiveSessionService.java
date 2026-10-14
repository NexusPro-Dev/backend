package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse;
import com.factech.nexus.modules.academy.application.LiveSessionRequests.Update;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSchedule;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries.Recurrence;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.zoom.ZoomMeetings;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso `RF-AC-045` y `RF-AC-050`: corregir una clase en vivo, también sus instructores
 * (`RN-AC-032`).
 *
 * <p><b>Solo lo que viene</b>; las listas, si vienen, reemplazan enteras. <b>Zoom primero</b>, con
 * la fila bloqueada, y solo si cambió algo que Zoom conoce —título, inicio, fin o la repetición
 * (`RF-AC-055`)—: si falla, {@code 503} y nada cambia (`RN-AC-026`). Quitar a alguien de la lista
 * no lo des-registra en Zoom (`RN-AC-027`).
 */
@Service
public class UpdateLiveSessionService {

  private final LiveSessionSupport apoyo;
  private final LiveSessionRepository escrituras;
  private final LiveSessionQueryRepository consultas;
  private final ZoomMeetings zoom;
  private final AuditWriter auditoria;

  public UpdateLiveSessionService(
      LiveSessionSupport apoyo,
      LiveSessionRepository escrituras,
      LiveSessionQueryRepository consultas,
      ZoomMeetings zoom,
      AuditWriter auditoria) {
    this.apoyo = apoyo;
    this.escrituras = escrituras;
    this.consultas = consultas;
    this.zoom = zoom;
    this.auditoria = auditoria;
  }

  @Transactional
  public LiveSessionDetailResponse update(UUID id, Update peticion) {
    return corregir(id, peticion, null);
  }

  /** `RF-AC-050`: la clase es de un curso que dicta, y no la mueve a otro ni la suelta. */
  @Transactional
  public LiveSessionDetailResponse updateOwn(UUID id, Update peticion) {
    return corregir(id, peticion, apoyo.actorId());
  }

  private LiveSessionDetailResponse corregir(UUID id, Update peticion, UUID instructor) {
    LiveSessionRow antes = escrituras.findForUpdate(id).orElseThrow(LiveSessionSupport::noExiste);
    if (instructor != null) {
      apoyo.exigirPropia(antes, instructor);
    }
    apoyo.exigirModificable(antes, "EX-003");
    Update cambios =
        peticion == null
            ? new Update(null, null, null, null, null, null, null, null, null, null)
            : peticion;

    List<FieldError> problemas = new ArrayList<>();
    String titulo = antes.title();
    if (cambios.title() != null) {
      titulo = cambios.title().trim();
      if (titulo.isEmpty() || titulo.length() > 150) {
        problemas.add(
            new FieldError("title", "VAL-002", "El título es obligatorio, de 1 a 150 caracteres."));
      }
    }
    OffsetDateTime inicio =
        cambios.startsAt() == null
            ? antes.startsAt()
            : LiveSessionSchedule.leer(
                cambios.startsAt(), "startsAt", apoyo.zoom().zona(), problemas);
    OffsetDateTime fin =
        cambios.endsAt() == null
            ? antes.endsAt()
            : LiveSessionSchedule.leer(cambios.endsAt(), "endsAt", apoyo.zoom().zona(), problemas);
    boolean cambiaHorario = cambios.startsAt() != null || cambios.endsAt() != null;
    if (cambiaHorario) {
      LiveSessionSchedule.comprobar(inicio, fin, apoyo.ahora(), problemas);
    }
    // `RN-AC-031`: la repetición, solo si viene; NONE la quita y con ella la fecha final.
    Recurrence frecuenciaAntes = antes.serie().recurrence();
    Recurrence frecuencia = frecuenciaAntes;
    int antesDeLaFrecuencia = problemas.size();
    if (cambios.recurrence() != null) {
      frecuencia =
          LiveSessionSeries.leerFrecuencia(cambios.recurrence(), true, problemas).orElse(null);
    }
    boolean frecuenciaIlegible = problemas.size() > antesDeLaFrecuencia;
    LocalDate hasta = frecuencia == null ? null : antes.repeatUntil();
    boolean hastaIlegible = false;
    if (cambios.repeatUntil() != null) {
      hasta = LiveSessionSeries.leerHasta(cambios.repeatUntil(), problemas);
      hastaIlegible = hasta == null && !cambios.repeatUntil().isBlank();
    }
    boolean cambiaSerie =
        cambiaHorario || cambios.recurrence() != null || cambios.repeatUntil() != null;
    if (cambiaSerie && !frecuenciaIlegible) {
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

    // `RN-AC-034`: la categoría, si viene, viva; antes que Zoom, como las demás referencias.
    UUID categoria =
        cambios.categoryId() == null ? antes.categoryId() : apoyo.categoria(cambios.categoryId());

    // `RN-AC-032`: la lista de instructores, si viene, reemplaza entera; por `/mine`, quien
    // corrige no se quita (`RN-AC-028`).
    List<UUID> instructores = null;
    if (cambios.instructorIds() != null) {
      instructores = apoyo.instructores(cambios.instructorIds());
      if (instructor != null && !instructores.contains(instructor)) {
        throw LiveSessionSupport.sinQuitarse();
      }
    }
    Set<UUID> membresiasAntes =
        consultas.findMembershipsOf(id).stream()
            .map(MembershipRef::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<UUID> serviciosAntes =
        consultas.findProductsOf(id).stream()
            .map(ProductRef::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<UUID> membresias =
        cambios.membershipIds() == null
            ? membresiasAntes
            : apoyo.membresias(cambios.membershipIds());
    Set<UUID> servicios =
        cambios.productIds() == null ? serviciosAntes : apoyo.servicios(cambios.productIds());
    String descripcion =
        cambios.description() == null
            ? antes.description()
            : ScheduleLiveSessionService.vacioANulo(cambios.description());

    LiveSessionSeries serie = new LiveSessionSeries(inicio, fin, frecuencia, hasta);
    boolean zoomCambia =
        !titulo.equals(antes.title())
            || !inicio.isEqual(antes.startsAt())
            || !fin.isEqual(antes.endsAt())
            || frecuencia != frecuenciaAntes
            || !Objects.equals(hasta, antes.repeatUntil());
    if (zoomCambia) {
      String tituloFinal = titulo;
      LiveSessionSupport.conZoom(
          "EX-004",
          () -> zoom.update(antes.zoomMeetingId(), apoyo.reunion(tituloFinal, descripcion, serie)));
    }

    escrituras.update(
        id,
        titulo,
        descripcion,
        inicio,
        fin,
        frecuencia == null ? null : frecuencia.name(),
        hasta,
        serie.finDeLaSerie(apoyo.zoom().zona()),
        categoria);
    if (cambios.membershipIds() != null) {
      escrituras.replaceMemberships(id, membresias);
    }
    if (cambios.productIds() != null) {
      escrituras.replaceProducts(id, servicios);
    }
    if (instructores != null) {
      escrituras.replaceInstructors(id, instructores);
    }

    Map<String, Object> viejo =
        LiveSessionSupport.instantanea(antes, membresiasAntes, serviciosAntes);
    Map<String, Object> nuevo =
        LiveSessionSupport.instantanea(consultas.find(id).orElseThrow(), membresias, servicios);
    Map<String, Object> diferencias = new LinkedHashMap<>();
    nuevo.forEach(
        (campo, valor) -> {
          Object anterior = viejo.get(campo);
          if (!Objects.equals(String.valueOf(anterior), String.valueOf(valor))) {
            Map<String, Object> par = new LinkedHashMap<>();
            par.put("before", anterior);
            par.put("after", valor);
            diferencias.put(campo, par);
          }
        });
    if (!diferencias.isEmpty()) {
      auditoria.recordChange(
          new ChangeEvent(
              LiveSessionSupport.MODULO,
              LiveSessionSupport.ENTIDAD,
              id,
              ChangeAction.UPDATE,
              diferencias));
    }
    return apoyo.detalle(id);
  }
}
