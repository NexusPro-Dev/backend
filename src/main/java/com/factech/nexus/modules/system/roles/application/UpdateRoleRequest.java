package com.factech.nexus.modules.system.roles.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Cuerpo de {@code PATCH /api/v1/roles/{id}} (`RF-SP-004`).
 *
 * <p>{@link Patchable} distingue los <b>tres</b> estados que un `PATCH` necesita: campo ausente,
 * campo presente con nulo explícito, y campo con valor. Sin los tres, «no lo envié» y «ponlo a
 * nulo» se confunden — y aquí esa diferencia es una orden real.
 *
 * <p><b>El nulo explícito significa cosas distintas en cada campo</b>, y es la asimetría que hay
 * que tener presente al leer esto junto a la edición de una persona:
 *
 * <ul>
 *   <li>{@code description} <b>admite el nulo</b>: la columna es nulable y borrar la descripción es
 *       una orden legítima (`spec.md` §13).
 *   <li>{@code name} <b>no</b>: la columna es {@code NOT NULL} y un rol sin nombre no existe, de
 *       modo que el nulo se rechaza con {@code 400} en lugar de producir una violación de
 *       integridad traducida a {@code 500}.
 * </ul>
 *
 * <p><b>Ni el código, ni la clasificación, ni el estado, ni el rol padre, ni los permisos están
 * aquí.</b> Cada uno tiene su requerimiento —o es inmutable por diseño—, y enviarlos devuelve
 * {@code 400} por propiedad desconocida: sin ese rechazo se ignorarían en silencio y quien los
 * enviara creería haberlos cambiado, que es exactamente lo que `CA-SP-151` y `CA-SP-024` verifican.
 */
public record UpdateRoleRequest(
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> name,
    @JsonDeserialize(using = PatchableStringDeserializer.class) Patchable<String> description) {

  /** El campo que Jackson no vio llega como {@code null} al constructor canónico. */
  public UpdateRoleRequest {
    name = name == null ? Patchable.ausente() : name;
    description = description == null ? Patchable.ausente() : description;
  }

  /** ¿Se envió alguno de los dos, con el valor que sea? (`VAL-001`) */
  public boolean informaAlgo() {
    return name.presente() || description.presente();
  }
}
