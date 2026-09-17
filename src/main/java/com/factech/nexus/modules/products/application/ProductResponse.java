package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.models.ProductImplementation;
import com.factech.nexus.modules.products.domain.models.ProductScope;
import com.factech.nexus.modules.products.domain.models.ProductStatus;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.products.domain.models.RatingSummary;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.memberships.application.MembershipCatalog.MembershipView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * El producto tal como sale de la API.
 *
 * <p><b>{@code JsonInclude.ALWAYS} no es decorativo</b>: sin él, el destino de un bot llegaría
 * <b>ausente</b> en lugar de {@code null}, y un campo que falta es indistinguible de uno que el
 * cliente no conoce (`CA-PM-025`). Lo mismo vale para la vigencia de un producto que no caduca.
 *
 * <p><b>El destino llega resuelto</b> y no como identificador suelto: resolverlo cuesta cero
 * consultas extra, porque la validación del alta ya lo trajo del catálogo que `SP` publica.
 *
 * <p><b>Lleva los DOS precios porque esta respuesta exige `products:create`</b> (`RN-PM-024`), que
 * solo tiene quien administra el catálogo. {@code purchasePrice} es lo que NEXUS paga por el
 * producto —el costo—, y la oferta de `RF-PM-007` y el hotlink de `RF-PM-008` <b>no lo
 * devuelven</b>: publicarlo enseñaría el margen a quien compra.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductResponse(
    UUID id,
    String code,
    ProductType type,
    String name,
    String description,
    String icon,
    /**
     * `RN-PM-032`: la dirección del video, tal cual se guardó, y nula y presente cuando no hay. Al
     * revés que {@code purchasePrice}, sale en las cuatro lecturas.
     */
    String videoUrl,
    /**
     * La dirección de la portada (`RN-PM-033`): la ruta pública de `RF-PM-016`, construida sobre
     * `cover_image_id` sin tocar `product_images`. Presente y nula cuando no hay.
     */
    String coverImageUrl,
    MembershipRef sourceMembership,
    MembershipRef targetMembership,
    BigDecimal price,
    BigDecimal purchasePrice,
    CurrencyRef currency,
    ExchangeRef exchange,
    Integer validityDays,
    ProductScope scope,
    ProductImplementation implementation,
    ProductStatus status,
    /**
     * `RN-PM-031`: promedio y cantidad de reseñas vivas. Presente siempre; `average` nulo sin
     * reseñas.
     */
    RatingSummary rating,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  /** Las membresías de origen y destino, resueltas. Nulas y presentes en los bots. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MembershipRef(UUID id, String code, String name, int level, String color) {}

  /** La moneda del precio, con los decimales que declara. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CurrencyRef(UUID id, String code, int decimalPlaces) {}

  public static ProductResponse from(
      Product producto,
      MembershipView origen,
      MembershipView destino,
      CurrencyView moneda,
      ExchangeRef conversion) {
    return new ProductResponse(
        producto.getId(),
        producto.getCode(),
        producto.getType(),
        producto.getName(),
        producto.getDescription(),
        producto.getIcon(),
        producto.getVideoUrl(),
        ProductImageUrls.de(producto.getCoverImageId()),
        ref(origen),
        ref(destino),
        enLaEscalaDe(producto.getPrice(), moneda),
        // Nulo y PRESENTE cuando no se conoce: su nulo SIGNIFICA «todavía no
        // tiene costo declarado», y un campo ausente no puede decir eso
        // (`CA-PM-146`).
        producto.getPurchasePrice() == null
            ? null
            : enLaEscalaDe(producto.getPurchasePrice(), moneda),
        new CurrencyRef(moneda.id(), moneda.code(), moneda.decimalPlaces()),
        conversion,
        producto.getValidityDays(),
        producto.getScope(),
        producto.getImplementation(),
        producto.getStatus(),
        // Un producto recién creado no tiene reseñas por definición (`RF-PM-001`
        // devuelve esta forma), y consultarlo sería pagar una sentencia por un
        // cero. Las demás lecturas traen el agregado en su propia consulta.
        RatingSummary.vacio(),
        enUtc(producto.getCreatedAt()),
        enUtc(producto.getUpdatedAt()));
  }

  /**
   * Nula y PRESENTE cuando no hay membresía, que es siempre el caso en un bot.
   *
   * <p>Un campo que desaparece del resultado es indistinguible de uno que el cliente no conoce, y
   * aquí la ausencia significa algo: este producto no cambia de nivel a nadie.
   */
  private static MembershipRef ref(MembershipView vista) {
    return vista == null
        ? null
        : new MembershipRef(vista.id(), vista.code(), vista.name(), vista.level(), vista.color());
  }

  /**
   * El precio en la escala de su moneda. La regla vive en {@link ProductPrice}, compartida por las
   * tres respuestas del módulo: escrita aquí y repetida en las otras dos, el mismo producto
   * llegaría con dos precios distintos según por dónde se pidiera.
   *
   * <p><b>Y desde el 08-09-2026 la comparten también los dos importes</b>: escrita dos veces, el
   * mismo producto acabaría enseñando su precio del sistema con dos decimales y el público con
   * cuatro.
   */
  private static BigDecimal enLaEscalaDe(BigDecimal precio, CurrencyView moneda) {
    return ProductPrice.enLaEscalaDe(precio, moneda.decimalPlaces());
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
