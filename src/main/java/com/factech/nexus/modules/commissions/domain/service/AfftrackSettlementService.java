package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementRepository;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementRepository.CountedFtd;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementRepository.FtdLine;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementRepository.NewAfftrackCommission;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementRepository.NewSettlement;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementRepository.PersonProduct;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.OpenBatch;
import com.factech.nexus.modules.commissions.domain.service.AfftrackTierPicker.Pick;
import com.factech.nexus.modules.commissions.domain.service.AfftrackTierPicker.Tier;
import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import com.factech.nexus.modules.system.users.application.SupervisorChain;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-CM-020` — liquidar las comisiones afftrack <b>dentro del cierre</b>.
 *
 * <p>Para cada persona y producto FTD: el remanente de su última liquidación, más los FTD activados
 * antes del corte que aún no se le contaron —los suyos y los de <b>toda su red</b>, con la cadena
 * del día de cada activación (`RN-CM-040`, `RN-CM-042`)—; contra su escala del día del cierre
 * (`RN-CM-039`) se paga el mayor límite alcanzado, una vez, y lo que sobra queda (`RN-CM-041`).
 *
 * <p><b>{@link Propagation#MANDATORY}</b>: solo corre dentro de la transacción del cierre
 * (`RN-CM-043`). Si falla, el cierre entero se revierte —ni pago, ni conteo, ni remanente a
 * medias—, que es lo contrario del barrido y es a propósito (`spec.md` `EX-001`).
 */
@Service
public class AfftrackSettlementService {

  private final AfftrackSettlementRepository liquidaciones;
  private final CommissionBatchRepository lotes;
  private final ProductCatalog productos;
  private final SupervisorChain cadenas;
  private final BusinessCalendar calendario;
  private final UuidV7Generator ids;

  public AfftrackSettlementService(
      AfftrackSettlementRepository liquidaciones,
      CommissionBatchRepository lotes,
      ProductCatalog productos,
      SupervisorChain cadenas,
      BusinessCalendar calendario,
      UuidV7Generator ids) {
    this.liquidaciones = liquidaciones;
    this.lotes = lotes;
    this.productos = productos;
    this.cadenas = cadenas;
    this.calendario = calendario;
    this.ids = ids;
  }

  /** Lo que hizo la liquidación de un cierre. */
  public record SettlementSummary(int liquidaciones, int pagadas) {}

  @Transactional(propagation = Propagation.MANDATORY)
  public SettlementSummary settle(UUID closingId, OffsetDateTime corte) {
    Set<UUID> ftd = productos.ftdProductIds();
    if (ftd.isEmpty()) {
      return new SettlementSummary(0, 0);
    }

    // 1. Los FTD nuevos, contados a toda la cadena del día de su activación.
    Map<PersonProduct, List<CountedFtd>> nuevos = new LinkedHashMap<>();
    for (FtdLine linea : liquidaciones.newFtdLines(ftd, corte)) {
      List<UUID> cadena = cadenas.chainAt(linea.sellerId(), linea.deliveredAt());
      // La cadena lleva guarda de ciclo; aun así, una persona cuenta el FTD una vez.
      Set<UUID> vistas = new LinkedHashSet<>();
      for (int nivel = 0; nivel < cadena.size(); nivel++) {
        UUID persona = cadena.get(nivel);
        if (vistas.add(persona)) {
          nuevos
              .computeIfAbsent(
                  new PersonProduct(persona, linea.productId()), k -> new ArrayList<>())
              .add(new CountedFtd(linea.detailId(), nivel));
        }
      }
    }

    // 2. Quién liquida: quien tiene FTD nuevos o remanente.
    Map<PersonProduct, Integer> remanentes = liquidaciones.carriedOut(ftd);
    Set<PersonProduct> aLiquidar = new LinkedHashSet<>(nuevos.keySet());
    aLiquidar.addAll(remanentes.keySet());
    if (aLiquidar.isEmpty()) {
      return new SettlementSummary(0, 0);
    }

    // 3. Las escalas del día del cierre, en bloque. El día es el del último instante del periodo:
    //    un cierre a las 00:00 del día 1 liquida con lo vigente el 30 (`RN-CM-039`).
    LocalDate dia = calendario.diaDe(corte.minusNanos(1_000));
    Set<UUID> personas = new LinkedHashSet<>();
    Set<UUID> productosALiquidar = new LinkedHashSet<>();
    for (PersonProduct pp : aLiquidar) {
      personas.add(pp.userId());
      productosALiquidar.add(pp.productId());
    }
    Map<PersonProduct, List<Tier>> escalas =
        liquidaciones.scales(personas, productosALiquidar, dia);
    Map<UUID, UUID> monedas = new HashMap<>();
    for (SaleView vista : productos.saleViewOf(productosALiquidar)) {
      monedas.put(vista.id(), vista.currencyId());
    }

    // 4. La cuenta, persona a persona.
    int pagadas = 0;
    for (PersonProduct pp : aLiquidar) {
      int traia = remanentes.getOrDefault(pp, 0);
      List<CountedFtd> contados = nuevos.getOrDefault(pp, List.of());
      Pick eleccion =
          AfftrackTierPicker.elegir(traia + contados.size(), escalas.getOrDefault(pp, List.of()));

      UUID liquidacion = ids.next();
      Tier escalon = eleccion.tier().orElse(null);
      liquidaciones.insertSettlement(
          new NewSettlement(
              liquidacion,
              closingId,
              pp.userId(),
              pp.productId(),
              traia,
              contados.size(),
              eleccion.pagados(),
              eleccion.remanente(),
              escalon == null ? null : escalon.source().name(),
              escalon == null ? null : escalon.rateId(),
              corte));
      liquidaciones.insertFtds(liquidacion, contados, corte);

      if (escalon != null) {
        // En el lote abierto de su persona y moneda, que el cierre cierra a continuación
        // (`RN-CM-033`, `RN-CM-043`).
        OpenBatch lote = lotes.lockOpenBatch(pp.userId(), monedas.get(pp.productId()), corte);
        liquidaciones.insertCommission(
            new NewAfftrackCommission(
                ids.next(),
                lote.id(),
                liquidacion,
                pp.userId(),
                escalon.source().name(),
                escalon.rateId(),
                dia,
                escalon.amountPerFtd(),
                escalon.threshold(),
                escalon.importe(),
                corte));
        lotes.addToTotal(lote.id(), escalon.importe(), corte);
        pagadas++;
      }
    }
    return new SettlementSummary(aLiquidar.size(), pagadas);
  }
}
