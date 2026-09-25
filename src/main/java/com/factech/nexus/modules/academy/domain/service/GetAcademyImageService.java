package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.domain.models.AcademyImage;
import com.factech.nexus.modules.academy.domain.repository.AcademyImageRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-032`: los bytes de una portada de academia, sin autenticación.
 *
 * <p><b>Una sentencia, y no mira a quién pertenece</b>: sirve la portada de una categoría retirada
 * o de un curso inactivo igual, como `RF-PM-016`. El `404` es el mismo para lo que nunca existió,
 * lo reemplazado y lo quitado.
 */
@Service
public class GetAcademyImageService {

  private final AcademyImageRepository imagenes;

  public GetAcademyImageService(AcademyImageRepository imagenes) {
    this.imagenes = imagenes;
  }

  @Transactional(readOnly = true)
  public AcademyImage get(UUID id) {
    return imagenes
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("EX-001", "La imagen no existe."));
  }
}
