package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.repository.ProductCommentQueryRepository.CommentRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Una reseña en la lista pública (`RF-PM-012`).
 *
 * <p><b>{@code author} son dos cadenas, y la proyección no tiene dónde poner nada más.</b> Ni
 * identificador, ni nombre de usuario, ni correo, ni estado, ni roles: lo que sostiene `RN-PM-030`
 * no es un filtro sino que este registro <b>no tenga el campo</b>, igual que {@code OfferItem} no
 * tiene el costo. Es un registro propio y no {@code SellerRef} del hotlink, aunque hoy tengan la
 * misma forma: ampliar aquella no debe ampliar esta sin que nadie lo decida.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductCommentPublicItem(
    UUID id,
    int rating,
    String comment,
    Author author,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  /** Nombre y apellido. Nada más (`RN-PM-030`). */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Author(String firstName, String lastName) {}

  public static ProductCommentPublicItem from(CommentRow fila) {
    return new ProductCommentPublicItem(
        fila.id(),
        fila.rating(),
        fila.comment(),
        new Author(fila.authorFirstName(), fila.authorLastName()),
        enUtc(fila.createdAt()),
        enUtc(fila.updatedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
