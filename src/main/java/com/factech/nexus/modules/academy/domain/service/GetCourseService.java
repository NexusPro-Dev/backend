package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-010`: el detalle de un curso, con sus relaciones y su árbol.
 *
 * <p>Es {@link CourseDetailReader} bajo una transacción de solo lectura, y nada más. <b>El retirado
 * no es un {@code 404}</b>: se devuelve con su fecha, su motivo y su árbol marcado, como el paquete
 * en `RF-PM-019`.
 */
@Service
public class GetCourseService {

  private final CourseDetailReader detalle;

  public GetCourseService(CourseDetailReader detalle) {
    this.detalle = detalle;
  }

  @Transactional(readOnly = true)
  public CourseDetailResponse detail(UUID id) {
    return detalle.leer(id);
  }
}
