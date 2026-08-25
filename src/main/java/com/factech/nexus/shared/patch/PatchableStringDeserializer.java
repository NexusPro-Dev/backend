package com.factech.nexus.shared.patch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * Deserializa un {@link Patchable} de texto conservando sus tres estados.
 *
 * <p>Lo que hace este tipo posible es {@link #getNullValue}: Jackson <b>no</b> llama a {@code
 * deserialize} cuando el JSON trae {@code null}, sino a ese método — de modo que ahí, y solo ahí,
 * se puede saber que el campo <b>venía</b> y venía nulo.
 *
 * <p>El campo <b>ausente</b> no pasa por aquí: Jackson entrega {@code null} al constructor canónico
 * del registro, y el constructor compacto lo convierte en {@code Patchable.ausente()}.
 */
public class PatchableStringDeserializer extends JsonDeserializer<Patchable<String>> {

  @Override
  public Patchable<String> deserialize(JsonParser lector, DeserializationContext contexto)
      throws IOException {
    return Patchable.de(lector.getValueAsString());
  }

  @Override
  public Patchable<String> getNullValue(DeserializationContext contexto) {
    // Presente y nulo. Es el estado que un `Optional` no sabe separar del ausente.
    return Patchable.de(null);
  }

  /**
   * El campo <b>ausente</b>.
   *
   * <p>Sin sobrescribir esto no hay tres estados: la implementación por omisión de {@code
   * getAbsentValue} <b>delega en {@code getNullValue}</b>, de modo que Jackson entrega lo mismo
   * para «no venía» y para «venía nulo» — que es exactamente el defecto que este tipo existe para
   * evitar, y el que tuvo el intento anterior con {@code Optional}.
   *
   * <p>El síntoma era concreto y no se parecía a su causa: enviar solo el nombre rechazaba la
   * petición por «apellido vacío».
   */
  @Override
  public Object getAbsentValue(DeserializationContext contexto) {
    return Patchable.ausente();
  }
}
