package com.factech.nexus.modules.system.auth.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La cuenta, vista desde el control de acceso.
 *
 * <p>Proyección y no agregado: lleva lo que hace falta para decidir si alguien puede entrar, y nada
 * de lo que se necesita para administrarla.
 *
 * <p><b>No proyecta {@code must_change_password}, y su ausencia es deliberada.</b> Desde el
 * 25-08-2026 quien decide si la credencial es ajena es {@code provisional_password_expires_at} y
 * solo él (ver {@link #credencialAjena()}). Dejar la columna aquí habría dejado dos valores que
 * dicen lo mismo y pueden discrepar, y el primero que los leyera al revés produciría un defecto
 * silencioso: la persona navegaría o quedaría retenida según qué campo mirase quien escribió el
 * código. Se retira del {@code SELECT} para que no haya nada que leer por error.
 */
public record AuthUser(
    UUID id,
    String passwordHash,
    String status,
    boolean deleted,
    int failedAttempts,
    OffsetDateTime lockedUntil,
    OffsetDateTime provisionalExpiresAt,
    List<String> roleCodes) {

  public boolean puedeEntrar() {
    return !deleted && "ACTIVO".equals(status);
  }

  /**
   * ¿La credencial la fijó <b>otra persona</b>? (`RF-SP-034`, `RF-SP-038`).
   *
   * <p>Es lo único que decide si la sesión sale marcada para cambio obligatorio, y por tanto si la
   * persona puede navegar o queda retenida en la pantalla de cambiar la contraseña. La regla es la
   * que pidió el responsable del proyecto el 25-08-2026: <b>nula, navega; con fecha, la cambia</b>.
   *
   * <p><b>La fecha no se compara con el reloj</b>, y esa ausencia es la decisión de ese día. Antes,
   * una caducidad vencida <b>dejaba de autenticar</b>: la credencial provisional moría y había que
   * restablecerla. Ahora vencida y por vencer valen igual —autentican y obligan a cambiar—, de modo
   * que <b>la credencial que fijó otra persona ya no expira</b>. Es una decisión con coste, tomada
   * a sabiendas: una contraseña anotada en un papel hace tres meses sigue abriendo la puerta
   * mientras nadie entre a cambiarla. El plazo de {@code nexus.security.password.provisional-ttl}
   * queda sin efecto sobre el acceso.
   */
  public boolean credencialAjena() {
    return provisionalExpiresAt != null;
  }

  public boolean bloqueadaPorIntentos(OffsetDateTime ahora) {
    return lockedUntil != null && lockedUntil.isAfter(ahora);
  }

  public boolean bloqueadaAMano() {
    return "BLOQUEADO".equals(status);
  }
}
