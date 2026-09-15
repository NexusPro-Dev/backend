package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ListPackagesRequest;
import com.factech.nexus.modules.products.application.PackageItemSummary;
import com.factech.nexus.modules.products.application.PackagePageResponse;
import com.factech.nexus.modules.products.application.PackageSortField;
import com.factech.nexus.modules.products.domain.models.PackageStatus;
import com.factech.nexus.modules.products.domain.models.ProductScope;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageDetail;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageItemRow;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageRow;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-018`: el listado de paquetes, con la cuenta hecha por fila.
 *
 * <p><b>El número de sentencias no crece con la página</b> (`CA-PM-274`): la página de paquetes, el
 * total, <b>todas</b> las filas de asociación de la página en una sentencia, la moneda de casa y
 * las tasas de las monedas presentes. {@code PackagePricing} y {@code PackageOfferability} corren
 * en Java por paquete sobre lo que ya vino.
 */
@Service
public class ListPackagesService {

  private final ProductPackageQueryRepository consultas;
  private final Pagination paginacion;
  private final ProductExchangeResolver conversiones;

  public ListPackagesService(
      ProductPackageQueryRepository consultas,
      Pagination paginacion,
      ProductExchangeResolver conversiones) {
    this.consultas = consultas;
    this.paginacion = paginacion;
    this.conversiones = conversiones;
  }

  @Transactional(readOnly = true)
  public PackagePageResponse list(ListPackagesRequest filtros) {
    List<FieldError> problemas = new ArrayList<>();
    Pagination.Slice trozo = resolverPaginacion(filtros, problemas);
    PackageSortField.Orden orden = resolverOrden(filtros, problemas);
    String estado =
        canonico(filtros.status(), PackageStatus.values(), "status", "VAL-002", problemas);
    String alcance =
        canonico(filtros.scope(), ProductScope.values(), "scope", "VAL-003", problemas);
    // Los `400` se devuelven JUNTOS (`CA-PM-276`). La forma de `currencyId` no
    // entra aquí: la rechaza el editor canónico de `shared/error` antes.
    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), resumen(problemas), problemas);
    }
    ListPackagesRequest canonicos =
        new ListPackagesRequest(
            filtros.page(),
            filtros.size(),
            filtros.sort(),
            estado,
            alcance,
            filtros.currencyId(),
            filtros.q(),
            filtros.includeDeleted());

    List<PackageRow> pagina =
        consultas.search(canonicos, orden.sql(), trozo.offset(), trozo.size());
    Map<UUID, List<PackageItemRow>> filasPorPaquete =
        consultas.findItemsOf(pagina.stream().map(PackageRow::id).toList()).stream()
            .collect(Collectors.groupingBy(PackageItemRow::packageId));
    List<PackageDetail> detalles =
        pagina.stream()
            .map(p -> new PackageDetail(p, filasPorPaquete.getOrDefault(p.id(), List.of())))
            .toList();

    ProductExchangeResolver.Conversor conversor =
        conversiones.para(pagina.stream().map(PackageRow::currencyId).toList());

    return PackagePageResponse.de(
        PageResponse.de(
            detalles.stream()
                .map(
                    d ->
                        PackageItemSummary.from(
                            d, conversor.de(d.paquete().currencyId(), d.precio().price())))
                .toList(),
            consultas.count(canonicos),
            trozo.page(),
            trozo.size()),
        orden.publico());
  }

  private Pagination.Slice resolverPaginacion(
      ListPackagesRequest filtros, List<FieldError> problemas) {
    try {
      return paginacion.resolver(filtros.page(), filtros.size());
    } catch (ValidationException fallo) {
      fallo.errors().stream()
          .map(error -> new FieldError(error.field(), "VAL-001", error.message()))
          .forEach(problemas::add);
      return new Pagination.Slice(0, 1);
    }
  }

  private static PackageSortField.Orden resolverOrden(
      ListPackagesRequest filtros, List<FieldError> problemas) {
    try {
      return PackageSortField.resolver(filtros.sort());
    } catch (ValidationException fallo) {
      problemas.addAll(fallo.errors());
      return PackageSortField.resolver(null);
    }
  }

  private static <E extends Enum<E>> String canonico(
      String valor, E[] dominio, String campo, String codigo, List<FieldError> problemas) {
    if (valor == null) {
      return null;
    }
    return Arrays.stream(dominio)
        .map(Enum::name)
        .filter(nombre -> nombre.equalsIgnoreCase(valor))
        .findFirst()
        .orElseGet(
            () -> {
              String mensaje =
                  "El valor '"
                      + valor
                      + "' no pertenece al dominio de "
                      + campo
                      + ". Valores admitidos: "
                      + String.join(", ", Arrays.stream(dominio).map(Enum::name).toList())
                      + ".";
              problemas.add(new FieldError(campo, codigo, mensaje));
              return null;
            });
  }

  private static String resumen(List<FieldError> problemas) {
    return problemas.size() == 1
        ? problemas.get(0).message()
        : "La consulta trae " + problemas.size() + " parámetros inválidos.";
  }
}
