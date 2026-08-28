package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.models.ProductStatus;
import com.factech.nexus.modules.products.domain.models.ProductType;
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
 * <p><b>{@code JsonInclude.ALWAYS} no es decorativo</b>: sin él, el destino de un servicio llegaría
 * <b>ausente</b> en lugar de {@code null}, y un campo que falta es indistinguible de uno que el
 * cliente no conoce (`CA-PM-025`). Lo mismo vale para la vigencia de un producto que no caduca.
 *
 * <p><b>El destino llega resuelto</b> y no como identificador suelto: resolverlo cuesta cero
 * consultas extra, porque la validación del alta ya lo trajo del catálogo que `SP` publica.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductResponse(
    UUID id,
    String code,
    ProductType type,
    String name,
    String description,
    MembershipRef targetMembership,
    BigDecimal price,
    CurrencyRef currency,
    Integer validityDays,
    ProductStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  /** La membresía destino, resuelta. Nula y presente en los servicios. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record MembershipRef(UUID id, String code, String name, int level) {}

  /** La moneda del precio, con los decimales que declara. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CurrencyRef(UUID id, String code, int decimalPlaces) {}

  public static ProductResponse from(
      Product producto, MembershipView destino, CurrencyView moneda) {
    return new ProductResponse(
        producto.getId(),
        producto.getCode(),
        producto.getType(),
        producto.getName(),
        producto.getDescription(),
        destino == null
            ? null
            : new MembershipRef(destino.id(), destino.code(), destino.name(), destino.level()),
        enLaEscalaDe(producto.getPrice(), moneda),
        new CurrencyRef(moneda.id(), moneda.code(), moneda.decimalPlaces()),
        producto.getValidityDays(),
        producto.getStatus(),
        enUtc(producto.getCreatedAt()),
        enUtc(producto.getUpdatedAt()));
  }

  /**
   * El precio en la escala de su moneda. La regla vive en {@link ProductPrice}, compartida por las
   * tres respuestas del módulo: escrita aquí y repetida en las otras dos, el mismo producto
   * llegaría con dos precios distintos según por dónde se pidiera.
   */
  private static BigDecimal enLaEscalaDe(BigDecimal precio, CurrencyView moneda) {
    return ProductPrice.enLaEscalaDe(precio, moneda.decimalPlaces());
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
