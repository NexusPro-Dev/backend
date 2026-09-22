package com.factech.nexus.modules.products.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * `RF-PM-027`: lo que un vendedor puede repartir — el catálogo de hotlinks.
 *
 * <p><b>La forma de la oferta sin {@code currentMembership}</b>: la membresía de quien llama no
 * interviene —el vendedor no compra lo que reparte— y publicar la forma de la oferta con ese campo
 * siempre nulo diría que a veces no lo es. Las dos listas llevan {@link OfferItem}, la forma de
 * venta: sin {@code purchasePrice}, con {@code price}, {@code exchange}, {@code links} —el video
 * resuelto, sin el cupón—, {@code coverImageUrl} y {@code rating}.
 *
 * <p><b>No trae el enlace armado.</b> El vendedor conoce su nombre de usuario (`RF-SP-039`) y la
 * forma de la ruta pública —{@code /api/v1/hotlinks/{username}/{code}}— es contrato; componerlo
 * aquí costaría una lectura de la persona por página. Los paquetes entrarán como segunda lista con
 * `RF-PM-026`, como la oferta ganó {@code packages}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HotlinkCatalogResponse(
    OfferResponse.Offered upgrades, OfferResponse.Offered services) {

  public static HotlinkCatalogResponse de(List<OfferItem> upgrades, List<OfferItem> bots) {
    return new HotlinkCatalogResponse(
        new OfferResponse.Offered(upgrades), new OfferResponse.Offered(bots));
  }
}
