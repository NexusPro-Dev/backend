package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.LineTeamFillResponse;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.FilledLine;
import com.factech.nexus.modules.system.teams.application.SellerTeamLookup;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-MV-058`: <b>rellenar la oficina de las líneas de venta que no la tienen</b>, con
 * la vigente HOY del director de la cadena de su vendedor (`RN-MV-078`).
 *
 * <p><b>Por qué la de hoy y no la del día de la venta.</b> Todo lo vendido antes de que hubiera
 * directores con equipo daría vacío con la regla del registro, y ese es el problema de partida. Por
 * eso esto es una orden de administración y no una migración: a la hora de {@code V99} nadie tenía
 * equipo.
 *
 * <p><b>Una transacción, cuatro sentencias, y a Java solo suben los vendedores</b> (`plan.md` §1):
 * bloquear las ventas afectadas en orden de identificador —la venta antes que sus líneas, el orden
 * de `RF-MV-016`—, leer los vendedores distintos, preguntar sus oficinas a `teams` en lote y
 * escribir una sentencia por equipo. <b>{@code team_id IS NULL} va dentro del {@code UPDATE}</b>:
 * es lo que garantiza que una línea con oficina no se toca, no una comprobación previa.
 *
 * <p><b>Repetible</b>: una segunda orden no encuentra nada vacío —o solo lo que ningún director
 * cubre todavía— y responde cero sin escribir.
 */
@Service
public class FillLineTeamsService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  private final MovementRepository movimientos;
  private final SellerTeamLookup equipos;
  private final AuditWriter auditoria;

  public FillLineTeamsService(
      MovementRepository movimientos, SellerTeamLookup equipos, AuditWriter auditoria) {
    this.movimientos = movimientos;
    this.equipos = equipos;
    this.auditoria = auditoria;
  }

  @Transactional
  public LineTeamFillResponse fill() {
    if (movimientos.lockSalesWithLinesWithoutTeam() == 0) {
      return new LineTeamFillResponse(0);
    }

    List<UUID> vendedores = movimientos.findSellersOfLinesWithoutTeam();
    Map<UUID, UUID> oficinaDe = equipos.currentTeamsOf(vendedores);

    // Por equipo: una sentencia por oficina y no una por vendedor. Quien no tiene
    // entrada —un manager, o sin director con equipo— se queda fuera.
    Map<UUID, List<UUID>> vendedoresPorEquipo = new LinkedHashMap<>();
    oficinaDe.forEach(
        (vendedor, equipo) ->
            vendedoresPorEquipo.computeIfAbsent(equipo, e -> new ArrayList<>()).add(vendedor));

    Map<UUID, List<FilledLine>> porVenta = new LinkedHashMap<>();
    int rellenadas = 0;
    for (Map.Entry<UUID, List<UUID>> grupo : vendedoresPorEquipo.entrySet()) {
      for (FilledLine linea : movimientos.fillLineTeam(grupo.getKey(), grupo.getValue())) {
        porVenta.computeIfAbsent(linea.movementId(), v -> new ArrayList<>()).add(linea);
        rellenadas++;
      }
    }

    porVenta.forEach(this::auditar);
    return new LineTeamFillResponse(rellenadas);
  }

  /**
   * Un {@code UPDATE} por venta tocada, como la asignación de vendedores (`RF-MV-016`): la pregunta
   * «¿cuándo ganó esta venta su oficina, y cuál?» se responde mirando su historia. Una entrada con
   * el total no tendría a qué entidad apuntar: {@code entity_id} es obligatorio.
   */
  private void auditar(UUID venta, List<FilledLine> lineas) {
    List<Map<String, Object>> antes = new ArrayList<>();
    List<Map<String, Object>> despues = new ArrayList<>();
    for (FilledLine linea : lineas) {
      Map<String, Object> a = new LinkedHashMap<>();
      a.put("product_id", linea.productId().toString());
      // Nulo y PRESENTE: la línea no tenía oficina.
      a.put("team_id", null);
      antes.add(a);
      Map<String, Object> d = new LinkedHashMap<>();
      d.put("product_id", linea.productId().toString());
      d.put("team_id", linea.teamId().toString());
      despues.add(d);
    }
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("lines", antes));
    cambios.put("after", Map.of("lines", despues));
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, venta, ChangeAction.UPDATE, cambios));
  }
}
