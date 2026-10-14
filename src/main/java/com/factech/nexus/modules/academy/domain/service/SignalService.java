package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.SignalRequests.Delete;
import com.factech.nexus.modules.academy.application.SignalRequests.ListFilter;
import com.factech.nexus.modules.academy.application.SignalRequests.Register;
import com.factech.nexus.modules.academy.application.SignalResponses.AvailableSignal;
import com.factech.nexus.modules.academy.application.SignalResponses.SignalMembershipRef;
import com.factech.nexus.modules.academy.application.SignalResponses.SignalResponse;
import com.factech.nexus.modules.academy.domain.models.Signal;
import com.factech.nexus.modules.academy.domain.models.SignalDirection;
import com.factech.nexus.modules.academy.domain.models.SubscriptionType;
import com.factech.nexus.modules.academy.domain.repository.SignalRepository;
import com.factech.nexus.modules.academy.domain.repository.SignalRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.SignalRepository.SignalFilter;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup.CurrentMembershipView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.audit.DeletionReason;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.CurrentActor;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso `RF-AC-067` a `RF-AC-072`: las señales de trading (`RN-AC-035`).
 *
 * <p><b>Registrar una, varias o un CSV es la misma operación</b> con tres entradas: cada señal se
 * comprueba con las mismas reglas, los errores de todas se juntan en una sola respuesta con el
 * campo de cada uno —{@code currencyPair}, {@code [3].currencyPair} o {@code
 * lines[4].currency_pair}— y <b>si alguna falla no se registra ninguna</b>. Primero el formato
 * ({@code 400}), después que existan las membresías ({@code 422}).
 */
@Service
public class SignalService {

  static final String MODULO = "AC";
  static final String ENTIDAD = "signals";
  static final int MAXIMO_POR_PETICION = 500;

  private final SignalRepository senales;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final BusinessCalendar calendario;
  private final CurrentActor actor;
  private final CurrentMembershipLookup membresias;
  private final StudentKeys alumnos;
  private final Pagination paginacion;

  public SignalService(
      SignalRepository senales,
      AuditWriter auditoria,
      UuidV7Generator ids,
      BusinessCalendar calendario,
      CurrentActor actor,
      CurrentMembershipLookup membresias,
      StudentKeys alumnos,
      Pagination paginacion) {
    this.senales = senales;
    this.auditoria = auditoria;
    this.ids = ids;
    this.calendario = calendario;
    this.actor = actor;
    this.membresias = membresias;
    this.alumnos = alumnos;
    this.paginacion = paginacion;
  }

  /** `RF-AC-067`: todas, las más nuevas primero. */
  @Transactional(readOnly = true)
  public PageResponse<SignalResponse> list(ListFilter filtros) {
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());
    SubscriptionType tipo = null;
    if (filtros.subscriptionType() != null && !filtros.subscriptionType().isBlank()) {
      List<FieldError> errores = new ArrayList<>();
      tipo = Signal.suscripcion(filtros.subscriptionType(), "subscriptionType", errores);
      lanzarSiHay(errores);
    }
    OffsetDateTime ahora = calendario.ahora();
    SignalFilter filtro =
        new SignalFilter(
            tipo, filtros.visible(), Boolean.TRUE.equals(filtros.includeDeleted()), ahora);
    List<Signal> pagina = senales.search(filtro, trozo.offset(), trozo.size());
    return PageResponse.de(
        respuestas(pagina, ahora), senales.count(filtro), trozo.page(), trozo.size());
  }

  /** `RF-AC-072`: las vigentes que abre la membresía de quien mira. */
  @Transactional(readOnly = true)
  public List<AvailableSignal> available() {
    UUID quien = alumnos.actorId();
    UUID membresia =
        membresias.currentMembershipOf(quien).map(CurrentMembershipView::id).orElse(null);
    return senales.available(calendario.ahora(), membresia).stream()
        .map(
            s ->
                new AvailableSignal(
                    s.getId(),
                    s.getCurrencyPair(),
                    s.horaTexto(),
                    s.getSignalType(),
                    s.getDirection().name(),
                    s.getSubscriptionType().name(),
                    s.getVisibleUntil(),
                    s.getCreatedAt()))
        .toList();
  }

  /** `RF-AC-068`. */
  @Transactional
  public SignalResponse register(Register peticion) {
    return registrar(List.of(desdeJson("", peticion))).getFirst();
  }

  /** `RF-AC-069`: todas o ninguna. */
  @Transactional
  public List<SignalResponse> registerBatch(List<Register> peticiones) {
    if (peticiones == null || peticiones.isEmpty()) {
      throw lote("VAL-007", "Envíe al menos una señal.");
    }
    if (peticiones.size() > MAXIMO_POR_PETICION) {
      throw lote("VAL-008", "No se pueden registrar más de 500 señales a la vez.");
    }
    List<Entrada> entradas = new ArrayList<>();
    for (int i = 0; i < peticiones.size(); i++) {
      entradas.add(desdeJson("[" + i + "].", peticiones.get(i)));
    }
    return registrar(entradas);
  }

  /** `RF-AC-070`: las del archivo, todas o ninguna. */
  @Transactional
  public List<SignalResponse> importCsv(byte[] archivo) {
    List<Entrada> entradas =
        SignalCsv.read(archivo).stream()
            .map(
                fila ->
                    new Entrada(
                        "lines[" + fila.line() + "].",
                        true,
                        fila.currencyPair(),
                        fila.signalTime(),
                        null,
                        fila.signalType(),
                        fila.direction(),
                        fila.subscriptionType(),
                        List.of(),
                        fila.memberships()))
            .toList();
    return registrar(entradas);
  }

  /** `RF-AC-071`: baja lógica con motivo. */
  @Transactional
  public void delete(UUID id, Delete peticion) {
    DeletionReason motivo = new DeletionReason(peticion == null ? null : peticion.reason());
    Signal senal =
        senales
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una señal con ese identificador."));
    if (senal.estaRetirada()) {
      String mensaje = "La señal ya está retirada.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("id", "EX-002", mensaje)));
    }
    List<UUID> suyas =
        senales.membershipsOf(List.of(id)).getOrDefault(id, List.of()).stream()
            .map(MembershipRef::id)
            .toList();
    Map<String, Object> instantanea = senal.instantanea(suyas);
    senal.delete(calendario.ahora());
    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, ENTIDAD, senal.getId(), DeletionType.LOGICAL, motivo.value(), instantanea));
  }

  // ---------------------------------------------------------------------------

  /**
   * Una señal por registrar, venga de JSON o de CSV: el prefijo de sus campos y los valores sin
   * comprobar. Del JSON llegan el tipo como número y las membresías por identificador; del CSV, el
   * tipo como texto y las membresías por código.
   */
  private record Entrada(
      String prefijo,
      boolean csv,
      String par,
      String hora,
      Integer tipoNumero,
      String tipoTexto,
      String direccion,
      String suscripcion,
      List<UUID> membresiaIds,
      List<String> membresiaCodigos) {

    String campo(String json, String columna) {
      return prefijo + (csv ? columna : json);
    }

    String campoMembresias() {
      return campo("membershipIds", "memberships");
    }
  }

  /** Una entrada ya comprobada, a falta de resolver sus membresías. */
  private record Valida(
      Entrada entrada,
      String par,
      LocalTime hora,
      int tipo,
      SignalDirection direccion,
      SubscriptionType suscripcion) {}

  private static Entrada desdeJson(String prefijo, Register peticion) {
    Register p = peticion == null ? new Register(null, null, null, null, null, null) : peticion;
    List<UUID> membresiaIds =
        p.membershipIds() == null
            ? List.of()
            : p.membershipIds().stream().filter(Objects::nonNull).distinct().toList();
    return new Entrada(
        prefijo,
        false,
        p.currencyPair(),
        p.signalTime(),
        p.signalType(),
        null,
        p.direction(),
        p.subscriptionType(),
        membresiaIds,
        List.of());
  }

  private List<SignalResponse> registrar(List<Entrada> entradas) {
    List<FieldError> errores = new ArrayList<>();
    List<Valida> validas = new ArrayList<>();
    for (Entrada e : entradas) {
      int antes = errores.size();
      String par = Signal.par(e.par(), e.campo("currencyPair", "currency_pair"), errores);
      LocalTime hora = Signal.hora(e.hora(), e.campo("signalTime", "signal_time"), errores);
      String campoTipo = e.campo("signalType", "signal_type");
      Integer tipo =
          e.csv()
              ? Signal.tipo(e.tipoTexto(), campoTipo, errores)
              : Signal.tipo(e.tipoNumero(), campoTipo, errores);
      SignalDirection direccion =
          Signal.direccion(e.direccion(), e.campo("direction", "direction"), errores);
      SubscriptionType suscripcion =
          Signal.suscripcion(
              e.suscripcion(), e.campo("subscriptionType", "subscription_type"), errores);
      int cuantas = e.csv() ? e.membresiaCodigos().size() : e.membresiaIds().size();
      Signal.membresias(suscripcion, cuantas, e.campoMembresias(), errores);
      if (errores.size() == antes) {
        validas.add(new Valida(e, par, hora, tipo, direccion, suscripcion));
      }
    }
    lanzarSiHay(errores);

    List<Set<UUID>> membresiasDeCada = resolverMembresias(validas);

    OffsetDateTime ahora = calendario.ahora();
    UUID quien = actor.currentActorId().orElse(null);
    List<Signal> creadas = new ArrayList<>();
    for (int i = 0; i < validas.size(); i++) {
      Valida v = validas.get(i);
      Signal senal =
          Signal.create(
              ids.next(),
              v.par(),
              v.hora(),
              v.tipo(),
              v.direccion(),
              v.suscripcion(),
              quien,
              ahora,
              calendario.zona());
      Set<UUID> suyas = membresiasDeCada.get(i);
      senales.insert(senal, suyas);
      auditoria.recordChange(
          new ChangeEvent(
              MODULO,
              ENTIDAD,
              senal.getId(),
              ChangeAction.CREATE,
              senal.instantanea(List.copyOf(suyas))));
      creadas.add(senal);
    }
    return respuestas(creadas, ahora);
  }

  /** Las membresías de cada válida, en su orden; las que no existen, todas juntas en un `422`. */
  private List<Set<UUID>> resolverMembresias(List<Valida> validas) {
    Set<UUID> pedidasPorId = new LinkedHashSet<>();
    Set<String> pedidasPorCodigo = new LinkedHashSet<>();
    for (Valida v : validas) {
      pedidasPorId.addAll(v.entrada().membresiaIds());
      pedidasPorCodigo.addAll(v.entrada().membresiaCodigos());
    }
    Set<UUID> existentes = senales.existingMemberships(pedidasPorId);
    Map<String, UUID> porCodigo = senales.membershipsByCode(pedidasPorCodigo);

    List<FieldError> errores = new ArrayList<>();
    List<Set<UUID>> resultado = new ArrayList<>();
    for (Valida v : validas) {
      Set<UUID> suyas = new LinkedHashSet<>();
      for (UUID id : v.entrada().membresiaIds()) {
        if (existentes.contains(id)) {
          suyas.add(id);
        } else {
          errores.add(
              new FieldError(
                  v.entrada().campoMembresias(),
                  "EX-002",
                  "La membresía %s no existe.".formatted(id)));
        }
      }
      for (String codigo : v.entrada().membresiaCodigos()) {
        UUID id = porCodigo.get(codigo);
        if (id != null) {
          suyas.add(id);
        } else {
          errores.add(
              new FieldError(
                  v.entrada().campoMembresias(),
                  "EX-002",
                  "La membresía %s no existe.".formatted(codigo)));
        }
      }
      resultado.add(suyas);
    }
    if (!errores.isEmpty()) {
      throw new UnprocessableEntityException(
          "EX-002", mensajeDe(errores, "no existe alguna de las membresías"), errores);
    }
    return resultado;
  }

  private List<SignalResponse> respuestas(List<Signal> lista, OffsetDateTime ahora) {
    Map<UUID, List<MembershipRef>> deCada =
        senales.membershipsOf(lista.stream().map(Signal::getId).toList());
    return lista.stream()
        .map(
            s ->
                new SignalResponse(
                    s.getId(),
                    s.getCurrencyPair(),
                    s.horaTexto(),
                    s.getSignalType(),
                    s.getDirection() == null ? null : s.getDirection().name(),
                    s.getSubscriptionType().name(),
                    deCada.getOrDefault(s.getId(), List.of()).stream()
                        .map(m -> new SignalMembershipRef(m.id(), m.code(), m.name()))
                        .toList(),
                    s.getVisibleUntil(),
                    !s.estaRetirada() && s.getVisibleUntil().isAfter(ahora),
                    s.getCreatedAt(),
                    s.getDeletedAt()))
        .toList();
  }

  private static void lanzarSiHay(List<FieldError> errores) {
    if (!errores.isEmpty()) {
      throw new ValidationException(
          errores.getFirst().code(), mensajeDe(errores, "hay datos inválidos"), errores);
    }
  }

  /** Un error, su mensaje; varios, cuántas señales fallaron y que no se registró ninguna. */
  private static String mensajeDe(List<FieldError> errores, String causa) {
    if (errores.size() == 1) {
      return errores.getFirst().message();
    }
    long filas =
        errores.stream()
            .map(
                e -> {
                  int punto = e.field().lastIndexOf('.');
                  return punto < 0 ? "" : e.field().substring(0, punto);
                })
            .distinct()
            .count();
    return filas <= 1
        ? "La señal tiene %d errores.".formatted(errores.size())
        : "En %d señales %s; no se registró ninguna.".formatted(filas, causa);
  }

  private static ValidationException lote(String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError("body", codigo, mensaje)));
  }
}
