package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.ChangeUserStatusRequest;
import com.factech.nexus.modules.system.users.application.UserStatusResponse;
import com.factech.nexus.modules.system.users.domain.models.ChangeReason;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.UserStatus;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.security.RootAdministratorPresence;
import com.factech.nexus.modules.system.users.domain.security.SelfOperationGuard;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ForbiddenException;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.security.SessionRevoker;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retirar o devolver el acceso de una persona (`RF-SP-028`).
 *
 * <p><b>Se recibe el estado destino y no una acción</b>, lo que hace la operación idempotente por
 * construcción: pedir el estado que ya se tiene no cambia nada.
 *
 * <p><b>Salvo en un caso, y es el que da sentido a todo el requerimiento.</b> Pasar de {@code
 * BLOQUEADO} automático a {@code BLOQUEADO} manual <b>sí es un cambio</b>, aunque el estado sea el
 * mismo: {@code locked_until} pasa de informado a nulo, y con ello el bloqueo deja de levantarse
 * solo. El dominio lo decide mirando esa columna y no un campo aparte, que es lo que hace
 * observable la distinción entre los dos bloqueos <b>sin ampliar el esquema</b>.
 *
 * <p>Orden de verificación (`plan.md` §4):
 *
 * <ol>
 *   <li>Formato: estado en su dominio y motivo coherente con el destino — {@code 400}.
 *   <li>La persona existe y no está eliminada, <b>bloqueada</b> — {@code 404}.
 *   <li>No es el propio actor (`RN-SP-017`) — {@code 403}.
 *   <li><b>Solo al retirar el acceso</b>: no es el último portador activo del rol raíz — {@code
 *       409}.
 *   <li><b>Solo al retirar el acceso</b>: no tiene personas a cargo — {@code 409}.
 * </ol>
 *
 * <p><b>El motivo se valida el primero de todos</b>, antes incluso de saber si la persona existe:
 * es formato, y `spec.md` §10 exige rechazar «antes de ejecutarla».
 *
 * <p><b>Reactivar nunca falla por regla.</b> Devolver el acceso no puede dejar a nadie sin
 * administración ni a ningún equipo huérfano, de modo que los pasos 4 y 5 no se evalúan — y
 * evaluarlos igualmente costaría dos consultas por reactivación.
 */
@Service
public class ChangeUserStatusService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "users";

  private final UserRepository usuarios;
  private final RootAdministratorPresence raiz;
  private final SessionRevoker sesiones;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public ChangeUserStatusService(
      UserRepository usuarios,
      RootAdministratorPresence raiz,
      SessionRevoker sesiones,
      AuthenticatedActor actor,
      AuditWriter auditoria) {
    this(usuarios, raiz, sesiones, actor, auditoria, Clock.systemUTC());
  }

  ChangeUserStatusService(
      UserRepository usuarios,
      RootAdministratorPresence raiz,
      SessionRevoker sesiones,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.usuarios = usuarios;
    this.raiz = raiz;
    this.sesiones = sesiones;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public UserStatusResponse change(UUID userId, ChangeUserStatusRequest peticion) {
    // 1. Formato, lo primero de todo.
    UserStatus destino = estadoDestino(peticion.status());
    boolean retiraElAcceso = destino != UserStatus.ACTIVO;
    ChangeReason motivo = motivoCoherente(peticion.reason(), retiraElAcceso);

    // 2. La persona, bloqueada.
    User usuario =
        usuarios
            .findNotDeletedByIdForUpdate(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-005", "No existe una persona con ese identificador."));

    // 3. `RN-SP-017` → 403 y no 409: es una prohibición sobre QUIÉN ejecuta, y
    //    el mismo cuerpo enviado por otro actor sería válido.
    if (SelfOperationGuard.esSuPropiaCuenta(actor.id(), userId)) {
      String mensaje = "No puede cambiar el estado de su propia cuenta.";
      throw new ForbiddenException(
          "RN-SP-017", mensaje, List.of(new FieldError("id", "RN-SP-017", mensaje)));
    }

    OffsetDateTime bloqueadoHasta = usuarios.lockedUntilOf(userId).orElse(null);
    UserStatus actual = usuario.getStatus();

    // `FA-003`: mismo estado, pero el bloqueo pasa de automático a manual.
    boolean pasaAManual =
        destino == UserStatus.BLOQUEADO && actual == UserStatus.BLOQUEADO && bloqueadoHasta != null;

    if (actual == destino && !pasaAManual) {
      // `FA-001`: sin cambio, sin escritura y sin evento.
      return UserStatusResponse.de(
          usuario.getId(),
          usuario.getUsername(),
          actual.name(),
          bloqueadoHasta,
          usuario.getUpdatedAt());
    }

    if (retiraElAcceso) {
      // 4 y 5, solo aquí.
      verificarSuperadministrador(userId);
      verificarEquipoACargo(userId);
    }

    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    // `limpiarAcceso` en verdadero tanto al reactivar —el contador vuelve a cero
    // y la cuenta empieza limpia— como al bloquear A MANO, donde el nulo de
    // `locked_until` ES la marca de que ese bloqueo no expira solo.
    usuarios.applyStatus(userId, destino.name(), true, ahora);

    if (retiraElAcceso) {
      // Dentro de la transacción: si falla, el cambio se revierte entero antes
      // que dejar vivo el acceso que se acaba de retirar.
      sesiones.revokeAllForAccessChange(userId);
    }

    auditar(usuario, actual, destino, bloqueadoHasta, motivo);

    return UserStatusResponse.de(
        usuario.getId(), usuario.getUsername(), destino.name(), null, ahora);
  }

  // ---------------------------------------------------------------------------
  // Formato
  // ---------------------------------------------------------------------------

  /**
   * `PENDIENTE` se rechaza aunque el esquema lo admita.
   *
   * <p>Ningún requerimiento lo produce, y admitirlo aquí abriría <b>el único camino</b> hacia un
   * estado del que nadie sabe salir.
   */
  private static UserStatus estadoDestino(String valor) {
    Optional<UserStatus> resuelto =
        java.util.Arrays.stream(UserStatus.values())
            .filter(estado -> estado.name().equalsIgnoreCase(valor == null ? "" : valor.trim()))
            .filter(estado -> estado != UserStatus.PENDIENTE)
            .findFirst();

    return resuelto.orElseThrow(
        () -> {
          String mensaje =
              "El estado debe ser ACTIVO, INACTIVO o BLOQUEADO. PENDIENTE no se admite.";
          return new ValidationException(
              "VAL-001", mensaje, List.of(new FieldError("status", "VAL-001", mensaje)));
        });
  }

  /**
   * El motivo es condicional <b>en los dos sentidos</b>.
   *
   * <p>Obligatorio al retirar el acceso y <b>rechazado</b> al devolverlo. Aceptarlo en silencio al
   * reactivar dejaría un texto que nadie sabría si interpretar como justificación de la
   * reactivación o como resto de una petición anterior.
   */
  private static ChangeReason motivoCoherente(String motivo, boolean retiraElAcceso) {
    boolean informado = motivo != null && !motivo.isBlank();

    if (retiraElAcceso && !informado) {
      String mensaje = "Debe indicar el motivo al retirar el acceso.";
      throw new ValidationException(
          "VAL-005", mensaje, List.of(new FieldError("reason", "VAL-005", mensaje)));
    }
    if (!retiraElAcceso && motivo != null) {
      String mensaje = "No se admite motivo al devolver el acceso.";
      throw new ValidationException(
          "VAL-006", mensaje, List.of(new FieldError("reason", "VAL-006", mensaje)));
    }
    return retiraElAcceso ? new ChangeReason(motivo) : null;
  }

  // ---------------------------------------------------------------------------
  // Reglas
  // ---------------------------------------------------------------------------

  /** `RN-SP-001` → {@code 409}. El mensaje <b>explica la consecuencia</b>, no solo niega. */
  private void verificarSuperadministrador(UUID userId) {
    if (!raiz.sobreviveSinEl(userId)) {
      String mensaje =
          "Es el último superadministrador activo: retirarle el acceso dejaría al sistema sin"
              + " ninguna vía de administración. Nombre otro antes.";
      throw new BusinessRuleException(
          "RN-SP-001", mensaje, List.of(new FieldError("id", "RN-SP-001", mensaje)));
    }
  }

  /** `RN-SP-022` → {@code 409}, diciendo <b>cuántas</b> personas y nunca quiénes. */
  private void verificarEquipoACargo(UUID userId) {
    int aCargo = usuarios.countSupervisees(userId);
    if (aCargo > 0) {
      String mensaje =
          "La persona tiene "
              + aCargo
              + " a cargo: reasigne su equipo antes de retirarle el acceso.";
      throw new BusinessRuleException(
          "RN-SP-022", mensaje, List.of(new FieldError("id", "RN-SP-022", mensaje)));
    }
  }

  // ---------------------------------------------------------------------------

  /**
   * Un evento de cambio y uno de seguridad, y el de cambio distingue los dos bloqueos.
   *
   * <p>Sin {@code locked_until} en el {@code before}, el registro de un paso de bloqueo automático
   * a manual sería idéntico al de una petición que no cambió nada — y `FA-003` dejaría de ser
   * reconstruible.
   */
  private void auditar(
      User usuario,
      UserStatus antes,
      UserStatus despues,
      OffsetDateTime bloqueoAnterior,
      ChangeReason motivo) {

    Map<String, Object> cambios = new HashMap<>();
    cambios.put("status", Map.of("before", antes.name(), "after", despues.name()));
    cambios.put("locked_until", Map.of("before", String.valueOf(bloqueoAnterior), "after", "null"));
    cambios.put("reason", motivo == null ? null : motivo.value());

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, usuario.getId(), ChangeAction.UPDATE, cambios));

    Map<String, Object> detalle = new HashMap<>();
    detalle.put("from", antes.name());
    detalle.put("to", despues.name());
    detalle.put("reason", motivo == null ? null : motivo.value());

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.USER_STATUS_CHANGED,
            Severity.ALTA,
            Outcome.SUCCESS,
            usuario.getId(),
            detalle));
  }
}
