package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.video.VideoLink;
import java.util.List;

/**
 * Un video como enlace (`RN-AC-005`): <b>de YouTube o de Vimeo</b>, en una de sus formas
 * reconocidas ({@link VideoLink}), sin espacios y de hasta 500 caracteres. Hasta el 25-09-2026
 * admitía cualquier dominio; desde entonces, por decisión del responsable del proyecto (`ac.md`
 * §5.2.11), cualquier otro se rechaza con el código de quien llama. <b>Aquí solo se valida</b>: la
 * duración de una lección se pregunta al proveedor en su caso de uso, no en este objeto.
 *
 * <p>Objeto de valor y no un método privado de {@code Course} porque lo llevan tres entidades: el
 * video de introducción del curso, el de presentación del módulo (`RF-AC-022`) y el contenido de
 * una lección {@code VIDEO} (`RF-AC-028`). Escrito una vez, con el código y el campo que cada
 * operación le da.
 */
public final class VideoUrl {

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
    return VideoLink.esValido(recortado);
  }

  public static String mensaje() {
    return "El enlace del video debe ser un video de YouTube o de Vimeo —youtube.com, youtu.be,"
        + " vimeo.com o player.vimeo.com—, sin espacios y de hasta 500 caracteres.";
  }
}
