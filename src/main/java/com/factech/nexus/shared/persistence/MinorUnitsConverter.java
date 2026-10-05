package com.factech.nexus.shared.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/**
 * Persiste un importe {@link BigDecimal} como las centésimas {@code bigint} que declara el esquema
 * (ADR-006, {@link MinorUnits}).
 *
 * <p>{@code autoApply = false}: se aplica con {@code @Convert} en cada columna de dinero. Un
 * porcentaje o una tasa también son {@code BigDecimal} y no se guardan en centésimas; aplicarlo a
 * todo {@code BigDecimal} los multiplicaría por cien sin que ninguna prueba de forma lo note.
 */
@Converter(autoApply = false)
public class MinorUnitsConverter implements AttributeConverter<BigDecimal, Long> {

  @Override
  public Long convertToDatabaseColumn(BigDecimal importe) {
    return MinorUnits.toMinor(importe);
  }

  @Override
  public BigDecimal convertToEntityAttribute(Long centesimas) {
    return MinorUnits.fromMinor(centesimas);
  }
}
