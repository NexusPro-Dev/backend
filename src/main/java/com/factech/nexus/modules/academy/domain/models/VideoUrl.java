package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Un video como enlace (`RN-AC-005`): URL absoluta {@code http} o {@code https}, sin espacios, de
 * hasta 500 caracteres, de cualquier dominio. <b>El sistema comprueba la forma y no lo sigue</b>:
 * no comprueba que exista, no lo descarga, no lo incrusta. Es `RN-PM-032` por extensión, con el
 * mismo patrón que {@code Product} y que {@code ck_courses_intro_video_url}.
 *
 * <p>Objeto de valor y no un método privado de {@code Course} porque lo llevan tres entidades: el
 * video de introducción del curso, el de presentación del módulo (`RF-AC-022`) y el contenido de
 * una lección {@code VIDEO} (`RF-AC-028`). Escrito una vez, con el código y el campo que cada
 * operación le da.
 */
public final class VideoUrl {

  private static final Pattern FORMA = Pattern.compile("^https?://\\S+$");
  private static final int LARGO_MAXIMO = 500;

  private VideoUrl() {}

  /**
   * El enlace recortado, o nulo si no vino nada; lanza con el código y el campo de quien llama si
   * la forma no es la admitida.
   */
  public static String normalizar(String valor, String codigo, String campo) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.trim();
    if (recortado.isEmpty()) {
      return null;
    }
    if (!esValida(recortado)) {
      throw new ValidationException(
          codigo, mensaje(), List.of(new FieldError(campo, codigo, mensaje())));
    }
    return recortado;
  }

  /** La forma admitida, ya recortada: para quien tiene su propio mensaje, como la lección. */
  public static boolean esValida(String recortado) {
    return recortado.length() <= LARGO_MAXIMO && FORMA.matcher(recortado).matches();
  }

  public static String mensaje() {
    return "El enlace del video debe ser una URL absoluta http o https, sin espacios y de hasta 500"
        + " caracteres.";
  }
}
