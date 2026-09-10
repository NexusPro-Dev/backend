package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.AssignMembershipRequest;
import com.factech.nexus.modules.system.users.application.UserMembershipResponse;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.MembershipCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserMembership;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
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
 * Fijar la membresía de una persona (`RF-SP-032`).
 *
 * <p>Orden de verificación (`plan.md` §4), y los tres rechazos caen en <b>tres categorías
 * distintas</b> a propósito:
 *
 * <ol>
 *   <li>La fecha de fin es posterior al momento de la asignación — {@code 400} `VAL-005`, porque se
 *       decide <b>mirando solo el cuerpo y el reloj</b>.
 *   <li>La persona existe y no está eliminada — {@code 404}.
 *   <li>La membresía existe en la cadena — {@code 422} `VAL-002`, porque es una <b>referencia del
 *       cuerpo que no resuelve</b>.
 *   <li>La persona porta al menos un rol {@code CONSUMIDOR} — {@code 409} `RN-SP-013`, porque es
 *       una <b>regla violada sobre datos que existen</b>: la persona existe, la membresía existe, y
 *       lo que falla es la relación entre ambas.
 * </ol>
 *
 * <p>La membresía se comprueba <b>antes</b> que el rol de consumidor. Es el orden que `spec.md` §8
 * fija y da el error más accionable cuando fallan los dos: una membresía inexistente es un dato
 * equivocado en la petición, y la falta de rol es una operación previa pendiente.
 *
 * <p><b>El {@code 409} cita `RF-SP-030` por su nombre.</b> Sin esa indicación, quien lo recibe no
 * tiene forma de saber que la operación que le falta es otra.
 */
@Service
public class AssignUserMembershipService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "user_memberships";

  private final UserRepository usuarios;

  private final MembershipCatalog membresias;
  private final AuditWriter auditoria;
  private final Clock reloj;
  private final UuidV7Generator ids;

  @Autowired
  public AssignUserMembershipService(
      UserRepository usuarios,
      MembershipCatalog membresias,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(usuarios, membresias, auditoria, Clock.systemUTC(), ids);
  }

  AssignUserMembershipService(
      UserRepository usuarios,
      MembershipCatalog membresias,
      AuditWriter auditoria,
      Clock reloj,
      UuidV7Generator ids) {
    this.usuarios = usuarios;

    this.membresias = membresias;
    this.auditoria = auditoria;
    this.reloj = reloj;
    this.ids = ids;
  }

  @Transactional
  public UserMembershipResponse assign(UUID userId, AssignMembershipRequest peticion) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    // 1. `EX-004`. La comprobación vive aquí y no en una anotación porque
    // `@Future` compara contra el reloj del sistema, y con un reloj inyectado la
    // prueba deja de depender de la hora a la que se ejecute.
    if (peticion.endsAt() != null && !peticion.endsAt().isAfter(ahora)) {
      String mensaje = "La fecha de fin debe ser posterior al momento de la asignación.";
      throw new ValidationException(
          "VAL-005", mensaje, List.of(new FieldError("endsAt", "VAL-005", mensaje)));
    }

    // 2. La persona. No se exige ACTIVA, por lo mismo que en `RF-SP-030`.
    User usuario =
        usuarios
            .findNotDeletedByIdForUpdate(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-004", "No existe una persona con ese identificador."));

    // 3. `EX-002`.
    MembershipCatalog.MembershipRef membresia =
        membresias
            .find(peticion.membershipId())
            .orElseThrow(
                () -> {
                  String mensaje = "La membresía indicada no existe.";
                  return new UnprocessableEntityException(
                      "VAL-002",
                      mensaje,
                      List.of(new FieldError("membershipId", "VAL-002", mensaje)));
                });

    // 4. `EX-001` SE RETIRÓ EL 05-09-2026, con `RN-SP-013`. Exigía que la
    //    persona portara algún rol de consumidor, y esa exigencia se contradice
    //    con la regla que la sustituye: `RN-SP-018` reescrita da nivel a TODA
    //    persona, y el superadministrador tiene `BECA` sin ser consumidor de
    //    nada. La numeración de los pasos no se recoloca: `spec.md` los cita.

    Optional<UserMembership> anterior = usuarios.findMembership(userId);

    // `FA-002` frente a `FA-003`: misma membresía y misma vigencia no escribe ni
    // audita; misma membresía con fecha distinta sí. Sin esta distinción, una
    // interfaz que reenvía el formulario al guardar dejaría una fila de
    // auditoría por cada pulsación describiendo un cambio que no ocurrió.
    boolean sinCambio =
        anterior.map(previa -> previa.coincideCon(membresia.id(), peticion.endsAt())).orElse(false);

    if (!sinCambio) {
      // LOS DOS CAMINOS DE `V56`, y cuál se toma lo decide si CAMBIA EL NIVEL o
      // solo la fecha.
      //
      // MISMA MEMBRESÍA CON OTRA FECHA: se corrige la fila abierta y NO SE GENERA
      // HISTORIAL. Corregir hasta cuándo vale un nivel es una corrección
      // administrativa, no un ascenso ni una bajada, y anotarla como un periodo
      // nuevo llenaría el historial de filas que no describen ningún cambio de
      // nivel — que es justo lo que ese historial existe para contar.
      //
      // OTRA MEMBRESÍA: se cierra la abierta y se abre una nueva. `assignMembership`
      // hace las dos cosas juntas.
      boolean mismoNivel =
          anterior.map(previa -> previa.membershipId().equals(membresia.id())).orElse(false);

      if (mismoNivel) {
        usuarios.updateMembershipEnd(userId, peticion.endsAt(), ahora);
      } else {
        usuarios.assignMembership(ids.next(), userId, membresia.id(), peticion.endsAt(), ahora);
      }
      auditar(usuario, anterior.orElse(null), membresia, peticion.endsAt());
    }

    return UserMembershipResponse.de(
        membresia.id(), membresia.code(), membresia.name(), membresia.level(), peticion.endsAt());
  }

  /**
   * Un evento de cambio, y <b>ninguno de seguridad</b>.
   *
   * <p>La membresía no concede permisos: es un dato comercial. Emitir aquí un evento de seguridad
   * diluiría el registro que existe para investigar accesos, y `RF-SP-014` acabaría devolviendo
   * ruido entre los incidentes.
   *
   * <p>El evento conserva <b>el nivel y la fecha, antes y después</b>. El nivel es lo que hace
   * legible el cambio: sin él, dos códigos de membresía en un registro no dicen si fue un ascenso o
   * una bajada, y esa diferencia es justo la que alguien querrá reconstruir.
   */
  private void auditar(
      User usuario,
      UserMembership anterior,
      MembershipCatalog.MembershipRef nueva,
      OffsetDateTime hasta) {

    Map<String, Object> cambios = new HashMap<>();
    // `before` en nulo es la forma correcta de decir `FA-001`: no había ninguna.
    cambios.put(
        "before",
        anterior == null
            ? null
            : Map.of(
                "membership_code", anterior.code(),
                "level", anterior.level(),
                "ends_at", String.valueOf(anterior.endsAt())));
    cambios.put(
        "after",
        Map.of(
            "membership_code", nueva.code(),
            "level", nueva.level(),
            "ends_at", String.valueOf(hasta)));

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            usuario.getId(),
            anterior == null ? ChangeAction.CREATE : ChangeAction.UPDATE,
            cambios));
  }
}
