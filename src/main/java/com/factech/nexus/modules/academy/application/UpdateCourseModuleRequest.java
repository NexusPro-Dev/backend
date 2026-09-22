package com.factech.nexus.modules.academy.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Cuerpo de la corrección de un módulo (`RF-AC-023` §11): cinco campos, cada uno ausente, con valor
 * o nulo. El nulo <b>vacía</b> las descripciones y el video; en título y orden <b>se rechaza</b>.
 * {@code courseId}, {@code status}, {@code lessons} y {@code coverImageUrl} son campos no admitidos
 * (`VAL-006`).
 */
public record UpdateCourseModuleRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> title,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> shortDescription,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> longDescription,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> presentationVideoUrl,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Integer> displayOrder) {

  public UpdateCourseModuleRequest {
    title = title == null ? Patchable.ausente() : title;
    shortDescription = shortDescription == null ? Patchable.ausente() : shortDescription;
    longDescription = longDescription == null ? Patchable.ausente() : longDescription;
    presentationVideoUrl =
        presentationVideoUrl == null ? Patchable.ausente() : presentationVideoUrl;
    displayOrder = displayOrder == null ? Patchable.ausente() : displayOrder;
  }

  public boolean informaAlgo() {
    return title.presente()
        || shortDescription.presente()
        || longDescription.presente()
        || presentationVideoUrl.presente()
        || displayOrder.presente();
  }
}
