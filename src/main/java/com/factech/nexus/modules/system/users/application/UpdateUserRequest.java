package com.factech.nexus.modules.system.users.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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
 * <p><b>El nombre de usuario no está aquí</b>, y tampoco el estado, los roles, la membresía ni la
 * contraseña. Cada uno tiene su requerimiento. Enviarlos devuelve {@code 400} por propiedad
 * desconocida — sin ese rechazo se ignorarían en silencio y quien los enviara creería haberlos
 * cambiado.
 */
public record UpdateUserRequest(
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> firstName,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> lastName,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> email) {

  /**
   * El campo que Jackson no vio llega como {@code null} al constructor canónico. Convertirlo aquí
   * es lo que permite que el resto del código no tenga que comprobar nulos por ningún lado.
   */
  public UpdateUserRequest {
    firstName = firstName == null ? Patchable.ausente() : firstName;
    lastName = lastName == null ? Patchable.ausente() : lastName;
    email = email == null ? Patchable.ausente() : email;
  }

  /** ¿Se envió alguno de los tres, con el valor que sea? */
  public boolean informaAlgo() {
    return firstName.presente() || lastName.presente() || email.presente();
  }
}
