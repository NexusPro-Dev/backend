package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserMembership;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.security.ConsumerStatus;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

  public RevokeUserMembershipService(
      UserRepository usuarios, RoleCatalog roles, AuditWriter auditoria) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.auditoria = auditoria;
  }

  @Transactional
  public void revoke(UUID userId) {
    User usuario =
        usuarios
            .findNotDeletedById(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-002", "No existe una persona con ese identificador."));

    if (ConsumerStatus.esConsumidor(roles.findAllById(roles.roleIdsOf(userId)))) {
      String mensaje =
          "La persona porta un rol de consumidor y todo consumidor debe tener membresía. Para"
              + " bajarla de nivel use la operación de membresía; para que deje de ser consumidor,"
              + " retírele el rol — el retiro arrastra la membresía por su cuenta.";
      throw new BusinessRuleException(
          "RN-SP-018", mensaje, List.of(new FieldError("membership", "RN-SP-018", mensaje)));
    }

    Optional<UserMembership> actual = usuarios.findMembership(userId);
    if (actual.isEmpty()) {
      // `FA-001`. No se escribe y no se audita: un evento de eliminación que no
      // eliminó nada es un dato falso en el registro.
      return;
    }

    usuarios.removeMembership(userId);
    auditar(usuario, actual.get());
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
