package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.HotlinkCatalogResponse;
import com.factech.nexus.modules.products.application.OfferItem;
import com.factech.nexus.modules.products.application.ProductLinkResponse;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository.ProductRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-PM-027` — el catálogo de hotlinks: lo que un vendedor puede repartir.
 *
 * <p><b>La oferta sin la membresía</b>: la misma secuencia que {@link GetOwnOfferService} —una
 * sentencia, la conversión en bloque, dos listas sin reordenar— menos el primer paso. Aquí no hay
 * {@code CurrentActor} ni {@code CurrentMembershipLookup} a propósito: el vendedor no compra lo que
 * reparte, y un {@code BECA → ORO} le interesa aunque él esté en {@code ORO} (`spec.md` `FA-003`).
 *
 * <p>Lo que se lista es {@code RN-PM-021} visto entero: exactamente el conjunto que el hotlink
 * público resuelve enlace a enlace. <b>No reserva nada</b>: que un producto aparezca aquí no
 * promete que siga publicable cuando el enlace se abra.
 */
@Service
public class GetHotlinkCatalogService {

  private final ProductQueryRepository consultas;
  private final ProductExchangeResolver conversiones;
  private final ProductLinkReader enlaces;

  public GetHotlinkCatalogService(
      ProductQueryRepository consultas,
      ProductExchangeResolver conversiones,
      ProductLinkReader enlaces) {
    this.consultas = consultas;
    this.conversiones = conversiones;
    this.enlaces = enlaces;
  }

  @Transactional(readOnly = true)
  public HotlinkCatalogResponse catalog() {
    List<ProductRow> filas = consultas.findHotlinkCatalog();

    // La conversión de todo el catálogo en dos consultas como máximo, y no dos
    // por producto: se ve contando sentencias (`CA-PM-347`), no en el cuerpo.
    ProductExchangeResolver.Conversor conversor =
        conversiones.para(filas.stream().map(ProductRow::currencyId).toList());

    List<OfferItem> upgrades = new ArrayList<>();
    List<OfferItem> bots = new ArrayList<>();
    // Se separa por tipo SIN reordenar: la sentencia ya devolvió los upgrades
    // por nivel de destino y los bots por fecha de alta (`CA-PM-343`).
    // La misma forma que la oferta, y por compartirla hereda que los enlaces
    // lleguen resueltos y sin el cupon: tener token no es administrar
    // (`CA-PM-399`).
    Map<UUID, List<ProductLinkResponse>> enlacesDelCatalogo =
        enlaces.publicablesDe(filas.stream().map(ProductRow::id).toList());

    for (ProductRow fila : filas) {
      OfferItem producto =
          OfferItem.from(
              fila,
              enlacesDelCatalogo.getOrDefault(fila.id(), List.of()),
              conversor.de(fila.currencyId(), fila.price()));
      if (producto.type() == ProductType.UPGRADE_MEMBRESIA) {
        upgrades.add(producto);
      } else {
        bots.add(producto);
      }
    }
    return HotlinkCatalogResponse.de(upgrades, bots);
  }
}
