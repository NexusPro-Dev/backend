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

  /**
   * Cuáles de esos productos publica el hotlink (`RN-PM-021`): el MISMO predicado que la lectura
   * del enlace público y el catálogo de hotlinks, sobre un lote y sin proyección.
   */
  java.util.List<java.util.UUID> findPublishedByHotlink(java.util.Collection<java.util.UUID> ids);

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
   * La oferta que le corresponde a quien mira desde <b>esa membresía</b> (`RF-PM-007` · `T-20`).
   *
   * <p><b>Coincidencia exacta por ORIGEN, no comparación de niveles</b> (`RN-PM-011`, reescrita el
   * 07-09-2026). Se devuelven los upgrades cuyo {@code source_membership_id} <b>es</b> la membresía
   * del actor, y ninguno más. Comparar niveles ofrecía a quien está en {@code ORO} un {@code
   * PLATINO → ORO}, que no es suyo, y sobre todo <b>no podía expresar la renovación</b>: un {@code
   * X → X} obliga a abrir la comparación a «igual», y ahí entra el salto ajeno.
   *
   * <p><b>Una sola sentencia para los dos tipos</b>, y no dos consultas: el filtro que los separa
   * es una condición, no una pregunta distinta, y dos sentencias acabarían con dos criterios de
   * «activo» que divergen.
   *
   * <p>Devuelve <b>solo lo activo y no retirado</b> (`RN-PM-009`), en el orden que exigen
   * `CA-PM-078` y `CA-PM-079`: primero los upgrades por nivel de destino, después los bots por
   * fecha de alta. Quien la consume solo tiene que separar por tipo, sin reordenar.
   *
   * <p><b>Que no se ofrezcan bajadas ya no lo sostiene esta consulta</b>, y conviene saberlo: lo
   * sostiene `RN-PM-017` al <b>registrar</b>. Un producto declarado desde mi membresía no puede
   * apuntar por debajo, porque no habría podido darse de alta.
   *
   * <p><b>No selecciona el precio de compra</b> (`RN-PM-024`): es el costo de NEXUS, y por esta
   * lectura solo viaja {@code price}, el que se cobra. {@code purchasePrice} llega <b>nulo a
   * propósito</b> en cada fila.
   *
   * @param membresia el identificador de la membresía <b>vigente</b> del actor, o {@code null} si
   *     no tiene ninguna. Nulo <b>no</b> significa «sin filtro»: no coincide con ningún origen, y
   *     por tanto <b>cero upgrades</b> y todos los bots (`FA-001`, `FA-003`)
   */
  List<ProductRow> findOffer(UUID membresia);

  /**
   * `RF-PM-027`: los productos activos de alcance `HOTLINKS`, de los dos tipos, en la proyección de
   * venta y en el orden de la oferta. <b>Sin la membresía de nadie</b>: el vendedor no compra lo
   * que reparte.
   */
  List<ProductRow> findHotlinkCatalog();

  /**
   * El producto que un hotlink señala, por su <b>código</b> (`RF-PM-008` · `T-04`).
   *
   * <p><b>Exige activo, no retirado y de alcance {@code HOTLINKS}</b> (`RN-PM-021`), y por eso
   * devuelve vacío en los tres casos: un producto de alcance {@code TIENDA} <b>no se publica sin
   * autenticación</b>. Es el primer sitio donde `RN-PM-019` filtra de verdad.
   *
   * <p><b>El código se compara sin distinguir mayúsculas</b>: un enlace se teclea.
   *
   * <p><b>Y tampoco selecciona el precio de compra</b> (`RN-PM-024`), igual que {@link #findOffer}.
   * Aquí no es prudencia sino condición del requerimiento: es la única lectura del módulo <b>sin
   * token</b>, y un costo publicado por descuido —el margen— no se puede retirar después.
   *
   * @return vacío si no existe o si no procede — <b>los cuatro casos iguales</b>, para que el
   *     {@code 404} de arriba no pueda filtrarse en respuestas distintas
   */
  Optional<ProductRow> findPublishedByCode(String code);

  /**
   * `RN-PM-028`: ¿se puede comprar? Existe, está {@code ACTIVO} y no está retirado. Una lectura por
   * clave, sin {@code JOIN}. La usan el alta de la reseña (`RF-PM-009`) y la lista pública
   * (`RF-PM-012`), y los tres casos en que responde falso son indistinguibles a propósito: los dos
   * requerimientos responden el mismo {@code 404} a los tres.
   */
  boolean isPurchasable(UUID productId);

  /**
   * Proyección de un producto del listado.
   *
   * <p><b>{@code type}, {@code status}, {@code scope} e {@code implementation} son texto y no sus
   * enumerados.</b> La proyección es lo que la base devuelve; convertir a enumerado es decisión del
   * modelo de lectura, y hacerlo aquí pondría a fallar la consulta entera —con un {@code 500}— si
   * algún día el esquema admitiera un valor que el código todavía no conoce.
   *
   * <p><b>No lleva el motivo del retiro</b> (`CA-PM-077`): la sentencia ni siquiera lo selecciona,
   * que es lo único que hace verificable el criterio. Cuando el detalle lo necesita, entra por el
   * puerto de {@code shared/audit} y no por esta proyección.
   *
   * <p>{@code updatedAt} llega <b>nulo desde el listado</b> y relleno desde el detalle: una lista
   * no responde cuándo se tocó cada fila por última vez, y seleccionarlo para descartarlo sería
   * pagar por un dato que nadie lee. Es el mismo trato que {@code UserRow} da a los suyos.
   *
   * <h2>{@code purchasePrice} llega nulo desde las DOS lecturas públicas, y ahí no significa lo
   * mismo</h2>
   *
   * <p>Desde el listado y el detalle es <b>el dato</b>: nulo significa que no se conoce el costo.
   * Desde {@link #findOffer} y {@link #findPublishedByCode} llega <b>siempre</b> nulo porque esas
   * consultas <b>no lo seleccionan</b>: es lo que NEXUS paga por el producto, y por ahí <b>solo
   * viaja {@code price}</b> (`RN-PM-024`, 12-09-2026).
   *
   * <p>Es deliberado y no una asimetría por descuido: si esas dos lecturas trajeran el costo,
   * estaría dentro del objeto que se serializa —a un campo de distancia de publicar el margen— y en
   * el hotlink eso ocurre <b>sin token</b>. Quien lea una de esas filas debe usar {@code price} y
   * no preguntar por el otro. Del 08-09-2026 al 12-09-2026 este campo se llamó {@code publicPrice}
   * y las dos lecturas públicas sí lo seleccionaban, cuando era lo que se anunciaba.
   */
  record ProductRow(
      UUID id,
      String code,
      String type,
      String name,
      String description,
      String icon,
      // `video_url` estuvo aquí entre el 14-09-2026 y el 22-09-2026. Los
      // enlaces son filas de `product_links` y NO salen de estas sentencias:
      // los lee `ProductLinkRepository` en UNA sentencia por página, y el caso
      // de uso los junta con la fila (`RN-PM-048`).
      // `RN-PM-033`: el IDENTIFICADOR de la portada y nada más — ninguna de las
      // cuatro sentencias toca `product_images`. La dirección la construye la
      // respuesta. Nulo cuando el producto no tiene portada.
      UUID coverImageId,
      UUID sourceMembershipId,
      String sourceMembershipCode,
      String sourceMembershipName,
      Integer sourceMembershipLevel,
      String sourceMembershipColor,
      UUID targetMembershipId,
      String targetMembershipCode,
      String targetMembershipName,
      Integer targetMembershipLevel,
      String targetMembershipColor,
      BigDecimal price,
      BigDecimal purchasePrice,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      Integer validityDays,
      String scope,
      String implementation,
      String status,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt,
      // `RN-PM-031`: el agregado de las reseñas VIVAS, calculado EN LA MISMA
      // sentencia por un LEFT JOIN LATERAL. `ratingAverage` llega bruto —el
      // redondeo es de `RatingSummary`— y nulo sin reseñas; `ratingCount`, cero.
      BigDecimal ratingAverage,
      long ratingCount) {

    public com.factech.nexus.modules.products.domain.models.RatingSummary rating() {
      return com.factech.nexus.modules.products.domain.models.RatingSummary.de(
          ratingAverage, ratingCount);
    }
  }
}
