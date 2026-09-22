package com.factech.nexus.modules.system.memberships.domain.repository;

import com.factech.nexus.modules.system.memberships.application.MembershipDetailItem;
import com.factech.nexus.modules.system.memberships.application.MembershipItem;
import com.factech.nexus.modules.system.memberships.application.MembershipNeighborItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de lectura de la cadena (`RF-SP-017`, `RF-SP-018`).
 *
 * <p><b>Una sola sentencia por consulta, y sin cruce con {@code user_memberships}.</b> Lo segundo
 * no es una omisión: ni el listado ni el detalle devuelven cuántas personas tienen cada membresía
 * (`spec.md` §14 de ambos), y que no exista el {@code JOIN} en el SQL es lo único que lo hace
 * verificable. Cuando `RF-SP-025` exista, esa pregunta se responderá filtrando allí.
 *
 * <p>El vecino de abajo se resuelve con una auto-unión: la hija de {@code X} es la fila que apunta
 * a {@code X}. Viene gratis en la misma sentencia y evita que cada consumidor implemente ese cruce
 * a su manera.
 */
@Repository
public class JpaMembershipQueryRepository implements MembershipQueryRepository {

  /**
   * Carácter de escape del {@code LIKE}, declarado explícito.
   *
   * <p>En PostgreSQL coincide con el de por omisión, pero dejarlo implícito hace que el escape
   * dependa de una configuración del motor en lugar de la consulta. Sin él, un término con {@code
   * %} devolvería la cadena entera.
   */
  private static final char ESCAPE = '\\';

  private final EntityManager em;

  public JpaMembershipQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MembershipItem> findChain(String search) {
    String termino = normalizar(search);

    // El ORDER BY es por `level` y no es negociable: el orden ES la
    // información (`RF-SP-017` spec.md §2). Un `sort` arbitrario produciría una
    // lista alfabética de niveles, que es un artefacto sin significado.
    String sql =
        """
        SELECT m.id            AS id,
               m.code          AS code,
               m.name          AS name,
               m.description   AS description,
               m.color         AS color,
               m.level         AS level,
               m.parent_membership_id AS parent_id,
               h.id            AS child_id
          FROM memberships m
          LEFT JOIN memberships h ON h.parent_membership_id = m.id
        """
            // `f_unaccent` se aplica también al TÉRMINO y no solo a la columna:
            // si solo se normalizara un lado, buscar «membresia» no encontraría
            // «Membresía», que es justo lo que la comparación insensible a
            // acentos existe para resolver.
            + (termino == null
                ? ""
                : """
                   WHERE f_unaccent(lower(m.code)) LIKE f_unaccent(CAST(:termino AS text)) ESCAPE '\\'
                      OR f_unaccent(lower(m.name)) LIKE f_unaccent(CAST(:termino AS text)) ESCAPE '\\'
                  """)
            + " ORDER BY m.level";

    Query consulta = em.createNativeQuery(sql, Tuple.class);
    if (termino != null) {
      consulta.setParameter("termino", "%" + termino + "%");
    }

    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    return filas.stream()
        .map(
            fila ->
                new MembershipItem(
                    (UUID) fila.get("id"),
                    (String) fila.get("code"),
                    (String) fila.get("name"),
                    (String) fila.get("description"),
                    (String) fila.get("color"),
                    ((Number) fila.get("level")).intValue(),
                    (UUID) fila.get("parent_id"),
                    (UUID) fila.get("child_id")))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MembershipDetailItem> findDetail(UUID id) {
    // Dos uniones externas: la superior por la clave foránea y la hija por la
    // inversa. Los vecinos llegan solo hasta el primer grado y NO traen sus
    // propios vecinos: anidarlos convertiría la respuesta en la cadena completa
    // por un camino distinto (`RF-SP-018` plan.md §4).
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT m.id          AS id,
                       m.code        AS code,
                       m.name        AS name,
                       m.description AS description,
                       m.color       AS color,
                       m.level       AS level,
                       p.id          AS parent_id,
                       p.code        AS parent_code,
                       p.name        AS parent_name,
                       p.color       AS parent_color,
                       p.level       AS parent_level,
                       h.id          AS child_id,
                       h.code        AS child_code,
                       h.name        AS child_name,
                       h.color       AS child_color,
                       h.level       AS child_level
                  FROM memberships m
                  LEFT JOIN memberships p ON p.id = m.parent_membership_id
                  LEFT JOIN memberships h ON h.parent_membership_id = m.id
                 WHERE m.id = :id
                """,
                Tuple.class)
            .setParameter("id", id)
            .getResultList();

    if (filas.isEmpty()) {
      return Optional.empty();
    }

    Tuple fila = filas.get(0);
    return Optional.of(
        new MembershipDetailItem(
            (UUID) fila.get("id"),
            (String) fila.get("code"),
            (String) fila.get("name"),
            (String) fila.get("description"),
            (String) fila.get("color"),
            ((Number) fila.get("level")).intValue(),
            vecino(fila, "parent"),
            vecino(fila, "child")));
  }

  /**
   * Nulo cuando la unión externa no encontró vecino: la cima no tiene superior, la última no tiene
   * hija.
   */
  private static MembershipNeighborItem vecino(Tuple fila, String prefijo) {
    UUID id = (UUID) fila.get(prefijo + "_id");
    if (id == null) {
      return null;
    }
    return new MembershipNeighborItem(
        id,
        (String) fila.get(prefijo + "_code"),
        (String) fila.get(prefijo + "_name"),
        ((Number) fila.get(prefijo + "_level")).intValue(),
        (String) fila.get(prefijo + "_color"));
  }

  /**
   * Recorta el término, lo normaliza y escapa los comodines.
   *
   * <p>En blanco equivale a ausente: un formulario que envía el campo vacío no está pidiendo un
   * filtro. El escape va antes de envolver en {@code %} para no escapar los que añade la consulta.
   */
  private static String normalizar(String search) {
    if (search == null || search.isBlank()) {
      return null;
    }
    return search
        .trim()
        .toLowerCase(java.util.Locale.ROOT)
        .replace(String.valueOf(ESCAPE), ESCAPE + "\\")
        .replace("%", ESCAPE + "%")
        .replace("_", ESCAPE + "_");
  }
}
