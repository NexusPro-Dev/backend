package com.factech.nexus.modules.system.teams.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * El cuerpo de la corrección de un equipo (`RF-SP-066` §6.1): <b>los dos únicos campos que se
 * corrigen</b>.
 *
 * <p><b>{@code Patchable} y no {@code String}</b>, porque hay tres situaciones y no dos: el campo
 * no viene —se conserva—, viene con valor —se cambia— o viene en {@code null} —se borra—. Un {@code
 * String} nulo confunde las dos últimas, y sin la distinción no habría forma de vaciar la
 * descripción sin inventarle un endpoint propio.
 *
 * <p><b>{@code status} y {@code members} no están, y su ausencia es la implementación</b>: el
 * estado se cambia con `RF-SP-067` y los miembros entran y salen con `RF-SP-069` y `RF-SP-070`,
 * cada uno con su permiso y sus reglas. Enviarlos aquí es `400` por campo desconocido (`VAL-004`),
 * y lo rechaza el editor canónico de JSON antes de llegar al caso de uso — no hace falta código que
 * los mire.
 */
public record UpdateTeamRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> name,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> description) {

  public UpdateTeamRequest {
    name = name == null ? Patchable.ausente() : name;
    description = description == null ? Patchable.ausente() : description;
  }

  /** Un `PATCH` que no informa nada no es una corrección: es una petición sin intención. */
  public boolean informaAlgo() {
    return name.presente() || description.presente();
  }
}
