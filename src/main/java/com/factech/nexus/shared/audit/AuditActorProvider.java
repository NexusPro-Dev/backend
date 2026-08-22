package com.factech.nexus.shared.audit;

import java.util.Optional;
import java.util.UUID;

/**
 * Quién está ejecutando la operación, para el {@code actor_id} del núcleo común.
 *
 * <p>Es un puerto y no una lectura directa del contexto de Spring Security porque {@code
 * shared/audit} debe poder escribirse desde una tarea programada o una migración, donde no hay
 * identidad probada. La implementación vive en {@code shared/security}.
 *
 * <p>{@code Optional.empty()} significa <b>anónimo o proceso del sistema</b>, y es un valor
 * legítimo: {@code architecture.md} §6.6.1 declara la columna nulable justo para eso.
 */
public interface AuditActorProvider {

  /** Identificador del actor autenticado, o vacío si la operación no tiene persona detrás. */
  Optional<UUID> currentActorId();
}
