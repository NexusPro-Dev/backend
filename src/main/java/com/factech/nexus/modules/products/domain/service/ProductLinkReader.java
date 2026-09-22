package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductLinkResponse;
import com.factech.nexus.modules.products.domain.models.ProductLink;
import com.factech.nexus.modules.products.domain.repository.ProductLinkRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lo que cada lectura publica de los enlaces de un producto (`RN-PM-048` a `RN-PM-050`).
 *
 * <p><b>Existe para que la respuesta a «¿qué ve quién?» esté escrita una sola vez.</b> Son siete
 * lecturas y dos respuestas distintas —administración ve <b>los crudos y todos los tipos</b>; todo
 * lo demás ve <b>los resueltos y sin el cupón</b>—, y repartir esa decisión por siete servicios
 * sería darle a alguien siete ocasiones de equivocarse en el sentido que <b>no falla, publica</b>.
 *
 * <p><b>Todo lo que devuelve va por lote.</b> Una página de veinte productos resuelve sus enlaces
 * en <b>una sentencia</b>; pedirlos producto a producto sería una {@code N+1} que el cuerpo de la
 * respuesta no delata, porque sale idéntica (`CA-PM-386`).
 */
@Component
public class ProductLinkReader {

  private final ProductLinkRepository enlaces;

  public ProductLinkReader(ProductLinkRepository enlaces) {
    this.enlaces = enlaces;
  }

  /**
   * Para <b>administración</b> (`RF-PM-001` a `RF-PM-004`): <b>todos los tipos, crudos</b>.
   *
   * <p>Crudos porque quien lee esto es quien va a mandar el {@code PATCH}, y con el {@code
   * CUPON_BOT} dentro porque es <b>donde se administra</b> (`RN-PM-050`).
   */
  public List<ProductLinkResponse> crudosDe(UUID productId) {
    return ProductLinkResponse.todas(enlaces.findByProduct(productId));
  }

  /** Como {@link #crudosDe(UUID)}, para una página entera y en una sola sentencia. */
  public Map<UUID, List<ProductLinkResponse>> crudosDe(Collection<UUID> productIds) {
    return convertir(enlaces.findByProducts(productIds), ProductLinkResponse::todas);
  }

  /**
   * Para las <b>lecturas de venta</b> (`RF-PM-007`, `RF-PM-008`, `RF-PM-026`, `RF-PM-027`):
   * <b>resueltos y sin el {@code CUPON_BOT}</b>.
   *
   * <p>El cupón <b>no sale de la base</b>: el filtro va en el predicado de la sentencia
   * (`RN-PM-050`). Y el enlace llega con el identificador ya pegado, porque quien mira la oferta lo
   * abre y no lo edita (`RN-PM-049`).
   */
  public Map<UUID, List<ProductLinkResponse>> publicablesDe(Collection<UUID> productIds) {
    return convertir(
        enlaces.findPublicablesByProducts(productIds), ProductLinkResponse::todasResueltas);
  }

  /** Los publicables de un solo producto. Nunca nula: <b>vacía</b> cuando no hay. */
  public List<ProductLinkResponse> publicablesDe(UUID productId) {
    return publicablesDe(List.of(productId)).getOrDefault(productId, List.of());
  }

  private static Map<UUID, List<ProductLinkResponse>> convertir(
      Map<UUID, List<ProductLink>> porProducto,
      Function<List<ProductLink>, List<ProductLinkResponse>> forma) {
    return porProducto.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entrada -> forma.apply(entrada.getValue())));
  }
}
