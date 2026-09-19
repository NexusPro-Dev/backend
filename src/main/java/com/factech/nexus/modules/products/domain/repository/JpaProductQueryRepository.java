package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.application.ListProductsRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de lectura del listado del catálogo (`RF-PM-002` · `T-05`).
 *
 * <p><b>El predicado se genera en un solo sitio</b> ({@link #predicado}) y lo usan tanto la página
 * como el conteo. Escritos por separado divergen, y la divergencia se manifiesta como un total que
 * no coincide con lo que se ve — de los defectos más difíciles de creer cuando se reporta.
 *
 * <h2>Por qué el destino entra por un {@code JOIN} y no por el puerto de `SP`</h2>
 *
 * <p>Y por qué no contradice a <b>D-25</b>: aquella decisión gobierna el <b>código</b> —`PM` no
 * importa entidades ni repositorios de `SP`, y la regla de ArchUnit lo comprueba—. Esto es SQL de
 * una consulta de lectura, y la alternativa —llamar al puerto una vez por fila— es el problema de
 * las {@code N+1} consultas con otro nombre: cien productos, cien llamadas.
 *
 * <p>La frontera se mantiene donde importa: <b>ninguna regla se decide con este {@code JOIN}</b>.
 * Lo que valida que el destino existe sigue siendo el puerto, en `RF-PM-001`; aquí solo se pinta un
 * nombre junto a un identificador que ya está en la fila.
 */
@Repository
public class JpaProductQueryRepository implements ProductQueryRepository {

  private final EntityManager em;

  public JpaProductQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProductRow> search(
      ListProductsRequest filtros, String ordenamiento, int offset, int limit) {

    String sql =
        """
        SELECT p.id AS id, p.code AS code, p.type AS type, p.name AS name,
               p.description AS description, p.icon AS icon, p.video_url AS video_url,
               p.cover_image_id AS cover_image_id,
               p.target_membership_id AS m_id, m.code AS m_code, m.name AS m_name,
               m.level AS m_level, m.color AS m_color,
               p.source_membership_id AS s_id, s.code AS s_code, s.name AS s_name,
               s.level AS s_level, s.color AS s_color,
               p.price AS price, p.purchase_price AS purchase_price,
               p.currency_id AS c_id, c.code AS c_code,
               c.decimal_places AS c_decimales,
               p.validity_days AS validity_days, p.scope AS scope,
               p.implementation AS implementation, p.status AS status,
               p.created_at AS created_at, p.deleted_at AS deleted_at,
               r.rating_avg AS rating_avg, r.rating_count AS rating_count
          FROM products p
          LEFT JOIN memberships m ON m.id = p.target_membership_id
          LEFT JOIN memberships s ON s.id = p.source_membership_id
          LEFT JOIN currencies  c ON c.id = p.currency_id
          LEFT JOIN LATERAL (
              SELECT avg(pc.rating) AS rating_avg, count(*) AS rating_count
                FROM product_comments pc
               WHERE pc.product_id = p.id AND pc.deleted_at IS NULL
          ) r ON true
         WHERE """
            // El espacio va aquí y no al final del bloque de texto: Java recorta
            // el espacio final de cada línea, y sin él la sentencia dice `WHEREp`.
            + " "
            + predicado(filtros)
            + " ORDER BY "
            + ordenamiento
            + " OFFSET :salto LIMIT :tope";

    Query consulta = em.createNativeQuery(sql, Tuple.class);
    enlazar(consulta, filtros);
    consulta.setParameter("salto", offset).setParameter("tope", limit);

    List<Tuple> filas = consulta.getResultList();
    List<ProductRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new ProductRow(
              (UUID) fila.get("id"),
              (String) fila.get("code"),
              (String) fila.get("type"),
              (String) fila.get("name"),
              (String) fila.get("description"),
              (String) fila.get("icon"),
              (String) fila.get("video_url"),
              (UUID) fila.get("cover_image_id"),
              (UUID) fila.get("s_id"),
              (String) fila.get("s_code"),
              (String) fila.get("s_name"),
              entero(fila.get("s_level")),
              (String) fila.get("s_color"),
              (UUID) fila.get("m_id"),
              (String) fila.get("m_code"),
              (String) fila.get("m_name"),
              entero(fila.get("m_level")),
              (String) fila.get("m_color"),
              (BigDecimal) fila.get("price"),
              // El precio de compra viaja SOLO en las dos lecturas de
              // administración —esta y el detalle— (`RN-PM-024`).
              (BigDecimal) fila.get("purchase_price"),
              (UUID) fila.get("c_id"),
              (String) fila.get("c_code"),
              ((Number) fila.get("c_decimales")).intValue(),
              entero(fila.get("validity_days")),
              (String) fila.get("scope"),
              (String) fila.get("implementation"),
              (String) fila.get("status"),
              momento(fila.get("created_at")),
              // El listado no selecciona `updated_at`: nadie pregunta a una
              // lista cuándo se tocó cada fila por última vez.
              null,
              momento(fila.get("deleted_at")),
              decimal(fila.get("rating_avg")),
              contador(fila.get("rating_count"))));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long count(ListProductsRequest filtros) {
    // Sin los LEFT JOIN: ningún filtro se apoya en ellos, y unirlos solo para
    // contar es trabajo que no cambia el número.
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM products p WHERE " + predicado(filtros));
    enlazar(consulta, filtros);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ProductRow> findDetail(UUID id) {
    if (id == null) {
      return Optional.empty();
    }

    // UNA sentencia con las dos uniones externas. Sin `deleted_at IS NULL`: un
    // producto retirado se devuelve marcado como tal (`CA-PM-026`), no como
    // inexistente.
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.type AS type, p.name AS name,
                       p.description AS description, p.icon AS icon, p.video_url AS video_url,
               p.cover_image_id AS cover_image_id,
                       p.target_membership_id AS m_id, m.code AS m_code, m.name AS m_name,
                       m.level AS m_level, m.color AS m_color, m.color AS m_color,
                       p.source_membership_id AS s_id, s.code AS s_code, s.name AS s_name,
                       s.level AS s_level, s.color AS s_color, s.color AS s_color,
                       p.price AS price, p.purchase_price AS purchase_price,
                       p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days, p.scope AS scope,
                       p.implementation AS implementation, p.status AS status,
                       p.created_at AS created_at, p.updated_at AS updated_at,
                       p.deleted_at AS deleted_at,
                       r.rating_avg AS rating_avg, r.rating_count AS rating_count
                  FROM products p
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                  LEFT JOIN memberships s ON s.id = p.source_membership_id
                  LEFT JOIN currencies  c ON c.id = p.currency_id
                  LEFT JOIN LATERAL (
                      SELECT avg(pc.rating) AS rating_avg, count(*) AS rating_count
                        FROM product_comments pc
                       WHERE pc.product_id = p.id AND pc.deleted_at IS NULL
                  ) r ON true
                 WHERE p.id = :id
                """,
                Tuple.class)
            .setParameter("id", id)
            .getResultList();

    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new ProductRow(
                    (UUID) fila.get("id"),
                    (String) fila.get("code"),
                    (String) fila.get("type"),
                    (String) fila.get("name"),
                    (String) fila.get("description"),
                    (String) fila.get("icon"),
                    (String) fila.get("video_url"),
                    (UUID) fila.get("cover_image_id"),
                    (UUID) fila.get("s_id"),
                    (String) fila.get("s_code"),
                    (String) fila.get("s_name"),
                    entero(fila.get("s_level")),
                    (String) fila.get("s_color"),
                    (UUID) fila.get("m_id"),
                    (String) fila.get("m_code"),
                    (String) fila.get("m_name"),
                    entero(fila.get("m_level")),
                    (String) fila.get("m_color"),
                    (BigDecimal) fila.get("price"),
                    (BigDecimal) fila.get("purchase_price"),
                    (UUID) fila.get("c_id"),
                    (String) fila.get("c_code"),
                    ((Number) fila.get("c_decimales")).intValue(),
                    entero(fila.get("validity_days")),
                    (String) fila.get("scope"),
                    (String) fila.get("implementation"),
                    (String) fila.get("status"),
                    momento(fila.get("created_at")),
                    momento(fila.get("updated_at")),
                    momento(fila.get("deleted_at")),
                    decimal(fila.get("rating_avg")),
                    contador(fila.get("rating_count"))));
  }

  /**
   * La oferta, en <b>una</b> sentencia (`RF-PM-007` · `T-20`).
   *
   * <h2>Coincidencia exacta por ORIGEN, y esta es la línea que decide el requerimiento</h2>
   *
   * <p>{@code p.source_membership_id = :membresia}. Se ofrecen los upgrades que <b>alguien declaró
   * desde donde el actor está</b>, y ninguno más. Quien registró el producto ya decidió a quién va
   * dirigido; esta consulta no lo vuelve a decidir.
   *
   * <p><b>Comparaba niveles hasta el 07-09-2026</b> —{@code m.level < :nivel}—, y cambiarlo no fue
   * una limpieza: una comparación de niveles <b>no puede expresar la renovación</b>. Para que quepa
   * un {@code X → X} hay que abrirla a {@code <=}, y entonces a quien está en {@code ORO} se le
   * ofrece también {@code PLATINO → ORO} — el salto de otro que acaba donde él ya está. El filtro
   * por origen distingue las dos cosas sin una condición más.
   *
   * <h2>Que no se ofrezcan bajadas ya NO lo sostiene esta consulta</h2>
   *
   * <p>Lo sostiene `RN-PM-017` al <b>registrar</b>: un producto cuyo origen sea la membresía del
   * actor no puede apuntar por debajo, porque no habría podido darse de alta. La regla se mudó de
   * la consulta al alta el 02-09-2026 (`requirements/pm.md` §5.2.1) y el código tardó cinco días en
   * seguirla. <b>Quien toque `RegisterProductService.verificarOrigen` está tocando también lo que
   * esta consulta da por cierto.</b>
   *
   * <h2>Sin membresía no es «sin filtro»</h2>
   *
   * <p>Con {@code :membresia} nulo, {@code p.source_membership_id = NULL} evalúa a {@code NULL} y
   * la fila <b>queda fuera</b> — que es justo lo que `FA-001` pide, y ahora <b>sale del propio
   * filtro</b> en lugar de necesitar una condición escrita aparte—. Aun así se deja dicho aquí:
   * este proyecto ya pagó una vez por confiar en cómo se comporta el nulo dentro de una condición
   * compuesta —{@code ck_deletion_reason} evaluaba a {@code NULL} y por tanto <b>aceptaba</b> la
   * fila—, y en un {@code WHERE} el nulo excluye mientras que en un {@code CHECK} admite.
   *
   * <h2>El orden</h2>
   *
   * <p>Los upgrades primero y los bots después (`CA-PM-078`); dentro de los upgrades, por <b>nivel
   * de destino</b> y no por precio ni por nombre, porque es el único orden en el que «subir»
   * significa algo (`CA-PM-079`). {@code DESC} sobre el número es <b>del salto más corto al más
   * largo</b>, y con la renovación dentro empieza por ella: {@code X → X} es el salto de longitud
   * cero, y aparece antes que el primer peldaño. Los bots, por fecha de alta.
   */
  @Override
  @Transactional(readOnly = true)
  public List<ProductRow> findOffer(UUID membresia) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.type AS type, p.name AS name,
                       p.description AS description, p.icon AS icon, p.video_url AS video_url,
               p.cover_image_id AS cover_image_id,
                       p.source_membership_id AS s_id, s.code AS s_code, s.name AS s_name,
                       s.level AS s_level, s.color AS s_color, s.color AS s_color,
                       p.target_membership_id AS m_id, m.code AS m_code, m.name AS m_name,
                       m.level AS m_level, m.color AS m_color, m.color AS m_color,
                       p.price AS price,
                       p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days, p.scope AS scope,
                       p.implementation AS implementation, p.status AS status,
                       p.created_at AS created_at,
                       r.rating_avg AS rating_avg, r.rating_count AS rating_count
                  FROM products p
                  LEFT JOIN memberships s ON s.id = p.source_membership_id
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                  LEFT JOIN currencies  c ON c.id = p.currency_id
                  LEFT JOIN LATERAL (
                      SELECT avg(pc.rating) AS rating_avg, count(*) AS rating_count
                        FROM product_comments pc
                       WHERE pc.product_id = p.id AND pc.deleted_at IS NULL
                  ) r ON true
                 WHERE p.deleted_at IS NULL
                   AND p.status = 'ACTIVO'
                   -- `RN-PM-019` (15-09-2026): la oferta filtra por alcance por
                   -- primera vez. HOTLINK y NINGUNO no son de la tienda.
                   AND p.scope IN ('TIENDA', 'AMBOS')
                   AND ( p.type = 'BOT'
                         OR p.source_membership_id = CAST(:membresia AS uuid) )
                 ORDER BY CASE WHEN p.type = 'UPGRADE_MEMBRESIA' THEN 0 ELSE 1 END,
                          m.level DESC,
                          p.created_at ASC,
                          p.id ASC
                """,
                Tuple.class)
            .setParameter("membresia", membresia)
            .getResultList();

    return filasDeVenta(filas);
  }

  /**
   * `RF-PM-027`: el catálogo de hotlinks — lo que un vendedor puede repartir.
   *
   * <p><b>La oferta sin la membresía y con el alcance como único predicado</b>: activo, no retirado
   * y de alcance {@code HOTLINKS}, de los dos tipos, en el mismo orden y con la misma proyección de
   * venta —<b>sin `purchase_price`</b> (`RN-PM-024`)—. Es `RN-PM-021` vista entera: exactamente el
   * conjunto que {@link #findPublishedByCode} resuelve enlace a enlace.
   *
   * <p><b>Es una sentencia propia y no un modo de {@link #findOffer}</b>: el predicado de origen y
   * el de alcance no se combinan, se sustituyen, y una consulta con dos modos es la que este módulo
   * ha evitado siempre. Lo que sí comparten es el mapeo (`filasDeVenta`).
   */
  @Override
  public List<ProductRow> findHotlinkCatalog() {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.type AS type, p.name AS name,
                       p.description AS description, p.icon AS icon, p.video_url AS video_url,
                       p.cover_image_id AS cover_image_id,
                       p.source_membership_id AS s_id, s.code AS s_code, s.name AS s_name,
                       s.level AS s_level, s.color AS s_color,
                       p.target_membership_id AS m_id, m.code AS m_code, m.name AS m_name,
                       m.level AS m_level, m.color AS m_color,
                       p.price AS price,
                       p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days, p.scope AS scope,
                       p.implementation AS implementation, p.status AS status,
                       p.created_at AS created_at,
                       r.rating_avg AS rating_avg, r.rating_count AS rating_count
                  FROM products p
                  LEFT JOIN memberships s ON s.id = p.source_membership_id
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                  LEFT JOIN currencies  c ON c.id = p.currency_id
                  LEFT JOIN LATERAL (
                      SELECT avg(pc.rating) AS rating_avg, count(*) AS rating_count
                        FROM product_comments pc
                       WHERE pc.product_id = p.id AND pc.deleted_at IS NULL
                  ) r ON true
                 WHERE p.deleted_at IS NULL
                   AND p.status = 'ACTIVO'
                   AND p.scope IN ('HOTLINK', 'AMBOS')
                 ORDER BY CASE WHEN p.type = 'UPGRADE_MEMBRESIA' THEN 0 ELSE 1 END,
                          m.level DESC,
                          p.created_at ASC,
                          p.id ASC
                """,
                Tuple.class)
            .getResultList();
    return filasDeVenta(filas);
  }

  /**
   * El mapeo de las dos lecturas de venta —la oferta y el catálogo de hotlinks—, que comparten
   * proyección: sin `purchase_price`, sin `updated_at` ni `deleted_at`.
   */
  private static List<ProductRow> filasDeVenta(List<Tuple> filas) {
    List<ProductRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new ProductRow(
              (UUID) fila.get("id"),
              (String) fila.get("code"),
              (String) fila.get("type"),
              (String) fila.get("name"),
              (String) fila.get("description"),
              (String) fila.get("icon"),
              (String) fila.get("video_url"),
              (UUID) fila.get("cover_image_id"),
              (UUID) fila.get("s_id"),
              (String) fila.get("s_code"),
              (String) fila.get("s_name"),
              entero(fila.get("s_level")),
              (String) fila.get("s_color"),
              (UUID) fila.get("m_id"),
              (String) fila.get("m_code"),
              (String) fila.get("m_name"),
              entero(fila.get("m_level")),
              (String) fila.get("m_color"),
              (BigDecimal) fila.get("price"),
              // NULO A PROPÓSITO: el precio de compra es el costo de NEXUS y
              // esta consulta NO LO SELECCIONA (`RN-PM-024`, 12-09-2026). Si lo
              // trajera, estaría a un campo de distancia de publicarse en la
              // oferta. Entre el 08-09-2026 y el 12-09-2026 aquí viajó el
              // precio público, cuando el segundo importe era lo que se
              // anunciaba.
              null,
              (UUID) fila.get("c_id"),
              (String) fila.get("c_code"),
              ((Number) fila.get("c_decimales")).intValue(),
              entero(fila.get("validity_days")),
              (String) fila.get("scope"),
              (String) fila.get("implementation"),
              (String) fila.get("status"),
              momento(fila.get("created_at")),
              // Ni `updated_at` ni `deleted_at`: la oferta no los selecciona.
              // El segundo es siempre nulo aquí —el predicado ya lo exige— y
              // seleccionarlo para descartarlo sugeriría que puede no serlo.
              null,
              null,
              decimal(fila.get("rating_avg")),
              contador(fila.get("rating_count"))));
    }
    return resultado;
  }

  // ---------------------------------------------------------------------------
  // El predicado, en un solo sitio

  /**
   * El producto del hotlink (`RF-PM-008` · `T-04`).
   *
   * <p><b>Tres condiciones y ninguna sobra</b>: activo (`RN-PM-009`), no retirado y de alcance
   * {@code HOTLINKS} (`RN-PM-021`). El tercero es el que hace que `RN-PM-019` **filtre por primera
   * vez** — llevaba desde el 07-09-2026 declarado sin acotar ninguna consulta.
   *
   * <p><b>Reutiliza la misma proyección que el detalle</b>, con las mismas uniones externas: dos
   * formas del mismo dato acabarían con dos criterios de «publicado» que divergen.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<ProductRow> findPublishedByCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.type AS type, p.name AS name,
                       p.description AS description, p.icon AS icon, p.video_url AS video_url,
               p.cover_image_id AS cover_image_id,
                       p.target_membership_id AS m_id, m.code AS m_code, m.name AS m_name,
                       m.level AS m_level, m.color AS m_color,
                       p.source_membership_id AS s_id, s.code AS s_code, s.name AS s_name,
                       s.level AS s_level, s.color AS s_color,
                       p.price AS price,
                       p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days, p.scope AS scope,
                       p.implementation AS implementation, p.status AS status,
                       p.created_at AS created_at,
                       r.rating_avg AS rating_avg, r.rating_count AS rating_count
                  FROM products p
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                  LEFT JOIN memberships s ON s.id = p.source_membership_id
                  LEFT JOIN currencies  c ON c.id = p.currency_id
                  LEFT JOIN LATERAL (
                      SELECT avg(pc.rating) AS rating_avg, count(*) AS rating_count
                        FROM product_comments pc
                       WHERE pc.product_id = p.id AND pc.deleted_at IS NULL
                  ) r ON true
                 WHERE upper(p.code) = upper(:codigo)
                   AND p.status = 'ACTIVO'
                   AND p.deleted_at IS NULL
                   AND p.scope IN ('HOTLINK', 'AMBOS')
                """,
                Tuple.class)
            .setParameter("codigo", code)
            .getResultList();

    return filas.stream().findFirst().map(JpaProductQueryRepository::fila);
  }

  @Override
  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<UUID> findPublishedByHotlink(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    // El predicado de `RN-PM-021`, el mismo del enlace público y del catálogo de
    // hotlinks: activo, no retirado y de alcance HOTLINK o AMBOS.
    return em.createNativeQuery(
            """
            SELECT p.id
              FROM products p
             WHERE p.id IN (:ids)
               AND p.status = 'ACTIVO'
               AND p.deleted_at IS NULL
               AND p.scope IN ('HOTLINK', 'AMBOS')
            """,
            UUID.class)
        .setParameter("ids", ids)
        .getResultList();
  }

  /**
   * La proyección, en un solo sitio: dos copias divergirían campo a campo.
   *
   * <p><b>La usa solo {@code findPublishedByCode}</b>, que es la lectura <b>pública</b>. <b>No
   * selecciona el precio de compra</b> y deja {@code purchasePrice} nulo a propósito (`RN-PM-024`,
   * 12-09-2026): es el costo de NEXUS, y por esta lectura —<b>sin token</b>— no debe poder viajar
   * ni por descuido. Entre el 08-09-2026 y el 12-09-2026 seleccionó los dos importes, cuando el
   * segundo era lo que se anunciaba (`requirements/pm.md` §5.2.5 y §5.2.6).
   */
  static ProductRow fila(Tuple fila) {
    return new ProductRow(
        (UUID) fila.get("id"),
        (String) fila.get("code"),
        (String) fila.get("type"),
        (String) fila.get("name"),
        (String) fila.get("description"),
        (String) fila.get("icon"),
        (String) fila.get("video_url"),
        (UUID) fila.get("cover_image_id"),
        (UUID) fila.get("s_id"),
        (String) fila.get("s_code"),
        (String) fila.get("s_name"),
        entero(fila.get("s_level")),
        (String) fila.get("s_color"),
        (UUID) fila.get("m_id"),
        (String) fila.get("m_code"),
        (String) fila.get("m_name"),
        entero(fila.get("m_level")),
        (String) fila.get("m_color"),
        (BigDecimal) fila.get("price"),
        // NULO A PROPÓSITO: el costo no se selecciona en la lectura sin token
        // (`RN-PM-024`, 12-09-2026). Ver el Javadoc de arriba.
        null,
        (UUID) fila.get("c_id"),
        (String) fila.get("c_code"),
        ((Number) fila.get("c_decimales")).intValue(),
        entero(fila.get("validity_days")),
        (String) fila.get("scope"),
        (String) fila.get("implementation"),
        (String) fila.get("status"),
        momento(fila.get("created_at")),
        null,
        null,
        decimal(fila.get("rating_avg")),
        contador(fila.get("rating_count")));
  }

  // ---------------------------------------------------------------------------

  /**
   * Los siete filtros, todos sobre columnas de {@code products}.
   *
   * <p><b>Ninguno puede multiplicar la fila</b>, y por eso aquí no hace falta el {@code EXISTS} que
   * el listado de personas necesitó: un producto tiene <b>un</b> destino y <b>una</b> moneda, no
   * colecciones. El día que se filtre por algo que sí lo sea, el conteo empezaría a contar
   * asignaciones en lugar de productos.
   *
   * <p>El filtro por destino combinado con {@code type=BOT} devuelve vacío y <b>no se rechaza</b>
   * (`spec.md` §13): la combinación es coherente aunque sea inútil, y rechazarla obligaría a
   * explicar por qué dos filtros válidos por separado no lo son juntos.
   */
  private static String predicado(ListProductsRequest filtros) {
    StringBuilder donde = new StringBuilder();
    donde.append(filtros.incluirEliminados() ? "1 = 1" : "p.deleted_at IS NULL");

    if (filtros.type() != null) {
      donde.append(" AND p.type = :tipo");
    }
    if (filtros.status() != null) {
      donde.append(" AND p.status = :estado");
    }
    if (filtros.scope() != null) {
      donde.append(" AND p.scope = :alcance");
    }
    if (filtros.implementation() != null) {
      donde.append(" AND p.implementation = :implementacion");
    }
    if (filtros.targetMembershipId() != null) {
      donde.append(" AND p.target_membership_id = :destino");
    }
    if (filtros.sourceMembershipId() != null) {
      donde.append(" AND p.source_membership_id = :origen");
    }
    if (filtros.search() != null) {
      // La normalización la hace LA BASE DE DATOS, con la misma función que
      // alimenta `ix_products_busqueda`: normalizar en Java produce un
      // resultado parecido y no idéntico, y cualquier divergencia se manifiesta
      // como un producto indexado que no aparece en su propia búsqueda.
      //
      // El ESCAPE se declara en la sentencia y no se hereda de la
      // configuración del motor: heredarlo haría que el escape del término
      // dependiera de un parámetro que nadie de este lado controla.
      donde.append(" AND f_unaccent(lower(p.name)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'");
    }
    return donde.toString();
  }

  private static void enlazar(Query consulta, ListProductsRequest filtros) {
    if (filtros.type() != null) {
      consulta.setParameter("tipo", filtros.type());
    }
    if (filtros.status() != null) {
      consulta.setParameter("estado", filtros.status());
    }
    if (filtros.scope() != null) {
      consulta.setParameter("alcance", filtros.scope());
    }
    if (filtros.implementation() != null) {
      consulta.setParameter("implementacion", filtros.implementation());
    }
    if (filtros.targetMembershipId() != null) {
      consulta.setParameter("destino", filtros.targetMembershipId());
    }
    if (filtros.sourceMembershipId() != null) {
      consulta.setParameter("origen", filtros.sourceMembershipId());
    }
    if (filtros.search() != null) {
      consulta.setParameter("termino", "%" + escapar(filtros.search()) + "%");
    }
  }

  /**
   * Escapa lo que {@code LIKE} interpreta.
   *
   * <p>Sin esto, buscar {@code %} devuelve el catálogo entero y buscar {@code _} devuelve todo lo
   * que tenga un carácter en esa posición: el término dejaría de ser un texto para pasar a ser un
   * patrón, y quien busque un nombre con guion bajo no encontraría el suyo.
   *
   * <p><b>La barra va primero</b>: escaparla después convertiría en literales las barras que este
   * mismo método acaba de introducir.
   */
  private static String escapar(String termino) {
    return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /**
   * {@code avg()} llega como {@code BigDecimal} —o nulo sin filas— y se redondea en {@code
   * RatingSummary}, no aquí.
   */
  private static BigDecimal decimal(Object valor) {
    return valor == null ? null : new BigDecimal(valor.toString());
  }

  /** {@code count(*)} llega como {@code Long}; cero sin filas, sin {@code COALESCE}. */
  private static long contador(Object valor) {
    return valor == null ? 0L : ((Number) valor).longValue();
  }

  private static Integer entero(Object valor) {
    return valor == null ? null : ((Number) valor).intValue();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isPurchasable(UUID productId) {
    if (productId == null) {
      return false;
    }
    return !em.createNativeQuery(
            "SELECT 1 FROM products WHERE id = :id AND status = 'ACTIVO' AND deleted_at IS NULL")
        .setParameter("id", productId)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  private static OffsetDateTime momento(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime instante -> instante;
      case Instant instante -> instante.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }
}
