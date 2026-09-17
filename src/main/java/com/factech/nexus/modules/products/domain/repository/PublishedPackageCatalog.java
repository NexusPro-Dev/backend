package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.application.PackageCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.products.domain.models.PackageOfferability;
import com.factech.nexus.modules.products.domain.models.ProductScope;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageDetail;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageItemRow;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageRow;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de {@link PackageCatalog}, la lectura que `PM` publica para vender un paquete
 * (`RF-MV-012` · `T-02`).
 *
 * <h2>Reutiliza, y no reescribe</h2>
 *
 * <p><b>La sentencia es la del detalle</b> ({@link ProductPackageQueryRepository#findDetail}) y
 * <b>la decisión es la del detalle</b> ({@link PackageDetail#ofrecibilidad}): un paquete vencido,
 * uno inactivo, uno con un producto retirado y uno correcto responden aquí <b>lo mismo</b> que
 * `RF-PM-019` publica en {@code offerable} y {@code offerableReason}. Es la tarea donde se decide
 * si la venta envejece bien: si copiara el predicado, el día que `PM` añada una condición —otra
 * fecha, otro alcance— la venta seguiría con el viejo y <b>nada fallaría</b>.
 *
 * <p><b>Lo único que se añade es el alcance</b>, porque {@link PackageOfferability} no lo mira: la
 * oferta lo filtra en el {@code WHERE} de {@code findOfferable} y el detalle no lo necesita. Aquí
 * hay que decirlo con palabras, porque un paquete que solo se publica por hotlink <b>existe y no se
 * ofrece a nadie por la tienda</b>, y eso es `EX-002` y no `EX-001`.
 *
 * <p><b>Y a quién se le ofrece lo responde el mismo predicado que la oferta</b>, {@link
 * PackageOfferability#correspondeA} — que se movió allí desde {@code GetOwnOfferService} el
 * 17-09-2026 justamente para que las dos lecturas no pudieran decir cosas distintas.
 *
 * <h2>Dos sentencias, y no una</h2>
 *
 * <p>El detalle trae las filas del paquete con lo que la cuenta necesita, pero la venta copia
 * <b>más</b> —descripción, vigencia, destino del upgrade, decimales de la moneda—, y eso es
 * exactamente {@link ProductCatalog#saleViewOf}. Se llama a esa lectura en lugar de ensanchar la
 * sentencia del detalle: así el producto dentro de un paquete se vende <b>con la misma vista</b>
 * con la que se vende suelto, y un campo que `MV` copie mañana llega a las dos ventas a la vez.
 */
@Repository
public class PublishedPackageCatalog implements PackageCatalog {

  private final ProductPackageQueryRepository consultas;
  private final ProductCatalog productos;
  private final CurrentMembershipLookup membresias;
  private final Clock reloj;

  @Autowired
  public PublishedPackageCatalog(
      ProductPackageQueryRepository consultas,
      ProductCatalog productos,
      CurrentMembershipLookup membresias) {
    this(consultas, productos, membresias, Clock.systemUTC());
  }

  // «Hoy» en UTC, el mismo reloj que `PackageDetailReader` y que la oferta
  // (`RN-PM-047`): el corte del día es una decisión pendiente y única, y no se
  // adelanta aquí.
  PublishedPackageCatalog(
      ProductPackageQueryRepository consultas,
      ProductCatalog productos,
      CurrentMembershipLookup membresias,
      Clock reloj) {
    this.consultas = consultas;
    this.productos = productos;
    this.membresias = membresias;
    this.reloj = reloj;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PackageSaleView> storeSaleViewOf(UUID packageId, UUID buyerId) {
    if (packageId == null || buyerId == null) {
      return Optional.empty();
    }
    Optional<PackageDetail> detalle = consultas.findDetail(packageId);
    // El retirado se colapsa con el inexistente A PROPÓSITO (`EX-001` de
    // `RF-MV-012`): al detalle de administración le sirve distinguirlos porque
    // desde ahí se arregla; a quien compra, un paquete retirado no existe.
    if (detalle.isEmpty() || detalle.get().paquete().retirado()) {
      return Optional.empty();
    }
    PackageDetail paquete = detalle.get();
    PackageRow fila = paquete.paquete();

    PackageOfferability ofrecibilidad = paquete.ofrecibilidad(LocalDate.now(reloj));
    boolean ofrecible = ofrecibilidad.offerable();
    String motivo = ofrecibilidad.reason();
    if (ofrecible && !enLaTienda(fila.scope())) {
      ofrecible = false;
      motivo = "El paquete no se publica en la tienda.";
    }

    // Vacío significa «hoy no tiene nivel», y con ello solo le corresponden los
    // paquetes sin upgrade — la misma lectura y el mismo nulo que la oferta.
    UUID membresia = membresias.currentMembershipOf(buyerId).map(m -> (UUID) m.id()).orElse(null);
    boolean leCorresponde =
        PackageOfferability.correspondeA(paquete.origenesDeSusUpgrades(), membresia);

    return Optional.of(
        new PackageSaleView(
            fila.id(),
            fila.code(),
            fila.name(),
            fila.currencyId(),
            fila.currencyCode(),
            fila.currencyDecimalPlaces(),
            ofrecible,
            motivo,
            leCorresponde,
            lineas(paquete)));
  }

  /** Las líneas en el orden del paquete, cada una con su vista de venta y su rebaja declarada. */
  private List<PackageSaleLine> lineas(PackageDetail paquete) {
    List<UUID> ids = paquete.items().stream().map(PackageItemRow::productId).toList();
    Map<UUID, SaleView> vistas = new HashMap<>();
    for (SaleView vista : productos.saleViewOf(ids)) {
      vistas.put(vista.id(), vista);
    }
    int decimales = paquete.paquete().currencyDecimalPlaces();
    List<PackageSaleLine> lineas = new ArrayList<>(ids.size());
    for (PackageItemRow item : paquete.items()) {
      SaleView vista = vistas.get(item.productId());
      if (vista == null) {
        // La fila de asociación referencia un producto que la otra sentencia
        // no devolvió: no es un caso de negocio, es una base inconsistente.
        throw new IllegalStateException(
            "El paquete «%s» referencia un producto que el catálogo no resuelve: %s"
                .formatted(paquete.paquete().code(), item.productId()));
      }
      lineas.add(
          new PackageSaleLine(
              vista,
              item.descuento().getType().name(),
              // Con la escala de su forma, como lo publica la oferta: `10.00`
              // el porcentaje, `1.00` el fijo en una moneda de dos.
              item.descuento().valorEnEscala(decimales)));
    }
    return lineas;
  }

  private static boolean enLaTienda(String scope) {
    return ProductScope.valueOf(scope).enTienda();
  }
}
