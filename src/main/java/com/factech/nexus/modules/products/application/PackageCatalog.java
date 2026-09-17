package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.application.ProductCatalog.SaleView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que `PM` publica de sus paquetes <b>para venderlos</b> (`RF-MV-012` · `T-01`, **D-25**).
 *
 * <p><b>Es la segunda interfaz que `PM` publica</b>, después de {@link ProductCatalog}, y nace por
 * el mismo motivo: `MV` no puede leer {@code product_packages} —la regla de ArchUnit de `RF-MV-001`
 * · `T-19` lo prohíbe—, y sin esta lectura «pregunta la ofrecibilidad, no la recalcules» sería una
 * frase de un documento. Quién puede ver un paquete lo deciden `RN-PM-039`, `RN-PM-044` y
 * `RN-PM-047`, que viven en `PM` con sus pruebas; duplicar el predicado en la venta significaría
 * que el día que `PM` lo cambie, la venta seguiría con el viejo <b>sin que nada falle</b>.
 *
 * <p><b>Publica lo que ya sabe, y publica el motivo.</b> El catálogo ya calcula {@code offerable}
 * con su causa para el detalle del paquete (`RF-PM-019`); reutilizarla es lo que permite que la
 * venta diga <b>qué</b> falla —vencido, inactivo, sin productos— en lugar de un «no se puede» que
 * obligue al cliente a adivinar.
 *
 * <p><b>La rebaja viaja declarada —tipo y valor— y no en dinero.</b> Es la decisión de `RF-MV-012`
 * · `plan.md` §3.2: `MV` la congela con su propia cuenta, porque devolver el importe ya rebajado le
 * obligaría a <b>restar para saber cuánto se rebajó</b> y a guardar como declaración un número que
 * nadie declaró. Lo que impide que las dos cuentas diverjan es `CA-MV-050`, que compara lo cobrado
 * con el {@code price} que el catálogo publica.
 *
 * <p><b>No arrastra nada de {@code domain}</b>: el tipo del descuento viaja como cadena, igual que
 * el estado del cliente en {@code ClientCatalog}, y por el mismo motivo — exportar el enumerado
 * obligaría a `MV` a depender de una clase de {@code products.domain}, que es justo la frontera que
 * la regla sostiene.
 */
public interface PackageCatalog {

  /**
   * El paquete tal como se le vendería <b>hoy, por la tienda y a esa persona</b>.
   *
   * <p><b>Vacío solo si no existe o está retirado</b> (`EX-001` de `RF-MV-012`). Todo lo demás
   * —inactivo, vencido, sin descripción, fuera de la tienda, con un producto caído, o que no le
   * corresponde a quien compra— <b>se devuelve con su motivo</b>, porque son rechazos distintos y
   * la venta tiene que poder nombrarlos: `EX-002` dice «este paquete no se ofrece» y `EX-003` «no
   * se te ofrece a ti».
   *
   * <p><b>Recibe la persona y no su nivel</b>, como {@link ProductCatalog#offeredTo}: quien
   * pregunta no sabe con qué criterio se decide `RN-PM-044`, de modo que el criterio puede cambiar
   * sin que cambie una línea en `MV`.
   *
   * @param packageId el paquete; un valor nulo devuelve vacío en lugar de fallar
   * @param buyerId quien compra; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<PackageSaleView> storeSaleViewOf(UUID packageId, UUID buyerId);

  /**
   * La vista de venta del paquete: lo que se comprueba y lo que se copia.
   *
   * <p>{@code offerable} y {@code offerableReason} son la decisión de `RN-PM-039` y `RN-PM-047`
   * <b>más el alcance</b>: un paquete de alcance {@code HOTLINK} o {@code NINGUNO} no se vende por
   * la tienda, y viene con ese motivo. {@code offeredTo} es `RN-PM-044`: si el upgrade del paquete
   * sale de la membresía vigente de quien compra. Las dos preguntas viajan separadas porque son
   * rechazos distintos.
   *
   * <p>{@code items} van <b>en el orden del paquete</b> —el orden en que se asociaron—, que es el
   * orden en que la venta escribe sus líneas.
   *
   * @param currencyDecimalPlaces los de la moneda del paquete, que son los de todas sus líneas: un
   *     paquete solo reúne productos en su moneda (`RF-PM-023`)
   */
  record PackageSaleView(
      UUID id,
      String code,
      String name,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      boolean offerable,
      String offerableReason,
      boolean offeredTo,
      List<PackageSaleLine> items) {}

  /**
   * Una línea del paquete: el producto <b>en la misma vista con la que se vende suelto</b> y el
   * descuento que el paquete le declara.
   *
   * @param discountType {@code PORCENTAJE} o {@code FIJO}, como cadena (ver el Javadoc de la
   *     interfaz)
   * @param discountValue lo declarado, con la escala de su forma: dos decimales el porcentaje, los
   *     de la moneda el fijo — la misma con la que el catálogo lo publica en la oferta
   */
  record PackageSaleLine(SaleView product, String discountType, BigDecimal discountValue) {}
}
