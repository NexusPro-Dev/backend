package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.CourseDifficulty;
import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de la corrección de un curso (`RF-AC-011` §11): siete campos, cada uno <b>ausente</b>,
 * <b>presente con valor</b> o <b>presente y nulo</b>. El nulo <b>vacía</b> las dos descripciones y
 * el video —también en un curso activo—; en título, instructores, dificultad y orden <b>se
 * rechaza</b>. {@code instructorIds}, desde el 13-10-2026, <b>reemplaza la lista entera</b>
 * (`RF-AC-062`, `RN-AC-033`).
 *
 * <p><b>Sin inmutables</b>: el curso no lleva código. Lo desconocido —{@code status}, las
 * relaciones, {@code modules}, {@code coverImageUrl}, {@code code}— lo rechaza {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} (`VAL-008`).
 */
public record UpdateCourseRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> title,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<List<UUID>> instructorIds,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<CourseDifficulty> difficulty,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> shortDescription,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> longDescription,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> introVideoUrl,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Integer> displayOrder) {

  public UpdateCourseRequest {
    title = title == null ? Patchable.ausente() : title;
    instructorIds = instructorIds == null ? Patchable.ausente() : instructorIds;
    difficulty = difficulty == null ? Patchable.ausente() : difficulty;
    shortDescription = shortDescription == null ? Patchable.ausente() : shortDescription;
    longDescription = longDescription == null ? Patchable.ausente() : longDescription;
    introVideoUrl = introVideoUrl == null ? Patchable.ausente() : introVideoUrl;
    displayOrder = displayOrder == null ? Patchable.ausente() : displayOrder;
  }

  public boolean informaAlgo() {
    return title.presente()
        || instructorIds.presente()
        || difficulty.presente()
        || shortDescription.presente()
        || longDescription.presente()
        || introVideoUrl.presente()
        || displayOrder.presente();
  }
}
