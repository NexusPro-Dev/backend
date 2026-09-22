package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseCategoryDetailResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-003`: el detalle de una categoría, con sus cursos.
 *
 * <p>Es {@link CourseCategoryDetailReader} bajo una transacción de solo lectura, y nada más. <b>La
 * retirada no es un {@code 404}</b>: se devuelve con su fecha, su motivo y sus cursos vivos, como
 * el paquete en `RF-PM-019`.
 */
@Service
public class GetCourseCategoryService {

  private final CourseCategoryDetailReader detalle;

  public GetCourseCategoryService(CourseCategoryDetailReader detalle) {
    this.detalle = detalle;
  }

  @Transactional(readOnly = true)
  public CourseCategoryDetailResponse detail(UUID id) {
    return detalle.leer(id);
  }
}
