package com.factech.nexus.modules.system.exchangerates.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * La tasa <b>vigente</b> entre dos monedas, publicada para otros módulos (**D-25**).
 *
 * <p><b>Devuelve la tasa YA ELEGIDA, no la lista del par</b>, y por el mismo motivo por el que
 * {@code CurrentMembershipLookup} devuelve la membresía ya evaluada: quién rige un día es una
 * decisión de `SP`, y reimplementarla fuera es el defecto que <b>no falla</b> — produce resultados
 * plausibles durante meses.
 *
 * <p><b>Que sea UNA sola lo garantiza `RN-SP-032`</b>: dos tasas activas del mismo par no pueden
 * solaparse. Sin esa regla, este puerto tendría que elegir entre dos precios simultáneos para el
 * mismo cambio, y elegiría el que el índice listara primero.
 */
public interface ExchangeRateLookup {

  /**
   * @param dia el día sobre el que se pregunta. <b>Los dos extremos de la vigencia entran</b>,
   *     igual que en el {@code daterange(..., '[]')} del {@code EXCLUDE}: si aquí se usara un
   *     intervalo abierto, habría días que la restricción da por cubiertos y esta lectura declara
   *     libres
   * @return vacío si nadie declaró una tasa para ese par o si la que hay no rige ese día
   */
  Optional<ExchangeRateView> rateOn(UUID sourceCurrencyId, UUID targetCurrencyId, LocalDate dia);

  /** La tasa que rige, con su precio de ocho decimales. */
  record ExchangeRateView(UUID id, BigDecimal price, LocalDate validFrom, LocalDate validTo) {}
}
