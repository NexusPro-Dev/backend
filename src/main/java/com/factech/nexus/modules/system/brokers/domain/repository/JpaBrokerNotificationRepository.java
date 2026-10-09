package com.factech.nexus.modules.system.brokers.domain.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link BrokerNotificationRepository} (`RF-SP-078` · `T-03`).
 *
 * <p><b>Sin entidad JPA</b>, como el catálogo de brokers y como {@code gateway_events}: la fila se
 * escribe una vez y no se vuelve a cargar para escribirla.
 */
@Repository
public class JpaBrokerNotificationRepository implements BrokerNotificationRepository {

  private final EntityManager em;

  public JpaBrokerNotificationRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<UUID> findActiveByName(String name) {
    @SuppressWarnings("unchecked")
    List<UUID> filas =
        em.createNativeQuery(
                "SELECT id FROM brokers"
                    + " WHERE f_unaccent(lower(name)) = f_unaccent(lower(:nombre)) AND is_active")
            .setParameter("nombre", name)
            .getResultList();
    return filas.stream().findFirst();
  }

  @Override
  public Optional<UUID> findActiveByAdvertiser(String advertiser) {
    @SuppressWarnings("unchecked")
    List<UUID> filas =
        em.createNativeQuery(
                "SELECT id FROM brokers WHERE lower(advertiser) = lower(:anunciante) AND is_active")
            .setParameter("anunciante", advertiser)
            .getResultList();
    return filas.stream().findFirst();
  }

  @Override
  public void insert(
      UUID id,
      UUID brokerId,
      String method,
      String queryParams,
      String headers,
      String body,
      String contentType,
      String ipAddress) {
    em.createNativeQuery(
            """
            INSERT INTO broker_notifications
                (id, broker_id, method, query_params, headers, body, content_type, ip_address)
            VALUES (CAST(:id AS uuid), CAST(:broker AS uuid), :metodo, CAST(:consulta AS jsonb),
                    CAST(:cabeceras AS jsonb), CAST(:cuerpo AS text), CAST(:tipo AS varchar), CAST(:ip AS varchar))
            """)
        .setParameter("id", id)
        .setParameter("broker", brokerId)
        .setParameter("metodo", method)
        .setParameter("consulta", queryParams)
        .setParameter("cabeceras", headers)
        .setParameter("cuerpo", body)
        .setParameter("tipo", contentType)
        .setParameter("ip", ipAddress)
        .executeUpdate();
  }
}
