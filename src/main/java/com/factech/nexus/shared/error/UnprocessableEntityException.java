package com.factech.nexus.shared.error;

import java.util.List;

/**
 * Referencia del cuerpo de la petición que no resuelve. La traduce el manejador global a {@code
 * 422}.
 *
 * <p>Se añadió a la jerarquía de {@code development-guide.md} §7.1 el 21-08-2026, al aprobarse el
 * plan de `RF-SP-001`, que es quien la estrena; la usan también `RF-SP-005` y `RF-SP-008`. Las dos
 * fronteras que la definen conviene tenerlas juntas, porque es donde se equivoca quien elige el
 * estado a ojo:
 *
 * <ul>
 *   <li><b>Frente a {@code 404}:</b> el {@code 404} se reserva para cuando el recurso <i>de la
 *       ruta</i> no existe. Cuando lo que no existe es una entidad <b>referenciada desde el
 *       cuerpo</b> —un rol padre, un permiso del catálogo—, la ruta sí existe y la petición es
 *       sintácticamente válida pero semánticamente irrealizable. Devolver {@code 404} en ese caso
 *       diría que el endpoint no está.
 *   <li><b>Frente a {@code 409}:</b> el {@code 409} es una regla de negocio violada sobre datos que
 *       <i>existen</i>; el {@code 422} es una referencia que no resuelve.
 * </ul>
 *
 * <p>En `RF-SP-001` la usan `EX-002` —rol padre inexistente, eliminado o inactivo— y `EX-005`
 * —permisos ausentes del catálogo—.
 */
public class UnprocessableEntityException extends DomainException {

  private static final long serialVersionUID = 1L;

  public UnprocessableEntityException(String errorCode, String message) {
    super(errorCode, message);
  }

  public UnprocessableEntityException(String errorCode, String message, List<FieldError> errors) {
    super(errorCode, message, errors);
  }
}
