package com.factech.nexus.modules.academy.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Filtros del catálogo del alumno (`RF-AC-033` §6.1): categoría, dificultad y {@code
 * onlyAccessible}. Sin paginación ni orden; el actor sale del token.
 */
public record ClassroomCatalogRequest(
    @Schema(description = "Solo los cursos clasificados en esta categoría viva.") UUID categoryId,
    @Schema(description = "PRINCIPIANTE, INTERMEDIO o AVANZADO.") String difficulty,
    @Schema(
            description =
                "true deja solo los cursos que abren algo a quien pregunta: el curso entero"
                    + " (accessible) o al menos una lección abierta (openLessonCount > 0).")
        Boolean onlyAccessible) {

  public ClassroomCatalogRequest {
    difficulty = difficulty == null || difficulty.isBlank() ? null : difficulty.trim();
  }

  public boolean soloAccesibles() {
    return Boolean.TRUE.equals(onlyAccessible);
  }
}
