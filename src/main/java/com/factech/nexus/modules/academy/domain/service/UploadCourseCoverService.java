package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.domain.models.AcademyImage;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-014`: subir o reemplazar la portada de un curso.
 *
 * <p>`RF-AC-006` con el curso, sobre {@link CoverUploader}. <b>En cualquier estado</b>: un curso
 * {@code INACTIVO}, sin módulos y sin membresías recibe su portada igual — es lo que se arma antes
 * de publicar. <b>El alta no admite la imagen</b> (`ac.md` §5.2.9): la portada se sube con esta
 * operación justo después.
 */
@Service
public class UploadCourseCoverService {

  private final CourseRepository cursos;
  private final CoverUploader portadas;
  private final CourseDetailReader detalle;

  public UploadCourseCoverService(
      CourseRepository cursos, CoverUploader portadas, CourseDetailReader detalle) {
    this.cursos = cursos;
    this.portadas = portadas;
    this.detalle = detalle;
  }

  @Transactional
  public CourseDetailResponse upload(UUID id, byte[] bytes) {
    AcademyImage nueva = portadas.preparar(bytes);
    Course curso = ClassifyCourseService.cursoVivo(cursos, id);
    portadas.colocar(curso, nueva, cursos::flush, CourseDetailReader.ENTIDAD);
    return detalle.leer(curso.getId());
  }
}
