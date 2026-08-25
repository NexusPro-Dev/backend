package com.factech.nexus.shared.error;

import java.beans.PropertyEditorSupport;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Exige la <b>forma canónica</b> en todo {@link UUID} que llegue por la ruta o por la consulta.
 *
 * <p>{@code UUID.fromString} del JDK es <b>permisivo</b>: acepta {@code 1-1-1-1-1} y lo convierte
 * en {@code 00000001-0001-0001-0001-000000000001}. La consecuencia no es teórica: una ruta con un
 * identificador manifiestamente inválido no devolvía {@code 400} sino {@code 404} —la respuesta de
 * «no existe»—, y quien la recibía se ponía a buscar un recurso que nunca pudo existir en lugar de
 * corregir su petición.
 *
 * <p>Era un hueco declarado desde el 24-08-2026 en `RF-SP-018` · `T-08`, y `RF-SP-026` §4 lo volvía
 * a exigir por su nombre. Se cierra aquí, en {@code shared}, porque alcanza a <b>todas</b> las
 * rutas con identificador: resolverlo endpoint por endpoint habría dejado el mismo agujero en el
 * siguiente.
 *
 * <h2>Por qué un editor y no un {@code Converter}</h2>
 *
 * <p>Se intentó dos veces con un {@code Converter<String, UUID>} —suelto en el contexto y
 * registrado en el {@code FormatterRegistry}— y las dos fallaron <b>en silencio</b>. El motivo está
 * en {@code TypeConverterDelegate}: consulta primero el servicio de conversión, pero si el
 * convertidor <b>lanza</b>, captura el fallo y <b>vuelve a intentarlo con el editor por
 * omisión</b>, que es el permisivo. Un convertidor que rechaza no rechaza nada: su excepción se
 * descarta y el valor entra igual.
 *
 * <p>Un editor <b>personalizado</b>, en cambio, se localiza <b>antes</b> que el servicio de
 * conversión y cortocircuita el resto del mecanismo: si lanza, la excepción sube. Spring la traduce
 * a {@code MethodArgumentTypeMismatchException}, que el manejador global ya convierte en {@code
 * 400} `VAL-001` señalando el parámetro por su nombre.
 *
 * <p>Se aplica a variables de ruta y parámetros de consulta, <b>no</b> al cuerpo JSON: ahí la
 * deserialización de Jackson ya rechaza un identificador malformado con su propio {@code 400}.
 */
@ControllerAdvice
public class CanonicalUuidConverter {

  /** Treinta y seis caracteres, ocho-cuatro-cuatro-cuatro-doce, hexadecimal. Nada más. */
  private static final Pattern CANONICO =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  @InitBinder
  public void registrar(WebDataBinder enlazador) {
    // Una instancia por petición: los editores guardan estado y no son seguros
    // entre hilos.
    enlazador.registerCustomEditor(UUID.class, new EditorCanonico());
  }

  /** Editor que rechaza todo lo que no sea la forma canónica. */
  static final class EditorCanonico extends PropertyEditorSupport {

    @Override
    public void setAsText(String texto) {
      // Vacío significa ausente, no inválido: un parámetro opcional que no se
      // envía llega aquí como cadena vacía, y rechazarlo convertiría en error
      // el caso normal de no filtrar.
      if (texto == null || texto.isBlank()) {
        setValue(null);
        return;
      }
      if (!CANONICO.matcher(texto).matches()) {
        throw new IllegalArgumentException(
            "El identificador no tiene la forma canónica de un UUID.");
      }
      setValue(UUID.fromString(texto));
    }
  }
}
