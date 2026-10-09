package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LessonProgressResponse;
import com.factech.nexus.modules.academy.application.ReportLessonProgressRequest;
import com.factech.nexus.modules.academy.domain.models.LessonProgress;
import com.factech.nexus.modules.academy.domain.models.LessonType;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.ProgressRow;
import com.factech.nexus.modules.academy.domain.repository.LessonQueryRepository.ClassroomLessonRow;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-039`: el reproductor dice hasta dónde va.
 *
 * <p><b>Las puertas de `RF-AC-035`</b> ({@link ClassroomLessonGate}) y después <b>una sentencia</b>
 * que guarda el máximo y completa al 90 % (`RN-AC-021`). La posición se acota y el umbral se
 * calcula aquí, con {@link LessonProgress}, para que el SQL no sepa del 90 %. <b>No se audita</b>
 * (`requirements/ac.md` §5.2.14): la fila guarda sus tres fechas.
 */
@Service
public class ReportLessonProgressService {

  static final String TEXTO_NO_REPORTA =
      "Una lección de texto se completa al abrirla; no reporta avance.";

  private final ClassroomLessonGate puertas;
  private final LessonProgressRepository progreso;
  private final StudentKeys llaves;

  public ReportLessonProgressService(
      ClassroomLessonGate puertas, LessonProgressRepository progreso, StudentKeys llaves) {
    this.puertas = puertas;
    this.progreso = progreso;
    this.llaves = llaves;
  }

  @Transactional
  public LessonProgressResponse report(
      UUID courseId, UUID lessonId, ReportLessonProgressRequest peticion) {
    Integer posicion = peticion == null ? null : peticion.positionSeconds();
    if (posicion == null || posicion < 0) {
      String mensaje = "La posición es obligatoria y no puede ser negativa.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("positionSeconds", "VAL-002", mensaje)));
    }

    ClassroomLessonRow fila = puertas.pass(courseId, lessonId);
    if (LessonType.TEXTO.name().equals(fila.lesson().type())) {
      throw new UnprocessableEntityException(
          "EX-003", TEXTO_NO_REPORTA, List.of(new FieldError(null, "EX-003", TEXTO_NO_REPORTA)));
    }

    int duracion = fila.lesson().durationSeconds();
    ProgressRow guardado =
        progreso.report(
            llaves.actorId(),
            lessonId,
            LessonProgress.acotar(posicion, duracion),
            LessonProgress.umbral(duracion));
    return LessonProgressResponse.from(guardado, duracion);
  }
}
