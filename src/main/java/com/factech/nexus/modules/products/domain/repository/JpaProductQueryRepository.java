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
               p.source_membership_id AS s_id, s.code AS s_code, s.name AS s_name,
               s.level AS s_level,
               p.price AS price, p.currency_id AS c_id, c.code AS c_code,
               c.decimal_places AS c_decimales,
               p.validity_days AS validity_days, p.status AS status,
               p.created_at AS created_at, p.deleted_at AS deleted_at
          FROM products p
          LEFT JOIN memberships m ON m.id = p.target_membership_id
          LEFT JOIN memberships s ON s.id = p.source_membership_id
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
              (UUID) fila.get("s_id"),
              (String) fila.get("s_code"),
              (String) fila.get("s_name"),
              entero(fila.get("s_level")),
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
                       p.source_membership_id AS s_id, s.code AS s_code, s.name AS s_name,
                       s.level AS s_level,
                       p.price AS price, p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days, p.status AS status,
                       p.created_at AS created_at, p.updated_at AS updated_at,
                       p.deleted_at AS deleted_at
                  FROM products p
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                  LEFT JOIN memberships s ON s.id = p.source_membership_id
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
                    (UUID) fila.get("s_id"),
                    (String) fila.get("s_code"),
                    (String) fila.get("s_name"),
                    entero(fila.get("s_level")),
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

  /**
   * La oferta, en <b>una</b> sentencia (`RF-PM-007` · `T-03`, `T-04`).
   *
   * <h2>«Nivel superior» es número MENOR, y esta es la línea que decide el requerimiento</h2>
   *
   * <p>La cadena de membresías crece hacia abajo: {@code 1} es la cima (`requirements/sp.md`
   * §10.4). De modo que ofrecer «hacia arriba» (`RN-PM-011`) es {@code m.level < :nivel}, con
   * <b>menor estricto</b>. Escrito al revés, esta consulta pasaría todas las pruebas de camino
   * feliz y ofrecería <b>exactamente lo contrario</b> —bajadas de nivel, cobrando por ellas—, que
   * es el riesgo 1 del plan y el motivo de que `T-06` pruebe los tres casos: destino inferior,
   * igual y superior.
   *
   * <p>El estricto es lo que implementa `CA-PM-060`: el upgrade hacia el nivel que la persona <b>ya
   * tiene</b> no se ofrece, porque sería cobrarle por quedarse donde está.
   *
   * <h2>Sin nivel no es «sin filtro»</h2>
   *
   * <p>Con {@code :nivel} nulo, {@code m.level < NULL} evalúa a {@code NULL} y la fila <b>queda
   * fuera</b> — que es justo lo que `FA-001` pide—. Aun así la condición se escribe con su {@code
   * IS NOT NULL} <b>explícito</b> y delante: este proyecto ya pagó una vez por confiar en cómo se
   * comporta el nulo dentro de una condición compuesta —{@code ck_deletion_reason} evaluaba a
   * {@code NULL} y por tanto <b>aceptaba</b> la fila—, y en un {@code WHERE} el nulo excluye
   * mientras que en un {@code CHECK} admite. Escribirlo dice cuál de los dos comportamientos se
   * está usando, en lugar de dejar que quien lo lea tenga que recordarlo.
   *
   * <h2>El orden</h2>
   *
   * <p>Los upgrades primero y los bots después (`CA-PM-078`); dentro de los upgrades, por <b>nivel
   * de destino</b> y no por precio ni por nombre, porque es el único orden en el que «subir»
   * significa algo (`CA-PM-079`). {@code DESC} sobre el número es <b>del salto más corto al más
   * largo</b>: quien está en el último peldaño ve primero el siguiente y al final la cima, que es
   * el orden en el que se sube. Los bots, por fecha de alta.
   */
  @Override
  @Transactional(readOnly = true)
  public List<ProductRow> findOffer(Integer nivel) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.type AS type, p.name AS name,
                       p.description AS description, p.icon AS icon,
                       p.source_membership_id AS s_id, s.code AS s_code, s.name AS s_name,
                       s.level AS s_level,
                       p.target_membership_id AS m_id, m.code AS m_code, m.name AS m_name,
                       m.level AS m_level,
                       p.price AS price, p.currency_id AS c_id, c.code AS c_code,
                       c.decimal_places AS c_decimales,
                       p.validity_days AS validity_days, p.status AS status,
                       p.created_at AS created_at
                  FROM products p
                  LEFT JOIN memberships s ON s.id = p.source_membership_id
                  LEFT JOIN memberships m ON m.id = p.target_membership_id
                  LEFT JOIN currencies  c ON c.id = p.currency_id
                 WHERE p.deleted_at IS NULL
                   AND p.status = 'ACTIVO'
                   AND ( p.type = 'BOT'
                         OR ( CAST(:nivel AS integer) IS NOT NULL
                              AND m.level < CAST(:nivel AS integer) ) )
                 ORDER BY CASE WHEN p.type = 'UPGRADE_MEMBRESIA' THEN 0 ELSE 1 END,
                          m.level DESC,
                          p.created_at ASC,
                          p.id ASC
                """,
                Tuple.class)
            .setParameter("nivel", nivel)
            .getResultList();

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
              (UUID) fila.get("s_id"),
              (String) fila.get("s_code"),
              (String) fila.get("s_name"),
              entero(fila.get("s_level")),
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
              // Ni `updated_at` ni `deleted_at`: la oferta no los selecciona.
              // El segundo es siempre nulo aquí —el predicado ya lo exige— y
              // seleccionarlo para descartarlo sugeriría que puede no serlo.
              null,
              null));
    }
    return resultado;
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
