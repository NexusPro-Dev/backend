package com.factech.nexus.modules.movements.domain.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** De lo que el driver devuelve para un {@code timestamptz} a {@link OffsetDateTime}. */
final class PayoutTimes {

  private PayoutTimes() {}

  static OffsetDateTime instante(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime momento -> momento;
      case Instant momento -> momento.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }
}
