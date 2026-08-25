package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.UserDetailResponse;
import com.factech.nexus.modules.system.users.domain.repository.UserQueryRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.security.EffectivePermissions;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detalle de una persona (`RF-SP-026`).
 *
 * <p><b>Los permisos efectivos salen del MISMO componente que autoriza.</b> No es una comodidad: es
 * lo que garantiza que la respuesta <b>no pueda contradecir a la autorización</b>. Si el detalle
 * dice que alguien tiene {@code users:delete}, el filtro que atienda su próxima petición dirá lo
 * mismo, porque ambos preguntan al mismo sitio. Con una consulta propia esa garantía no existe: dos
 * implementaciones de la misma regla que hoy coinciden pueden dejar de hacerlo con un cambio en
 * cualquiera de las dos, y la divergencia se manifiesta como una pantalla que afirma lo contrario
 * de lo que el sistema hace.
 *
 * <p>La contrapartida es que el detalle hereda la frescura de ese componente — y está acotada por
 * construcción: <b>el detalle no puede mentir más de lo que miente el sistema</b>. Una consulta
 * propia sería más fresca que la realidad, que suena mejor y es peor: mostraría un permiso ya
 * retirado como ausente mientras la autorización lo sigue admitiendo, y quien lo mirara concluiría
 * que el retiro surtió efecto.
 *
 * <p><b>El orden de las dos sentencias importa.</b> Primero la persona: si no existe o está
 * eliminada, se devuelve {@code 404} <b>sin leer sus roles</b> y sin preguntar por permiso alguno.
 *
 * <p>Una persona eliminada devuelve <b>el mismo</b> {@code 404} y el mismo mensaje que una
 * inexistente, sin ninguna pista de que existió. Reconstruir qué era corresponde a la auditoría de
 * eliminación, que tiene su propio permiso.
 */
@Service
public class GetUserService {

  private final UserQueryRepository consultas;
  private final EffectivePermissions permisos;

  public GetUserService(UserQueryRepository consultas, EffectivePermissions permisos) {
    this.consultas = consultas;
    this.permisos = permisos;
  }

  @Transactional(readOnly = true)
  public UserDetailResponse detail(UUID id) {
    UserQueryRepository.UserRow fila =
        consultas
            .findDetail(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una persona con ese identificador."));

    List<UserQueryRepository.RoleRow> roles =
        consultas.rolesOf(List.of(id)).getOrDefault(id, List.of());

    // Ordenados y sin duplicados: la respuesta tiene que ser estable entre
    // llamadas y comparable entre personas.
    Set<String> efectivos = permisos.forUser(id).orElseGet(Set::of);

    return new UserDetailResponse(
        fila.id(),
        fila.username(),
        fila.email(),
        fila.firstName(),
        fila.lastName(),
        fila.status(),
        roles.stream()
            .map(
                rol ->
                    new UserDetailResponse.RoleRef(rol.id(), rol.code(), rol.name(), rol.status()))
            .toList(),
        efectivos.stream().sorted().toList(),
        fila.tieneMembresia()
            ? new UserDetailResponse.MembershipRef(
                fila.membershipId(),
                fila.membershipCode(),
                fila.membershipName(),
                fila.membershipLevel() == null ? 0 : fila.membershipLevel(),
                fila.membershipEndsAt(),
                Boolean.TRUE.equals(fila.membershipCurrent()))
            : null,
        fila.lastLoginAt(),
        fila.lockedUntil(),
        fila.createdAt(),
        fila.updatedAt());
  }
}
