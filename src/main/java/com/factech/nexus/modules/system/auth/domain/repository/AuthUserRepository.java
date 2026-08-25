package com.factech.nexus.modules.system.auth.domain.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Puerto de lectura y control de acceso sobre las cuentas. */
public interface AuthUserRepository {

  /**
   * Localiza por nombre de usuario <b>o</b> correo, con una sola sentencia.
   *
   * <p>El cliente no declara con cuál se presenta y no hace falta: la prohibición del {@code @} en
   * el nombre de usuario (`RF-SP-024`) garantiza que ningún valor sea ambiguo, de modo que a lo
   * sumo una de las dos columnas resuelve.
   *
   * <p>El nombre de usuario se compara <b>sin distinguir mayúsculas</b>, que es la obligación que
   * {@code uq_users_username} impone: sin ella, quien se registró como {@code JPerez} no podría
   * entrar escribiendo {@code jperez}.
   */
  Optional<AuthUser> findByIdentifier(String identificador);

  Optional<AuthUser> findById(UUID id);

  /**
   * Anota un intento fallido y bloquea si toca.
   *
   * @param bloquearHasta instante hasta el que queda bloqueada, o {@code null} si aún no toca
   */
  void registrarFallo(UUID userId, int intentos, OffsetDateTime bloquearHasta);

  /** Limpia los intentos, levanta el bloqueo automático y anota la entrada. */
  void registrarEntrada(UUID userId, OffsetDateTime ahora);

  /**
   * Sustituye la credencial del titular (`RF-SP-037`).
   *
   * <p>Limpia <b>a la vez</b> la marca de cambio obligatorio, la caducidad provisional y el
   * contador de intentos: los tres describen el mismo hecho —«esta contraseña no es suya»— y dejar
   * uno sin limpiar produce un estado que ninguna regla contempla. El { CHECK} del esquema
   * rechazaría además una caducidad sin la marca.
   */
  void cambiarContrasena(UUID userId, String passwordHash, OffsetDateTime ahora);
}
