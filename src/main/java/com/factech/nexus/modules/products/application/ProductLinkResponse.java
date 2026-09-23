package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductLink;
import com.factech.nexus.modules.products.domain.models.ProductLinkType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Un enlace de un producto tal como se publica (`RN-PM-048`).
 *
 * <p><b>La misma forma sirve cruda y resuelta, y quien decide es el caso de uso</b>, no este
 * registro: administración recibe {@link #de(ProductLink) la cruda} —la dirección tal cual y el
 * identificador en su campo, que es lo que `RF-PM-004` espera recibir de vuelta— y las lecturas de
 * venta reciben {@link #resuelta(ProductLink) la resuelta}, con el identificador ya pegado, porque
 * quien mira la oferta abre el enlace y no lo edita (`RN-PM-049`).
 *
 * <p><b>El nombre del esquema es explícito</b> y no accidental: springdoc funde dos registros con
 * el mismo nombre simple en un solo esquema, y un {@code ProductLinkResponse} en otro módulo
 * saldría mezclado con este sin que el contrato lo dijera.
 */
@Schema(name = "ProductLinkResponse")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductLinkResponse(
    ProductLinkType type,
    String url,
    /**
     * El identificador de un sistema ajeno, <b>solo en las lecturas de administración</b>.
     *
     * <p>En las de venta llega <b>nulo y presente</b>, porque allí el enlace viaja resuelto y el
     * identificador ya está dentro de {@link #url}: repetirlo sería publicar dos veces el mismo
     * dato y dar a entender que hay algo que componer.
     */
    String externalId) {

  /** La forma <b>cruda</b>: lo que administración edita (`RF-PM-002`, `RF-PM-003`, `RF-PM-001`). */
  public static ProductLinkResponse de(ProductLink enlace) {
    return new ProductLinkResponse(enlace.getType(), enlace.getUrl(), enlace.getExternalId());
  }

  /**
   * La forma <b>resuelta</b>: lo que se abre (`RF-PM-007`, `RF-PM-008`, `RF-PM-026`, `RF-PM-027`).
   */
  public static ProductLinkResponse resuelta(ProductLink enlace) {
    return new ProductLinkResponse(enlace.getType(), enlace.resolver(), null);
  }

  /** Todos los de un producto, crudos. Nunca nula: <b>vacía</b> cuando no hay ninguno. */
  public static List<ProductLinkResponse> todas(List<ProductLink> enlaces) {
    return enlaces == null ? List.of() : enlaces.stream().map(ProductLinkResponse::de).toList();
  }

  /** Todos los de un producto, resueltos. Nunca nula: <b>vacía</b> cuando no hay ninguno. */
  public static List<ProductLinkResponse> todasResueltas(List<ProductLink> enlaces) {
    return enlaces == null
        ? List.of()
        : enlaces.stream().map(ProductLinkResponse::resuelta).toList();
  }
}
