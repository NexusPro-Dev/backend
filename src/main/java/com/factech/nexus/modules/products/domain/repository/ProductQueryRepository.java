package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.application.ListProductsRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lecturas del listado del catálogo (`RF-PM-002`).
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
   * Proyección de un producto del listado.
   *
   * <p><b>{@code type} y {@code status} son texto y no sus enumerados.</b> La proyección es lo que
   * la base devuelve; convertir a enumerado es decisión del modelo de lectura, y hacerlo aquí
   * pondría a fallar la consulta entera —con un {@code 500}— si algún día el esquema admitiera un
   * valor que el código todavía no conoce.
   *
   * <p><b>No lleva el motivo del retiro</b> (`CA-PM-077`): la sentencia ni siquiera lo selecciona,
   * que es lo único que hace verificable el criterio.
   */
  record ProductRow(
      UUID id,
      String code,
      String type,
      String name,
      String description,
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
      OffsetDateTime deletedAt) {}
}
