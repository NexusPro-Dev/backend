package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.MembershipGrant;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.MembershipCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserMembership;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La escritura publicada de `SP` (D-26): conceder el nivel comprado.
 *
 * <p><b>Bloquea la fila de la persona antes de escribir</b>, como `RF-SP-032`: sin ese bloqueo, una
 * confirmación y una asignación manual simultáneas leerían la misma fila abierta, las dos la
 * cerrarían y las dos insertarían. El orden de bloqueo es siempre venta y luego persona —lo fija
 * `RF-MV-003` · `plan.md` §7— y `RF-SP-032` solo toma la persona, así que no hay ciclo posible.
 *
 * <p><b>{@code MANDATORY} y no {@code REQUIRED}</b>: la norma de `architecture.md` §15.2.1 dice que
 * una escritura publicada se une a la transacción del que llama, y con {@code REQUIRED} eso sería
 * una convención que un caso de uso sin {@code @Transactional} rompería sin que nada avisara —
 * conceder quedaría confirmado en su propia transacción y la venta podría deshacerse después.
 * {@code MANDATORY} lo convierte en un fallo inmediato.
 */
@Service
public class PublishedMembershipGrant implements MembershipGrant {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "user_memberships";

  private final UserRepository usuarios;
  private final MembershipCatalog membresias;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;

  public PublishedMembershipGrant(
      UserRepository usuarios,
      MembershipCatalog membresias,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this.usuarios = usuarios;
    this.membresias = membresias;
    this.auditoria = auditoria;
    this.ids = ids;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public GrantedMembership grant(GrantOrder orden) {
    // LANZA, no devuelve vacío (§15.2.1, regla 3): que la persona no exista no
    // puede ocurrir desde una venta registrada, y si ocurre tiene que deshacer
    // la confirmación entera, no producir un 4xx.
    User usuario =
        usuarios
            .findNotDeletedByIdForUpdate(orden.userId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No se puede conceder una membresía a una persona que no existe: "
                            + orden.userId()));
    MembershipCatalog.MembershipRef membresia =
        membresias
            .find(orden.membershipId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No existe la membresía que se quiere conceder: " + orden.membershipId()));

    OffsetDateTime desde = orden.at();
    OffsetDateTime hasta =
        orden.validityDays() == null ? null : desde.plusDays(orden.validityDays());

    Optional<UserMembership> anterior = usuarios.findMembership(usuario.getId());

    // SIEMPRE cierra e inserta, también con la misma membresía: una compra es un
    // periodo nuevo pagado, y el historial tiene que decir cuántas veces se pagó.
    // Los días que quedaban del anterior no se suman ni se descuentan
    // (`requirements/mv.md` §5.4, decisión 2); `closed_at` deja constancia.
    usuarios.assignMembership(ids.next(), usuario.getId(), membresia.id(), hasta, desde);

    auditar(usuario, anterior.orElse(null), membresia, hasta);

    return new GrantedMembership(
        membresia.id(), membresia.code(), membresia.name(), membresia.level(), desde, hasta);
  }

  /** El mismo asiento que `RF-SP-032`: el nivel y la fecha, antes y después. */
  private void auditar(
      User usuario,
      UserMembership anterior,
      MembershipCatalog.MembershipRef nueva,
      OffsetDateTime hasta) {
    Map<String, Object> cambios = new HashMap<>();
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
    // Por qué se concedió va en el asiento: es lo que distingue una compra de
    // una asignación de oficina cuando alguien reconstruya el historial.
    cambios.put("reason", "PURCHASE");

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            usuario.getId(),
            anterior == null ? ChangeAction.CREATE : ChangeAction.UPDATE,
            cambios));
  }
}
