package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.ProductLink;
import com.factech.nexus.modules.products.domain.models.ProductLinkType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los enlaces de un producto (`RN-PM-048`).
 *
 * <p><b>Todo lo que lee aquí lee un LOTE</b>, y no es un adorno: los enlaces de una página de
 * veinte productos se resuelven en <b>una sentencia</b>, no en veinte. Una lectura por producto
 * sería una {@code N+1} que el cuerpo de la respuesta no delata — sale idéntica—, y por eso
 * `CA-PM-386` cuenta sentencias en lugar de comparar cuerpos.
 *
 * <p><b>Y hay dos lecturas y no una</b>: {@link #findByProducts} trae todos los tipos y {@link
 * #findPublicablesByProducts} trae solo los que son material de venta. La segunda existe para que
 * el {@code CUPON_BOT} <b>no salga de la base</b> en la oferta ni en los hotlinks (`RN-PM-050`):
 * filtrarlo en memoria funcionaría igual hasta el día que alguien reutilice la proyección, y ese
 * día el defecto <b>no falla, publica</b>.
 */
public interface ProductLinkRepository {

  /** Los enlaces de un producto, <b>todos los tipos</b>, en el orden del enumerado. */
  List<ProductLink> findByProduct(UUID productId);

  /**
   * Los enlaces de varios productos, <b>todos los tipos</b>, en <b>una sola sentencia</b>.
   *
   * <p>Para las lecturas de administración (`RF-PM-002`, `RF-PM-003`), que son las únicas que ven
   * el {@code CUPON_BOT} porque son donde se administra.
   *
   * @return un mapa por producto; un producto sin enlaces <b>no aparece</b> en el mapa, y quien lo
   *     consulta obtiene la lista vacía
   */
  Map<UUID, List<ProductLink>> findByProducts(Collection<UUID> productIds);

  /**
   * Como {@link #findByProducts}, pero <b>solo los tipos que son material de venta</b>
   * (`RN-PM-050`): el filtro va <b>en el predicado de la sentencia</b>.
   */
  Map<UUID, List<ProductLink>> findPublicablesByProducts(Collection<UUID> productIds);

  /**
   * Los enlaces de un tipo concreto para varios productos, <b>resueltos</b> (`RN-PM-049`).
   *
   * <p>Es lo que `PM` publica hacia `MV` para el cupón de `RF-MV-014` (`RN-MV-032`): el otro módulo
   * <b>no lee esta tabla</b> ni compone nada, porque la composición es una regla de aquí.
   *
   * @return un mapa por producto con el enlace ya resuelto; los que no tienen ese tipo no aparecen
   */
  Map<UUID, String> findResolvedByType(Collection<UUID> productIds, ProductLinkType type);

  /**
   * Reemplaza <b>el conjunto entero</b> de enlaces de un producto (`RF-PM-004`).
   *
   * <p>La colección que llega <b>es la que queda</b>: un tipo que estaba y no viene se borra, uno
   * que viene y no estaba se crea, y uno que viene y estaba se reescribe. Una lista vacía los quita
   * todos.
   *
   * @return los enlaces que quedaron, para que el caso de uso arme la respuesta sin releer
   */
  List<ProductLink> replace(UUID productId, List<ProductLink> nuevos);

  /** Inserta los enlaces de un producto recién registrado (`RF-PM-001`). */
  List<ProductLink> saveAll(List<ProductLink> enlaces);
}
