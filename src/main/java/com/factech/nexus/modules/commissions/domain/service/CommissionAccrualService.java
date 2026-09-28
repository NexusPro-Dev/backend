package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.models.AccrualOutcome;
import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualRepository.AccrualRow;
import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualRepository.NewCommission;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.OpenBatch;
import com.factech.nexus.modules.commissions.domain.service.ChainCommissionCalculator.Level;
import com.factech.nexus.modules.commissions.domain.service.ChainCommissionCalculator.LevelCommission;
import com.factech.nexus.modules.commissions.domain.service.ChainCommissionCalculator.Verdict;
import com.factech.nexus.modules.movements.application.CommissionableLines;
import com.factech.nexus.modules.movements.application.CommissionableLines.CommissionableLine;
import com.factech.nexus.modules.system.users.application.SupervisorChain;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <b>Devengar las comisiones de una línea de venta</b> (`RF-CM-013`, `RN-CM-031`).
 *
 * <p><b>Una transacción por línea</b>, abierta aquí con {@code REQUIRES_NEW} y dentro de un bucle
 * que captura la excepción de cada una: una línea que falla no arrastra a sus vecinas, y <b>ninguna
 * excepción sale de este servicio</b> (`CA-CM-166`). Lo llama un escuchador {@code AFTER_COMMIT}, y
 * una excepción que saliera de él llegaría a quien confirmó la venta, que ya está confirmada.
 *
 * <p><b>Se relee, no se confía en el aviso</b>: cada línea se busca en lo que `MV` publica y se
 * comprueba que siga siendo comisionable. Por eso el mismo servicio sirve al aviso, al barrido y al
 * reintento.
 *
 * <p><b>Dos fechas distintas</b> (`RN-CM-024`, `RN-CM-033`): la tasa y la cadena se resuelven con
 * el instante de la venta —el día, en la zona del negocio—; el lote lo decide el instante del
 * devengo.
 */
@Service
public class CommissionAccrualService {

  private static final Logger LOG = LoggerFactory.getLogger(CommissionAccrualService.class);
  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_accruals";

  private final CommissionableLines lineas;
  private final SupervisorChain cadenas;
  private final ResolveCommissionService resolucion;
  private final CommissionAccrualRepository desenlaces;
  private final CommissionBatchRepository lotes;
  private final BusinessCalendar calendario;
  private final AuditWriter auditoria;
  private final TransactionTemplate porLinea;

  public CommissionAccrualService(
      CommissionableLines lineas,
      SupervisorChain cadenas,
      ResolveCommissionService resolucion,
      CommissionAccrualRepository desenlaces,
      CommissionBatchRepository lotes,
      BusinessCalendar calendario,
      AuditWriter auditoria,
      PlatformTransactionManager transacciones) {
    this.lineas = lineas;
    this.cadenas = cadenas;
    this.resolucion = resolucion;
    this.desenlaces = desenlaces;
    this.lotes = lotes;
    this.calendario = calendario;
    this.auditoria = auditoria;
    this.porLinea = new TransactionTemplate(transacciones);
    this.porLinea.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Las líneas de un aviso, o las que el barrido encontró sin desenlace. */
  public AccrualSummary accrue(Collection<UUID> detailIds) {
    return atender(detailIds, false);
  }

  /** Las rechazadas, otra vez (`RN-CM-034`). */
  public AccrualSummary retryRejected() {
    return atender(desenlaces.rejected(), true);
  }

  private AccrualSummary atender(Collection<UUID> detailIds, boolean reintento) {
    AccrualSummary.Builder resumen = new AccrualSummary.Builder();
    for (UUID id : detailIds) {
      try {
        resumen.contar(porLinea.execute(estado -> atenderLinea(id, reintento)));
      } catch (RuntimeException e) {
        // `EX-001` y `EX-002`: la línea se queda sin desenlace y la recoge el
        // siguiente barrido. No se escribe nada más: la ausencia ES la constancia.
        LOG.error("No se pudo devengar la línea {}; la recogerá el siguiente cierre.", id, e);
        resumen.fallo();
      }
    }
    return resumen.build();
  }

  /**
   * @return el desenlace escrito, o vacío si la línea no se atendió (`FA-001`)
   */
  private Optional<AccrualOutcome> atenderLinea(UUID id, boolean reintento) {
    desenlaces.lockLine(id);
    Optional<AccrualRow> previo = desenlaces.find(id);
    if (previo.isPresent() && !(reintento && previo.get().outcome() == AccrualOutcome.RECHAZADA)) {
      return Optional.empty();
    }
    Optional<CommissionableLine> hallada = lineas.of(List.of(id)).stream().findFirst();
    if (hallada.isEmpty()) {
      return Optional.empty();
    }
    CommissionableLine linea = hallada.get();
    LocalDate diaDeVenta = calendario.diaDe(linea.occurredAt());

    List<Level> niveles = new ArrayList<>();
    List<UUID> cadena = cadenas.chainAt(linea.sellerId(), linea.occurredAt());
    for (int nivel = 0; nivel < cadena.size(); nivel++) {
      UUID persona = cadena.get(nivel);
      niveles.add(
          new Level(persona, nivel, resolucion.rateFor(persona, linea.productId(), diaDeVenta)));
    }
    Verdict veredicto =
        ChainCommissionCalculator.calcular(linea.unitPrice(), linea.quantity(), niveles);

    OffsetDateTime ahora = calendario.ahora();
    // Los lotes se toman en orden de persona: dos líneas cuyas cadenas se
    // crucen no pueden bloquearse la una a la otra en orden contrario.
    List<LevelCommission> cobran = new ArrayList<>(veredicto.commissions());
    cobran.sort(Comparator.comparing(LevelCommission::userId));
    for (LevelCommission c : cobran) {
      OpenBatch lote = lotes.lockOpenBatch(c.userId(), linea.currencyId(), ahora);
      OffsetDateTime devengo = lote.periodStart().isAfter(ahora) ? lote.periodStart() : ahora;
      desenlaces.insertCommission(
          new NewCommission(
              lote.id(),
              id,
              c.userId(),
              c.level(),
              c.rate().source(),
              c.rate().rateId(),
              diaDeVenta,
              c.rate().rateType(),
              c.rate().value(),
              linea.unitPrice(),
              linea.quantity(),
              c.amount(),
              devengo));
      lotes.addToTotal(lote.id(), c.amount(), ahora);
    }

    if (previo.isPresent()) {
      desenlaces.updateOutcome(id, veredicto.outcome(), veredicto.reason(), ahora);
    } else {
      desenlaces.insertOutcome(id, veredicto.outcome(), veredicto.reason(), ahora);
    }
    auditar(id, linea, veredicto, previo, reintento);
    return Optional.of(veredicto.outcome());
  }

  private void auditar(
      UUID id,
      CommissionableLine linea,
      Verdict veredicto,
      Optional<AccrualRow> previo,
      boolean reintento) {
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("movement_id", linea.movementId().toString());
    despues.put("outcome", veredicto.outcome().name());
    despues.put("commissions", veredicto.commissions().size());
    if (veredicto.reason() != null) {
      despues.put("reason", veredicto.reason());
    }
    despues.put("retry", reintento);
    Map<String, Object> cambios = new LinkedHashMap<>();
    previo.ifPresent(p -> cambios.put("before", Map.of("outcome", p.outcome().name())));
    cambios.put("after", despues);
    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            id,
            previo.isPresent() ? ChangeAction.UPDATE : ChangeAction.CREATE,
            cambios));
  }

  /** Cuántas líneas acabaron en cada desenlace, para el resumen del cierre (`RF-CM-009`). */
  public record AccrualSummary(
      int devengadas, int sinComision, int rechazadas, int ignoradas, int fallidas) {

    public int atendidas() {
      return devengadas + sinComision + rechazadas;
    }

    static final class Builder {
      private int devengadas;
      private int sinComision;
      private int rechazadas;
      private int ignoradas;
      private int fallidas;

      void contar(Optional<AccrualOutcome> desenlace) {
        if (desenlace == null || desenlace.isEmpty()) {
          ignoradas++;
          return;
        }
        switch (desenlace.get()) {
          case DEVENGADA -> devengadas++;
          case SIN_COMISION -> sinComision++;
          case RECHAZADA -> rechazadas++;
        }
      }

      void fallo() {
        fallidas++;
      }

      AccrualSummary build() {
        return new AccrualSummary(devengadas, sinComision, rechazadas, ignoradas, fallidas);
      }
    }
  }
}
