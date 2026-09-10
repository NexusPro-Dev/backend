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
 *     <p><b>El documento y el país NO están en este cuerpo</b> (`RN-SP-035`, `RN-SP-037`): son
 *     identidad y no contacto, y los corrige un administrador por `RF-SP-027`. Enviarlos devuelve
 *     {@code 400} por propiedad desconocida, no se ignoran — el mismo trato que el nombre de
 *     usuario.
 * @param phone nuevo teléfono (`RN-SP-037`, 08-09-2026). <b>No exige contraseña actual</b>, al
 *     contrario que el correo: la contraseña se pide cuando el campo <b>es una vía de acceso</b>, y
 *     el teléfono hoy no lo es. <b>La condición para revisarlo queda escrita</b>: el día que exista
 *     verificación por SMS o segundo factor telefónico, pasa a la familia del correo
 * @param addressLine1 nueva dirección. <b>Admite el nulo explícito y lo vacía</b>: es opcional, y
 *     «ya no vivo ahí» es un hecho que hay que poder registrar
 * @param addressLine2 nuevo complemento, con el mismo trato
 * @param city nueva ciudad, con el mismo trato
 * @param currentPassword la contraseña vigente. <b>Obligatoria si y solo si viene {@code
 *     email}</b>: el correo es la vía de recuperación de `RF-SP-040`, de modo que cambiarlo es
 *     cambiar quién puede recuperar la cuenta, y una sesión robada no lleva la contraseña
 */
public record UpdateOwnProfileRequest(
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> firstName,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> lastName,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> email,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> phone,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> addressLine1,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> addressLine2,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> city,
    String currentPassword) {

  public UpdateOwnProfileRequest {
    firstName = firstName == null ? Patchable.ausente() : firstName;
    lastName = lastName == null ? Patchable.ausente() : lastName;
    email = email == null ? Patchable.ausente() : email;
    phone = phone == null ? Patchable.ausente() : phone;
    addressLine1 = addressLine1 == null ? Patchable.ausente() : addressLine1;
    addressLine2 = addressLine2 == null ? Patchable.ausente() : addressLine2;
    city = city == null ? Patchable.ausente() : city;
  }

  /** ¿Pide cambiar algo? La contraseña no cuenta: acompaña al cambio, no es uno. */
  public boolean informaAlgo() {
    return firstName.presente()
        || lastName.presente()
        || email.presente()
        || phone.presente()
        || addressLine1.presente()
        || addressLine2.presente()
        || city.presente();
  }

  /** ¿Toca el correo? Es lo que decide si la contraseña se exige. */
  public boolean tocaElCorreo() {
    return email.presente();
  }
}
