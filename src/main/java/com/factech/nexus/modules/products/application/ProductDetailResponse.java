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
 * El detalle de un producto (`RF-PM-003`).
 *
 * <p><b>Dos ausencias son deliberadas y una de ellas es un criterio</b>:
 *
 * <ul>
 *   <li><b>Ninguna autoría</b> (`CA-PM-081`). Ni quién lo creó, ni quién lo corrigió, ni quién lo
 *       retiró — <b>ni siquiera resuelta desde la auditoría</b>, que sí lo sabe. El Art. V.7
 *       mantiene la autoría donde vive, y traerla aquí convertiría el detalle en media consulta de
 *       auditoría sin su permiso.
 *   <li><b>Ningún dato del registro de eliminación salvo el motivo</b>: ni el actor, ni la
 *       instantánea de lo retirado, ni cuándo se registró. El puerto por el que entra el motivo no
 *       los devuelve, que es lo que hace verificable el límite.
 * </ul>
 *
 * <h2>Qué aparece y qué está presente en nulo</h2>
 *
 * <p>La distinción no es un capricho de serialización:
 *
 * <ul>
 *   <li>{@code targetMembership} y {@code validityDays} van <b>presentes en nulo</b> (`CA-PM-025`):
 *       un servicio sin destino y un producto que no caduca son estados normales, y un campo que
 *       falta es indistinguible de uno que el cliente no conoce.
 *   <li>{@code deletedAt} y {@code deletionReason} <b>no aparecen</b> si el producto está vivo
 *       (`plan.md` §5). Ahí sí: su ausencia <b>significa</b> que el producto no está retirado, y
 *       enviarlos en nulo obligaría a comprobar dos cosas para saber una.
 * </ul>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProductDetailResponse(
    UUID id,
    String code,
    ProductType type,
    String name,
    String description,
    ProductResponse.MembershipRef targetMembership,
    BigDecimal price,
    ProductResponse.CurrencyRef currency,
    Integer validityDays,
    ProductStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String deletionReason) {

  /**
   * Proyecta la fila leída y le añade el motivo, que viene de otro sitio.
   *
   * <p><b>El motivo es el único dato que no está en la misma sentencia</b>, y por eso llega como
   * parámetro: vive en el registro de eliminación, no en {@code products}. Duplicarlo en una
   * columna crearía dos verdades que divergen en cuanto una se corrija.
   *
   * @param motivo el motivo del retiro, o nulo si el producto está vivo o no se registró ninguno
   */
  public static ProductDetailResponse from(ProductRow fila, String motivo) {
    return new ProductDetailResponse(
        fila.id(),
        fila.code(),
        ProductType.valueOf(fila.type()),
        fila.name(),
        fila.description(),
        fila.targetMembershipId() == null
            ? null
            : new ProductResponse.MembershipRef(
                fila.targetMembershipId(),
                fila.targetMembershipCode(),
                fila.targetMembershipName(),
                // El nivel es el ACTUAL y no el que tenía al crearse el
                // producto: la cadena se reordena al insertar un eslabón
                // (`RN-SP-007`), y devolver el de entonces obligaría a
                // guardarlo, que es duplicar un dato que cambia.
                fila.targetMembershipLevel()),
        ProductPrice.enLaEscalaDe(fila.price(), fila.currencyDecimalPlaces()),
        new ProductResponse.CurrencyRef(
            fila.currencyId(), fila.currencyCode(), fila.currencyDecimalPlaces()),
        fila.validityDays(),
        ProductStatus.valueOf(fila.status()),
        enUtc(fila.createdAt()),
        enUtc(fila.updatedAt()),
        enUtc(fila.deletedAt()),
        motivo);
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
