package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.OwnProfileResponse;
import com.factech.nexus.modules.system.users.domain.repository.UserQueryRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import com.factech.nexus.shared.security.EffectivePermissions;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El perfil de quien pregunta (`RF-SP-039`).
 *
 * <p><b>No recibe ningún identificador</b>, y esa ausencia es la implementación de la regla: el
 * sujeto sale del contexto de seguridad. La ruta es un literal —{@code /me}— y no admite el propio
 * identificador como forma alternativa, porque esa es la consulta de detalle y exige permiso.
 *
 * <p><b>Los permisos salen del MISMO componente que autoriza</b>, igual que en el detalle: es lo
 * que garantiza que lo que esta pantalla dice que la persona puede hacer sea exactamente lo que el
 * sistema le dejará hacer. Con una consulta propia, el menú podría ofrecer una opción que el filtro
 * después rechaza — que es el defecto que este endpoint existe para eliminar.
 *
 * <p><b>Hay dos formas de {@code 401} y ninguna de {@code 404}.</b> Si el actor está autenticado su
 * perfil existe por definición; y si su cuenta fue eliminada después de emitirse el token, lo que
 * ha dejado de ser válido es <b>la sesión</b>, no la ruta. Devolver {@code 404} diría que el
 * recurso no existe cuando lo que no existe es el derecho a pedirlo.
 *
 * <p><b>{@code lastLoginAt} es un dato informativo de la sesión en curso, no una señal de
 * intrusión</b>: el inicio de sesión sobrescribe ese valor al entrar, de modo que lo que se
 * devuelve es cuándo empezó esta sesión y no la anterior. Conservar la anterior habría obligado a
 * reabrir aquel requerimiento.
 */
@Service
public class GetOwnProfileService {

  private final UserQueryRepository consultas;
  private final UserRepository usuarios;
  private final EffectivePermissions permisos;
  private final CurrentActor actor;

  public GetOwnProfileService(
      UserQueryRepository consultas,
      UserRepository usuarios,
      EffectivePermissions permisos,
      CurrentActor actor) {
    this.consultas = consultas;
    this.usuarios = usuarios;
    this.permisos = permisos;
    this.actor = actor;
  }

  @Transactional(readOnly = true)
  public OwnProfileResponse profile() {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));

    // La misma consulta que el detalle, sin escribir una paralela: dos lecturas
    // del mismo dato divergen, y la que se quede atrás mostraría un perfil que
    // no coincide con la ficha administrativa de la misma persona.
    UserQueryRepository.UserRow fila =
        consultas
            .findDetail(quien)
            .orElseThrow(
                () ->
                    new UnauthorizedException(
                        "AUTH-001", "La sesión ya no es válida: la cuenta fue eliminada."));

    List<UserQueryRepository.RoleRow> roles =
        consultas.rolesOf(List.of(quien)).getOrDefault(quien, List.of());

    Set<String> efectivos = permisos.forUser(quien).orElseGet(Set::of);

    return new OwnProfileResponse(
        fila.username(),
        fila.email(),
        fila.firstName(),
        fila.lastName(),
        fila.status(),
        roles.stream()
            .map(rol -> new OwnProfileResponse.RoleRef(rol.code(), rol.name(), rol.status()))
            .toList(),
        efectivos.stream().sorted().toList(),
        fila.tieneMembresia()
            ? new OwnProfileResponse.MembershipRef(
                fila.membershipCode(),
                fila.membershipLevel() == null ? 0 : fila.membershipLevel(),
                fila.membershipEndsAt())
            : null,
        fila.lastLoginAt(),
        usuarios
            .findActiveSupervisor(quien)
            .map(
                jefe ->
                    new OwnProfileResponse.SupervisorRef(
                        jefe.username(), jefe.firstName(), jefe.lastName(), jefe.roleCode()))
            .orElse(null),
        mustChangePassword(quien));
  }

  /**
   * La marca de cambio obligatorio <b>sí</b> se devuelve aquí, y no en el detalle.
   *
   * <p>La diferencia es a quién le sirve: al titular le dice que tiene que actuar; a un tercero con
   * permiso de lectura solo le diría que esa cuenta arrastra una contraseña que otra persona fijó.
   */
  private boolean mustChangePassword(UUID quien) {
    return usuarios
        .findNotDeletedById(quien)
        .map(usuario -> usuario.isMustChangePassword())
        .orElse(false);
  }
}
