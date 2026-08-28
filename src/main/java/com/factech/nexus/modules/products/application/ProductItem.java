package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductStatus;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository.ProductRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Una fila del listado del catálogo (`RF-PM-002`).
 *
 * <p><b>Reutiliza las referencias de {@link ProductResponse}</b> —destino y moneda— y no declara
 * unas propias: dos formas del mismo dato obligarían a la interfaz a escribir dos lectores, y el
 * segundo acabaría asumiendo lo que el primero hacía.
 *
 * <p>Lo que <b>no</b> lleva es tan deliberado como lo que lleva:
 *
 * <ul>
 *   <li><b>El motivo del retiro</b> (`CA-PM-077`). El listado dice <b>que</b> un producto está
 *       retirado y <b>desde cuándo</b>, no <b>por qué</b>. Uno a uno el motivo es una consulta y lo
 *       devuelve `RF-PM-003`; en bloque sería una exportación de decisiones comerciales.
 *   <li><b>{@code updatedAt}</b> — no responde ninguna pregunta que se le haga a una lista.
 * </ul>
 *
 * <p>{@code JsonInclude.ALWAYS} no es decorativo: sin él, el destino de un bot y la vigencia de un
 * producto que no caduca llegarían <b>ausentes</b> en lugar de {@code null}, y un campo que falta
 * es indistinguible de uno que el cliente no conoce.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductItem(
    UUID id,
    String code,
    ProductType type,
    String name,
    String description,
    String icon,
    ProductResponse.MembershipRef targetMembership,
    BigDecimal price,
    ProductResponse.CurrencyRef currency,
    Integer validityDays,
    ProductStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime deletedAt) {

  /**
   * Proyecta la fila leída, con el destino y la moneda que trajo la <b>misma</b> sentencia.
   *
   * <p><b>Aquí no se consulta nada</b> (`CA-PM-019`): resolver el destino fila a fila contra el
   * puerto de `SP` es el problema de las {@code N+1} consultas con otro nombre —cien productos,
   * cien llamadas—, y por eso viaja en el {@code LEFT JOIN}.
   */
  public static ProductItem from(ProductRow fila) {
    return new ProductItem(
        fila.id(),
        fila.code(),
        ProductType.valueOf(fila.type()),
        fila.name(),
        fila.description(),
        fila.icon(),
        fila.targetMembershipId() == null
            ? null
            : new ProductResponse.MembershipRef(
                fila.targetMembershipId(),
                fila.targetMembershipCode(),
                fila.targetMembershipName(),
                fila.targetMembershipLevel()),
        ProductPrice.enLaEscalaDe(fila.price(), fila.currencyDecimalPlaces()),
        new ProductResponse.CurrencyRef(
            fila.currencyId(), fila.currencyCode(), fila.currencyDecimalPlaces()),
        fila.validityDays(),
        ProductStatus.valueOf(fila.status()),
        enUtc(fila.createdAt()),
        enUtc(fila.deletedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
