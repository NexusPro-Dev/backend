package com.factech.nexus.modules.system.brokers.domain.models;

import java.util.Arrays;
import java.util.Optional;

/**
 * En qué punto está una cuenta de broker (`RN-SP-045`, {@code ck_user_brokers_status}).
 *
 * <p><b>Los dos valores van en inglés, y es el único enumerado del sistema que lo hace</b>: {@code
 * UserStatus} es {@code ACTIVO | INACTIVO | BLOQUEADO | FTD_PENDIENTE}, y {@code ProductStatus} y
 * {@code MovementStatus} van igual. No es un descuido — son <b>el vocabulario del broker</b>, y
 * quien va a escribir esta columna es la integración con él (`RF-SP-054`). Traducirlos obligaría a
 * mantener un diccionario entre lo que llega por el webhook y lo que se guarda, y ese diccionario
 * es exactamente donde se pierden los estados que nadie previó.
 *
 * <p><b>Hoy nadie mueve una cuenta a {@link #FIRST_DEPOSIT}</b>, y conviene saberlo antes de leer
 * una consulta y creerla rota: quien la mueve es el webhook de `RF-SP-054`, que no está construido.
 * Mientras no exista, <b>ninguna cuenta tiene depósito confirmado</b> y {@link #REGISTER} no es un
 * relleno: es la verdad.
 *
 * <p><b>No es el estado de la persona.</b> {@code users.status} dice si la cuenta del sistema opera
 * y este dice qué ha pasado en el broker. Una persona con dos cuentas puede tener una depositada y
 * otra no, de modo que <b>uno no se deriva del otro</b> — y decidir qué significa ese caso es de
 * `RF-SP-054`, no de aquí.
 */
public enum UserBrokerStatus {

  /** Declarada, sin depósito confirmado por el broker. Es el estado en el que nace. */
  REGISTER,

  /** El primer depósito está confirmado. Lo pondrá el webhook de `RF-SP-054`. */
  FIRST_DEPOSIT;

  /**
   * El estado que designa ese texto, o vacío si no designa ninguno.
   *
   * <p><b>Vacío y no una excepción</b>: quien lo llama decide qué significa el fallo. Para el
   * filtro de `RF-SP-056` significa `400` —una pregunta mal escrita, no una respuesta vacía—, y
   * para un valor leído de la base significaría un dato corrupto, que es otra cosa.
   *
   * <p><b>Sin normalizar la caja</b>, a propósito: el `CHECK` del motor solo admite mayúsculas, de
   * modo que aceptar {@code register} aquí crearía una forma de escribirlo que la base rechazaría
   * al filtrar por ella.
   */
  public static Optional<UserBrokerStatus> de(String valor) {
    if (valor == null || valor.isBlank()) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(estado -> estado.name().equals(valor.trim())).findFirst();
  }
}
