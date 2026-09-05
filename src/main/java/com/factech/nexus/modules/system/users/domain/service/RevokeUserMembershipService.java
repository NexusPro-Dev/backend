package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.UserMembershipResponse;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.MembershipCatalog;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserMembership;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retirar la membresía de una persona (`RF-SP-033`).
 *
 * <p><b>`EX-001` rechaza a quien SÍ es consumidor</b>, que es lo contrario de lo que sugiere el
 * nombre del requerimiento y es el defecto más probable de todo él. La razón es `RN-SP-018`: no
 * existe el estado «consumidor sin nivel», de modo que esta operación solo sirve para <b>corregir
 * un estado incoherente</b> —alguien con membresía que ya no porta ningún rol de consumidor—, no
 * para quitarle el nivel a un consumidor en activo.
 *
 * <p>El {@code 409} cita <b>las dos</b> salidas reales, y `spec.md` `EX-001` lo exige: bajar de
 * nivel con `RF-SP-032`, o dejar de ser consumidor con `RF-SP-031`, que retira la membresía por su
 * cuenta. Sin esa explicación, quien recibe el error concluye que el sistema tiene un defecto.
 *
 * <p>`FA-001` —sin membresía previa— <b>no es un error</b>: {@code 204} sin escribir ni auditar. La
 * operación es idempotente y su resultado prometido ya se cumplía.
 */
@Service
public class RevokeUserMembershipService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "user_memberships";

  private final UserRepository usuarios;
  private final RoleCatalog roles;
  private final AuditWriter auditoria;
  private final MembershipCatalog membresias;
  private final UuidV7Generator ids;

  /**
   * Desde el 05-09-2026 esta operación <b>escribe una fecha</b>, y por eso necesita reloj: retirar
   * pasó de borrar la fila a <b>cerrarla</b> con {@code closed_at} (`V56`). Se inyecta, y no se
   * toma de {@code OffsetDateTime.now()}, para que una prueba pueda fijar el instante del cierre.
   */
  private final Clock reloj;

  @Autowired
  public RevokeUserMembershipService(
      UserRepository usuarios,
      RoleCatalog roles,
      AuditWriter auditoria,
      MembershipCatalog membresias,
      UuidV7Generator ids) {
    this(usuarios, roles, auditoria, membresias, ids, Clock.systemUTC());
  }

  RevokeUserMembershipService(
      UserRepository usuarios,
      RoleCatalog roles,
      AuditWriter auditoria,
      MembershipCatalog membresias,
      UuidV7Generator ids,
      Clock reloj) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.auditoria = auditoria;
    this.membresias = membresias;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public UserMembershipResponse resetToFloor(UUID userId) {
    User usuario =
        usuarios
            .findNotDeletedByIdForUpdate(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-002", "No existe una persona con ese identificador."));

    // `EX-001` SE RETIRÓ EL 05-09-2026, con `RN-SP-013`. Exigía que la persona
    // NO portara ningún rol de consumidor —lo contrario de lo que sugiere el
    // nombre del requerimiento— porque `RN-SP-018` no admitía consumidores sin
    // nivel. Reescrita esa regla, la precondición no protege nada: devolver a
    // alguien al suelo es válido lo porte o no.

    MembershipCatalog.MembershipRef suelo = membresias.floor();
    Optional<UserMembership> actual = usuarios.findMembership(userId);

    // `FA-001` —sin membresía previa— DEJÓ DE SER ALCANZABLE: toda persona tiene
    // nivel. El caso se conserva escrito por lo que costó decidirlo, pero aquí
    // no hay rama que lo trate: si no hubiera fila abierta, cerrar no afecta a
    // ninguna y la inserción de abajo repara el invariante, que es lo correcto.
    boolean yaEstabaEnElSuelo =
        actual.map(previa -> previa.membershipId().equals(suelo.id())).orElse(false);

    if (!yaEstabaEnElSuelo) {
      // CIERRA E INSERTA, la misma escritura con la que `RF-SP-032` sustituye.
      usuarios.assignMembership(ids.next(), userId, suelo.id(), null, OffsetDateTime.now(reloj));
      actual.ifPresent(previa -> auditar(usuario, previa));
    }

    // SIEMPRE DEVUELVE EL SUELO, haya escrito o no. Quien llama necesita saber
    // en qué quedó la persona, y repetir la operación tiene que decir lo mismo.
    return UserMembershipResponse.de(suelo.id(), suelo.code(), suelo.name(), suelo.level(), null);
  }

  /**
   * Eliminación de asociación, <b>sin motivo</b> y sin evento de seguridad.
   *
   * <p>Sin motivo por la excepción del Art. V.13 que `RN-SP-005` aplicó a las asociaciones, y el
   * endpoint no lo pide. Sin evento de seguridad porque la membresía no concede permisos.
   *
   * <p>El {@code snapshot} conserva <b>la membresía y su vigencia</b>: sin la fecha, el registro no
   * permite distinguir si se retiró una membresía viva o una ya vencida, que es justamente lo que
   * alguien querría saber al reconstruir el caso.
   */
  private void auditar(User usuario, UserMembership retirada) {
    Map<String, Object> foto = new HashMap<>();
    foto.put("membership_code", retirada.code());
    foto.put("level", retirada.level());
    foto.put("ends_at", String.valueOf(retirada.endsAt()));

    auditoria.recordDeletion(
        new DeletionEvent(MODULO, ENTIDAD, usuario.getId(), DeletionType.ASSOCIATION, null, foto));
  }
}
