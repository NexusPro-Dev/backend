package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Lo que una persona pide cambiar de sí misma (`RF-SP-044`).
 *
 * <p><b>No lleva identificador, y esa ausencia es la mitad de la seguridad de este
 * requerimiento.</b> El sujeto es quien porta el token: sin campo que manipular, no hay forma de
 * desviar la operación hacia otra persona, y `CA-SP-495` queda garantizado por construcción en
 * lugar de por una comprobación que alguien pueda olvidar. La configuración de Jackson rechaza los
 * campos desconocidos, de modo que enviar uno produce un {@code 400} y no un silencio.
 *
 * <p><b>Los tres campos modificables son {@link Patchable} y {@code currentPassword} no.</b>
 * Aquellos necesitan distinguir «ausente» de «puesto a nulo» —es un {@code PATCH}—; esta no: o
 * viene y se comprueba, o no viene y se exige cuando toca.
 *
 * @param firstName nuevo nombre
 * @param lastName nuevos apellidos
 * @param email nuevo correo. Si viene, {@code currentPassword} pasa a ser obligatorio
 * @param currentPassword la contraseña vigente. <b>Obligatoria si y solo si viene {@code
 *     email}</b>: el correo es la vía de recuperación de `RF-SP-040`, de modo que cambiarlo es
 *     cambiar quién puede recuperar la cuenta, y una sesión robada no lleva la contraseña
 */
public record UpdateOwnProfileRequest(
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> firstName,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> lastName,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> email,
    String currentPassword) {

  public UpdateOwnProfileRequest {
    firstName = firstName == null ? Patchable.ausente() : firstName;
    lastName = lastName == null ? Patchable.ausente() : lastName;
    email = email == null ? Patchable.ausente() : email;
  }

  /** ¿Pide cambiar algo? La contraseña no cuenta: acompaña al cambio, no es uno. */
  public boolean informaAlgo() {
    return firstName.presente() || lastName.presente() || email.presente();
  }

  /** ¿Toca el correo? Es lo que decide si la contraseña se exige. */
  public boolean tocaElCorreo() {
    return email.presente();
  }
}
