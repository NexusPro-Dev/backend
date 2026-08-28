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
               p.description AS description, p.icon AS icon,
               p.target_membership_id AS m_id, m.code AS m_code, m.name AS m_name,
               m.level AS m_level,
               p.price AS price, p.currency_id AS c_id, c.code AS c_code,
               c.decimal_places AS c_decimales,
               p.validity_days AS validity_days, p.status AS status,
               p.created_at AS created_at, p.deleted_at AS deleted_at
          FROM products p
          LEFT JOIN memberships m ON m.id = p.target_membership_id
          LEFT JOIN currencies  c ON c.id = p.currency_id
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
              (UUID) fila.get("m_id"),
              (String) fila.get("m_code"),
              (String) fila.get("m_name"),
              entero(fila.get("m_level")),
              (BigDecimal) fila.get("price"),
              (UUID) fila.get("c_id"),
              (String) fila.get("c_code"),
              ((Number) fila.get("c_decimales")).intValue(),
              entero(fila.get("validity_days")),
              (String) fila.get("status"),
              momento(fila.get("created_at")),
              // El listado no selecciona `updated_at`: nadie pregunta a una
              // lista cuándo se tocó cada fila por última vez.
              null,
              momento(fila.get("deleted_at"))));
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
                       p.description AS description, p.icon AS icon,
                       p.target_membership_id AS m_id, m.code AS m_code, m.name AS m_name,
                       m.level AS m_level,
                       p.price AS price, p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days, p.status AS status,
                       p.created_at AS created_at, p.updated_at AS updated_at,
                       p.deleted_at AS deleted_at
                  FROM products p
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                  LEFT JOIN currencies  c ON c.id = p.currency_id
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
                    (UUID) fila.get("m_id"),
                    (String) fila.get("m_code"),
                    (String) fila.get("m_name"),
                    entero(fila.get("m_level")),
                    (BigDecimal) fila.get("price"),
                    (UUID) fila.get("c_id"),
                    (String) fila.get("c_code"),
                    ((Number) fila.get("c_decimales")).intValue(),
                    entero(fila.get("validity_days")),
                    (String) fila.get("status"),
                    momento(fila.get("created_at")),
                    momento(fila.get("updated_at")),
                    momento(fila.get("deleted_at"))));
  }

  // ---------------------------------------------------------------------------
  // El predicado, en un solo sitio
  // ---------------------------------------------------------------------------

  /**
   * Los cinco filtros, todos sobre columnas de {@code products}.
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
    if (filtros.targetMembershipId() != null) {
      donde.append(" AND p.target_membership_id = :destino");
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
    if (filtros.targetMembershipId() != null) {
      consulta.setParameter("destino", filtros.targetMembershipId());
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

  private static Integer entero(Object valor) {
    return valor == null ? null : ((Number) valor).intValue();
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
