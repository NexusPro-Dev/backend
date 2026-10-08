package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionClosingResponse;
import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionClosingRepository;
import com.factech.nexus.modules.commissions.domain.service.CommissionAccrualService.AccrualSummary;
import com.factech.nexus.modules.movements.application.CommissionableLines;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <b>Cerrar el periodo de comisiones</b> (`RF-CM-009`, `RN-CM-033` a `RN-CM-035`).
 *
 * <p><b>Una transacción externa sostiene el bloqueo consultivo de principio a fin</b>, y dentro de
 * ella el barrido abre las suyas —una por línea, en otras conexiones del pool— de modo que el
 * bloqueo sigue tomado mientras barre. Al final, en la externa, los lotes abiertos pasan a {@code
 * PENDIENTE}. <b>La constancia va en su propia transacción</b>, para que un fallo del cierre no la
 * borre (`EX-002`).
 *
 * <p><b>Contra las réplicas decide la fila del turno, no el bloqueo</b>: un bloqueo solo ordena, y
 * la instancia que llegase segunda cerraría otra vez con un periodo de medio segundo
 * (`requirements/cm.md` v0.20.0 §7.8). La fila se confirma antes de empezar, y la instancia que
 * choca se va sin hacer nada.
 */
@Service
public class CloseCommissionPeriodService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_closings";
  private static final int TANDA = 500;
  private static final String MOTIVO_VACIO =
      "RN-CM-052: el lote abierto no tenía comisiones y se borró al cerrar el periodo.";

  private final CommissionClosingRepository cierres;
  private final CommissionableLines lineas;
  private final CommissionAccrualRepository desenlaces;
  private final CommissionAccrualService devengo;
  private final AfftrackSettlementService afftrack;
  private final DeleteEmptyBatchesService vacios;
  private final BusinessCalendar calendario;
  private final UuidV7Generator ids;
  private final AuditWriter auditoria;
  private final TransactionTemplate aparte;

  public CloseCommissionPeriodService(
      CommissionClosingRepository cierres,
      CommissionableLines lineas,
      CommissionAccrualRepository desenlaces,
      CommissionAccrualService devengo,
      AfftrackSettlementService afftrack,
      DeleteEmptyBatchesService vacios,
      BusinessCalendar calendario,
      UuidV7Generator ids,
      AuditWriter auditoria,
      PlatformTransactionManager transacciones) {
    this.cierres = cierres;
    this.lineas = lineas;
    this.desenlaces = desenlaces;
    this.devengo = devengo;
    this.afftrack = afftrack;
    this.vacios = vacios;
    this.calendario = calendario;
    this.ids = ids;
    this.auditoria = auditoria;
    this.aparte = new TransactionTemplate(transacciones);
    this.aparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * El cierre del reloj.
   *
   * @param turno la hora nominal del disparo, la misma en todas las instancias
   * @return vacío si otra instancia ya tomó el turno (`FA-001`)
   */
  public Optional<CommissionClosingResponse> closeScheduled(OffsetDateTime turno) {
    UUID id = ids.next();
    Boolean tomado = aparte.execute(estado -> cierres.openScheduled(id, turno, calendario.ahora()));
    if (!Boolean.TRUE.equals(tomado)) {
      return Optional.empty();
    }
    return Optional.of(
        aparte.execute(
            estado -> {
              // Espera a un cierre manual en curso: aquí no hay nadie a quien responder.
              cierres.lock();
              return cerrar(id, null);
            }));
  }

  /** El cierre a mano, para relanzar el que no corrió (`RF-CM-009`, `CA-CM-176`). */
  public CommissionClosingResponse closeManually(UUID actor) {
    return aparte.execute(
        estado -> {
          if (!cierres.tryLock()) {
            String mensaje = "Hay un cierre en curso: espera a que termine.";
            throw new BusinessRuleException(
                "EX-001", mensaje, List.of(new FieldError("closing", "EX-001", mensaje)));
          }
          UUID id = ids.next();
          OffsetDateTime inicio = calendario.ahora();
          aparte.executeWithoutResult(t -> cierres.openManual(id, actor, inicio));
          return cerrar(id, actor);
        });
  }

  private CommissionClosingResponse cerrar(UUID id, UUID actor) {
    AccrualSummary barrido = barrer();
    AccrualSummary reintento = devengo.retryRejected();
    int reintentadas = reintento.atendidas() + reintento.ignoradas() + reintento.fallidas();

    // `RN-CM-043` (29-09-2026): lo afftrack se liquida DESPUÉS del barrido y ANTES de cerrar, en
    // esta misma transacción —si falla, no se cierra nada—. El instante del cierre es posterior al
    // corte: un lote que la liquidación abre nace en el corte, y `ck_commission_batches_periodo`
    // exige un fin mayor que el inicio.
    OffsetDateTime corte = calendario.ahora();
    afftrack.settle(id, corte);
    OffsetDateTime ahora = posterior(calendario.ahora(), corte);
    int lotes = cierres.closeOpenBatches(id, ahora);
    // `RN-CM-052` (08-10-2026): los abiertos que no se cerraron por vacíos se borran, en esta
    // misma transacción (`plan.md` §15).
    int borrados = vacios.deleteEmpty(MOTIVO_VACIO, BatchStatus.ABIERTO).deletedCount();
    cierres.finish(id, ahora, lotes, barrido.atendidas(), reintentadas, reintento.devengadas());

    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("origin", actor == null ? "PROGRAMADO" : "MANUAL");
    despues.put("closed_at", ahora.toString());
    despues.put("batches_closed", lotes);
    despues.put("empty_batches_deleted", borrados);
    despues.put("lines_swept", barrido.atendidas());
    despues.put("lines_retried", reintentadas);
    despues.put("lines_recovered", reintento.devengadas());
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, id, ChangeAction.UPDATE, Map.of("after", despues)));

    return cierres
        .find(id)
        .map(CommissionClosingResponse::from)
        .orElseThrow(() -> new IllegalStateException("El cierre desapareció."));
  }

  /**
   * `RN-CM-034`: las líneas comisionables <b>sin desenlace</b>, recorridas por clave en tandas. Lo
   * que devenga entra en el lote abierto de este momento, y por tanto en este cierre.
   */
  private static OffsetDateTime posterior(OffsetDateTime ahora, OffsetDateTime corte) {
    OffsetDateTime minimo = corte.plusNanos(1_000);
    return ahora.isBefore(minimo) ? minimo : ahora;
  }

  private AccrualSummary barrer() {
    List<UUID> pendientes = new ArrayList<>();
    UUID cursor = null;
    while (true) {
      List<UUID> tanda = lineas.idsAfter(cursor, TANDA);
      if (tanda.isEmpty()) {
        break;
      }
      Set<UUID> atendidas = desenlaces.withOutcome(tanda);
      for (UUID linea : tanda) {
        if (!atendidas.contains(linea)) {
          pendientes.add(linea);
        }
      }
      cursor = tanda.get(tanda.size() - 1);
      if (tanda.size() < TANDA) {
        break;
      }
    }
    return devengo.accrue(pendientes);
  }
}
