package com.factech.nexus.modules.system.exchangerates.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
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

  /**
   * Las tasas vigentes de <b>varias</b> monedas de origen hacia una misma de destino, en <b>una
   * sola sentencia</b> (`RF-PM-002` · `T-20`, 08-09-2026).
   *
   * <p><b>Existe por el listado de productos, y por eso vive aquí y no en un bucle de `PM`.</b>
   * Desde que `RN-PM-024` obliga a que cada fila lleve su conversión, una página de veinte
   * productos llamando a {@link #rateOn} son veinte consultas donde había una — y el cuerpo de la
   * respuesta es <b>idéntico</b> en los dos casos, de modo que ninguna prueba de API lo distingue.
   * Poniendo el bucle en `PM`, la decisión de cuántas sentencias cuesta una página quedaría fuera
   * del módulo que las paga.
   *
   * <p><b>El mapa NO trae las monedas sin tasa vigente</b>, en lugar de traerlas con valor nulo:
   * quien lo consume pregunta con {@code get} y trata la ausencia, que es lo mismo que hace con
   * {@link #rateOn} vacío. Un mapa con nulos dentro obliga a distinguir «no se preguntó» de «no
   * hay», que aquí no significan cosas distintas.
   *
   * @param sourceCurrencyIds las monedas de origen; vacío o nulo devuelve un mapa vacío <b>sin
   *     consultar</b>
   * @return de cada moneda de origen a su tasa vigente ese día, <b>solo las que la tienen</b>
   */
  Map<UUID, ExchangeRateView> ratesOn(
      Collection<UUID> sourceCurrencyIds, UUID targetCurrencyId, LocalDate dia);

  /** La tasa que rige, con su precio de ocho decimales. */
  record ExchangeRateView(UUID id, BigDecimal price, LocalDate validFrom, LocalDate validTo) {}
}
