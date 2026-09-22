package com.factech.nexus.modules.system.users.domain.models;

import java.util.UUID;

/**
 * La identidad documental de una persona: tipo y número (`RN-SP-035`).
 *
 * <p><b>Existe para que los dos campos no puedan viajar sueltos.</b> `ck_users_document_pair`
 * prohíbe en el motor que uno esté sin el otro —un número sin decir de qué documento es, o un tipo
 * sin número, no significan nada—, y pasar dos argumentos separados por el dominio dejaría esa
 * regla a merced de cada quien la invoque.
 *
 * <p><b>El número se normaliza al construirse</b> —recortado y en mayúsculas—, igual que {@link
 * Email}: la forma normalizada <b>es</b> el valor. Sin eso, `abc123` y `ABC123` serían dos personas
 * distintas y {@code uq_users_document} dejaría de significar lo que dice.
 *
 * <p><b>No valida el formato del documento</b> más allá de rechazar el blanco. Los formatos reales
 * dependen del país y del tipo, y una validación a medias rechazaría documentos legítimos; la
 * comprobación de forma mínima vive en {@code ck_users_document_number_format} y en el DTO.
 */
public record DocumentIdentity(UUID typeId, String number) {

  public DocumentIdentity {
    number = number == null ? null : number.trim().toUpperCase();
    if (number != null && number.isEmpty()) {
      number = null;
    }
    if ((typeId == null) != (number == null)) {
      throw new IllegalArgumentException(
          "RN-SP-035: el tipo y el número de documento van juntos o no van.");
    }
  }

  /** La ausente: ninguna de las dos mitades. Es el estado de las personas anteriores a `V71`. */
  public static DocumentIdentity ausente() {
    return new DocumentIdentity(null, null);
  }

  /** ¿Tiene las dos mitades? El constructor garantiza que no hay medias tintas. */
  public boolean completa() {
    return typeId != null;
  }
}
