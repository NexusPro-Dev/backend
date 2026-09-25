package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;

/**
 * El contenido de una lección según su tipo (`RN-AC-016`): <b>el tipo manda</b>.
 *
 * <p>Un {@code VIDEO} lleva una URL con la forma de `RN-AC-005`; un {@code TEXTO} lleva cualquier
 * texto, <b>sin validar como Markdown</b> —no hay Markdown inválido— y <b>sin sanear</b>: el
 * backend no lo sirve como HTML en ningún sitio, y la obligación de pintarlo sin ejecutar lo que
 * encuentre es del frontend (`requirements/ac.md` §5.2.4). Vacío o de solo espacios queda
 * <b>nulo</b>: la lección se prepara sin contenido y no se activa hasta tenerlo (`RN-AC-009`).
 *
 * <p>Objeto de valor porque el alta y la corrección deciden lo mismo en un solo sitio: en la
 * corrección, <b>la pareja resultante</b> —tipo nuevo o el que había, contenido nuevo o el que
 * había— es lo que se valida, antes de aplicar nada (`RF-AC-029`). Se recorta solo por los
 * extremos: un Markdown conserva sus saltos de línea.
 */
public final class LessonContent {

  private LessonContent() {}

  /**
   * El contenido normalizado para ese tipo, o nulo si no hay; lanza {@code VAL-006} (o el código
   * que se le dé) si es un video y no es una URL.
   */
  public static String de(LessonType tipo, String valor, String codigo) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.strip();
    if (recortado.isEmpty()) {
      return null;
    }
    if (tipo == LessonType.VIDEO && !VideoUrl.esValida(recortado)) {
      throw new ValidationException(
          codigo, MENSAJE_VIDEO, List.of(new FieldError("content", codigo, MENSAJE_VIDEO)));
    }
    return recortado;
  }

  public static final String MENSAJE_VIDEO =
      "El contenido de una lección de video debe ser un video de YouTube o de Vimeo —youtube.com,"
          + " youtu.be, vimeo.com o player.vimeo.com—, sin espacios y de hasta 500 caracteres.";
}
