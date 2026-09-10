package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableStringDeserializer;
import com.factech.nexus.shared.patch.PatchableUuidDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/users/{id}} (`RF-SP-027`).
 *
 * <p>{@link Patchable} distingue los <b>tres</b> estados que un `PATCH` necesita: campo ausente,
 * campo presente con nulo explícito, y campo con valor. Sin los tres, «no lo envié» y «ponlo a
 * nulo» se confunden.
 *
 * <p><b>El nulo explícito se RECHAZA</b>, y aquí está la diferencia con la edición de un rol, que
 * es el error fácil de copiar: allí el nulo era una orden —«borra la descripción»— porque la
 * columna lo admite. Aquí las tres columnas son {@code NOT NULL} y `ck_users_names_not_blank`
 * impide además el blanco: el nulo no puede ser una orden, y <b>aceptarlo en silencio sería peor
 * que rechazarlo</b>, porque produciría una violación de integridad traducida a {@code 500} en
 * lugar del {@code 400} que corresponde.
 *
 * <p><b>Desde el 08-09-2026 el nulo explícito deja de significar lo mismo en todo el cuerpo</b>, y
 * es la novedad que hay que tener presente al leer esta clase. Hasta hoy la regla era única y
 * cómoda —el nulo siempre se rechaza— porque todas las columnas eran {@code NOT NULL}. Con el
 * contacto entran <b>tres columnas nulables</b>, y en ellas el nulo <b>sí es una orden</b>: «ya no
 * vive ahí» es un hecho que hay que poder registrar, y rechazarlo dejaría la dirección vieja pegada
 * para siempre.
 *
 * <p>De modo que el cuerpo tiene ahora <b>dos familias</b>: {@code firstName}, {@code lastName},
 * {@code email}, {@code countryId}, {@code documentTypeId}, {@code documentNumber} y {@code phone}
 * <b>rechazan</b> el nulo; {@code addressLine1}, {@code addressLine2}, {@code city} y {@code
 * companyPhone} lo <b>aceptan y vacían</b>. La línea que las separa no es técnica sino de negocio —
 * es la de lo obligatorio y lo opcional de `RN-SP-035` y `RN-SP-037`.
 *
 * <p><b>{@code companyPhone} entró en la segunda familia el 10-09-2026, y es la comprobación de que
 * la línea está bien trazada.</b> Es un teléfono, comparte forma y validación con {@code phone}, y
 * aun así cae del otro lado: lo que decide no es qué dato es, sino si la regla lo exige.
 *
 * <p><b>El tipo y el número de documento se validan como una unidad</b>: enviar uno solo es {@code
 * 400} y no un cambio a medias. {@code ck_users_document_pair} lo impediría de todas formas, y
 * dejarlo llegar al motor daría un {@code 500} sobre una regla de negocio.
 *
 * <p><b>{@code countryId} es el cuarto campo y hereda el trato de los otros tres</b> (`RN-SP-034`,
 * 07-09-2026): {@code {"countryId": null}} se rechaza con {@code 400}, porque {@code country_id} es
 * {@code NOT NULL} y el estado «persona sin país» no existe. Es además <b>el único campo del cuerpo
 * que se verifica contra otra tabla</b> —existe y está activo—, y esa verificación es sobre el país
 * <b>de destino</b> y nunca sobre el actual: esta es la operación con la que se saca a alguien de
 * un país recién desactivado.
 *
 * <p><b>El nombre de usuario no está aquí</b>, y tampoco el estado, los roles, la membresía ni la
 * contraseña. Cada uno tiene su requerimiento. Enviarlos devuelve {@code 400} por propiedad
 * desconocida — sin ese rechazo se ignorarían en silencio y quien los enviara creería haberlos
 * cambiado.
 */
public record UpdateUserRequest(
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> firstName,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> lastName,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> email,
    @JsonDeserialize(using = PatchableUuidDeserializer.class) Patchable<UUID> countryId,
    @JsonDeserialize(using = PatchableUuidDeserializer.class) Patchable<UUID> documentTypeId,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> documentNumber,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> phone,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> companyPhone,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> addressLine1,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> addressLine2,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> city) {

  /**
   * El campo que Jackson no vio llega como {@code null} al constructor canónico. Convertirlo aquí
   * es lo que permite que el resto del código no tenga que comprobar nulos por ningún lado.
   */
  public UpdateUserRequest {
    firstName = firstName == null ? Patchable.ausente() : firstName;
    lastName = lastName == null ? Patchable.ausente() : lastName;
    email = email == null ? Patchable.ausente() : email;
    countryId = countryId == null ? Patchable.ausente() : countryId;
    documentTypeId = documentTypeId == null ? Patchable.ausente() : documentTypeId;
    documentNumber = documentNumber == null ? Patchable.ausente() : documentNumber;
    phone = phone == null ? Patchable.ausente() : phone;
    companyPhone = companyPhone == null ? Patchable.ausente() : companyPhone;
    addressLine1 = addressLine1 == null ? Patchable.ausente() : addressLine1;
    addressLine2 = addressLine2 == null ? Patchable.ausente() : addressLine2;
    city = city == null ? Patchable.ausente() : city;
  }

  /** ¿Se envió alguno de los once, con el valor que sea? */
  public boolean informaAlgo() {
    return firstName.presente()
        || lastName.presente()
        || email.presente()
        || countryId.presente()
        || documentTypeId.presente()
        || documentNumber.presente()
        || phone.presente()
        || companyPhone.presente()
        || addressLine1.presente()
        || addressLine2.presente()
        || city.presente();
  }
}
