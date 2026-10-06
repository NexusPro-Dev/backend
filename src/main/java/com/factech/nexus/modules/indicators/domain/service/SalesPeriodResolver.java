package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * El periodo de un indicador y sus tramos (`RF-IN-001` · `T-04` y `T-14`, `RN-IN-010`).
 *
 * <p><b>Se pide en días y no en instantes</b>, al revés que los listados: un indicador se pregunta
 * en días —«septiembre»— y quien lo pide no tendría que calcular a qué hora UTC empieza el uno de
 * septiembre en Bogotá. Por dentro es <b>semiabierto</b>: del comienzo del primer día al comienzo
 * del día siguiente al último, en la zona del negocio (`RN-IN-007`).
 *
 * <p><b>Sin fechas, toda la historia</b> (`RN-IN-010`, 06-10-2026): «desde» ausente es sin límite
 * inferior, y «hasta» ausente es hoy. <b>No hay tope de días</b>: si la historia entera se puede
 * pedir sin fechas, acotar un rango con fechas no protege nada.
 */
@Component
public class SalesPeriodResolver {

  private final BusinessCalendar calendario;

  public SalesPeriodResolver(BusinessCalendar calendario) {
    this.calendario = calendario;
  }

  /**
   * El periodo efectivo, o nulo si no es válido; los problemas se añaden a {@code problemas} para
   * devolverlos junto a los demás. {@code from} nulo es «desde el principio».
   */
  public IndicatorPeriod resolve(LocalDate from, LocalDate to, List<FieldError> problemas) {
    LocalDate hasta = to != null ? to : calendario.hoy();
    if (from != null && from.isAfter(hasta)) {
      problemas.add(
          new FieldError("from", "VAL-002", "La fecha inicial no puede ser posterior a la final."));
      return null;
    }
    return new IndicatorPeriod(from, hasta, calendario.zona().getId());
  }

  /**
   * Del comienzo del primer día —o sin límite— al comienzo del día siguiente al último, en la zona
   * del negocio.
   */
  public Interval interval(IndicatorPeriod periodo) {
    return new Interval(
        periodo.from() == null
            ? null
            : periodo.from().atStartOfDay(calendario.zona()).toOffsetDateTime(),
        periodo.to().plusDays(1).atStartOfDay(calendario.zona()).toOffsetDateTime());
  }

  /**
   * Los inicios de tramo del periodo, todos (`RF-IN-002` §2.1). Sin «desde», arrancan en el tramo
   * del <b>primer dato</b>, que es el menor de {@code conDatos}; sin datos, en el de «hasta».
   */
  public static List<LocalDate> starts(
      IndicatorPeriod periodo, Granularity tramo, Collection<LocalDate> conDatos) {
    LocalDate primero =
        periodo.from() != null
            ? periodo.from()
            : conDatos.stream().min(LocalDate::compareTo).orElse(periodo.to());
    return SalesCalendar.starts(primero, periodo.to(), tramo);
  }

  /**
   * El tramo pedido, sin distinguir mayúsculas; {@code porDefecto} si no viene. Uno desconocido es
   * {@code VAL-005}, junto a los demás problemas.
   */
  public static Granularity granularity(
      String valor, Granularity porDefecto, List<FieldError> problemas) {
    if (valor == null || valor.isBlank()) {
      return porDefecto;
    }
    String normalizado = valor.trim().toUpperCase();
    for (Granularity g : Granularity.values()) {
      if (g.name().equals(normalizado)) {
        return g;
      }
    }
    problemas.add(
        new FieldError(
            "granularity",
            "VAL-005",
            "El tramo '"
                + valor
                + "' no existe. Valores admitidos: "
                + Arrays.stream(Granularity.values()).map(Enum::name).toList()
                + "."));
    return porDefecto;
  }
}
