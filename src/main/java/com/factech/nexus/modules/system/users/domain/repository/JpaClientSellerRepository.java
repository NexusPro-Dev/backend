package com.factech.nexus.modules.system.users.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * `client_sellers` con {@link EntityManager} y consultas nativas (`RF-SP-059` · `T-03`).
 *
 * <p>La proyección cruza {@code client_sellers} con {@code users} para traer los datos del vendedor
 * en la misma consulta: son dos o tres filas por cliente y no hay agregado que justifique una
 * entidad.
 */
@Repository
public class JpaClientSellerRepository implements ClientSellerRepository {

  private static final String PROYECCION =
      """
      SELECT cs.seller_id AS seller_id, s.username AS username,
             s.first_name AS first_name, s.last_name AS last_name,
             cs.origin AS origin, cs.created_at AS linked_at
        FROM client_sellers cs
        JOIN users s ON s.id = cs.seller_id
       WHERE cs.client_id = :cliente
      """;

  /**
   * La cartera: la misma tabla mirada por {@code seller_id}. `JOIN users c` con {@code deleted_at
   * IS NULL} deja fuera al cliente eliminado (`RF-SP-061` `FA-007`); el origen entra como filtro
   * opcional resuelto en SQL —{@code :origen IS NULL}— para que conteo y página compartan el
   * predicado y no puedan discrepar.
   */
  private static final String CARTERA =
      """
        FROM client_sellers cs
        JOIN users c ON c.id = cs.client_id AND c.deleted_at IS NULL
       WHERE cs.seller_id = :vendedor
         AND (CAST(:origen AS text) IS NULL OR cs.origin = CAST(:origen AS text))
      """;

  private final EntityManager em;

  public JpaClientSellerRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional
  public void registerPrincipal(UUID clientId, UUID sellerId, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)
            VALUES (:cliente, :vendedor, 'REGISTRO', NULL, :ahora)
            """)
        .setParameter("cliente", clientId)
        .setParameter("vendedor", sellerId)
        .setParameter("ahora", ahora)
        .executeUpdate();
  }

  @Override
  @Transactional
  public void attachFirstMovement(UUID clientId, UUID sellerId, UUID movementId) {
    em.createNativeQuery(
            """
            UPDATE client_sellers
               SET first_movement_id = :venta
             WHERE client_id = :cliente AND seller_id = :vendedor
               AND first_movement_id IS NULL
            """)
        .setParameter("venta", movementId)
        .setParameter("cliente", clientId)
        .setParameter("vendedor", sellerId)
        .executeUpdate();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ClientSellerRow> findPrincipalOf(UUID clientId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(PROYECCION + " AND cs.origin = 'REGISTRO'", Tuple.class)
            .setParameter("cliente", clientId)
            .getResultList();
    return filas.stream().map(JpaClientSellerRepository::fila).findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ClientSellerRow> findSellersOf(UUID clientId) {
    // El principal primero, después por antigüedad del vínculo, y el nombre de
    // usuario como desempate: dos vínculos en el mismo instante no bailan entre
    // llamadas.
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                PROYECCION + " ORDER BY (cs.origin = 'REGISTRO') DESC, cs.created_at, s.username",
                Tuple.class)
            .setParameter("cliente", clientId)
            .getResultList();
    return filas.stream().map(JpaClientSellerRepository::fila).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countClientsOf(UUID sellerId, String origin) {
    Object total =
        em.createNativeQuery("SELECT count(*)" + CARTERA)
            .setParameter("vendedor", sellerId)
            .setParameter("origen", origin)
            .getSingleResult();
    return ((Number) total).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public List<SellerClientRow> findClientsOf(UUID sellerId, String origin, int offset, int limit) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT cs.client_id AS client_id, c.username AS username,
                       c.first_name AS first_name, c.last_name AS last_name,
                       c.status AS status, cs.origin AS origin, cs.created_at AS linked_at
                """
                    + CARTERA
                    + " ORDER BY cs.created_at DESC, c.username",
                Tuple.class)
            .setParameter("vendedor", sellerId)
            .setParameter("origen", origin)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList();
    return filas.stream().map(JpaClientSellerRepository::cliente).toList();
  }

  private static SellerClientRow cliente(Tuple fila) {
    return new SellerClientRow(
        (UUID) fila.get("client_id"),
        (String) fila.get("username"),
        (String) fila.get("first_name"),
        (String) fila.get("last_name"),
        (String) fila.get("status"),
        (String) fila.get("origin"),
        momento(fila.get("linked_at")));
  }

  private static ClientSellerRow fila(Tuple fila) {
    return new ClientSellerRow(
        (UUID) fila.get("seller_id"),
        (String) fila.get("username"),
        (String) fila.get("first_name"),
        (String) fila.get("last_name"),
        (String) fila.get("origin"),
        momento(fila.get("linked_at")));
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
