package com.factech.nexus.modules.academy.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

/** Las peticiones de las categorías de las clases en vivo (`RF-AC-064` a `RF-AC-066`). */
public final class LiveSessionCategoryRequests {

  private LiveSessionCategoryRequests() {}

  /** Registrar (`RF-AC-064`). */
  @Schema(name = "RegisterLiveSessionCategoryRequest")
  public record Register(
      @Schema(description = "De 1 a 100 caracteres; único entre las vivas.") String name,
      @Schema(description = "Entero mayor o igual que cero; no único.") Integer displayOrder) {}

  /** Corregir (`RF-AC-065`): solo lo que viene, y ninguno se vacía. */
  @Schema(name = "UpdateLiveSessionCategoryRequest")
  public record Update(
      @JsonDeserialize(using = PatchableDeserializer.class) Patchable<String> name,
      @JsonDeserialize(using = PatchableDeserializer.class) Patchable<Integer> displayOrder) {

    public Update {
      name = name == null ? Patchable.ausente() : name;
      displayOrder = displayOrder == null ? Patchable.ausente() : displayOrder;
    }

    public boolean informaAlgo() {
      return name.presente() || displayOrder.presente();
    }
  }

  /** Retirar (`RF-AC-066`): el motivo, que valida {@code DeletionReason}. */
  @Schema(name = "DeleteLiveSessionCategoryRequest")
  public record Delete(String reason) {}
}
