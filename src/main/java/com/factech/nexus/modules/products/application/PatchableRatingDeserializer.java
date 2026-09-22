package com.factech.nexus.modules.products.application;

import com.factech.nexus.shared.patch.Patchable;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * {@link RatingDeserializer} para el {@code PATCH}: los tres estados de {@code Patchable} —ausente,
 * nulo, con valor— con la misma exigencia de entero estricto. El nulo llega <b>presente y nulo</b>,
 * que es lo que permite rechazarlo con el mensaje de la spec en lugar de confundirlo con ausente
 * (`RF-PM-010` §4).
 */
public class PatchableRatingDeserializer extends JsonDeserializer<Patchable<Integer>> {

  @Override
  public Patchable<Integer> deserialize(JsonParser lector, DeserializationContext contexto)
      throws IOException {
    return Patchable.de(RatingDeserializer.leerEntero(lector, contexto));
  }

  @Override
  public Patchable<Integer> getNullValue(DeserializationContext contexto) {
    return Patchable.de(null);
  }

  @Override
  public Object getAbsentValue(DeserializationContext contexto) {
    return Patchable.ausente();
  }
}
