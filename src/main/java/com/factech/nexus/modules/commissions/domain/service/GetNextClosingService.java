package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.NextClosingResponse;
import com.factech.nexus.modules.commissions.domain.models.PaymentMode;
import com.factech.nexus.modules.commissions.domain.repository.PaymentChoiceRepository;
import com.factech.nexus.modules.commissions.domain.repository.PaymentChoiceRepository.PaymentChoice;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Consultar el próximo cierre y cómo se pagará</b> (`RF-CM-028`, `RN-CM-054`).
 *
 * <p>También arma la respuesta de `RF-CM-029`, que es la misma con la elección ya hecha.
 */
@Service
public class GetNextClosingService {

  private final ClosingSchedule horario;
  private final PaymentChoiceRepository elecciones;
  private final BusinessCalendar calendario;

  public GetNextClosingService(
      ClosingSchedule horario, PaymentChoiceRepository elecciones, BusinessCalendar calendario) {
    this.horario = horario;
    this.elecciones = elecciones;
    this.calendario = calendario;
  }

  @Transactional(readOnly = true)
  public NextClosingResponse get() {
    exigirEncendido(horario);
    OffsetDateTime ahora = calendario.ahora();
    return respuesta(horario.next(ahora), ahora);
  }

  /** La respuesta de un turno, leyendo su elección en la transacción de quien llama. */
  NextClosingResponse respuesta(OffsetDateTime turno, OffsetDateTime ahora) {
    OffsetDateTime apertura = horario.windowOpensAt(turno);
    boolean abierta = !ahora.isBefore(apertura) && ahora.isBefore(turno);
    Optional<PaymentChoice> eleccion = elecciones.find(turno);
    return new NextClosingResponse(
        turno,
        apertura,
        abierta,
        eleccion.map(PaymentChoice::mode).orElse(PaymentMode.AUTOMATICO).name(),
        eleccion.isPresent(),
        eleccion.map(PaymentChoice::chosenBy).orElse(null),
        eleccion.map(PaymentChoice::chosenAt).orElse(null));
  }

  /** `EX-001`: sin cierre programado no hay próximo cierre. */
  static void exigirEncendido(ClosingSchedule horario) {
    if (!horario.enabled()) {
      String mensaje = "El cierre programado está apagado: no hay próximo cierre.";
      throw new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("closing", "EX-001", mensaje)));
    }
  }
}
