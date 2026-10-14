package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse;
import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse.LiveSessionInstructorRef;
import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse.LiveSessionRegistrationItem;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSchedule;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries.Occurrence;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionCategoryRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.RegistrationRow;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.KindView;
import com.factech.nexus.modules.system.memberships.application.MembershipCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.security.CurrentActor;
import com.factech.nexus.shared.zoom.ZoomMeetings.Frequency;
import com.factech.nexus.shared.zoom.ZoomMeetings.MeetingSpec;
import com.factech.nexus.shared.zoom.ZoomMeetings.Repeat;
import com.factech.nexus.shared.zoom.ZoomSettings;
import com.factech.nexus.shared.zoom.ZoomUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lo que comparten los casos de uso de las clases en vivo (`RF-AC-042` a `RF-AC-054`): las
 * referencias que se validan al programar y al corregir (`RN-AC-025`), la propiedad del instructor
 * (`RN-AC-028`), el estado que admite cambios (`RN-AC-029`), el detalle que devuelven las
 * escrituras y la traducción de un fallo de Zoom a {@code 503} (`RN-AC-026`).
 *
 * <p><b>Un solo sitio para «no existe»</b>: la clase que no existe y la que no es de los cursos del
 * instructor responden el mismo {@code 404} con el mismo mensaje.
 */
@Component
public class LiveSessionSupport {

  static final String MODULO = "AC";
  static final String ENTIDAD = "live_sessions";
  static final String NO_EXISTE = "No existe una clase en vivo con ese identificador.";

  private final LiveSessionQueryRepository consultas;
  private final LiveSessionCategoryRepository categorias;
  private final InstructorVerifier instructores;
  private final MembershipCatalog membresias;
  private final ProductCatalog productos;
  private final UserCatalog usuarios;
  private final ZoomSettings zoom;
  private final CurrentActor actor;
  private final Clock reloj;

  public LiveSessionSupport(
      LiveSessionQueryRepository consultas,
      LiveSessionCategoryRepository categorias,
      InstructorVerifier instructores,
      MembershipCatalog membresias,
      ProductCatalog productos,
      UserCatalog usuarios,
      ZoomSettings zoom,
      CurrentActor actor) {
    this.consultas = consultas;
    this.categorias = categorias;
    this.instructores = instructores;
    this.membresias = membresias;
    this.productos = productos;
    this.usuarios = usuarios;
    this.zoom = zoom;
    this.actor = actor;
    this.reloj = Clock.systemUTC();
  }

  Instant ahora() {
    return reloj.instant();
  }

  ZoomSettings zoom() {
    return zoom;
  }

  UUID actorId() {
    return actor
        .currentActorId()
        .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
  }

  static ResourceNotFoundException noExiste() {
    return new ResourceNotFoundException("EX-001", NO_EXISTE);
  }

  /** `RN-AC-028`: con los permisos propios, solo las clases de las que es instructor. */
  void exigirPropia(LiveSessionRow clase, UUID instructor) {
    if (!clase.deInstructor(instructor)) {
      throw noExiste();
    }
  }

  /** `RN-AC-029`: solo una programada que no ha terminado se corrige, se cancela o se inicia. */
  void exigirModificable(LiveSessionRow clase, String codigo) {
    if (!clase.programada()) {
      throw new BusinessRuleException(codigo, "La clase está cancelada y ya no se puede cambiar.");
    }
    if (clase.terminada(ahora())) {
      throw new BusinessRuleException(codigo, "La clase ya terminó y no se puede cambiar.");
    }
  }

  /**
   * `RN-AC-032` con `RN-AC-006`: los instructores, sin repetir, y cada uno existe, no está retirado
   * y porta {@code courses:teach}, comprobado al asignar.
   */
  List<UUID> instructores(List<UUID> ids) {
    Set<UUID> unicos = sinRepetir(ids, "instructorIds");
    for (UUID id : unicos) {
      instructores.verificar(id, "instructorIds");
    }
    return List.copyOf(unicos);
  }

  /** `RN-AC-034`: la categoría que se asigna existe y está viva; si no, `422` `EX-002`. */
  UUID categoria(UUID id) {
    return categorias
        .findAliveById(id)
        .map(c -> c.getId())
        .orElseThrow(
            () ->
                referencia(
                    "categoryId",
                    "EX-002",
                    "La categoría %s no existe o está retirada.".formatted(id)));
  }

  /** `RN-AC-028`: por `/mine`, quien corrige no se quita de la lista. */
  static UnprocessableEntityException sinQuitarse() {
    return referencia(
        "instructorIds",
        "EX-004",
        "No puede quitarse de los instructores de una clase propia: lo hace administración.");
  }

  /** Los instructores de cada clase, con su nombre actual, en un solo viaje a `SP`. */
  Map<UUID, List<LiveSessionInstructorRef>> instructoresDe(Map<UUID, List<UUID>> porClase) {
    Set<UUID> todos =
        porClase.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<UUID, UserView> personas =
        todos.isEmpty()
            ? Map.of()
            : usuarios.findAll(todos).stream()
                .collect(Collectors.toMap(UserView::id, Function.identity()));
    Map<UUID, List<LiveSessionInstructorRef>> resultado = new LinkedHashMap<>();
    porClase.forEach(
        (clase, ids) ->
            resultado.put(
                clase,
                ids.stream()
                    .map(
                        id -> {
                          UserView persona = personas.get(id);
                          return new LiveSessionInstructorRef(
                              id,
                              persona == null ? null : persona.username(),
                              persona == null ? null : persona.fullName());
                        })
                    .toList()));
    return resultado;
  }

  /** `RN-AC-025` con `RN-AC-012`: membresías existentes y sin repetir. */
  Set<UUID> membresias(List<UUID> ids) {
    Set<UUID> unicas = sinRepetir(ids, "membershipIds");
    for (UUID id : unicas) {
      if (membresias.find(id).isEmpty()) {
        throw referencia("membershipIds", "EX-002", "La membresía %s no existe.".formatted(id));
      }
    }
    return unicas;
  }

  /** `RN-AC-025` con `RN-AC-020`: servicios {@code BOT} no retirados y sin repetir. */
  Set<UUID> servicios(List<UUID> ids) {
    Set<UUID> unicos = sinRepetir(ids, "productIds");
    for (UUID id : unicos) {
      KindView producto =
          productos
              .findKind(id)
              .filter(p -> !p.retired())
              .orElseThrow(
                  () ->
                      referencia(
                          "productIds",
                          "EX-002",
                          "El producto %s no existe o está retirado.".formatted(id)));
      if (!producto.bot()) {
        throw referencia(
            "productIds",
            "EX-002",
            "Solo un servicio abre una clase: el producto %s no lo es.".formatted(producto.code()));
      }
    }
    return unicos;
  }

  /** La fecha en curso o la siguiente de una clase programada; nula si terminó o se canceló. */
  Occurrence siguiente(LiveSessionSeries serie, String estado) {
    if (!"PROGRAMADA".equals(estado)) {
      return null;
    }
    return serie.siguiente(ahora(), zoom.zona()).orElse(null);
  }

  /**
   * Lo que se le pide a Zoom de una clase: título, agenda, la primera fecha y, si se repite, la
   * repetición con su número de fechas (`RN-AC-031`).
   */
  MeetingSpec reunion(String titulo, String agenda, LiveSessionSeries serie) {
    int minutos = new LiveSessionSchedule(serie.startsAt(), serie.endsAt()).minutos();
    if (serie.recurrence() == null) {
      return new MeetingSpec(titulo, agenda, serie.startsAt(), minutos);
    }
    var primera = serie.startsAt().atZoneSameInstant(zoom.zona());
    Repeat repeticion =
        new Repeat(
            Frequency.valueOf(serie.recurrence().name()),
            serie.veces(zoom.zona()),
            primera.getDayOfWeek(),
            primera.getDayOfMonth());
    return new MeetingSpec(titulo, agenda, serie.startsAt(), minutos, repeticion);
  }

  /** El detalle de `RF-AC-043`, que devuelven también las escrituras. */
  LiveSessionDetailResponse detalle(UUID id) {
    LiveSessionRow clase = consultas.find(id).orElseThrow(LiveSessionSupport::noExiste);
    List<RegistrationRow> registros = consultas.findRegistrationsOf(id);
    Map<UUID, UserView> personas =
        registros.isEmpty()
            ? Map.of()
            : usuarios
                .findAll(
                    registros.stream()
                        .map(RegistrationRow::userId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .stream()
                .collect(Collectors.toMap(UserView::id, Function.identity()));
    List<LiveSessionRegistrationItem> registrados =
        registros.stream()
            .map(
                r -> {
                  UserView persona = personas.get(r.userId());
                  return new LiveSessionRegistrationItem(
                      r.userId(),
                      persona == null ? null : persona.username(),
                      persona == null ? null : persona.fullName(),
                      r.registeredAt());
                })
            .toList();
    return LiveSessionDetailResponse.from(
        clase,
        clase.terminada(ahora()),
        siguiente(clase.serie(), clase.status()),
        instructoresDe(Map.of(id, clase.instructorIds())).getOrDefault(id, List.of()),
        consultas.findMembershipsOf(id),
        consultas.findProductsOf(id),
        registrados);
  }

  /** La instantánea de una clase para la auditoría de cambios. */
  static Map<String, Object> instantanea(
      LiveSessionRow clase, Set<UUID> membresias, Set<UUID> servicios) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("title", clase.title());
    estado.put("description", clase.description());
    estado.put(
        "instructorIds", clase.instructorIds().stream().map(UUID::toString).sorted().toList());
    estado.put("coverImageId", clase.coverImageId());
    estado.put("categoryId", clase.categoryId());
    estado.put("startsAt", clase.startsAt());
    estado.put("endsAt", clase.endsAt());
    estado.put("recurrence", clase.recurrence());
    estado.put("repeatUntil", clase.repeatUntil());
    estado.put("status", clase.status());
    estado.put("zoomMeetingId", clase.zoomMeetingId());
    estado.put("membershipIds", membresias.stream().map(UUID::toString).sorted().toList());
    estado.put("productIds", servicios.stream().map(UUID::toString).sorted().toList());
    return estado;
  }

  /** Llama a Zoom y traduce su fallo a {@code 503} con el código del requerimiento. */
  static <T> T conZoom(String codigo, Supplier<T> llamada) {
    try {
      return llamada.get();
    } catch (ZoomUnavailableException fallo) {
      String mensaje = "Zoom no está disponible en este momento; intente de nuevo.";
      throw new ServiceUnavailableException(
          codigo, mensaje, List.of(new FieldError("zoom", codigo, mensaje)));
    }
  }

  static void conZoom(String codigo, Runnable llamada) {
    conZoom(
        codigo,
        () -> {
          llamada.run();
          return null;
        });
  }

  static void lanzarSiHay(List<FieldError> problemas) {
    if (!problemas.isEmpty()) {
      String mensaje =
          problemas.size() == 1
              ? problemas.get(0).message()
              : "La petición tiene %d errores de validación.".formatted(problemas.size());
      throw new ValidationException(problemas.get(0).code(), mensaje, problemas);
    }
  }

  private static Set<UUID> sinRepetir(List<UUID> ids, String campo) {
    if (ids == null) {
      return Set.of();
    }
    Set<UUID> vistos = new HashSet<>();
    for (UUID id : ids) {
      if (id == null || !vistos.add(id)) {
        String mensaje = "La lista no admite elementos vacíos ni repetidos.";
        throw new ValidationException(
            "VAL-004", mensaje, List.of(new FieldError(campo, "VAL-004", mensaje)));
      }
    }
    return new LinkedHashSet<>(ids);
  }

  private static UnprocessableEntityException referencia(
      String campo, String codigo, String mensaje) {
    return new UnprocessableEntityException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }
}
