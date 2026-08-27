package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.models.ProductStatus;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.memberships.application.MembershipCatalog.MembershipView;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
   * El precio con los decimales que declara su moneda, no con la escala de la columna
   * (`CA-PM-082`): {@code 49.99} en una moneda de dos decimales y no {@code 49.9900}.
   *
   * <p>La escala de {@code numeric(14,4)} es una decisión de almacenamiento —existe para admitir
   * monedas de más de dos decimales— y no algo que el contrato deba exponer.
   *
   * <p><b>Se redondea a la baja y no hacia arriba</b>, y en la práctica no redondea nada:
   * `RN-PM-007` impide al escribir que un precio tenga más decimales de los que su moneda admite,
   * de modo que aquí solo se recorta la cola de ceros. Si algún día llegara un valor con más
   * decimales —una carga directa en la base—, redondear al alza cobraría de más.
   */
  private static BigDecimal enLaEscalaDe(BigDecimal precio, CurrencyView moneda) {
    return precio.setScale(moneda.decimalPlaces(), RoundingMode.DOWN);
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
