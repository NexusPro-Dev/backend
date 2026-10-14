package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.LiveSessionCategory;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Una categoría de las clases en vivo (`RF-AC-063` a `RF-AC-065`). */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "LiveSessionCategoryResponse")
public record LiveSessionCategoryResponse(
    UUID id, String name, int displayOrder, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

  public static LiveSessionCategoryResponse from(LiveSessionCategory categoria) {
    return new LiveSessionCategoryResponse(
        categoria.getId(),
        categoria.getName(),
        categoria.getDisplayOrder(),
        categoria.getCreatedAt(),
        categoria.getUpdatedAt());
  }
}
