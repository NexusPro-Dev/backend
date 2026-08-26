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

  /**
   * Sustituye la credencial de quien la recuperó por su cuenta (`RF-SP-040`).
   *
   * <p><b>No es {@link #cambiarContrasena} con otro nombre</b>, y la diferencia importa: aquel
   * <b>levanta el bloqueo automático</b> —tiene sentido, porque quien acierta su contraseña actual
   * demuestra ser el titular ante el sistema—, y este <b>no debe hacerlo</b> (`CA-SP-464`).
   *
   * <p>Recuperar la contraseña prueba que se tiene acceso al <b>correo</b>, no que alguien haya
   * decidido devolver el acceso a la cuenta. Devolverlo es `RF-SP-028` y exige un actor con
   * permiso. Si esta operación levantara el bloqueo, quien tuviera el correo de una cuenta
   * bloqueada a mano podría desbloquearla sola — y el bloqueo manual, que por diseño no expira,
   * dejaría de significar nada.
   *
   * <p>Sí limpia el <b>contador</b> de fallos consecutivos: cuenta intentos contra una contraseña
   * que ya no existe. Y limpia la marca de cambio obligatorio con su caducidad, porque quien
   * recibió una credencial provisional y la olvidó antes de usarla llega por aquí, y su cuenta debe
   * quedar tan libre como si hubiera ejecutado `RF-SP-037`.
   */
  void recuperarContrasena(UUID userId, String passwordHash, OffsetDateTime ahora);

  /**
   * El correo al que se le escribe a esa persona (`RF-SP-040`).
   *
   * <p><b>Va aparte de {@link AuthUser} a propósito.</b> Esa proyección es la del control de acceso
   * y no lleva ningún dato personal: se lee en cada inicio de sesión y en cada refresco, y añadirle
   * el correo lo haría viajar por todos esos caminos para servir a una operación que ocurre unas
   * pocas veces al día. Se pide solo cuando hay algo que enviar.
   */
  Optional<String> correoDe(UUID userId);
}
