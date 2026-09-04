package com.factech.nexus.modules.products.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que `PM` publica de sus productos para que otro módulo pueda consultarlos (**D-25**).
 *
 * <p><b>Es la primera interfaz que `PM` publica.</b> Hasta el 28-08-2026 no publicaba ninguna, y no
 * por descuido: nadie lo consumía. Nace con `RF-CM-001` · `T-06`, y la escribe quien la necesita —
 * es el reparto que se decidió al cerrar D-25, y por el mismo motivo: ningún actor pide «publicar
 * una interfaz» como comportamiento.
 *
 * <p><b>{@code retired} es el motivo de que esta interfaz exista en esta forma.</b> `CM` necesita
 * distinguir un producto retirado de uno inexistente, porque son dos rechazos distintos: sobre el
 * retirado no se declaran tarifas nuevas (`RN-CM-010`) y quien escribió bien el identificador no
 * debe buscar el error donde no está. Devolver vacío para el retirado colapsaría los dos casos.
 *
 * <p><b>El precio no viaja en {@link ProductView}, y la moneda sigue sin viajar.</b> Una interfaz
 * por lectura y no una fachada: quien necesita el importe de un producto pide su propia lectura
 * —{@link #findPrice}, desde `cm.md` v0.8.0— sin que este contrato ni sus dobles de prueba cambien.
 */
public interface ProductCatalog {

  /**
   * El producto, si existe.
   *
   * @param id identificador del producto; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<ProductView> find(UUID id);

  /**
   * El precio del producto, si existe. Nadie más necesita este dato, y por eso no viaja en {@link
   * ProductView} — es la lectura propia que el Javadoc de esta interfaz ya anticipaba (`RN-CM-019`,
   * `requirements/cm.md` v0.8.0).
   *
   * <p><b>No filtra por retirado</b>, igual que {@link #find}: comprobar el tope de un producto ya
   * asociado y luego retirado es legítimo (`RN-CM-010` prohíbe declarar, no conservar).
   *
   * @param id identificador del producto; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<BigDecimal> findPrice(UUID id);

  /**
   * Lo que hace falta saber de un producto <b>para venderlo</b> (`RF-MV-001` · `T-05`).
   *
   * <p><b>Es un método nuevo y no una ampliación de {@link ProductView}</b>, y ahí está la
   * decisión. `CM` ya consume aquella vista y no necesita ni el precio, ni la moneda, ni la
   * vigencia, ni el destino: un registro compartido que crece por cada consumidor acaba llevando
   * campos que a la mitad no le sirven, y obliga a recompilar a quien no pidió nada. Una interfaz
   * por lectura, que es lo mismo que hicieron {@link #findPrice} y los tres puertos de `SP`.
   *
   * <p><b>Recibe un lote y no un identificador</b> (`RF-MV-001` · `plan.md` §9). Una venta de cinco
   * productos que preguntara cinco veces cruzaría la frontera cinco veces para lo mismo: es una
   * {@code N+1} que no se ve —cada llamada es un método Java— y que aparece entera en el registro
   * de sentencias.
   *
   * <p><b>No filtra nada</b>: devuelve lo que encuentre, retirado o inactivo incluido. Quién puede
   * comprar qué lo responde {@link #offeredTo}, y colapsar las dos preguntas haría indistinguible
   * un producto inexistente de uno que existe y no corresponde — que es justo la distinción que
   * `EX-004` y `EX-011` de `RF-MV-001` existen para mantener.
   *
   * @param ids identificadores a resolver; una colección nula o vacía devuelve la lista vacía
   * @return los que existen, sin orden garantizado y sin marcador para los que no
   */
  List<SaleView> saleViewOf(Collection<UUID> ids);

  /**
   * De esos productos, <b>cuáles puede comprar esa persona</b> (`RF-MV-001` · `T-06`).
   *
   * <p><b>Recibe la persona y no su nivel</b>, y es lo que hace que esta interfaz siga valiendo el
   * día que `RF-PM-007` · `T-20` reescriba la oferta para que coincida por <b>origen</b> en lugar
   * de comparar niveles: quien pregunta no sabe con qué criterio se decide, de modo que el criterio
   * puede cambiar sin que cambie ni una línea en `MV`.
   *
   * <p><b>Y responde con la MISMA consulta que `RF-PM-007`</b>, no con una equivalente. Recalcular
   * la oferta en el consumidor crearía <b>dos definiciones de «lo que alguien puede comprar»</b>, y
   * el día que una cambiara la otra seguiría vendiendo lo que la primera ya no ofrece — un defecto
   * que no falla: vende de más, en silencio, y se descubre al reclamar.
   *
   * @param userId la persona por la que se pregunta; un valor nulo devuelve el conjunto vacío
   * @param ids productos a comprobar; una colección nula o vacía devuelve el conjunto vacío
   * @return el subconjunto de {@code ids} que hoy se le ofrece a esa persona
   */
  Set<UUID> offeredTo(UUID userId, Collection<UUID> ids);

  /** Lo que cruza la frontera: datos planos, sin comportamiento y sin entidad. */
  record ProductView(UUID id, String code, String name, boolean retired) {}

  /**
   * La vista de venta: lo que se <b>copia</b> y lo que se <b>comprueba</b>.
   *
   * <p><b>{@code upgrade} viaja explícito y no se deduce de que haya destino</b>, aunque hoy {@code
   * ck_products_type_target} haga las dos cosas equivalentes. Deducirlo ataría `MV` a una
   * restricción de una tabla de `PM` que nadie le prometió mantener; declararlo lo convierte en
   * parte del contrato.
   *
   * <p><b>{@code targetMembershipLevel} viaja porque `RN-MV-006` es de `MV` y no de `PM`.</b> Que
   * una venta no baje a nadie de nivel se comprueba aunque la oferta ya lo garantice hoy: la oferta
   * es una decisión de `PM` y puede ampliarse —renovaciones del mismo nivel, por ejemplo—, y esta
   * regla no puede depender de que otro módulo siga decidiendo lo mismo.
   *
   * <p><b>{@code price} llega con la escala de la columna</b> ({@code numeric(14,4)}), de modo que
   * {@code 49.99} llega como {@code 49.9900}. Quien lo compare con los decimales de su moneda debe
   * usar {@link ProductPrice#cabeEn}, que mide la escala significativa.
   *
   * <p>{@code validityDays} nulo significa <b>no caduca</b> (`RN-PM-015`), no «sin dato».
   */
  record SaleView(
      UUID id,
      String code,
      String name,
      boolean upgrade,
      BigDecimal price,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      Integer validityDays,
      UUID targetMembershipId,
      Integer targetMembershipLevel) {}
}
