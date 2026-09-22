package com.factech.nexus.modules.system.exchangerates.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las reglas que el agregado sostiene por sí solo (`RF-SP-047` · `T-03`).
 *
 * <p><b>Sin Spring y sin base de datos</b>: lo que se comprueba aquí son las tres reglas que no
 * dependen de nada más —`RN-SP-029`, `RN-SP-030` y `RN-SP-031`—, y arrancar un contexto para
 * verificarlas escondería que no lo necesitan. El solapamiento (`RN-SP-032`) <b>no</b> está aquí a
 * propósito: depende de las demás filas, y su garantía es la restricción del esquema.
 */
class ExchangeRateTest {

  private static final UUID USD = UUID.randomUUID();
  private static final UUID COP = UUID.randomUUID();
  private static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Test
  @DisplayName("RN-SP-029 — una moneda no se cambia por sí misma")
  void monedasDistintas() {
    assertThat(codigoDe(() -> tasa(USD, USD, "1.00", DESDE, null))).isEqualTo("VAL-003");
  }

  @Test
  @DisplayName("RN-SP-030 — el precio de una tasa es mayor que cero y admite ocho decimales")
  void precio() {
    assertThat(codigoDe(() -> tasa(USD, COP, "0.00", DESDE, null))).isEqualTo("VAL-004");
    assertThat(codigoDe(() -> tasa(USD, COP, "-0.00000001", DESDE, null))).isEqualTo("VAL-004");

    // El noveno decimal se rechaza en vez de redondearse: guardar una tasa
    // distinta de la declarada es peor que no guardarla.
    assertThat(codigoDe(() -> tasa(USD, COP, "0.000000001", DESDE, null))).isEqualTo("VAL-005");

    assertThat(tasa(USD, COP, "0.00024096", DESDE, null).getPrice())
        .isEqualByComparingTo("0.00024096");
  }

  @Test
  @DisplayName("RN-SP-031 — la vigencia de UN SOLO DÍA es legítima; la invertida no")
  void vigencia() {
    assertThat(codigoDe(() -> tasa(USD, COP, "1.00", DESDE, DESDE.minusDays(1))))
        .isEqualTo("VAL-007");

    // `isBefore` y no `<=`: una tasa que rige una jornada es exactamente lo que
    // necesita quien liquida a la tasa del día.
    assertThat(tasa(USD, COP, "1.00", DESDE, DESDE).getValidTo()).isEqualTo(DESDE);

    // Y sin fecha de fin la tasa es vitalicia, que no es un estado aparte.
    assertThat(tasa(USD, COP, "1.00", DESDE, null).getValidTo()).isNull();
  }

  @Test
  @DisplayName("la instantánea de auditoría lleva el precio como TEXTO, con su escala intacta")
  void instantanea() {
    var estado = tasa(USD, COP, "0.00024096", DESDE, null).instantanea();

    assertThat(estado)
        .containsEntry("source_currency_id", USD.toString())
        .containsEntry("target_currency_id", COP.toString())
        // Como número, un `BigDecimal` serializado puede perder la escala por el
        // camino, y la auditoría dejaría de decir qué se declaró.
        .containsEntry("price", "0.00024096")
        .containsEntry("valid_from", "2026-09-01")
        .containsEntry("valid_to", null)
        .containsEntry("is_active", true);
  }

  /**
   * El código del rechazo, que es lo que la API traduce a `error_code`.
   *
   * <p>El mensaje NO lo lleva dentro —se comprobó a la primera y falló—, de modo que afirmarlo por
   * el texto habría comprobado la redacción y no la regla.
   */
  private static String codigoDe(org.junit.jupiter.api.function.Executable alta) {
    ValidationException fallo =
        catchThrowableOfType(() -> alta.execute(), ValidationException.class);
    assertThat(fallo).isNotNull();
    return fallo.errorCode();
  }

  private static ExchangeRate tasa(
      UUID origen, UUID destino, String precio, LocalDate desde, LocalDate hasta) {
    return ExchangeRate.create(
        UUID.randomUUID(), origen, destino, new BigDecimal(precio), desde, hasta, true, AHORA);
  }
}
