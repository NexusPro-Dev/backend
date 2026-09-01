package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository.ProductRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un producto tal como se le ofrece a quien puede comprarlo (`RF-PM-007`).
 *
 * <p><b>No es {@link ProductItem} con otro nombre</b>, y la diferencia es lo que este requerimiento
 * defiende. Aquel es una fila del catálogo administrativo y lleva {@code status}, {@code createdAt}
 * y {@code deletedAt}; aquí los tres <b>no significan nada</b>: la oferta solo contiene productos
 * activos y vivos, de modo que {@code status} sería siempre {@code ACTIVO} y {@code deletedAt}
 * siempre nulo. Publicar campos cuyo valor está predeterminado invita a que un cliente los lea y
 * construya una condición sobre ellos que nunca se cumplirá.
 *
 * <p><b>Y sobre todo no lleva el motivo del retiro</b> (`CA-PM-067`): no puede llevarlo, porque
 * ningún producto retirado llega hasta aquí.
 *
 * <p>Reutiliza en cambio las <b>referencias</b> de {@link ProductResponse} —destino y moneda— y no
 * declara unas propias: dos formas del mismo dato obligarían al frontend a escribir dos lectores, y
 * el segundo acabaría asumiendo lo que el primero hacía.
 *
 * <p>{@code JsonInclude.ALWAYS} no es decorativo: sin él, el destino de un bot y la vigencia de un
 * producto que no caduca llegarían <b>ausentes</b> en lugar de {@code null}, y un campo que falta
 * es indistinguible de uno que el cliente no conoce.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OfferItem(
    UUID id,
    String code,
    ProductType type,
    String name,
    String description,
    String icon,
    ProductResponse.MembershipRef targetMembership,
    BigDecimal price,
    ProductResponse.CurrencyRef currency,
    Integer validityDays) {

  /**
   * Proyecta la fila leída, con el destino y la moneda que trajo la <b>misma</b> sentencia.
   *
   * <p>Aquí no se consulta nada: resolver el destino fila a fila contra el puerto de `SP` es el
   * problema de las {@code N+1} consultas con otro nombre, y por eso viaja en el {@code LEFT JOIN}.
   *
   * <p><b>El precio sale sin ajuste alguno</b> (`CA-PM-090`): dos personas de niveles distintos ven
   * el mismo importe para el mismo producto. Un precio que dependiera de quién mira es un
   * descuento, y los descuentos son promociones — que `requirements/pm.md` §1.3 deja fuera del
   * alcance a propósito. La única transformación es la escala, que la decide la <b>moneda</b> y no
   * la columna, y la aplica {@link ProductPrice} para las tres respuestas del módulo por igual.
   */
  public static OfferItem from(ProductRow fila) {
    return new OfferItem(
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
        // `RN-PM-015`: nula significa que lo adquirido NO caduca, y es un valor
        // de la respuesta y no la ausencia de uno (`CA-PM-095`). Sin este dato,
        // dos upgrades al mismo nivel y al mismo precio son indistinguibles
        // aunque uno dure un mes y el otro para siempre.
        fila.validityDays());
  }
}
