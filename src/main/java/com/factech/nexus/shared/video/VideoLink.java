package com.factech.nexus.shared.video;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un enlace de video reconocido: de qué proveedor es y qué video señala (`RN-AC-005`, 25-09-2026).
 *
 * <p><b>Solo YouTube y Vimeo, en sus formas reconocidas</b>, y el reconocimiento es lo que protege
 * la consulta de la duración: quien la hace no llama nunca al enlace tal cual, sino a la dirección
 * fija del proveedor con el {@link #id() identificador} sacado de aquí. Un enlace que no casa con
 * ninguna forma no es un video para el sistema, aunque apunte a uno.
 *
 * <p>{@link #PATRON} es la misma regla como expresión, <b>constante</b> para que quepa en un
 * {@code @Pattern}: las dos no pueden divergir porque {@link #reconocer} la usa primero.
 *
 * @param proveedor de quién es el video
 * @param id el identificador del video en su proveedor
 * @param hash el código de un video no listado de Vimeo, o nulo
 */
public record VideoLink(VideoProvider proveedor, String id, String hash) {

  /** El tope de la columna: el mismo de antes de que hubiera proveedores. */
  public static final int LARGO_MAXIMO = 500;

  private static final String YT_ID = "[A-Za-z0-9_-]{11}";
  private static final String COLA = "(?:[?&#/]\\S*)?";

  /** Las siete formas reconocidas, en una expresión. */
  public static final String PATRON =
      "^(?:https?://(?:www\\.|m\\.)?youtube\\.com/(?:watch\\?(?:\\S*&)?v=|embed/|shorts/|live/)"
          + YT_ID
          + COLA
          + "|https?://youtu\\.be/"
          + YT_ID
          + COLA
          + "|https?://(?:www\\.)?vimeo\\.com/\\d+(?:/[0-9a-f]+)?(?:[?#]\\S*)?"
          + "|https?://player\\.vimeo\\.com/video/\\d+"
          + COLA
          + ")$";

  private static final Pattern FORMA = Pattern.compile(PATRON);
  private static final Pattern YOUTUBE =
      Pattern.compile(
          "^https?://(?:(?:www\\.|m\\.)?youtube\\.com/(?:watch\\?(?:\\S*&)?v=|embed/|shorts/|live/)"
              + "|youtu\\.be/)("
              + YT_ID
              + ")");
  private static final Pattern VIMEO =
      Pattern.compile("^https?://(?:www\\.)?vimeo\\.com/(\\d+)(?:/([0-9a-f]+))?");
  private static final Pattern VIMEO_PLAYER =
      Pattern.compile("^https?://player\\.vimeo\\.com/video/(\\d+)(?:\\S*[?&]h=([0-9a-f]+))?");

  /** El video que señala el enlace, o vacío si no es de YouTube ni de Vimeo en forma reconocida. */
  public static Optional<VideoLink> reconocer(String enlace) {
    if (enlace == null) {
      return Optional.empty();
    }
    String recortado = enlace.trim();
    if (recortado.length() > LARGO_MAXIMO || !FORMA.matcher(recortado).matches()) {
      return Optional.empty();
    }
    Matcher m = YOUTUBE.matcher(recortado);
    if (m.find()) {
      return Optional.of(new VideoLink(VideoProvider.YOUTUBE, m.group(1), null));
    }
    m = VIMEO.matcher(recortado);
    if (m.find()) {
      return Optional.of(new VideoLink(VideoProvider.VIMEO, m.group(1), m.group(2)));
    }
    m = VIMEO_PLAYER.matcher(recortado);
    if (m.find()) {
      return Optional.of(new VideoLink(VideoProvider.VIMEO, m.group(1), m.group(2)));
    }
    return Optional.empty();
  }

  /** La forma admitida, ya recortada. */
  public static boolean esValido(String recortado) {
    return reconocer(recortado).isPresent();
  }
}
