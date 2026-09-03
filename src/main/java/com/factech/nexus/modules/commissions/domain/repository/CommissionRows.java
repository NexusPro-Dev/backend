package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
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

  /**
   * La forma de la comisión, que en el motor es {@code varchar(20)}.
   *
   * <p>Las consultas son nativas, de modo que la columna llega como texto y no como enum: la
   * traducción tiene que hacerla alguien. <b>Está aquí y no en cada adaptador</b> por lo mismo que
   * el resto de esta clase — las tres consultas la necesitan, y el día que la forma gane un tercer
   * valor hay un solo sitio que mirar.
   *
   * <p><b>Un valor desconocido revienta, y es lo que se quiere.</b> {@code ck_*_type} lo impide en
   * el motor; si alguna vez llegara uno, el fallo debe ser ruidoso y no un nulo silencioso que
   * acabe en una respuesta diciendo que esa tasa no tiene forma.
   */
  static CommissionRateType forma(Object valor) {
    if (valor == null) {
      return null;
    }
    return CommissionRateType.valueOf(valor.toString());
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
