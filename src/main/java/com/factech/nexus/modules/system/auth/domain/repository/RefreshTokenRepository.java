package com.factech.nexus.modules.system.auth.domain.repository;

import com.factech.nexus.modules.system.auth.domain.models.RefreshToken;
import com.factech.nexus.modules.system.auth.domain.models.RevokedReason;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Puerto de las sesiones renovables. */
public interface RefreshTokenRepository {

  RefreshToken save(RefreshToken token);

  /**
   * Localiza por hash <b>bloqueando la fila</b>.
   *
   * <p>El bloqueo es lo que hace atómica la detección de reutilización: dos refrescos simultáneos
   * con el mismo token se serializan, y el segundo encuentra la fila ya revocada por rotación — que
   * es exactamente la señal de robo. Sin él, los dos podrían rotar y la copia quedaría indetectada.
   */
  Optional<RefreshToken> findByHashForUpdate(String tokenHash);

  /** Revoca la familia entera, incluidas las filas ya revocadas por rotación. */
  int revokeFamily(UUID familyId, RevokedReason motivo, OffsetDateTime ahora);

  /** Revoca todas las sesiones vigentes de una persona. */
  int revokeAllActive(UUID userId, RevokedReason motivo, OffsetDateTime ahora);
}
