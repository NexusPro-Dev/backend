package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.AcademyImage;
import java.util.Optional;
import java.util.UUID;

/** Las portadas de academia (`RF-AC-006`, `RF-AC-014`, `RF-AC-032`), como las de `PM`. */
public interface AcademyImageRepository {

  /** Inserta y vuelca: la columna que la va a señalar exige que exista antes. */
  AcademyImage save(AcademyImage imagen);

  /** La única lectura de `AC` que carga {@code content}: la que sirve la imagen. */
  Optional<AcademyImage> findById(UUID id);

  /**
   * Borra la imagen que dejó de ser portada, <b>después</b> de que la columna haya dejado de
   * señalarla y ese cambio se haya volcado: las tres claves foráneas no tienen {@code ON DELETE}.
   */
  void deleteById(UUID id);
}
