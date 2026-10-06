package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Los tramos de calendario de un periodo (`RF-IN-002` · `T-02`, `spec.md` §2.2): dónde empieza cada
 * uno, también los que no tendrán ventas.
 *
 * <p><b>Una serie con huecos miente al dibujarse</b> (`spec.md` §2.1), y por eso los tramos no
 * salen de las ventas sino de aquí. La semana empieza el <b>lunes</b> —como {@code date_trunc} de
 * PostgreSQL, con el que esta clase tiene que coincidir— y el mes el día uno. El primer tramo es el
 * que <b>contiene</b> el primer día del periodo, aunque empiece antes.
 */
final class SalesCalendar {

  private SalesCalendar() {}

  static List<LocalDate> starts(LocalDate from, LocalDate to, Granularity granularity) {
    LocalDate tramo =
        switch (granularity) {
          case DAY -> from;
          case WEEK -> from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
          case MONTH -> from.withDayOfMonth(1);
        };
    List<LocalDate> inicios = new ArrayList<>();
    while (!tramo.isAfter(to)) {
      inicios.add(tramo);
      tramo =
          switch (granularity) {
            case DAY -> tramo.plusDays(1);
            case WEEK -> tramo.plusWeeks(1);
            case MONTH -> tramo.plusMonths(1);
          };
    }
    return inicios;
  }
}
