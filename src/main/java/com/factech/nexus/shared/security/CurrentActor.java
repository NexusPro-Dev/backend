package com.factech.nexus.shared.security;

import com.factech.nexus.shared.audit.AuditActorProvider;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Publica hacia el resto del sistema quién está autenticado y qué puede hacer (`RF-SP-001` ·
 * `T-09`).
 *
 * <p>Es la primera vez que se necesita: hasta ahora la seguridad solo decidía <b>acceso</b>, y aquí
 * empieza a alimentar una <b>regla de negocio</b>. `RN-SEG-010` —nadie otorga permisos que no
 * posee— no se resuelve con el permiso de acceso: {@code roles:create} habilita a crear roles, no a
 * decidir con qué alcance. El techo lo pone el conjunto de permisos efectivos del actor.
 *
 * <p>Implementa además {@link AuditActorProvider}, de modo que el {@code actor_id} del núcleo común
 * y el actor que evalúa `RN-SEG-010` salen del <b>mismo</b> sitio. Si salieran de dos, la auditoría
 * podría atribuir a una identidad una decisión que tomó otra.
 *
 * <p><b>Sin identidad probada devuelve vacío</b>, no falla: migraciones y tareas programadas operan
 * sin actor y su fila de auditoría lleva {@code actor_id} en nulo (`architecture.md` §6.6.1).
 */
@Component
public class CurrentActor implements AuditActorProvider {

  /**
   * Identificador del actor autenticado.
   *
   * <p>Se toma del nombre del {@code Authentication}, donde `RF-SP-034` depositará el sujeto del
   * token. Un nombre que no sea un UUID —el {@code anonymousUser} de Spring, o un usuario de prueba
   * con nombre simbólico— se trata como ausencia de actor y no como error: quien no tiene identidad
   * en la base de datos no puede figurar como actor de un evento.
   */
  @Override
  public Optional<UUID> currentActorId() {
    return autenticacion().map(Authentication::getName).flatMap(CurrentActor::comoUuid);
  }

  /**
   * Permisos efectivos del actor.
   *
   * <p><b>Limitación conocida y declarada.</b> `plan.md` §5 exige leerlos <b>de la base de
   * datos</b> y no de la caché de `security.md` §4.5, con un argumento correcto: aquí se decide un
   * techo de privilegios, y una entrada obsoleta se traduciría en una concesión que el actor ya no
   * tenía derecho a hacer. Ese camino —{@code users} → {@code user_roles} → {@code roles} → {@code
   * role_permissions}— <b>no existe todavía</b>: {@code users} y {@code user_roles} los crea
   * `RF-SP-024`, que va después en el orden de `requirements/sp.md` §6.1.
   *
   * <p>Mientras tanto se leen del {@code Authentication}, que es donde `RF-SP-034` los depositará
   * al emitir el token. Queda anotado como bloqueo en `tasks.md` §4: cuando `RF-SP-024` integre sus
   * tablas, este método debe pasar a resolverlos contra la base de datos, y `RF-SP-001` no está
   * terminado hasta entonces.
   */
  public Set<String> currentPermissions() {
    return autenticacion()
        .map(
            auth ->
                auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toUnmodifiableSet()))
        .orElseGet(Set::of);
  }

  private static Optional<Authentication> autenticacion() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return Optional.empty();
    }
    return Optional.of(auth);
  }

  private static Optional<UUID> comoUuid(String valor) {
    try {
      return Optional.of(UUID.fromString(valor));
    } catch (IllegalArgumentException noEsUuid) {
      return Optional.empty();
    }
  }
}
