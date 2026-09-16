package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.HotlinkResponse;
import com.factech.nexus.modules.products.application.PackageDetailResponse.DiscountRef;
import com.factech.nexus.modules.products.application.PackageHotlinkResponse;
import com.factech.nexus.modules.products.application.PackageHotlinkResponse.Item;
import com.factech.nexus.modules.products.application.PackageHotlinkResponse.PackageRef;
import com.factech.nexus.modules.products.application.ProductImageUrls;
import com.factech.nexus.modules.products.domain.models.PackagePricing;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PublishedPackage;
import com.factech.nexus.modules.system.users.application.PublicSellerLookup;
import com.factech.nexus.modules.system.users.application.PublicSellerLookup.PublicSellerView;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-026`: el hotlink de un paquete, sin autenticación.
 *
 * <p><b>Compone lo que ya existe y no vuelve a decidir nada.</b> El vendedor sale de {@link
 * PublicSellerLookup}; la cuenta, de {@link PackagePricing}; la ofrecibilidad, de {@code
 * PackageOfferability}; y cada producto, de la misma fila y la misma forma que el hotlink del
 * producto. <b>El orden es el del coste</b>: vendedor, paquete con productos, ofrecibilidad sobre
 * las filas que ya vinieron —sin sentencia—, y la tasa solo si todo lo anterior salió.
 *
 * <p><b>Un solo {@code 404} para todo lo que no procede</b>, con el mismo cuerpo que el hotlink del
 * producto — también para el paquete que hoy no se puede ofrecer: distinguirlo publicaría, sin
 * token, que el paquete existe y qué le pasa. Quien tiene que saberlo es administración, y lo sabe
 * por `RF-PM-019` con permiso.
 */
@Service
public class GetPackageHotlinkService {

  private final ProductPackageQueryRepository paquetes;
  private final PublicSellerLookup vendedores;
  private final ProductExchangeResolver conversiones;

  public GetPackageHotlinkService(
      ProductPackageQueryRepository paquetes,
      PublicSellerLookup vendedores,
      ProductExchangeResolver conversiones) {
    this.paquetes = paquetes;
    this.vendedores = vendedores;
    this.conversiones = conversiones;
  }

  @Transactional(readOnly = true)
  public PackageHotlinkResponse hotlink(String username, String code) {
    PublicSellerView vendedor =
        vendedores.findSellerByUsername(username).orElseThrow(GetPackageHotlinkService::noExiste);
    PublishedPackage publicado =
        paquetes.findPublishedByCode(code).orElseThrow(GetPackageHotlinkService::noExiste);
    // Decidido en Java y no en el WHERE, para que `RN-PM-039` viva en un sitio.
    if (!publicado.ofrecibilidad().offerable()) {
      throw noExiste();
    }

    PackagePricing cuenta = publicado.precio();
    UUID moneda = publicado.paquete().currencyId();
    // UN conversor para el paquete y sus líneas: todas están en su moneda, y la
    // tasa se pide una vez (`CA-PM-334`).
    ProductExchangeResolver.Conversor conversor = conversiones.para(List.of(moneda));

    List<Item> items =
        publicado.items().stream()
            .map(
                linea ->
                    new Item(
                        GetHotlinkService.producto(
                            linea.producto(), conversor.de(moneda, linea.producto().price())),
                        new DiscountRef(
                            linea.descuento().getType(),
                            linea
                                .descuento()
                                .valorEnEscala(publicado.paquete().currencyDecimalPlaces())),
                        cuenta.precioDe(linea.producto().id())))
            .toList();

    return new PackageHotlinkResponse(
        new HotlinkResponse.SellerRef(vendedor.firstName(), vendedor.lastName()),
        new PackageRef(
            publicado.paquete().code(),
            publicado.paquete().name(),
            publicado.paquete().description(),
            ProductImageUrls.de(publicado.paquete().coverImageId()),
            new HotlinkResponse.CurrencyRef(
                publicado.paquete().currencyCode(), publicado.paquete().currencyDecimalPlaces()),
            items,
            cuenta.listPrice(),
            cuenta.price(),
            cuenta.savings(),
            conversor.de(moneda, cuenta.price())));
  }

  /** El mismo mensaje que `RF-PM-008` `EX-001`, para que ni el texto distinga. */
  private static ResourceNotFoundException noExiste() {
    return new ResourceNotFoundException("EX-001", "El enlace solicitado no existe.");
  }
}
