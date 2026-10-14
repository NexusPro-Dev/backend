package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.Signal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * {@link SignalRepository} sobre JPA. Las lecturas filtradas van por SQL nativo con la entidad como
 * resultado; las membresías se leen de {@code memberships} directamente, como las de las clases en
 * vivo.
 */
@Repository
public class JpaSignalRepository implements SignalRepository {

  private static final String VIGENTE = "s.deleted_at IS NULL AND s.visible_until > :ahora";

  private final EntityManager em;

  public JpaSignalRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void insert(Signal senal, Collection<UUID> membresias) {
    em.persist(senal);
    em.flush();
    for (UUID membresia : membresias) {
      em.createNativeQuery(
              "INSERT INTO signal_memberships (signal_id, membership_id) VALUES (:senal, :m)")
          .setParameter("senal", senal.getId())
          .setParameter("m", membresia)
          .executeUpdate();
    }
  }

  @Override
  public Optional<Signal> findByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT s FROM Signal s WHERE s.id = :id", Signal.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public List<Signal> search(SignalFilter filtro, int offset, int size) {
    Map<String, Object> parametros = new LinkedHashMap<>();
    String sql =
        "SELECT s.* FROM signals s"
            + donde(filtro, parametros)
            + " ORDER BY s.created_at DESC, s.id DESC OFFSET :desde LIMIT :cuantos";
    parametros.put("desde", offset);
    parametros.put("cuantos", size);
    Query consulta = em.createNativeQuery(sql, Signal.class);
    parametros.forEach(consulta::setParameter);
    @SuppressWarnings("unchecked")
    List<Signal> filas = consulta.getResultList();
    return filas;
  }

  @Override
  public long count(SignalFilter filtro) {
    Map<String, Object> parametros = new LinkedHashMap<>();
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM signals s" + donde(filtro, parametros));
    parametros.forEach(consulta::setParameter);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  public List<Signal> available(OffsetDateTime ahora, UUID membresia) {
    if (membresia == null) {
      return List.of();
    }
    @SuppressWarnings("unchecked")
    List<Signal> filas =
        em.createNativeQuery(
                """
                SELECT s.* FROM signals s
                 WHERE s.deleted_at IS NULL AND s.visible_until > :ahora
                   AND (s.subscription_type = 'FREE'
                        OR (s.subscription_type = 'PREMIUM'
                            AND (SELECT m.level FROM memberships m WHERE m.id = :membresia)
                                < (SELECT b.level FROM memberships b WHERE b.code = 'BECA'))
                        OR (s.subscription_type = 'PERSONALIZED'
                            AND EXISTS (SELECT 1 FROM signal_memberships x
                                         WHERE x.signal_id = s.id
                                           AND x.membership_id = :membresia)))
                 ORDER BY s.visible_until, s.created_at, s.id
                """,
                Signal.class)
            .setParameter("ahora", ahora)
            .setParameter("membresia", membresia)
            .getResultList();
    return filas;
  }

  @Override
  public Map<UUID, List<MembershipRef>> membershipsOf(Collection<UUID> senales) {
    if (senales.isEmpty()) {
      return Map.of();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT x.signal_id AS senal, m.id AS id, m.code AS code, m.name AS name
                  FROM signal_memberships x
                  JOIN memberships m ON m.id = x.membership_id
                 WHERE x.signal_id IN (:senales)
                 ORDER BY m.level, m.id
                """,
                Tuple.class)
            .setParameter("senales", senales)
            .getResultList();
    return filas.stream()
        .collect(
            Collectors.groupingBy(
                f -> (UUID) f.get("senal"),
                LinkedHashMap::new,
                Collectors.mapping(
                    f ->
                        new MembershipRef(
                            (UUID) f.get("id"), (String) f.get("code"), (String) f.get("name")),
                    Collectors.toList())));
  }

  @Override
  public Set<UUID> existingMemberships(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Set.of();
    }
    @SuppressWarnings("unchecked")
    List<UUID> filas =
        em.createNativeQuery("SELECT id FROM memberships WHERE id IN (:ids)")
            .setParameter("ids", ids)
            .getResultList();
    return new HashSet<>(filas);
  }

  @Override
  public Map<String, UUID> membershipsByCode(Collection<String> codigos) {
    if (codigos.isEmpty()) {
      return Map.of();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT upper(code) AS code, id AS id FROM memberships"
                    + " WHERE upper(code) IN (:codigos)",
                Tuple.class)
            .setParameter("codigos", codigos)
            .getResultList();
    return filas.stream()
        .collect(Collectors.toMap(f -> (String) f.get("code"), f -> (UUID) f.get("id")));
  }

  private static String donde(SignalFilter filtro, Map<String, Object> parametros) {
    StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
    if (!filtro.includeDeleted()) {
      sql.append(" AND s.deleted_at IS NULL");
    }
    if (filtro.subscriptionType() != null) {
      sql.append(" AND s.subscription_type = :tipo");
      parametros.put("tipo", filtro.subscriptionType().name());
    }
    if (filtro.visible() != null) {
      sql.append(filtro.visible() ? " AND " + VIGENTE : " AND NOT (" + VIGENTE + ")");
      parametros.put("ahora", filtro.ahora());
    }
    return sql.toString();
  }
}
