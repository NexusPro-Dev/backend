package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.LessonType;
import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Cuerpo de la corrección de una lección (`RF-AC-029` §11): siete campos, cada uno ausente, con
 * valor o nulo. El nulo <b>vacía</b> la descripción y el contenido; en tipo, título, duración,
 * orden y {@code open} <b>se rechaza</b>. {@code moduleId}, {@code courseId} y {@code status} son
 * campos no admitidos (`VAL-006`).
 */
public record UpdateLessonRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<LessonType> type,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> title,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> description,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> content,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Integer> durationSeconds,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Integer> displayOrder,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Boolean> open) {

  public UpdateLessonRequest {
    type = type == null ? Patchable.ausente() : type;
    title = title == null ? Patchable.ausente() : title;
    description = description == null ? Patchable.ausente() : description;
    content = content == null ? Patchable.ausente() : content;
    durationSeconds = durationSeconds == null ? Patchable.ausente() : durationSeconds;
    displayOrder = displayOrder == null ? Patchable.ausente() : displayOrder;
    open = open == null ? Patchable.ausente() : open;
  }

  public boolean informaAlgo() {
    return type.presente()
        || title.presente()
        || description.presente()
        || content.presente()
        || durationSeconds.presente()
        || displayOrder.presente()
        || open.presente();
  }
}
