package com.factech.nexus.modules.products.application;

import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.patch.PatchableStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El cuerpo de la corrección de una reseña (`RF-PM-010`).
 *
 * <p>Semántica de {@code PATCH}: lo que no viene no cambia. Se conserva {@link Patchable} aunque
 * <b>ningún campo admita el nulo explícito</b> —los dos son obligatorios en la columna— para que
 * ese nulo se rechace con el mensaje de la spec en lugar de confundirse con ausente, que es el
 * fallo silencioso que `RF-SP-027` pagó con {@code Optional}.
 *
 * <p>{@code productId} y {@code userId} no se declaran: son campos desconocidos y el cuerpo entero
 * se rechaza (`VAL-004`).
 */
public record UpdateProductCommentRequest(
    @Schema(description = "Puntuación nueva, entera de 1 a 5. No admite nulo.")
        @JsonDeserialize(using = PatchableRatingDeserializer.class)
        Patchable<Integer> rating,
    @Schema(description = "Texto nuevo, de 1 a 1000 caracteres tras recortar. No admite nulo.")
        @JsonDeserialize(using = PatchableStringDeserializer.class)
        Patchable<String> comment) {

  /** El campo que Jackson no vio llega como {@code null} al constructor canónico. */
  public UpdateProductCommentRequest {
    rating = rating == null ? Patchable.ausente() : rating;
    comment = comment == null ? Patchable.ausente() : comment;
  }

  public boolean informaAlgo() {
    return rating.presente() || comment.presente();
  }
}
