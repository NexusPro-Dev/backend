package com.factech.nexus.shared.error;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

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
 *
 * <p><b>El momento de expiración va en un campo, no solo en el mensaje</b> (`EX-002`, `CA-SP-378`).
 * Es lo que permite al cliente pintar una cuenta atrás y deshabilitar el envío hasta que termine,
 * en lugar de pedirle a la persona que vuelva «más tarde» sin decirle cuándo. El bloqueo
 * <b>manual</b> se construye sin él: esa cuenta no se desbloquea sola, y enviar un instante
 * cualquiera sería mentir.
 */
public class BlockedAccountException extends DomainException {

  private static final long serialVersionUID = 1L;

  public BlockedAccountException(String message) {
    super("VAL-004", message);
  }

  /**
   * Con el momento de expiración del bloqueo automático.
   *
   * @param desbloqueoEn instante en que el bloqueo se levanta solo
   * @param segundosDeEspera lo que falta hasta entonces, en segundos
   */
  public BlockedAccountException(
      String message, OffsetDateTime desbloqueoEn, long segundosDeEspera) {
    super(
        "VAL-004",
        message,
        List.of(),
        // Los dos, y no uno: el instante sirve para decir «hasta las 14:32» y
        // sobrevive a que la respuesta se cachee; los segundos sirven para la
        // cuenta atrás y NO dependen de que el reloj del navegador coincida con
        // el del servidor. Con uno solo, uno de los dos usos sale mal.
        Map.of("unlockAt", desbloqueoEn, "retryAfterSeconds", segundosDeEspera));
  }
}
