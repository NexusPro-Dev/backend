package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.repository.PermissionCatalog;
import com.factech.nexus.modules.system.roles.domain.repository.RoleRepository;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ForbiddenException;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Las tres puertas que cruzan <b>todas</b> las escrituras sobre un rol (`RF-SP-004` a `RF-SP-009`).
 *
 * <p>Existen en un solo sitio por el modo de fallo que evita: seis copias de la misma comprobación
 * divergen con el tiempo, y <b>la que se queda atrás no falla — concede</b>. Es el mismo criterio
 * con el que `RF-SP-030` centralizó `RN-SEG-010` en un único componente.
 *
 * <p>El orden en que se cruzan no es indiferente, y es el que las seis especificaciones declaran:
 *
 * <ol>
 *   <li><b>El rol existe y no está eliminado</b> → {@code 404}. Un rol eliminado y uno que nunca
 *       existió comparten respuesta: distinguirlos revelaría que ese identificador existió (Art.
 *       V.13).
 *   <li><b>No es de sistema</b> (`RN-SEG-012`) → {@code 409}. El catálogo sembrado por migración es
 *       la base de la contención de privilegios y no se toca por API.
 *   <li><b>El actor no lo tiene asignado</b> (`RN-SEG-011`) → {@code 403}. Impide que alguien se
 *       edite, se desactive o se borre su propio acceso — y que amplíe el alcance del rol con el
 *       que está entrando.
 * </ol>
 *
 * <p><b>Por qué el tercero es {@code 403} y no {@code 409}</b>: no es un dato inválido sino una
 * operación que ese actor no puede ejecutar sobre ese recurso. La diferencia se nota en la
 * auditoría — el manejador global lo registra como denegación de autorización con severidad alta en
 * {@code audit_security_log}, y no como error de operación.
 */
@Component
public class RoleWriteAccess {

  private final RoleRepository roles;
  private final PermissionCatalog catalogo;
  private final AuthenticatedActor actor;

  public RoleWriteAccess(
      RoleRepository roles, PermissionCatalog catalogo, AuthenticatedActor actor) {
    this.roles = roles;
    this.catalogo = catalogo;
    this.actor = actor;
  }

  /**
   * Carga el rol bloqueado y le aplica las tres puertas.
   *
   * @param codigoInexistente código de la excepción de rol inexistente, que cada especificación
   *     numera distinto — {@code EX-003} en el cambio de estado, {@code EX-004} en la edición,
   *     {@code EX-006} en el resto
   */
  public Role cargarModificable(UUID roleId, String codigoInexistente) {
    Role rol = cargarVigente(roleId, codigoInexistente);
    verificarNoEsDeSistema(rol);
    verificarNoEsDelActor(rol);
    return rol;
  }

  /** Solo la primera puerta: el rol existe, está vigente y queda bloqueado. */
  public Role cargarVigente(UUID roleId, String codigoInexistente) {
    return roles
        .findNotDeletedByIdForUpdate(roleId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    codigoInexistente, "No existe un rol con ese identificador."));
  }

  /** `RN-SEG-012` → {@code 409}. */
  public void verificarNoEsDeSistema(Role rol) {
    if (rol.isSystem()) {
      String mensaje = "Los roles de sistema no se modifican por la API.";
      throw new BusinessRuleException(
          "RN-SEG-012", mensaje, List.of(new FieldError("id", "RN-SEG-012", mensaje)));
    }
  }

  /**
   * `RN-SEG-011` → {@code 403}.
   *
   * <p>Alcanza <b>solo a los roles asignados directamente</b>: un ancestro del rol propio sí puede
   * tocarse (`CA-SP-152`), porque `RN-SEG-010` ya impide conceder permisos que no se poseen y
   * extender la regla añadiría un recorrido del árbol en cada escritura sin cerrar ningún hueco.
   */
  public void verificarNoEsDelActor(Role rol) {
    if (roles.isAssignedTo(rol.getId(), actor.id())) {
      throw new ForbiddenException(
          "RN-SEG-011", "No puede modificar un rol que usted tiene asignado.");
    }
  }

  /**
   * `RN-SEG-007` → {@code 409}. Prohíbe desactivar o eliminar el rol raíz.
   *
   * <p>Se comprueba <b>aparte de {@code isSystem}</b> y no por redundancia: hoy la raíz es de
   * sistema y la puerta anterior ya la detendría, pero un rol raíz inactivo no concede nada
   * (`RN-SEG-002`) y eso dejaría al sistema sin su última vía de administración. La prohibición no
   * debe depender de que alguien recuerde marcarlo como de sistema.
   */
  public void verificarNoEsLaRaiz(Role rol) {
    if (rol.isRoot()) {
      String mensaje = "El rol raíz no admite esta operación.";
      throw new BusinessRuleException(
          "RN-SEG-007", mensaje, List.of(new FieldError("id", "RN-SEG-007", mensaje)));
    }
  }

  /**
   * Arma la respuesta del rol resolviendo su padre y sus permisos.
   *
   * <p>Dos lecturas más, y se pagan a conciencia: la alternativa es que cada operación de escritura
   * devuelva una forma distinta del rol según lo que tuviera a mano, y el cliente acabe con seis
   * variantes del mismo recurso.
   */
  public RoleResponse respuesta(Role rol) {
    Role padre =
        rol.getParentRoleId() == null ? null : roles.findById(rol.getParentRoleId()).orElse(null);
    List<PermissionItem> permisos = catalogo.findAllById(rol.getPermissionIds());
    return RoleResponse.from(rol, padre, permisos);
  }

  /** El rol padre vigente, o {@code null} si el rol es la raíz. */
  public Role padreDe(Role rol) {
    return rol.getParentRoleId() == null
        ? null
        : roles.findById(rol.getParentRoleId()).orElse(null);
  }
}
