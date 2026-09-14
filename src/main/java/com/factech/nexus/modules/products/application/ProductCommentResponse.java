package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductComment;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * La reseña tal como la ve su autor: la forma que comparten el alta (`RF-PM-009`), la corrección
 * (`RF-PM-010`) y la lectura de la propia (`RF-PM-013`).
 *
 * <p><b>Sin autor.</b> Quien la recibe es quien la escribió, y publicar su nombre en su propia
 * respuesta no le dice nada. La forma pública —con el nombre— es {@link ProductCommentPublicItem},
 * y no son dos formas del mismo dato: son dos lectores distintos.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductCommentResponse(
    UUID id,
    UUID productId,
    int rating,
    String comment,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static ProductCommentResponse from(ProductComment resena) {
    return new ProductCommentResponse(
        resena.getId(),
        resena.getProductId(),
        resena.getRating(),
        resena.getComment(),
        enUtc(resena.getCreatedAt()),
        enUtc(resena.getUpdatedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
