package com.factech.nexus.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Las seis formas de error que la API publica, con su estado, su {@code type} y su {@code title}.
 *
 * <p><b>Este enumerado es el único lugar donde una excepción se convierte en un código de
 * estado</b> (`development-guide.md` §7.1). Tenerlo aparte de la jerarquía de excepciones es lo que
 * permite que el dominio no conozca HTTP: {@link GlobalExceptionHandler} elige la forma, y la
 * excepción solo dice qué regla se incumplió.
 *
 * <p>Los {@code type} son URI de documentación, no direcciones que haya que resolver: RFC 9457 los
 * define como identificadores. Se escriben en español porque identifican conceptos del contrato de
 * este sistema, cuyo idioma es el español (`development-guide.md` §4.1).
 */
public enum ProblemKind {
  VALIDACION(HttpStatus.BAD_REQUEST, "validacion", "La solicitud contiene campos inválidos"),

  REGLA_DE_NEGOCIO(
      HttpStatus.CONFLICT, "regla-de-negocio", "La operación incumple una regla de negocio"),

  ENTIDAD_NO_PROCESABLE(
      HttpStatus.UNPROCESSABLE_ENTITY,
      "entidad-no-procesable",
      "La solicitud referencia datos que no existen"),

  NO_ENCONTRADO(HttpStatus.NOT_FOUND, "no-encontrado", "El recurso solicitado no existe"),

  CUENTA_BLOQUEADA(HttpStatus.LOCKED, "cuenta-bloqueada", "La cuenta está bloqueada"),

  METODO_NO_PERMITIDO(
      HttpStatus.METHOD_NOT_ALLOWED, "metodo-no-permitido", "El recurso no admite ese método"),

  NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "no-autenticado", "Se requiere autenticación"),

  /**
   * Demasiadas peticiones en poco tiempo (`security.md` §5.5).
   *
   * <p>Lo produce el filtro de límite de tasa, <b>antes</b> de la cadena de seguridad y por tanto
   * fuera del alcance de {@link GlobalExceptionHandler}: la respuesta la escribe el propio filtro,
   * con esta misma forma para que el cliente no tenga que distinguir dos formatos de error.
   *
   * <p>Acompaña al estado la cabecera {@code Retry-After} y el miembro {@code retryAfterSeconds}:
   * un mensaje que dijera «vuelva en dos minutos» es cierto al serializarse y deja de serlo
   * enseguida, mientras que un número de segundos el cliente lo <b>descuenta</b>.
   */
  DEMASIADAS_PETICIONES(
      HttpStatus.TOO_MANY_REQUESTS,
      "demasiadas-peticiones",
      "Demasiadas peticiones en poco tiempo"),

  SIN_PERMISO(HttpStatus.FORBIDDEN, "sin-permiso", "No tiene permiso para ejecutar esta operación"),

  /**
   * La credencial la fijó otra persona y todavía no se ha cambiado (`RF-SP-034` · `FA-002`).
   *
   * <p>Lo produce {@code MustChangePasswordFilter}, dentro de la cadena de seguridad y por tanto
   * fuera del alcance de {@link GlobalExceptionHandler}, igual que {@link #DEMASIADAS_PETICIONES}.
   *
   * <p><b>Comparte el {@code 403} con {@link #SIN_PERMISO} y no comparte su {@code type}</b>, que
   * es lo que aquí importa. Los dos son denegaciones, pero lo que el cliente debe hacer con cada
   * una es opuesto: ante «no tiene permiso» la interfaz oculta la opción, y ante esta lleva a la
   * pantalla de cambiar la contraseña. Distinguirlas por el estado es imposible; por el {@code
   * type}, inmediato. Un cliente que solo mirase el {@code 403} dejaría a la persona ante un menú
   * que la rechaza entero sin decirle por qué.
   *
   * <p><b>Por qué {@code 403} y no {@code 428}.</b> RFC 6585 §3 define {@code 428} para
   * <i>precondiciones de la petición</i> —el {@code If-Match} que evita la actualización perdida—,
   * de modo que un cliente puede reintentar añadiendo una cabecera. Aquí no hay nada que añadir a
   * la petición: lo que falta es un cambio de estado de la cuenta, hecho desde <b>otro</b>
   * endpoint. {@code 403} dice lo que ocurre —denegado, no lo reintentes tal cual— y es lo que las
   * bibliotecas de cliente ya tratan como terminal.
   */
  CAMBIO_DE_CONTRASENA_REQUERIDO(
      HttpStatus.FORBIDDEN,
      "cambio-de-contrasena-requerido",
      "Debe cambiar su contraseña antes de operar"),

  INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "interno", "Error interno");

  private static final String BASE = "https://nexus.factech.co/errors/";

  private final HttpStatus status;
  private final String slug;
  private final String title;

  ProblemKind(HttpStatus status, String slug, String title) {
    this.status = status;
    this.slug = slug;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String type() {
    return BASE + slug;
  }

  public String title() {
    return title;
  }
}
