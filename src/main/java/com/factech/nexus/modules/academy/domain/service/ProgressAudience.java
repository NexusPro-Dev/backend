package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.system.users.application.CommercialReach;
import com.factech.nexus.modules.system.users.application.CommercialReach.Reach;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A quién alcanza quien consulta el progreso (`RN-AC-024`, `RF-AC-040` y `RF-AC-041` · plan §1).
 *
 * <p><b>El alcance comercial lo resuelve `SP` y lo traduce `AC`</b>, como {@code
 * SalesScopeResolver} en `IN`: {@code EVERYTHING} es todos; {@code NETWORK}, la red —con él dentro—
 * <b>y los clientes principales de alguien de la red</b>; {@code OWN}, él. <b>El instructor no está
 * aquí</b>: depende del curso de cada fila, y lo aplica quien pregunta por un curso o el SQL del
 * listado. Las dos condiciones se suman.
 */
@Component
public class ProgressAudience {

  private final CommercialReach alcance;

  public ProgressAudience(CommercialReach alcance) {
    this.alcance = alcance;
  }

  /** Lo que alcanza un actor por su estructura comercial. */
  public record Audience(UUID actorId, boolean everyone, Set<UUID> people) {

    public Audience {
      people = Set.copyOf(people);
    }

    /** Si alcanza a ese alumno, o si lo alcanza por ser el instructor del curso. */
    public boolean reaches(UUID studentId, UUID instructorOfCourse) {
      return everyone || people.contains(studentId) || actorId.equals(instructorOfCourse);
    }
  }

  public Audience of(UUID actorId) {
    Reach hastaDonde = alcance.reachOf(actorId);
    return switch (hastaDonde.kind()) {
      case EVERYTHING -> new Audience(actorId, true, Set.of());
      case NETWORK -> {
        Set<UUID> personas = new LinkedHashSet<>(hastaDonde.sellers());
        personas.addAll(alcance.principalClientsOf(hastaDonde.sellers()));
        yield new Audience(actorId, false, personas);
      }
      case OWN -> new Audience(actorId, false, Set.of(actorId));
    };
  }
}
