package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.roles.application.ChangeRoleParentRequest;
import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.repository.RoleRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reubicar un rol bajo otro rol padre (`RF-SP-008`).
 *
 * <p>Es la escritura con más invariantes en juego, y el orden en que se comprueban decide qué error
 * recibe una petición que incumple varias cosas:
 *
 * <ol>
 *   <li>El rol vigente, no de sistema y no del actor — las tres puertas comunes.
 *   <li>No es el rol raíz (`EX-003` → {@code 409}): darle padre crearía una jerarquía sin cima.
 *   <li>El nuevo padre existe y está <b>activo</b> (`EX-004` → {@code 422}).
 *   <li>El nuevo padre no es el propio rol ni uno de sus descendientes (`EX-002` → {@code 409}).
 *   <li>Los permisos del rol caben en el nuevo padre (`EX-001` → {@code 409}), enumerando los que
 *       sobran. Esa comprobación vive en el agregado, porque es una regla y no orquestación.
 * </ol>
 *
 * <p><b>El ciclo se comprueba antes que la contención</b> y no al revés: una jerarquía con ciclo
 * corrompe la estructura entera —el recorrido de la descendencia deja de terminar— mientras que un
 * exceso de permisos solo afecta a ese rol. Ante las dos faltas a la vez, la que conviene reportar
 * es la que rompe más.
 *
 * <p><b>Los hijos acompañan al rol y no se revisan</b> (`CA-SP-061`): si este cabe en el nuevo
 * padre, ellos caben en este por transitividad. Es la misma aritmética por la que `RN-SEG-004`
 * prohíbe recorrer la cadena de ancestros.
 *
 * <p><b>Nada se retira al reubicar</b> (`CA-SP-162`): si el rol excede al nuevo padre, la operación
 * se rechaza entera y el actor decide qué permisos quitar con `RF-SP-006`. Recortarlos aquí dejaría
 * al rol concediendo menos de lo que su titular cree.
 */
@Service
public class ChangeRoleParentService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "roles";

  private final RoleRepository roles;
  private final RoleWriteAccess acceso;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public ChangeRoleParentService(
      RoleRepository roles, RoleWriteAccess acceso, AuditWriter auditoria) {
    this(roles, acceso, auditoria, Clock.systemUTC());
  }

  ChangeRoleParentService(
      RoleRepository roles, RoleWriteAccess acceso, AuditWriter auditoria, Clock reloj) {
    this.roles = roles;
    this.acceso = acceso;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public RoleResponse change(UUID roleId, ChangeRoleParentRequest peticion) {
    Role rol = acceso.cargarModificable(roleId, "EX-006");
    acceso.verificarNoEsLaRaiz(rol);

    UUID anterior = rol.getParentRoleId();
    Role nuevoPadre = resolverPadre(peticion.parentRoleId());
    verificarSinCiclo(rol, nuevoPadre);

    // La contención contra el nuevo padre la decide el agregado (`RN-SEG-013`).
    if (!rol.changeParent(nuevoPadre, OffsetDateTime.now(reloj))) {
      // `FA-001`: el mismo padre que ya tenía no es un cambio ni un error.
      return acceso.respuesta(rol);
    }

    Map<String, Object> cambio = new HashMap<>();
    cambio.put("before", anterior == null ? null : anterior.toString());
    cambio.put("after", nuevoPadre.getId().toString());

    auditoria.recordChange(
        new ChangeEvent(
            MODULO, ENTIDAD, rol.getId(), ChangeAction.UPDATE, Map.of("parent_role_id", cambio)));

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.ROLE_UPDATED,
            Severity.ALTA,
            Outcome.SUCCESS,
            null,
            Map.of(
                "roleId", rol.getId().toString(),
                "roleCode", rol.getCode().value(),
                "parentRole", nuevoPadre.getCode().value())));

    return acceso.respuesta(rol);
  }

  /**
   * `EX-004` → {@code 422}.
   *
   * <p>{@code 422} y no {@code 404} porque el recurso de la ruta es el rol que se mueve, que sí
   * existe: lo que no resuelve es una referencia del cuerpo. Es el mismo criterio con el que el
   * alta trata a su rol padre.
   *
   * <p>Ausente, eliminado e inactivo comparten respuesta: distinguirlos le diría a quien pregunta
   * qué roles existen y en qué estado están.
   */
  private Role resolverPadre(UUID parentRoleId) {
    return roles
        .findById(parentRoleId)
        .filter(Role::isUsableAsParent)
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "EX-004",
                    "El rol padre indicado no es válido.",
                    List.of(
                        new FieldError(
                            "parentRoleId", "EX-004", "El rol padre indicado no es válido."))));
  }

  /**
   * `EX-002` → {@code 409} (`RN-SEG-006`).
   *
   * <p>Cubre los dos casos con una sola pregunta: colgar el rol de sí mismo y colgarlo de uno de
   * sus descendientes producen el mismo ciclo. El recorrido va con profundidad acotada, para que
   * una jerarquía ya corrupta produzca un rechazo y no una petición colgada.
   */
  private void verificarSinCiclo(Role rol, Role nuevoPadre) {
    if (roles.isSelfOrDescendant(nuevoPadre.getId(), rol.getId())) {
      String mensaje = "El cambio formaría un ciclo en la jerarquía.";
      throw new BusinessRuleException(
          "RN-SEG-006", mensaje, List.of(new FieldError("parentRoleId", "RN-SEG-006", mensaje)));
    }
  }
}
