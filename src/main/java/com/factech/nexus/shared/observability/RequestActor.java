package com.factech.nexus.shared.observability;

import java.util.Optional;
import java.util.UUID;

/**
 * Quién resultó ser el actor de la petición en curso, para quien lo necesite <b>al terminar</b>.
 *
 * <p><b>Por qué hace falta esto y no basta con preguntar al contexto de seguridad.</b> Spring
 * Security <b>limpia su contexto</b> en su propio {@code finally}, y esa limpieza ocurre
 * <b>antes</b> que la de cualquier filtro que lo envuelva: para cuando {@link RequestLogFilter}
 * escribe su fila —después de emitida la respuesta— el actor ya no está, y toda petición del
 * sistema quedaría registrada como anónima. El síntoma es engañoso, porque la fila existe y parece
 * correcta.
 *
 * <p>Se apunta mientras el actor <b>sí</b> se conoce —dentro de la cadena de seguridad, en {@link
 * com.factech.nexus.shared.security.ActorCaptureFilter}— y se lee al final.
 *
 * <p><b>Un {@code ThreadLocal} exige limpiarlo.</b> Los hilos del contenedor se reutilizan: uno
 * olvidado le atribuiría a la siguiente petición el actor de esta, que en un registro de auditoría
 * es peor que no tener el dato. Lo limpia quien lo lee.
 */
public final class RequestActor {

  private static final ThreadLocal<UUID> ACTUAL = new ThreadLocal<>();

  private RequestActor() {}

  /** Apunta el actor ya resuelto. Un nulo significa anónimo y también es un dato. */
  public static void bind(UUID actorId) {
    ACTUAL.set(actorId);
  }

  /** El actor de la petición en curso, o vacío si nunca llegó a resolverse. */
  public static Optional<UUID> current() {
    return Optional.ofNullable(ACTUAL.get());
  }

  public static void unbind() {
    ACTUAL.remove();
  }
}
