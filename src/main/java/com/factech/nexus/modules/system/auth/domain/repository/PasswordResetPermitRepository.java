package com.factech.nexus.modules.system.auth.domain.repository;

import com.factech.nexus.modules.system.auth.domain.models.PasswordResetPermit;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Puerto sobre los permisos de recuperación (`RF-SP-040`). */
public interface PasswordResetPermitRepository {

  /**
   * Marca como sustituido el permiso vivo de esa persona, si lo hay.
   *
   * <p>Va <b>antes</b> de emitir el nuevo y en la misma transacción: el índice único parcial {@code
   * uq_password_reset_permits_vigente} solo admite uno vivo, de modo que sin esto la segunda
   * solicitud choca. Que el esquema lo imponga es deliberado — escrito solo aquí, dos solicitudes
   * concurrentes dejarían <b>dos vías de entrada abiertas</b> a la misma cuenta.
   *
   * @return cuántos se sustituyeron; cero es normal
   */
  int sustituirVigente(UUID userId, OffsetDateTime ahora);

  void guardar(PasswordResetPermit permiso);

  /**
   * Localiza por hash <b>con bloqueo de fila</b>.
   *
   * <p>El bloqueo no es opcional y es lo que decide el caso límite más importante del
   * requerimiento: dos confirmaciones concurrentes con el mismo permiso. Sin él, las dos lo leen
   * vigente y las dos sustituyen la credencial — la segunda pisando a la primera, con una
   * contraseña que quien la eligió no sabe que quedó descartada.
   */
  Optional<PasswordResetPermit> buscarPorHashParaActualizar(String permitHash);
}
