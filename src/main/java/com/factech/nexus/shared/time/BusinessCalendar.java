package com.factech.nexus.shared.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <b>Dónde se corta el día del negocio</b> (`architecture.md` §15.1.1, v0.39.0).
 *
 * <p>El sistema guarda instantes —{@code timestamptz}, sin ambigüedad— y el negocio decide por
 * <b>días</b>: la tasa que regía el día de una venta, el comprobante del día. Convertir un instante
 * en un día exige una zona, y leída en UTC una venta de las 20:00 en Bogotá cae en el día
 * siguiente. Esta clase es el único sitio donde esa conversión se decide: toda lectura de negocio
 * que convierta un instante en una fecha pasa por aquí.
 *
 * <p><b>La zona es configuración</b> ({@code nexus.business.zone}, {@code America/Bogota} por
 * defecto), como toda decisión de entorno del proyecto. Una tarea programada de negocio declara su
 * {@code zone} con la misma propiedad, no con el literal.
 */
@Component
public class BusinessCalendar {

  private final ZoneId zona;
  private final Clock reloj;

  @Autowired
  public BusinessCalendar(@Value("${nexus.business.zone:America/Bogota}") String zona) {
    this(ZoneId.of(zona), Clock.systemUTC());
  }

  public BusinessCalendar(ZoneId zona, Clock reloj) {
    this.zona = zona;
    this.reloj = reloj;
  }

  /** La zona del negocio. */
  public ZoneId zona() {
    return zona;
  }

  /** El instante actual. Se guarda como tal; la zona solo decide el día. */
  public OffsetDateTime ahora() {
    return OffsetDateTime.now(reloj);
  }

  /** Qué día es hoy para el negocio. */
  public LocalDate hoy() {
    return LocalDate.now(reloj.withZone(zona));
  }

  /** Qué día fue ese instante para el negocio. */
  public LocalDate diaDe(OffsetDateTime instante) {
    return instante.atZoneSameInstant(zona).toLocalDate();
  }
}
