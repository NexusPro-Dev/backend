package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.application.ListProductsRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecturas del listado y del detalle del catálogo (`RF-PM-002`, `RF-PM-003`).
 *
 * <p>Puerto <b>separado</b> del de escritura, y no un método más de {@link ProductRepository}: lo
 * que devuelve no son agregados sino proyecciones, y mezclarlos invitaría a cargar la entidad para
 * responder una consulta — que es el camino al {@code N+1} que este requerimiento existe para
 * evitar.
 */
public interface ProductQueryRepository {

  /** Una página del catálogo, con su destino y su moneda resueltos en la misma sentencia. */
  List<ProductRow> search(ListProductsRequest filtros, String ordenamiento, int offset, int limit);

  /** El conteo, con <b>el mismo predicado</b> que la página: se generan desde el mismo sitio. */
  long count(ListProductsRequest filtros);

  /**
   * Un producto por su identificador, con su destino y su moneda resueltos (`RF-PM-003`).
   *
   * <p><b>NO excluye los retirados</b>, al revés que el detalle de un rol: `CA-PM-026` exige que un
   * producto retirado se devuelva marcado como tal y no como inexistente. Es lo correcto aquí y no
   * allí porque el catálogo <b>conserva</b> lo retirado a propósito —entender por qué algo dejó de
   * venderse es media razón de existir de este módulo— mientras que un rol eliminado no debe dejar
   * ni rastro de que existió.
   *
   * @return vacío solo si no existe ninguna fila con ese identificador
   */
  Optional<ProductRow> findDetail(UUID id);

  /**
   * Proyección de un producto del listado.
   *
   * <p><b>{@code type} y {@code status} son texto y no sus enumerados.</b> La proyección es lo que
   * la base devuelve; convertir a enumerado es decisión del modelo de lectura, y hacerlo aquí
   * pondría a fallar la consulta entera —con un {@code 500}— si algún día el esquema admitiera un
   * valor que el código todavía no conoce.
   *
   * <p><b>No lleva el motivo del retiro</b> (`CA-PM-077`): la sentencia ni siquiera lo selecciona,
   * que es lo único que hace verificable el criterio. Cuando el detalle lo necesita, entra por el
   * puerto de {@code shared/audit} y no por esta proyección.
   *
   * <p>{@code updatedAt} llega <b>nulo desde el listado</b> y relleno desde el detalle: una lista
   * no responde cuándo se tocó cada fila por última vez, y seleccionarlo para descartarlo sería
   * pagar por un dato que nadie lee. Es el mismo trato que {@code UserRow} da a los suyos.
   */
  record ProductRow(
      UUID id,
      String code,
      String type,
      String name,
      String description,
      String icon,
      UUID sourceMembershipId,
      String sourceMembershipCode,
      String sourceMembershipName,
      Integer sourceMembershipLevel,
      UUID targetMembershipId,
      String targetMembershipCode,
      String targetMembershipName,
      Integer targetMembershipLevel,
      BigDecimal price,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      Integer validityDays,
      String status,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {}
}
