package com.factech.nexus.shared.patch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import java.io.IOException;

/**
 * Deserializa un {@link Patchable} de <b>cualquier tipo</b> conservando sus tres estados.
 *
 * <p>Hace lo mismo que {@link PatchableStringDeserializer} sin quedarse en el texto: `RF-PM-004`
 * necesita distinguir los tres estados en un importe, en un identificador y en un número de días, y
 * escribir un deserializador por tipo habría multiplicado por cinco la misma clase.
 *
 * <p><b>Lo que lo hace posible es {@link ContextualDeserializer}</b>: en el momento de construirse,
 * Jackson entrega la propiedad concreta que se está deserializando, y de ella sale el tipo interno
 * del {@code Patchable}. Sin ese enganche, un deserializador genérico no puede saber si lo que
 * envuelve es un {@code BigDecimal} o un {@code UUID}, y devolvería el nodo JSON en crudo.
 *
 * <p><b>Los dos métodos que sostienen la distinción son los otros dos</b>, y valen lo mismo aquí
 * que en el de texto:
 *
 * <ul>
 *   <li>{@link #getNullValue} — Jackson <b>no</b> llama a {@code deserialize} cuando el JSON trae
 *       {@code null}, sino a este: es el único sitio donde se puede saber que el campo <b>venía</b>
 *       y venía nulo.
 *   <li>{@link #getAbsentValue} — sin sobrescribirlo, su implementación por omisión <b>delega en
 *       {@code getNullValue}</b> y los dos estados que hay que separar se funden en uno. Es el
 *       defecto exacto que tuvo el intento con {@code Optional} en `RF-SP-027`, y no se manifestó
 *       como un fallo de deserialización sino como «enviar solo el nombre rechaza la petición por
 *       apellido vacío».
 * </ul>
 *
 * <p><b>Convive con {@link PatchableStringDeserializer}, que es anterior y solo sirve para
 * texto.</b> Este lo cubre entero y podría sustituirlo, pero migrar los dos DTO de `SP` que lo usan
 * no es trabajo de este requerimiento. Queda anotado como deuda: son dos anotaciones.
 */
public class PatchableDeserializer extends JsonDeserializer<Patchable<?>>
    implements ContextualDeserializer {

  private final JavaType tipoInterno;

  /** Jackson lo instancia sin tipo; {@link #createContextual} devuelve el que sí lo tiene. */
  public PatchableDeserializer() {
    this(null);
  }

  private PatchableDeserializer(JavaType tipoInterno) {
    this.tipoInterno = tipoInterno;
  }

  @Override
  public JsonDeserializer<?> createContextual(
      DeserializationContext contexto, BeanProperty propiedad) {

    JavaType envoltura = propiedad == null ? contexto.getContextualType() : propiedad.getType();
    return new PatchableDeserializer(envoltura.containedTypeOrUnknown(0));
  }

  @Override
  public Patchable<?> deserialize(JsonParser lector, DeserializationContext contexto)
      throws IOException {
    return Patchable.de(contexto.readValue(lector, tipoInterno));
  }

  @Override
  public Patchable<?> getNullValue(DeserializationContext contexto) {
    // Presente y nulo: el estado que un `Optional` no sabe separar del ausente.
    return Patchable.de(null);
  }

  @Override
  public Object getAbsentValue(DeserializationContext contexto) {
    return Patchable.ausente();
  }
}
