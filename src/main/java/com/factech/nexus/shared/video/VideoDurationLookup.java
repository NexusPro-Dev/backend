package com.factech.nexus.shared.video;

/**
 * La duración de un video, preguntada a su proveedor (`RN-AC-017`, 25-09-2026).
 *
 * <p>Una interfaz y no la clase, para que las pruebas de academia sustituyan a los proveedores sin
 * red. La implementación ({@link ProviderVideoDurationLookup}) habla con YouTube y con Vimeo.
 */
public interface VideoDurationLookup {

  /**
   * Los segundos que dura el video, mayor que cero.
   *
   * @throws VideoDurationUnavailable si el proveedor no la da: el video no existe o es privado, no
   *     respondió a tiempo, falta la credencial, o dura cero —una emisión en vivo—
   */
  int segundosDe(VideoLink video);
}
