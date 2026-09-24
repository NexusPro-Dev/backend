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
  private static final String ENTIDAD = "user_products";

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
  public Optional<GrantedMembership> grant(GrantOrder orden) {
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
    // NULA CUANDO LO ENTREGADO NO CONCEDE NIVEL, y es el caso corriente desde el
    // 23-09-2026: un bot se posee y no sube a nadie (`RN-MV-036`). Lo que no se
    // admite es una membresía que se pide y no existe.
    MembershipCatalog.MembershipRef membresia =
        orden.membershipId() == null
            ? null
            : membresias
                .find(orden.membershipId())
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "No existe la membresía que se quiere conceder: "
                                + orden.membershipId()));

    OffsetDateTime desde = orden.at();
    OffsetDateTime hasta =
        orden.validityDays() == null ? null : desde.plusDays(orden.validityDays());

    // Se lee ANTES de escribir, y solo importa si esta concesión toca el nivel:
    // es el «antes» del asiento.
    Optional<UserMembership> anterior =
        membresia == null ? Optional.empty() : usuarios.findMembership(usuario.getId());

    // SIEMPRE cierra e inserta, también con la misma membresía: una compra es un
    // periodo nuevo pagado, y el historial tiene que decir cuántas veces se pagó.
    // Los días que quedaban del anterior no se suman ni se descuentan
    // (`requirements/mv.md` §5.4, decisión 2); `closed_at` deja constancia.
    //
    // QUIEN DECIDE SI SE CIERRA ALGO ES LA PROPIA CONCESIÓN, no este servicio:
    // `grantProduct` cierra la membresía abierta solo si la que entra concede
    // nivel, de modo que entregar un bot no le quita a nadie lo que tenía.
    usuarios.grantProduct(
        new UserRepository.ProductGrant(
            ids.next(),
            usuario.getId(),
            orden.productId(),
            membresia == null ? null : membresia.id(),
            orden.movementDetailId(),
            orden.validityDays(),
            desde,
            hasta));

    if (membresia == null) {
      auditarPosesion(usuario, orden, hasta);
      return Optional.empty();
    }

    auditar(usuario, anterior.orElse(null), membresia, hasta);

    return Optional.of(
        new GrantedMembership(
            membresia.id(), membresia.code(), membresia.name(), membresia.level(), desde, hasta));
  }

  /**
   * El asiento de lo que se posee <b>sin conceder nivel</b>.
   *
   * <p>Va aparte del de abajo y no es una rama de aquel: aquel describe un <b>cambio de nivel</b> —
   * con su antes y su después— y este describe que alguien <b>pasó a tener algo</b>, que no tiene
   * antes. Meterlos en el mismo asiento obligaría a escribir un «before» nulo que significaría dos
   * cosas distintas: «no tenía nivel» y «esto no es de nivel».
   */
  private void auditarPosesion(User usuario, GrantOrder orden, OffsetDateTime hasta) {
    Map<String, Object> cambios = new HashMap<>();
    cambios.put("before", null);
    Map<String, Object> despues = new HashMap<>();
    despues.put("product_id", String.valueOf(orden.productId()));
    despues.put("ends_at", String.valueOf(hasta));
    cambios.put("after", despues);
    cambios.put("reason", "PURCHASE");

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, usuario.getId(), ChangeAction.CREATE, cambios));
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
