package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleLineTeamTransfer;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovedLine;
import com.factech.nexus.modules.system.teams.application.SellerTeamLookup;
import com.factech.nexus.modules.system.users.application.CommercialReach;
import com.factech.nexus.modules.system.users.application.CommercialReach.Kind;
import com.factech.nexus.modules.system.users.application.CommercialReach.Reach;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RN-MV-080`: <b>un cambio de superior se lleva la oficina de lo vendido en el periodo
 * abierto</b>.
 *
 * <p>La misma forma que `RF-MV-058` ({@link FillLineTeamsService}): bloquear las ventas, preguntar
 * las oficinas de hoy a `teams` en lote, una sentencia por equipo y un {@code UPDATE} de auditoría
 * por venta. Difiere en qué toca: las líneas del vendedor <b>y de su red</b> desde el corte, que
 * tengan otra oficina o ninguna. Quien no tiene oficina hoy no tiene entrada, y su línea se queda
 * como estaba.
 */
@Service
public class TransferLineTeamsService implements SaleLineTeamTransfer {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  private final MovementRepository movimientos;
  private final SellerTeamLookup equipos;
  private final CommercialReach alcance;
  private final AuditWriter auditoria;

  public TransferLineTeamsService(
      MovementRepository movimientos,
      SellerTeamLookup equipos,
      CommercialReach alcance,
      AuditWriter auditoria) {
    this.movimientos = movimientos;
    this.equipos = equipos;
    this.alcance = alcance;
    this.auditoria = auditoria;
  }

  @Override
  @Transactional
  public int transfer(UUID sellerId, OffsetDateTime since) {
    // Su red con él dentro; quien además es funcionario alcanza «todo» y se queda en él solo.
    Reach red = alcance.reachOf(sellerId);
    Set<UUID> vendedores = new LinkedHashSet<>();
    vendedores.add(sellerId);
    if (red.kind() == Kind.NETWORK) {
      vendedores.addAll(red.sellers());
    }

    movimientos.lockSalesOfSellersSince(vendedores, since);

    Map<UUID, List<UUID>> vendedoresPorEquipo = new LinkedHashMap<>();
    equipos
        .currentTeamsOf(vendedores)
        .forEach(
            (vendedor, equipo) ->
                vendedoresPorEquipo.computeIfAbsent(equipo, e -> new ArrayList<>()).add(vendedor));

    Map<UUID, List<MovedLine>> porVenta = new LinkedHashMap<>();
    int movidas = 0;
    for (Map.Entry<UUID, List<UUID>> grupo : vendedoresPorEquipo.entrySet()) {
      for (MovedLine linea : movimientos.moveLineTeam(grupo.getKey(), grupo.getValue(), since)) {
        porVenta.computeIfAbsent(linea.movementId(), v -> new ArrayList<>()).add(linea);
        movidas++;
      }
    }

    porVenta.forEach(this::auditar);
    return movidas;
  }

  private void auditar(UUID venta, List<MovedLine> lineas) {
    List<Map<String, Object>> antes = new ArrayList<>();
    List<Map<String, Object>> despues = new ArrayList<>();
    for (MovedLine linea : lineas) {
      Map<String, Object> a = new LinkedHashMap<>();
      a.put("product_id", linea.productId().toString());
      a.put("team_id", linea.fromTeamId() == null ? null : linea.fromTeamId().toString());
      antes.add(a);
      Map<String, Object> d = new LinkedHashMap<>();
      d.put("product_id", linea.productId().toString());
      d.put("team_id", linea.toTeamId().toString());
      despues.add(d);
    }
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("lines", antes));
    cambios.put("after", Map.of("lines", despues));
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, venta, ChangeAction.UPDATE, cambios));
  }
}
