package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ExchangeRef;
import com.factech.nexus.modules.products.application.ExchangeRef.TargetCurrency;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.exchangerates.application.ExchangeRateLookup;
import com.factech.nexus.modules.system.exchangerates.application.ExchangeRateLookup.ExchangeRateView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * La conversión de un producto a la moneda de casa, para las <b>cuatro</b> lecturas del módulo
 * (`RN-PM-024`, 08-09-2026).
 *
 * <p><b>Nació dentro de {@code GetHotlinkService} y sale de ahí el día que deja de ser cosa del
 * hotlink.</b> Dejarla allí y llamarla desde los otros tres obligaría a que el listado dependiera
 * del servicio de una ruta pública; copiarla daría cuatro versiones del mismo cálculo, y la cuarta
 * redondearía distinto sin que nada fallara.
 *
 * <h2>Por qué devuelve un {@code Conversor} y no un {@code ExchangeRef}</h2>
 *
 * <p><b>Porque el listado es una lista, y ahí está el defecto que no se ve.</b> Una página de
 * veinte productos que pidiera la conversión producto a producto costaría <b>cuarenta consultas</b>
 * —la moneda de casa y la tasa, por fila— donde bastan dos, y <b>el cuerpo de la respuesta sería
 * idéntico</b>: ninguna prueba de API lo distingue, y solo lo destapa contar sentencias
 * (`CA-PM-165`, `CA-PM-168`).
 *
 * <p>El {@code Conversor} se pide <b>una vez por lectura</b>, con todas las monedas que aparecen, y
 * a partir de ahí convierte en memoria. Las lecturas de una sola fila —el detalle y el hotlink—
 * usan el mismo camino con una moneda: dos consultas, no una forma aparte que pueda divergir.
 */
@Component
public class ProductExchangeResolver {

  private final CurrencyCatalog monedas;
  private final ExchangeRateLookup tasas;
  private final Clock reloj;

  @Autowired
  public ProductExchangeResolver(CurrencyCatalog monedas, ExchangeRateLookup tasas) {
    this(monedas, tasas, Clock.systemUTC());
  }

  ProductExchangeResolver(CurrencyCatalog monedas, ExchangeRateLookup tasas, Clock reloj) {
    this.monedas = monedas;
    this.tasas = tasas;
    this.reloj = reloj;
  }

  /**
   * Prepara la conversión de una lectura entera: <b>dos consultas</b>, sean una fila o cien.
   *
   * @param monedasDeOrigen las monedas de los productos que se van a devolver. Se colapsan las
   *     repetidas antes de consultar
   */
  public Conversor para(Collection<UUID> monedasDeOrigen) {
    // Sin filas no hay nada que convertir, y por eso NO se pide siquiera la
    // moneda de casa: una página vacía debe costar lo mismo que antes de que
    // esta conversión existiera.
    if (monedasDeOrigen == null || monedasDeOrigen.isEmpty()) {
      return new Conversor(null, Map.of());
    }

    Optional<CurrencyView> casa = monedas.findDefault();
    if (casa.isEmpty()) {
      // Sin moneda por omisión no hay a qué convertir. NO es un error de esta
      // lectura: un catálogo que devolviera `500` porque el catálogo de monedas
      // está incompleto sería un catálogo rehén de otro módulo.
      return new Conversor(null, Map.of());
    }
    CurrencyView destino = casa.get();

    // La moneda de casa se quita ANTES de preguntar: un producto que ya está en
    // ella no tiene nada que convertir, y con un catálogo entero en una sola
    // moneda —que es el caso normal— esa consulta no devolvería ni una fila.
    // `RN-SP-029` además impide que exista una tasa de una moneda a sí misma,
    // de modo que preguntar por ella es preguntar por lo imposible.
    List<UUID> convertibles =
        monedasDeOrigen.stream().filter(moneda -> !destino.id().equals(moneda)).distinct().toList();

    if (convertibles.isEmpty()) {
      return new Conversor(destino, Map.of());
    }

    return new Conversor(destino, tasas.ratesOn(convertibles, destino.id(), LocalDate.now(reloj)));
  }

  /** La conversión ya resuelta de una lectura, que convierte en memoria y no consulta más. */
  public record Conversor(CurrencyView destino, Map<UUID, ExchangeRateView> tasas) {

    /**
     * La conversión de un producto, o {@code null} si no procede.
     *
     * <p><b>Devuelve nulo en tres casos y ninguno es un error</b>: cuando no hay moneda por
     * omisión, cuando el producto <b>ya está</b> en ella —no hay nada que convertir, y `RN-SP-029`
     * impide que exista una tasa de una moneda a sí misma— y cuando <b>nadie declaró una tasa
     * vigente</b> para esa moneda. El tercero es el que más fácil se convierte en un `404` por
     * descuido: escondería un producto perfectamente vendible porque falta un dato de otro módulo.
     *
     * @param importeMostrado el importe sobre el que se convierte, que es <b>el que se muestra</b>
     *     —el público si el producto lo declara y el del sistema si no—, nunca los dos
     */
    public ExchangeRef de(UUID monedaDelProducto, BigDecimal importeMostrado) {
      if (destino == null || monedaDelProducto == null || importeMostrado == null) {
        return null;
      }
      if (destino.id().equals(monedaDelProducto)) {
        return null;
      }
      ExchangeRateView tasa = tasas.get(monedaDelProducto);
      if (tasa == null) {
        return null;
      }

      BigDecimal importe =
          importeMostrado
              .multiply(tasa.price())
              // A los decimales de la moneda de DESTINO, que es en la que queda
              // expresado el importe. La escala de la tasa —ocho— no manda aquí:
              // el resultado ya no es un cociente, es dinero.
              .setScale(destino.decimalPlaces(), RoundingMode.HALF_UP);

      return new ExchangeRef(
          new TargetCurrency(destino.code(), destino.decimalPlaces()),
          // Como CADENA: ocho decimales no sobreviven a la coma flotante de
          // doble precisión de un cliente JavaScript.
          tasa.price().toPlainString(),
          importe);
    }
  }

  /**
   * El importe que se muestra: el público si el producto lo declara y el del sistema si no.
   *
   * <p><b>Vive aquí y no en cada lectura</b> porque es la definición de «el que se muestra», y
   * cuatro copias de un {@code if} son cuatro sitios donde alguien puede invertirlo. Lo usan las
   * cuatro para decidir sobre qué importe se convierte — <b>no</b> para elegir cuál publicar, que
   * desde el 08-09-2026 son los dos.
   */
  public static BigDecimal importeMostrado(BigDecimal precioSistema, BigDecimal precioPublico) {
    return precioPublico != null ? precioPublico : precioSistema;
  }
}
