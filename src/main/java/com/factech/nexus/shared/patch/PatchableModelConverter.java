package com.factech.nexus.shared.patch;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Iterator;
import org.springframework.stereotype.Component;

/**
 * Publica un {@link Patchable} en el contrato <b>como el tipo que envuelve</b>: un {@code
 * Patchable<String>} es un {@code string}, un {@code Patchable<List<ProductLinkRequest>>} es el
 * array de enlaces.
 *
 * <p><b>Por qué hace falta.</b> Jackson entiende {@code Patchable} por sus deserializadores
 * propios, pero springdoc solo mira la clase: no encuentra propiedades públicas y emitía {@code {}}
 * con un esquema {@code Patchable*} por tipo envuelto. Así, hasta el 30-09-2026 el contrato no
 * decía qué campo admitía ningún {@code PATCH} de la API, y el cliente generado los tipaba como
 * desconocidos (issue #94).
 *
 * <p><b>Por qué no se marca {@code null} en el esquema.</b> El tercer estado —presente y nulo— no
 * significa lo mismo en todos los campos: en unos vacía el valor y en otros es un {@code 400}, y
 * eso lo decide cada requerimiento, no el tipo. Marcarlos todos como anulables sería afirmar en el
 * contrato algo que para la mitad es falso; lo dice la prosa de cada operación.
 *
 * <p><b>{@code Patchable<Object>} es otra cosa: un inmutable.</b> Así se declaran los campos que no
 * se corrigen —el código de un producto, el rol de una tasa— solo para rechazarlos con su propio
 * mensaje en lugar del genérico de Jackson. Publicarlos como {@code Object} diría «cualquier valor
 * vale», justo lo contrario; se publican con {@code not: {}}, que en JSON Schema es «ningún valor
 * es válido».
 *
 * <p>Se resuelve con {@code context.resolve} y no con el siguiente de la cadena para que el tipo
 * envuelto vuelva a pasar por todos los convertidores —también por este, si alguna vez se anidara—
 * y conserve las anotaciones del campo.
 */
@Component
public class PatchableModelConverter implements ModelConverter {

  static final String INMUTABLE = "No se corrige: enviarlo, con cualquier valor, se rechaza.";

  @Override
  public Schema<?> resolve(
      AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
    JavaType javaType = Json.mapper().constructType(type.getType());
    if (javaType != null && javaType.getRawClass() == Patchable.class) {
      JavaType valor = javaType.containedTypeOrUnknown(0);
      if (valor.getRawClass() == Object.class) {
        return new Schema<>().not(new Schema<>()).description(INMUTABLE);
      }
      AnnotatedType envuelto =
          new AnnotatedType(valor)
              .ctxAnnotations(type.getCtxAnnotations())
              .parent(type.getParent())
              .schemaProperty(type.isSchemaProperty())
              .name(type.getName())
              .propertyName(type.getPropertyName())
              .resolveAsRef(type.isResolveAsRef())
              .jsonViewAnnotation(type.getJsonViewAnnotation())
              .skipOverride(type.isSkipOverride());
      return context.resolve(envuelto);
    }
    return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
  }
}
