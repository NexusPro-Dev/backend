package com.factech.nexus.shared.zoom;

/**
 * Zoom no hizo lo que se le pidió: sin credenciales, sin respuesta a tiempo o con un error. Quien
 * la recibe responde {@code 503} y no deja nada a medias (`RN-AC-026`).
 *
 * <p>El mensaje <b>nunca lleva la petición</b>: ni el token ni las credenciales.
 */
public class ZoomUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ZoomUnavailableException(String motivo) {
    super(motivo);
  }
}
