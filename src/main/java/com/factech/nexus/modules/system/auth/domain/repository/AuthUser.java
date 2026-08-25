package com.factech.nexus.modules.system.auth.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La cuenta, vista desde el control de acceso.
 *
 * <p>Proyección y no agregado: lleva lo que hace falta para decidir si alguien puede entrar, y nada
 * de lo que se necesita para administrarla.
 */
public record AuthUser(
    UUID id,
    String passwordHash,
    String status,
    boolean deleted,
    boolean mustChangePassword,
    int failedAttempts,
    OffsetDateTime lockedUntil,
    OffsetDateTime provisionalExpiresAt,
    List<String> roleCodes) {

  public boolean puedeEntrar() {
    return !deleted && "ACTIVO".equals(status);
  }

  /**
   * ¿Caducó la credencial que <b>otra persona</b> le fijó? (`RF-SP-038`, `security.md` §3.2).
   *
   * <p>Solo aplica a la credencial provisional: la que el titular se pone él mismo no caduca. Sin
   * esta comprobación, una cuenta restablecida y nunca usada conserva indefinidamente una
   * contraseña que otra persona conoce, <b>y nadie se entera porque no falla nada</b>.
   */
  public boolean credencialProvisionalCaducada(OffsetDateTime ahora) {
    return provisionalExpiresAt != null && !provisionalExpiresAt.isAfter(ahora);
  }

  public boolean bloqueadaPorIntentos(OffsetDateTime ahora) {
    return lockedUntil != null && lockedUntil.isAfter(ahora);
  }

  public boolean bloqueadaAMano() {
    return "BLOQUEADO".equals(status);
  }
}
