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

  SIN_PERMISO(HttpStatus.FORBIDDEN, "sin-permiso", "No tiene permiso para ejecutar esta operación"),

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
