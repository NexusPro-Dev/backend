package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * El periodo de un indicador (`RF-IN-001` · `T-04`, `spec.md` §6.1 y §11): sus valores por defecto,
 * su validación y su traducción a instantes.
 *
 * <p><b>Se pide en días y no en instantes</b>, al revés que los listados: un indicador se pregunta
 * en días —«septiembre»— y quien lo pide no tendría que calcular a qué hora UTC empieza el uno de
 * septiembre en Bogotá. Por dentro sigue siendo <b>semiabierto</b>: del comienzo del primer día al
 * comienzo del día siguiente al último, en la zona del negocio (`RN-IN-007`).
 */
@Component
public class SalesPeriodResolver {

  /** El año con bisiesto: cualquier comparación anual cabe, y el libro de varios años no. */
  static final long MAXIMO_DE_DIAS = 366;

  private final BusinessCalendar calendario;

  public SalesPeriodResolver(BusinessCalendar calendario) {
    this.calendario = calendario;
  }

  /**
   * El periodo efectivo, o nulo si no es válido; los problemas se añaden a {@code problemas} para
   * devolverlos junto a los demás.
   *
   * <p>Sin fechas, el mes en curso hasta hoy. Un «desde» sin «hasta» acaba hoy. Un «hasta» sin
   * «desde» empieza el primero de <b>su</b> mes, para que pedir «hasta el 31 de agosto» no dé un
   * rango invertido.
   */
  public IndicatorPeriod resolve(LocalDate from, LocalDate to, List<FieldError> problemas) {
    LocalDate hasta = to != null ? to : calendario.hoy();
    LocalDate desde = from != null ? from : hasta.withDayOfMonth(1);

    if (desde.isAfter(hasta)) {
      problemas.add(
          new FieldError("from", "VAL-002", "La fecha inicial no puede ser posterior a la final."));
      return null;
    }
    if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DE_DIAS) {
      problemas.add(
          new FieldError(
              "to", "VAL-003", "El periodo no puede pasar de " + MAXIMO_DE_DIAS + " días."));
      return null;
    }
    return new IndicatorPeriod(desde, hasta, calendario.zona().getId());
  }

  /**
   * Del comienzo del primer día al comienzo del día siguiente al último, en la zona del negocio.
   */
  public Interval interval(IndicatorPeriod periodo) {
    return new Interval(
        periodo.from().atStartOfDay(calendario.zona()).toOffsetDateTime(),
        periodo.to().plusDays(1).atStartOfDay(calendario.zona()).toOffsetDateTime());
  }
}
