package com.factech.nexus.modules.products.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * La puntuación es un ENTERO, y Jackson por omisión no lo garantiza.
 *
 * <p>Con {@code Integer} a secas, {@code 4.5} llega como {@code 4} —{@code ACCEPT_FLOAT_AS_INT}
 * está encendido por omisión— y {@code "5"} llega como {@code 5}. La spec dice que el decimal <b>se
 * rechaza, no se redondea</b> (`spec.md` §11, `VAL-003`) y que la cadena no es una puntuación
 * (§13): decidir por el autor lo que el autor no dijo es exactamente lo que este deserializador
 * impide. Solo admite el token numérico entero; lo demás es un cuerpo ilegible y responde {@code
 * 400}.
 *
 * <p>El rango (uno a cinco) <b>no</b> se comprueba aquí: eso es del dominio, con su código y su
 * mensaje. Aquí solo se decide qué es un número entero.
 */
public class RatingDeserializer extends JsonDeserializer<Integer> {

  @Override
  public Integer deserialize(JsonParser lector, DeserializationContext contexto)
      throws IOException {
    return leerEntero(lector, contexto);
  }

  static Integer leerEntero(JsonParser lector, DeserializationContext contexto) throws IOException {
    if (lector.currentToken() != JsonToken.VALUE_NUMBER_INT) {
      return (Integer)
          contexto.handleUnexpectedToken(
              Integer.class,
              lector.currentToken(),
              lector,
              "La puntuación debe ser un número entero, sin decimales ni comillas.");
    }
    return lector.getIntValue();
  }
}
