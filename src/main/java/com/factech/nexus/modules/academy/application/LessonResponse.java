package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.repository.LessonQueryRepository.LessonDetailRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * La lección entera, <b>con su contenido</b>: la respuesta de sus escrituras (`RF-AC-028` a
 * `RF-AC-030`) y del detalle de administración (`RF-AC-036`), que le añade {@code deletedAt} y
 * {@code deletionReason} solo cuando está retirada. Es la forma que lleva el contenido hacia
 * administración: el detalle del curso y el del módulo no lo traen.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "LessonResponse")
public record LessonResponse(
    UUID id,
    UUID moduleId,
    UUID courseId,
    String type,
    String title,
    String description,
    String content,
    int durationMinutes,
    int displayOrder,
    boolean open,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String deletionReason) {

  public static LessonResponse from(LessonDetailRow fila, String motivoDeRetiro) {
    return new LessonResponse(
        fila.id(),
        fila.moduleId(),
        fila.courseId(),
        fila.type(),
        fila.title(),
        fila.description(),
        fila.content(),
        fila.durationMinutes(),
        fila.displayOrder(),
        fila.open(),
        fila.status(),
        fila.createdAt(),
        fila.updatedAt(),
        fila.deletedAt(),
        motivoDeRetiro);
  }
}
