package com.factech.nexus.modules.system.roles.application;

import java.util.Set;
import java.util.UUID;

/**
 * Quién ejecuta el alta y hasta dónde llega (`RF-SP-001` · `T-15`).
 *
 * <p>Es un puerto declarado en {@code application} —la capa sin dependencias— para que el caso de
 * uso pueda probarse con un doble y sin levantar la seguridad. Solo usa tipos del JDK, que es lo
 * que {@code architecture.md} §5.2 exige de esta capa.
 *
 * <p>Existe porque `RN-SEG-010` <b>no</b> se resuelve con el permiso de acceso: {@code
 * roles:create} habilita a crear roles, no a decidir con qué alcance. El techo lo pone el conjunto
 * de permisos efectivos del actor, y eso hay que preguntárselo a alguien.
 */
public interface AuthenticatedActor {

  /** Identificador del actor, o {@code null} si la operación no tiene persona detrás. */
  UUID id();

  /** Permisos efectivos, por código. Nunca nulo; vacío significa que no puede otorgar nada. */
  Set<String> permissions();
}
