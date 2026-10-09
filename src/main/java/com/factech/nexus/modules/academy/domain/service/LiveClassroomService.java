package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionRequests.AvailableFilter;
import com.factech.nexus.modules.academy.application.LiveSessionResponses.Available;
import com.factech.nexus.modules.academy.application.LiveSessionResponses.Join;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSchedule;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.RegistrationRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.UpcomingRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionRepository;
import com.factech.nexus.modules.system.users.application.UserContactLookup;
import com.factech.nexus.modules.system.users.application.UserContactLookup.UserContact;
import com.factech.nexus.shared.error.NotEntitledException;
import com.factech.nexus.shared.zoom.ZoomMeetings;
import com.factech.nexus.shared.zoom.ZoomMeetings.Registrant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso `RF-AC-053` y `RF-AC-054`: las clases en vivo del alumno, y entrar.
 *
 * <p><b>Las mismas llaves que el aula</b> ({@link StudentKeys} y {@code StudentAccess}): una clase
 * se abre a quien tiene vigente una de sus membresías o uno de sus servicios, o a todos si no
 * declara ninguno (`RN-AC-025`). <b>Entrar es registrarse</b> (`RN-AC-027`): con la fila de la
 * clase bloqueada —dos clics simultáneos no registran dos veces—, el acceso <b>se comprueba en cada
 * petición</b>, el registro existente se devuelve sin llamar a Zoom, y si no lo hay se pide a Zoom
 * y se guarda.
 */
@Service
public class LiveClassroomService {

  static final String NO_SE_ABRE = "Ni tu membresía ni tus servicios abren esta clase.";

  private final LiveSessionSupport apoyo;
  private final LiveSessionRepository escrituras;
  private final LiveSessionQueryRepository consultas;
  private final StudentKeys llaves;
  private final UserContactLookup contactos;
  private final ZoomMeetings zoom;

  public LiveClassroomService(
      LiveSessionSupport apoyo,
      LiveSessionRepository escrituras,
      LiveSessionQueryRepository consultas,
      StudentKeys llaves,
      UserContactLookup contactos,
      ZoomMeetings zoom) {
    this.apoyo = apoyo;
    this.escrituras = escrituras;
    this.consultas = consultas;
    this.llaves = llaves;
    this.contactos = contactos;
    this.zoom = zoom;
  }

  /** `RF-AC-053`: la vitrina de lo que viene. */
  @Transactional(readOnly = true)
  public List<Available> available(AvailableFilter filtros) {
    UUID quien = llaves.actorId();
    Instant ahora = apoyo.ahora();
    List<UpcomingRow> clases =
        consultas.findUpcoming(ahora, filtros == null ? null : filtros.courseId());
    if (clases.isEmpty()) {
      return List.of();
    }
    StudentKeys.Keys alumno = llaves.ofCurrentActor();
    Set<UUID> registradas =
        consultas.registeredAmong(quien, clases.stream().map(UpcomingRow::id).toList());
    List<Available> lista = new ArrayList<>();
    for (UpcomingRow clase : clases) {
      boolean accesible =
          alumno.opens(
              clase.memberships().stream().map(MembershipRef::id).toList(),
              clase.products().stream().map(ProductRef::id).toList());
      if (filtros != null && filtros.soloAccesibles() && !accesible) {
        continue;
      }
      lista.add(
          Available.from(
              clase,
              LiveSessionSchedule.enCurso(clase.startsAt(), clase.endsAt(), ahora),
              accesible,
              registradas.contains(clase.id())));
    }
    return lista;
  }

  /** El resultado de entrar: el enlace, y si se acaba de registrar ({@code 201}) o ya lo estaba. */
  public record Entrada(Join join, boolean nuevo) {}

  /** `RF-AC-054`: registrarse en Zoom y recibir el enlace personal. */
  @Transactional
  public Entrada join(UUID id) {
    UUID quien = llaves.actorId();
    LiveSessionRow clase =
        escrituras
            .findForUpdate(id)
            .filter(c -> c.programada() && !c.terminada(apoyo.ahora()))
            .orElseThrow(LiveSessionSupport::noExiste);

    List<MembershipRef> membresias = consultas.findMembershipsOf(id);
    List<ProductRef> servicios = consultas.findProductsOf(id);
    boolean abre =
        llaves
            .ofCurrentActor()
            .opens(
                membresias.stream().map(MembershipRef::id).toList(),
                servicios.stream().map(ProductRef::id).toList());
    if (!abre) {
      throw new NotEntitledException(
          "EX-002",
          NO_SE_ABRE,
          Map.of(
              "memberships",
              membresias.stream()
                  .map(
                      m ->
                          Map.of(
                              "id", m.id(), "code", m.code(), "name", m.name(), "color", m.color()))
                  .toList(),
              "products",
              servicios.stream()
                  .map(s -> Map.of("id", s.id(), "code", s.code(), "name", s.name()))
                  .toList()));
    }

    Optional<RegistrationRow> previo = consultas.findRegistration(id, quien);
    if (previo.isPresent()) {
      return new Entrada(
          new Join(
              previo.get().joinUrl(),
              clase.startsAt(),
              clase.endsAt(),
              previo.get().registeredAt()),
          false);
    }

    UserContact persona = contactos.contactOf(quien).orElseThrow(LiveSessionSupport::noExiste);
    Registrant registro =
        LiveSessionSupport.conZoom(
            "EX-003",
            () ->
                zoom.register(
                    clase.zoomMeetingId(),
                    persona.email(),
                    persona.firstName(),
                    persona.lastName()));
    escrituras.saveRegistration(id, quien, registro.registrantId(), registro.joinUrl());
    RegistrationRow guardado = consultas.findRegistration(id, quien).orElseThrow();
    return new Entrada(
        new Join(guardado.joinUrl(), clase.startsAt(), clase.endsAt(), guardado.registeredAt()),
        true);
  }
}
