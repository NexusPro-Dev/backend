package com.factech.nexus.shared.video;

/** Los dos proveedores de video que el sistema admite (`RN-AC-005`, 25-09-2026). */
public enum VideoProvider {
  YOUTUBE("YouTube"),
  VIMEO("Vimeo");

  private final String nombre;

  VideoProvider(String nombre) {
    this.nombre = nombre;
  }

  /** Como se nombra en un mensaje. */
  public String nombre() {
    return nombre;
  }
}
