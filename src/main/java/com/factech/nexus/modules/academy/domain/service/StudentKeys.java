package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.domain.models.StudentAccess;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup.CurrentMembershipView;
import com.factech.nexus.modules.system.users.application.CurrentProductsLookup;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Las llaves que trae quien mira el aula: su membresía vigente y sus productos vigentes, pedidos a
 * los dos puertos de `SP` (`CurrentMembershipLookup`, `CurrentProductsLookup`).
 *
 * <p><b>El actor sale del token</b>, como en la oferta de `PM`: no hay por dónde preguntar por otra
 * persona. Las tres vistas del aula lo piden aquí para que ninguna decida «vigente» por su cuenta.
 */
@Component
public class StudentKeys {

  private final CurrentActor actor;
  private final CurrentMembershipLookup membresias;
  private final CurrentProductsLookup productos;

  public StudentKeys(
      CurrentActor actor, CurrentMembershipLookup membresias, CurrentProductsLookup productos) {
    this.actor = actor;
    this.membresias = membresias;
    this.productos = productos;
  }

  /** Lo que el alumno trae, resuelto una vez por petición. */
  public record Keys(Optional<CurrentMembershipView> membership, Set<UUID> products) {

    public boolean opens(Collection<UUID> membresiasDelCurso, Collection<UUID> serviciosDelCurso) {
      return StudentAccess.courseAccessible(
          membership.map(CurrentMembershipView::id).orElse(null),
          products,
          membresiasDelCurso,
          serviciosDelCurso);
    }
  }

  public Keys ofCurrentActor() {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
    return new Keys(membresias.currentMembershipOf(quien), productos.currentProductIdsOf(quien));
  }
}
