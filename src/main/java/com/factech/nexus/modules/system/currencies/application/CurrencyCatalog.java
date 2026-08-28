package com.factech.nexus.modules.system.currencies.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que `SP` publica de sus monedas para que otro módulo pueda consultarlas (**D-25**).
 *
 * <p><b>Una interfaz por lectura y no una fachada con todo dentro.</b> Cada consumidor depende solo
 * de lo que usa —`RF-PM-007` no necesita saber nada de monedas— y una prueba puede doblar esta sin
 * arrastrar las otras dos. Con una fachada única, añadir un método cambiaría el contrato de todos
 * los que ya la usan, incluidos sus dobles.
 *
 * <p><b>Devuelve si la moneda está activa en lugar de filtrar por ello</b>, y la diferencia
 * importa: `RN-PM-008` exige rechazar una moneda desactivada con un mensaje distinto del de una
 * inexistente —una es una decisión del sistema y la otra un dato equivocado—, y eso solo puede
 * distinguirlo quien recibe las dos respuestas por separado.
 *
 * <p>Ver `architecture.md` §15.2.
 */
public interface CurrencyCatalog {

  /**
   * La moneda, si existe.
   *
   * @param id identificador de la moneda; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<CurrencyView> find(UUID id);

  /**
   * Lo que cruza la frontera: datos planos, sin comportamiento.
   *
   * <p>{@code decimalPlaces} viaja porque es lo que decide la escala admisible de un precio
   * (`RN-PM-007`), y <b>no siempre vale dos</b>: hay monedas sin fracción, donde cero es un valor
   * legítimo.
   */
  record CurrencyView(UUID id, String code, int decimalPlaces, boolean active) {}
}
