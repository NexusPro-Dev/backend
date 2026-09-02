package com.factech.nexus.modules.commissions.domain.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Las conversiones que los tres adaptadores de consulta necesitan.
 *
 * <p><b>Están aquí y no repetidas tres veces</b> porque no son código de conveniencia: son el
 * resultado de un defecto vivido, y una copia que se quedara atrás volvería a producirlo en la
 * única consulta que no se hubiera corregido.
 */
final class CommissionRows {

  private CommissionRows() {}

  /**
   * El instante en UTC, venga como venga.
   *
   * <p><b>Los tres casos están porque el driver devuelve los tres</b>, según la consulta y la
   * versión: {@code OffsetDateTime}, {@code Instant} y {@code Timestamp}. Faltaba el del medio y el
   * síntoma fue un {@code ClassCastException} que salía como {@code 500} <b>solo</b> al pedir las
   * tasas retiradas — porque es la única columna de instante que el listado proyecta, y solo se lee
   * cuando hay alguna retirada que devolver.
   */
  static OffsetDateTime momento(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof OffsetDateTime instante) {
      return instante.withOffsetSameInstant(ZoneOffset.UTC);
    }
    if (valor instanceof java.time.Instant instante) {
      return instante.atOffset(ZoneOffset.UTC);
    }
    return ((Timestamp) valor).toInstant().atOffset(ZoneOffset.UTC);
  }

  /** La fecha, venga como {@code LocalDate} o como {@code java.sql.Date}. */
  static LocalDate fecha(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof LocalDate local) {
      return local;
    }
    return ((Date) valor).toLocalDate();
  }

  /** El nombre completo, o nulo si no hay ninguna de las dos mitades. */
  static String nombreCompleto(String nombre, String apellido) {
    if (nombre == null && apellido == null) {
      return null;
    }
    String completo =
        ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
    return completo.isEmpty() ? null : completo;
  }
}
