package com.factech.nexus.modules.academy.application;

import java.util.UUID;

/**
 * La única función que sabe cómo se llama la ruta pública de una portada de Academia (`RN-AC-004`,
 * `RF-AC-032`).
 *
 * <p>Es {@code ProductImageUrls} sobre otra tabla y otra ruta: {@code academy_images} es de este
 * módulo porque `modules.md` §7 prohíbe que `AC` escriba la tabla de `PM`, y la ruta la sigue
 * (`requirements/ac.md` §5.2.3). Lo demás se hereda entero: <b>es una ruta y no una URL
 * absoluta</b>, y es <b>por imagen y no por entidad</b>, para que la dirección no cambie nunca de
 * contenido y se pueda cachear un año — reemplazar la portada es otra dirección.
 *
 * <p>Nace con `RF-AC-001`, sin nada que señalar todavía: las respuestas la usan desde el primer día
 * para que `RF-AC-006` no tenga que abrir ningún DTO.
 */
public final class AcademyImageUrls {

  public static final String RUTA = "/api/v1/academy-images/";

  private AcademyImageUrls() {}

  /** La dirección de la portada, o nulo cuando no hay (presente y nulo en el JSON). */
  public static String de(UUID coverImageId) {
    return coverImageId == null ? null : RUTA + coverImageId;
  }
}
