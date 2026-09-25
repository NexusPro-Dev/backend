package com.factech.nexus.modules.academy.application;

import com.factech.nexus.shared.video.VideoLink;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del alta de un módulo (`RF-AC-022` §11): título y orden obligatorios; descripciones y
 * video de presentación opcionales. <b>Ni estado, ni lecciones, ni portada, ni curso</b>: el curso
 * va en la ruta, y lo demás lo rechaza {@code FAIL_ON_UNKNOWN_PROPERTIES} (`VAL-006`). Las cinco
 * validaciones de forma se devuelven juntas.
 */
public record RegisterCourseModuleRequest(
    @NotBlank(message = "VAL-002: El título es obligatorio y no puede superar los 150 caracteres.")
        @Size(
            max = 150,
            message = "VAL-002: El título es obligatorio y no puede superar los 150 caracteres.")
        String title,
    @Size(max = 300, message = "VAL-004: La descripción corta no puede exceder 300 caracteres.")
        String shortDescription,
    @Size(
            max = 10_000,
            message = "VAL-004: La descripción larga no puede exceder 10 000 caracteres.")
        String longDescription,
    @Size(
            max = 500,
            message =
                "VAL-005: El enlace del video debe ser un video de YouTube o de Vimeo, sin espacios y de"
                    + " hasta 500 caracteres.")
        @Pattern(
            regexp = VideoLink.PATRON,
            message =
                "VAL-005: El enlace del video debe ser un video de YouTube o de Vimeo, sin espacios y de"
                    + " hasta 500 caracteres.")
        String presentationVideoUrl,
    @NotNull(
            message =
                "VAL-003: El orden es obligatorio y debe ser un entero mayor o igual que cero.")
        @PositiveOrZero(
            message =
                "VAL-003: El orden es obligatorio y debe ser un entero mayor o igual que cero.")
        Integer displayOrder) {

  public RegisterCourseModuleRequest {
    title = title == null ? null : title.trim();
    shortDescription = recortar(shortDescription);
    longDescription = recortar(longDescription);
    presentationVideoUrl = recortar(presentationVideoUrl);
  }

  private static String recortar(String valor) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.trim();
    return recortado.isEmpty() ? null : recortado;
  }
}
