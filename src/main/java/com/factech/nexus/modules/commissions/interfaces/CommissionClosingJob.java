package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Dispara el cierre del periodo a su hora (`RF-CM-009`, `RN-CM-035`).
 *
 * <p><b>Separado del servicio</b>, como {@code ExpiredTokenPurgeJob}: aquí solo vive <i>cuándo</i>,
 * y el <i>qué</i> está en {@link CloseCommissionPeriodService}, que es lo que las pruebas invocan.
 *
 * <p><b>La frecuencia es de configuración</b> ({@code nexus.commissions.closing.cron}) y corre en
 * la <b>zona del negocio</b>, no en UTC: cerrar «a las 00:00 del día 1» es la medianoche de Bogotá.
 * <b>Se puede apagar</b>, y la suite lo apaga.
 *
 * <p><b>El turno es la hora nominal y no la real</b>: dos réplicas que disparan a la vez tienen que
 * nombrar el mismo turno para que la fila única deje pasar a una sola, y {@code now()} difiere en
 * milisegundos entre ellas.
 */
@Component
@ConditionalOnProperty(
    name = "nexus.commissions.closing.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CommissionClosingJob {

  private static final Logger LOG = LoggerFactory.getLogger(CommissionClosingJob.class);

  private final CloseCommissionPeriodService cierre;
  private final BusinessCalendar calendario;
  private final CronExpression expresion;

  public CommissionClosingJob(
      CloseCommissionPeriodService cierre,
      BusinessCalendar calendario,
      @Value("${nexus.commissions.closing.cron}") String cron) {
    this.cierre = cierre;
    this.calendario = calendario;
    this.expresion = CronExpression.parse(cron);
  }

  /**
   * Una excepción aquí no puede tumbar el planificador: Spring cancelaría los cierres futuros. Se
   * registra, y el barrido del siguiente turno recoge lo que este no cerró.
   */
  @Scheduled(cron = "${nexus.commissions.closing.cron}", zone = "${nexus.business.zone}")
  public void ejecutar() {
    try {
      OffsetDateTime turno = turnoNominal(calendario.ahora().atZoneSameInstant(calendario.zona()));
      cierre
          .closeScheduled(turno)
          .ifPresentOrElse(
              hecho ->
                  LOG.info(
                      "Cierre de comisiones {}: {} lotes, {} líneas barridas",
                      hecho.id(),
                      hecho.batchesClosed(),
                      hecho.linesSwept()),
              () -> LOG.info("El turno {} lo cerró otra instancia", turno));
    } catch (RuntimeException fallo) {
      LOG.error("El cierre de comisiones falló; lo recogerá el siguiente turno", fallo);
    }
  }

  /** El disparo que acaba de ocurrir: el primero de la expresión posterior a hace un minuto. */
  OffsetDateTime turnoNominal(ZonedDateTime ahora) {
    ZonedDateTime disparo = expresion.next(ahora.minusMinutes(1));
    if (disparo == null || disparo.isAfter(ahora)) {
      return ahora.truncatedTo(ChronoUnit.SECONDS).toOffsetDateTime();
    }
    return disparo.toOffsetDateTime();
  }
}
