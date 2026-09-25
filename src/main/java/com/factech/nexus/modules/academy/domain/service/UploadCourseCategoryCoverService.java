package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseCategoryDetailResponse;
import com.factech.nexus.modules.academy.domain.models.AcademyImage;
import com.factech.nexus.modules.academy.domain.models.CourseCategory;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-006`: subir o reemplazar la portada de una categoría.
 *
 * <p>{@code UploadPackageCoverService} con otra entidad: el archivo antes que la base, la categoría
 * viva y bloqueada, y lo demás en {@link CoverUploader}. <b>Sin condición</b>: una categoría sin
 * portada se pinta con su color y su icono, y subirla nunca la deja peor.
 */
@Service
public class UploadCourseCategoryCoverService {

  private final CourseCategoryRepository categorias;
  private final CoverUploader portadas;
  private final CourseCategoryDetailReader detalle;

  public UploadCourseCategoryCoverService(
      CourseCategoryRepository categorias,
      CoverUploader portadas,
      CourseCategoryDetailReader detalle) {
    this.categorias = categorias;
    this.portadas = portadas;
    this.detalle = detalle;
  }

  @Transactional
  public CourseCategoryDetailResponse upload(UUID id, byte[] bytes) {
    // Primero el archivo, y sin base de por medio: los tres `VAL` salen de aquí.
    AcademyImage nueva = portadas.preparar(bytes);

    CourseCategory categoria =
        categorias
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una categoría viva con ese identificador."));

    portadas.colocar(categoria, nueva, categorias::flush, CourseCategoryDetailReader.ENTIDAD);
    return detalle.leer(categoria.getId());
  }
}
