package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.LessonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del alta de una lección (`RF-AC-028` §11): tipo, título, duración y orden obligatorios;
 * descripción, contenido y {@code open} opcionales. <b>El contenido no se valida aquí</b>: su forma
 * depende del tipo (`VAL-006`, solo si es {@code VIDEO}) y la decide {@code LessonContent} en el
 * agregado, después de que Bean Validation haya devuelto juntos los demás. {@code status}, {@code
 * moduleId} y {@code courseId} son campos no admitidos (`VAL-008`).
 */
public record RegisterLessonRequest(
    @NotNull(message = "VAL-002: El tipo es obligatorio y debe ser VIDEO o TEXTO.") LessonType type,
    @NotBlank(message = "VAL-003: El título es obligatorio y no puede superar los 150 caracteres.")
        @Size(
            max = 150,
            message = "VAL-003: El título es obligatorio y no puede superar los 150 caracteres.")
        String title,
    @Size(max = 1000, message = "VAL-007: La descripción no puede exceder 1000 caracteres.")
        String description,
    String content,
    @NotNull(
            message =
                "VAL-004: La duración es obligatoria y debe ser un entero de minutos mayor que"
                    + " cero.")
        @Positive(
            message =
                "VAL-004: La duración es obligatoria y debe ser un entero de minutos mayor que"
                    + " cero.")
        Integer durationMinutes,
    @NotNull(
            message =
                "VAL-005: El orden es obligatorio y debe ser un entero mayor o igual que cero.")
        @PositiveOrZero(
            message =
                "VAL-005: El orden es obligatorio y debe ser un entero mayor o igual que cero.")
        Integer displayOrder,
    Boolean open) {

  public RegisterLessonRequest {
    title = title == null ? null : title.trim();
    description = description == null || description.isBlank() ? null : description.trim();
    // El contenido se recorta solo por los extremos y en el agregado: un
    // Markdown conserva sus saltos de línea.
  }
}
