package com.factech.nexus.shared.notification;

/**
 * Puerto de envío saliente (`architecture.md` §15.1).
 *
 * <p><b>Es infraestructura transversal y no de `SP`.</b> `RF-SP-040` es el primero que lo necesita,
 * pero no es suyo: lo consumirán la verificación de correo de `RF-SP-027`, el aviso de `RF-SP-038`,
 * y después academia y comisiones. Hacerlo un submódulo de `SP` habría puesto a `SP` a mandar los
 * correos de todo el mundo; hacerlo un módulo propio habría fijado un código inmutable sobre un
 * alcance que `modules.md` §6 dice expresamente que todavía no puede fijarse.
 *
 * <p><b>El envío no participa de ninguna transacción ni de la respuesta</b>, y en `RF-SP-040` eso
 * es una decisión de <b>seguridad</b> y no de rendimiento: si la respuesta esperase al envío, el
 * tiempo delataría si la identidad existe y la defensa central del requerimiento caería. Quien
 * llama a este puerto lo hace <b>después del commit</b> y sin mirar el resultado.
 *
 * <p><b>Por eso no devuelve nada y no lanza.</b> Un fallo de envío no puede alterar la respuesta ni
 * revertir lo que ya se confirmó: el permiso está emitido y la base lo sabe. Lo que sí hace la
 * implementación es <b>dejar constancia</b>, porque un envío que no ocurre y no se registra es
 * indistinguible de uno que sí ocurrió.
 */
public interface NotificationSender {

  /**
   * Envía, o lo intenta.
   *
   * <p>No lanza. Ver la nota de arriba: quien lo llama ya respondió.
   */
  void send(Notification notificacion);
}
