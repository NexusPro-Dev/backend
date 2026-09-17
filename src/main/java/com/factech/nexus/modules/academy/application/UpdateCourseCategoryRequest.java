package com.factech.nexus.modules.academy.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Cuerpo de la corrección de una categoría (`RF-AC-004` §11): nombre, descripción, color, icono y
 * orden, cada uno <b>ausente</b>, <b>presente con valor</b> o <b>presente y nulo</b>. El nulo
 * <b>vacía</b> la descripción; en los otros cuatro <b>se rechaza</b>.
 *
 * <p><b>Sin inmutables</b>: la categoría no lleva código ni moneda, de modo que no hay nada que
 * declarar para rechazarlo con mensaje propio. Lo desconocido —{@code coverImageUrl}, {@code
 * courses}, {@code status}— lo rechaza {@code FAIL_ON_UNKNOWN_PROPERTIES} (`VAL-007`).
 */
public record UpdateCourseCategoryRequest(
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> name,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> description,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> color,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> icon,
    @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Integer> displayOrder) {

  public UpdateCourseCategoryRequest {
    name = name == null ? Patchable.ausente() : name;
    description = description == null ? Patchable.ausente() : description;
    color = color == null ? Patchable.ausente() : color;
    icon = icon == null ? Patchable.ausente() : icon;
    displayOrder = displayOrder == null ? Patchable.ausente() : displayOrder;
  }

  public boolean informaAlgo() {
    return name.presente()
        || description.presente()
        || color.presente()
        || icon.presente()
        || displayOrder.presente();
  }
}
