package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.AfftrackProgressResponse;
import com.factech.nexus.modules.commissions.application.AfftrackProgressResponse.NextTier;
import com.factech.nexus.modules.commissions.application.AfftrackProgressResponse.Product;
import com.factech.nexus.modules.commissions.application.AfftrackProgressResponse.ReachedTier;
import com.factech.nexus.modules.commissions.application.AfftrackRateResponse;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementRepository;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementRepository.FtdLine;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementRepository.PersonProduct;
import com.factech.nexus.modules.commissions.domain.repository.CommissionClosingRepository;
import com.factech.nexus.modules.commissions.domain.service.AfftrackTierPicker.Next;
import com.factech.nexus.modules.commissions.domain.service.AfftrackTierPicker.Pick;
import com.factech.nexus.modules.commissions.domain.service.AfftrackTierPicker.Tier;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.system.users.application.SupervisorChain;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-CM-030` — mi avance de FTD: <b>la liquidación de {@link AfftrackSettlementService}, sin
 * escribir y para una persona</b>, con el instante de la consulta como corte y la escala de hoy
 * (`RN-CM-056`). Usa las mismas piezas que el cierre, para que si su regla cambia, esto cambie con
 * ella.
 */
@Service
public class GetOwnAfftrackProgressService {

  private final AfftrackSettlementRepository liquidaciones;
  private final CommissionClosingRepository cierres;
  private final ClosingSchedule horario;
  private final ProductCatalog productos;
  private final SupervisorChain cadenas;
  private final BusinessCalendar calendario;

  public GetOwnAfftrackProgressService(
      AfftrackSettlementRepository liquidaciones,
      CommissionClosingRepository cierres,
      ClosingSchedule horario,
      ProductCatalog productos,
      SupervisorChain cadenas,
      BusinessCalendar calendario) {
    this.liquidaciones = liquidaciones;
    this.cierres = cierres;
    this.horario = horario;
    this.productos = productos;
    this.cadenas = cadenas;
    this.calendario = calendario;
  }

  @Transactional(readOnly = true)
  public AfftrackProgressResponse get(UUID yo) {
    OffsetDateTime ahora = calendario.ahora();
    OffsetDateTime inicio = cierres.lastCut().orElse(null);
    OffsetDateTime proximo = horario.enabled() ? horario.next(ahora) : null;

    Set<UUID> ftd = productos.ftdProductIds();
    if (ftd.isEmpty()) {
      return new AfftrackProgressResponse(inicio, proximo, List.of());
    }

    // 1. Los FTD nuevos que me cuentan, con la cadena del día de cada activación (`RN-CM-042`).
    // shortcut: una cadena por cada FTD sin contar de toda la empresa, como el cierre; si la
    // medición lo pide, una sentencia que filtre por mí en el historial (`plan.md` §9, §10).
    Map<UUID, int[]> nuevos = new HashMap<>(); // producto → {directos, de la red}
    for (FtdLine linea : liquidaciones.newFtdLines(ftd, ahora)) {
      int nivel = cadenas.chainAt(linea.sellerId(), linea.deliveredAt()).indexOf(yo);
      if (nivel >= 0) {
        nuevos.computeIfAbsent(linea.productId(), k -> new int[2])[nivel == 0 ? 0 : 1]++;
      }
    }

    // 2. Mi remanente y mi escala de hoy.
    Map<UUID, Integer> remanentes = new HashMap<>();
    liquidaciones
        .carriedOut(ftd)
        .forEach(
            (pp, n) -> {
              if (pp.userId().equals(yo)) {
                remanentes.put(pp.productId(), n);
              }
            });
    Map<PersonProduct, List<Tier>> escalas =
        liquidaciones.scales(Set.of(yo), ftd, calendario.hoy());

    Set<UUID> conAlgo = new LinkedHashSet<>(nuevos.keySet());
    conAlgo.addAll(remanentes.keySet());
    escalas.forEach(
        (pp, escala) -> {
          if (!escala.isEmpty()) {
            conAlgo.add(pp.productId());
          }
        });
    if (conAlgo.isEmpty()) {
      return new AfftrackProgressResponse(inicio, proximo, List.of());
    }

    // 3. La cuenta del cierre, producto a producto (`RN-CM-041`).
    List<SaleView> vistas = new ArrayList<>(productos.saleViewOf(conAlgo));
    vistas.sort(Comparator.comparing(SaleView::code));
    List<Product> salida = new ArrayList<>(vistas.size());
    for (SaleView v : vistas) {
      int[] n = nuevos.getOrDefault(v.id(), new int[2]);
      int traia = remanentes.getOrDefault(v.id(), 0);
      int disponibles = traia + n[0] + n[1];
      List<Tier> escala = escalas.getOrDefault(new PersonProduct(yo, v.id()), List.of());
      Pick alcanzado = AfftrackTierPicker.elegir(disponibles, escala);
      Next siguiente = AfftrackTierPicker.siguiente(disponibles, escala).orElse(null);
      salida.add(
          new Product(
              new AfftrackRateResponse.ProductRef(
                  v.id(),
                  v.code(),
                  v.name(),
                  new AfftrackRateResponse.CurrencyRef(
                      v.currencyId(), v.currencyCode(), v.currencyDecimalPlaces())),
              traia,
              n[0],
              n[1],
              n[0] + n[1],
              disponibles,
              alcanzado
                  .tier()
                  .map(
                      t ->
                          new ReachedTier(
                              t.threshold(), t.amountPerFtd(), t.source().name(), t.importe()))
                  .orElse(null),
              alcanzado.remanente(),
              siguiente == null
                  ? null
                  : new NextTier(
                      siguiente.tier().threshold(),
                      siguiente.tier().amountPerFtd(),
                      siguiente.tier().source().name(),
                      siguiente.faltan())));
    }
    return new AfftrackProgressResponse(inicio, proximo, salida);
  }
}
