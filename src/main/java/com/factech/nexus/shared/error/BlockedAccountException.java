package com.factech.nexus.shared.error;

/**
 * Cuenta bloqueada. La traduce el manejador global a {@code 423 Locked}.
 *
 * <p><b>{@code 423} y no {@code 401}</b>, y es una decisión con su motivo: `RF-SP-034` exige una
 * respuesta <b>distinta y distinguible</b> para la cuenta bloqueada, y hacerlo solo en el mensaje
 * dejaría al cliente decidiendo por el texto. RFC 4918 define {@code 423} como «el recurso existe y
 * está bloqueado», que es exactamente lo que aquí se comunica a conciencia.
 *
 * <p><b>Sí, revela que la cuenta existe</b>, y es la excepción consciente al mensaje genérico de
 * `security.md` §3.2: quien provocó un bloqueo por fuerza bruta ya lo sabe —fue él quien lo
 * provocó—, de modo que callarlo solo perjudica al titular legítimo, que se queda sin entender por
 * qué no entra.
 */
public class BlockedAccountException extends DomainException {

  private static final long serialVersionUID = 1L;

  public BlockedAccountException(String message) {
    super("VAL-004", message);
  }
}
