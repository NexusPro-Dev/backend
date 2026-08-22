package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link AuthenticatedActor} sobre {@code shared/security} (`RF-SP-001` · `T-09`).
 *
 * <p><b>Por qué vive en {@code domain/repository}.</b> {@code architecture.md} §5.1 destina ese
 * paquete a «puertos de persistencia y sus adaptadores», y este no persiste nada. Se sitúa aquí
 * igualmente porque es la única capa del módulo que la disposición admite para un adaptador: {@code
 * application} no puede depender de nada, {@code domain/models} no puede conocer Spring y {@code
 * interfaces} es de controladores. La alternativa —que el caso de uso dependiera directamente de
 * {@code CurrentActor}— le quitaría al servicio la posibilidad de probarse con un doble, que es
 * justo lo que el puerto existe para dar.
 */
@Component
public class SecurityContextActor implements AuthenticatedActor {

  private final CurrentActor actor;

  public SecurityContextActor(CurrentActor actor) {
    this.actor = actor;
  }

  @Override
  public UUID id() {
    return actor.currentActorId().orElse(null);
  }

  @Override
  public Set<String> permissions() {
    return actor.currentPermissions();
  }
}
