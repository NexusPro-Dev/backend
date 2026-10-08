package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.NextClosingResponse;
import com.factech.nexus.modules.commissions.domain.models.PaymentMode;
import com.factech.nexus.modules.commissions.domain.repository.CommissionClosingRepository;
import com.factech.nexus.modules.commissions.domain.repository.PaymentChoiceRepository;
import com.factech.nexus.modules.commissions.domain.repository.PaymentChoiceRepository.PaymentChoice;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Elegir si el pago del próximo cierre es automático o manual</b> (`RF-CM-029`, `RN-CM-054`).
 *
 * <p><b>El bloqueo del turno va antes que la comprobación de la ventana</b>, y es el mismo que toma
 * el cierre al abrir ese turno: si el cierre ya lo abrió, su fila está escrita y esto responde
 * {@code EX-002}; si no, el cierre espera y lee esta elección confirmada (`CA-CM-389`).
 */
@Service
public class ChoosePaymentModeService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_payment_choices";

  private final ClosingSchedule horario;
  private final PaymentChoiceRepository elecciones;
  private final CommissionClosingRepository cierres;
  private final GetNextClosingService consulta;
  private final BusinessCalendar calendario;
  private final UuidV7Generator ids;
  private final AuditWriter auditoria;

  public ChoosePaymentModeService(
      ClosingSchedule horario,
      PaymentChoiceRepository elecciones,
      CommissionClosingRepository cierres,
      GetNextClosingService consulta,
      BusinessCalendar calendario,
      UuidV7Generator ids,
      AuditWriter auditoria) {
    this.horario = horario;
    this.elecciones = elecciones;
    this.cierres = cierres;
    this.consulta = consulta;
    this.calendario = calendario;
    this.ids = ids;
    this.auditoria = auditoria;
  }

  @Transactional
  public NextClosingResponse choose(PaymentMode modo, UUID actor) {
    GetNextClosingService.exigirEncendido(horario);
    OffsetDateTime ahora = calendario.ahora();
    OffsetDateTime turno = horario.next(ahora);
    elecciones.lockTurn(turno);
    OffsetDateTime apertura = horario.windowOpensAt(turno);
    if (ahora.isBefore(apertura) || cierres.existsScheduled(turno)) {
      String mensaje =
          "La ventana para elegir cómo se paga el próximo cierre está cerrada; se abre el "
              + apertura
              + ".";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("paymentMode", "EX-002", mensaje)));
    }

    Optional<PaymentChoice> anterior = elecciones.find(turno);
    UUID fila = elecciones.upsert(ids.next(), turno, modo, actor, ahora);
    auditar(fila, turno, anterior, modo);
    return consulta.respuesta(turno, ahora);
  }

  private void auditar(
      UUID fila, OffsetDateTime turno, Optional<PaymentChoice> anterior, PaymentMode modo) {
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("scheduled_for", turno.toString());
    despues.put("payment_mode", modo.name());
    Map<String, Object> cambios = new LinkedHashMap<>();
    anterior.ifPresent(
        a ->
            cambios.put(
                "before",
                Map.of("scheduled_for", turno.toString(), "payment_mode", a.mode().name())));
    cambios.put("after", despues);
    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            fila,
            anterior.isPresent() ? ChangeAction.UPDATE : ChangeAction.CREATE,
            cambios));
  }
}
