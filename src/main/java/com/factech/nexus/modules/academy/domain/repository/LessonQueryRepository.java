package com.factech.nexus.modules.academy.domain.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * La lectura de una lección entera, <b>con su contenido</b>, para las operaciones que la devuelven
 * (`RF-AC-028` a `RF-AC-030`) y para el detalle de administración (`RF-AC-036`), que la lee en
 * cualquier estado. La pertenencia en los tres niveles va en el {@code WHERE}.
 */
public interface LessonQueryRepository {

  Optional<LessonDetailRow> findDetail(UUID courseId, UUID moduleId, UUID lessonId);

  /** La lección como sale de la tabla, contenido incluido. */
  record LessonDetailRow(
      UUID id,
      UUID moduleId,
      UUID courseId,
      String type,
      String title,
      String description,
      String content,
      int durationSeconds,
      int displayOrder,
      boolean open,
      String status,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {

    public boolean retirada() {
      return deletedAt != null;
    }
  }
}
