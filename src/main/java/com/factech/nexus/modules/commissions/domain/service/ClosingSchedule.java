package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * <b>Cuándo es el próximo cierre programado</b>, y cuándo se abre su ventana para elegir cómo se
 * paga (`RN-CM-054`; `specs/cm/028-consultar-proximo-cierre/plan.md` §1).
 *
 * <p><b>Solo calcula</b>: quien dispara sigue siendo {@code CommissionClosingJob}, que no existe
 * cuando el cierre está apagado. Este componente sí existe siempre, y por eso puede decir que está
 * apagado.
 */
@Component
public class ClosingSchedule {

  /** La ventana: «2 días antes» del cierre. */
  public static final Duration VENTANA = Duration.ofHours(48);

  private final boolean encendido;
  private final CronExpression expresion;
  private final ZoneId zona;

  @Autowired
  public ClosingSchedule(
      @Value("${nexus.commissions.closing.enabled:true}") boolean encendido,
      @Value("${nexus.commissions.closing.cron}") String cron,
      BusinessCalendar calendario) {
    this(encendido, cron, calendario.zona());
  }

  public ClosingSchedule(boolean encendido, String cron, ZoneId zona) {
    this.encendido = encendido;
    this.expresion = CronExpression.parse(cron);
    this.zona = zona;
  }

  /** Si el cierre programado está encendido (`RN-CM-035`). */
  public boolean enabled() {
    return encendido;
  }

  /**
   * El próximo disparo, <b>estrictamente posterior</b> a {@code ahora}: a la hora exacta de un
   * turno, el próximo es el siguiente (`FA-002`).
   */
  public OffsetDateTime next(OffsetDateTime ahora) {
    ZonedDateTime disparo = expresion.next(ahora.atZoneSameInstant(zona));
    if (disparo == null) {
      throw new IllegalStateException("La expresión del cierre no tiene próximo disparo.");
    }
    return disparo.toOffsetDateTime();
  }

  /** Cuándo se abre la ventana para elegir cómo se paga ese turno. */
  public OffsetDateTime windowOpensAt(OffsetDateTime turno) {
    return turno.minus(VENTANA);
  }
}
