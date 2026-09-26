package com.factech.nexus.shared.video;

/**
 * El proveedor no dio la duración del video. Lleva el proveedor y un motivo legible, que quien la
 * captura convierte en su rechazo —en academia, el `422` `EX-003`—.
 */
public class VideoDurationUnavailable extends RuntimeException {

  private final VideoProvider proveedor;
  private final String motivo;

  public VideoDurationUnavailable(VideoProvider proveedor, String motivo) {
    super("No se pudo obtener la duración del video de " + proveedor.nombre() + ": " + motivo);
    this.proveedor = proveedor;
    this.motivo = motivo;
  }

  public VideoProvider proveedor() {
    return proveedor;
  }

  public String motivo() {
    return motivo;
  }
}
