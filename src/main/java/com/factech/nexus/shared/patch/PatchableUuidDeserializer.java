package com.factech.nexus.shared.patch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.UUID;

/**
 * Deserializa un {@link Patchable} de identificador conservando sus tres estados.
 *
 * <p>Gemelo de {@link PatchableStringDeserializer}, y todo lo que aquel documenta vale aquí: {@link
 * #getNullValue} es lo que distingue «venía nulo» de «no venía», y {@link #getAbsentValue} hay que
 * sobrescribirlo o Jackson entrega lo mismo para los dos casos.
 *
 * <p>Lo propio es el <b>rechazo del identificador mal formado</b>. {@link UUID#fromString} lanza
 * {@link IllegalArgumentException}, que sin traducir sube como {@code 500}; se convierte en un
 * error de deserialización de Jackson, que el manejador global ya traduce a {@code 400} — el mismo
 * trato que recibe cualquier otro campo con formato inválido.
 *
 * <p><b>Y no acepta la forma no canónica</b> —sin guiones, o entre llaves—, al contrario que {@link
 * UUID#fromString} en algunas de sus variantes: es el criterio que {@code CanonicalUuidConverter}
 * fijó para los identificadores de ruta, y dos formas de escribir el mismo identificador acaban
 * produciendo dos entradas distintas en la auditoría.
 */
public class PatchableUuidDeserializer extends JsonDeserializer<Patchable<UUID>> {

  private static final java.util.regex.Pattern CANONICO =
      java.util.regex.Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  @Override
  public Patchable<UUID> deserialize(JsonParser lector, DeserializationContext contexto)
      throws IOException {

    String bruto = lector.getValueAsString();
    if (bruto == null || bruto.isBlank()) {
      // Cadena vacía no es «nulo»: es un valor mal formado, y se rechaza como tal.
      throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(
          lector, "No es un identificador válido.", bruto, UUID.class);
    }
    String valor = bruto.trim();
    if (!CANONICO.matcher(valor).matches()) {
      throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(
          lector, "No es un identificador en forma canónica.", valor, UUID.class);
    }
    return Patchable.de(UUID.fromString(valor));
  }

  /** Presente y nulo. Quien llama decide si eso es una orden o un rechazo. */
  @Override
  public Patchable<UUID> getNullValue(DeserializationContext contexto) {
    return Patchable.de(null);
  }

  /** El campo ausente. Sin esto, «no venía» y «venía nulo» llegan iguales. */
  @Override
  public Object getAbsentValue(DeserializationContext contexto) {
    return Patchable.ausente();
  }
}
