package com.factech.nexus.shared.observability;

import java.util.Optional;
import java.util.UUID;

/**
 * Núcleo común de origen de una petición: correlación, dirección de red y cliente.
 *
 * <p>Es lo que {@code architecture.md} §6.6.1 exige en las cuatro tablas de auditoría y lo que el
 * formato de error de §7.3 publica como {@code correlationId}. Vive aquí, en {@code observability},
 * porque no pertenece a ningún módulo: lo escribe un filtro al entrar la petición y lo leen tanto
 * la auditoría como el manejador de errores.
 *
 * <p><b>Por qué un {@code ThreadLocal} y no un bean de ámbito de petición.</b> Un bean con
 * {@code @RequestScope} obliga a inyectar un proxy en cada consumidor y falla —con una excepción de
 * ámbito, no con un valor ausente— cuando quien lo pide corre fuera de una petición HTTP. Y correr
 * fuera de una petición es un caso <b>normal</b> aquí: las migraciones y las tareas programadas
 * también auditan, y su fila lleva las tres columnas de origen en nulo, que es como el esquema dice
 * «no vino de la red» (Art. V.15). {@link #current()} devuelve un {@code Optional} vacío en ese
 * caso, que es exactamente el dato que la auditoría necesita.
 *
 * <p><b>Se limpia siempre.</b> El filtro que lo puebla lo retira en un {@code finally}: los hilos
 * del contenedor se reutilizan, y un contexto olvidado le atribuiría a la siguiente petición la
 * correlación y la IP de la anterior.
 *
 * @param correlationId enlace con {@code request_log} (Art. XV.1). Nunca nulo dentro de una
 *     petición
 * @param ipAddress dirección de origen ya resuelta contra la cadena de proxies confiables
 * @param userAgent cliente desde el que se originó la operación; nulo si no lo envía
 */
public record RequestContext(UUID correlationId, String ipAddress, String userAgent) {

  private static final ThreadLocal<RequestContext> ACTUAL = new ThreadLocal<>();

  /**
   * Contexto de la petición en curso, o vacío si esto no se ejecuta dentro de una.
   *
   * @return el contexto, o {@code Optional.empty()} en migraciones, tareas y procesos internos
   */
  public static Optional<RequestContext> current() {
    return Optional.ofNullable(ACTUAL.get());
  }

  /** Lo invoca únicamente {@link CorrelationFilter}. */
  static void bind(RequestContext contexto) {
    ACTUAL.set(contexto);
  }

  /** Lo invoca únicamente {@link CorrelationFilter}, siempre desde un {@code finally}. */
  static void unbind() {
    ACTUAL.remove();
  }
}
