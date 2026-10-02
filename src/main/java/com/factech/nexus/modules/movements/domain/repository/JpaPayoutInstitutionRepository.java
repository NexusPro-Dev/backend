package com.factech.nexus.modules.movements.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador del catálogo de entidades de cobro. SQL nativo, como el resto de `MV`. */
@Repository
public class JpaPayoutInstitutionRepository implements PayoutInstitutionRepository {

  private static final String SELECCION =
      """
      SELECT i.id AS id, i.code AS code, i.name AS name, i.kind AS kind,
             c.id AS pais, c.code AS pais_codigo, c.name AS pais_nombre,
             i.is_active AS activa, i.created_at AS creada
        FROM payout_institutions i
        JOIN countries c ON c.id = i.country_id
      """;

  private final EntityManager em;

  public JpaPayoutInstitutionRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean insert(
      UUID id, String code, String name, String kind, UUID countryId, OffsetDateTime at) {
    return em.createNativeQuery(
                """
                INSERT INTO payout_institutions (id, code, name, kind, country_id, is_active,
                                                 created_at, updated_at)
                VALUES (:id, :codigo, :nombre, :tipo, :pais, true, :ahora, :ahora)
                ON CONFLICT (code) DO NOTHING
                """)
            .setParameter("id", id)
            .setParameter("codigo", code)
            .setParameter("nombre", name)
            .setParameter("tipo", kind)
            .setParameter("pais", countryId)
            .setParameter("ahora", at)
            .executeUpdate()
        == 1;
  }

  @Override
  public Optional<InstitutionRow> find(UUID id) {
    return una(SELECCION + " WHERE i.id = :id", id);
  }

  @Override
  public Optional<InstitutionRow> lock(UUID id) {
    return una(SELECCION + " WHERE i.id = :id FOR UPDATE OF i", id);
  }

  @Override
  public List<InstitutionRow> list(UUID countryId, String kind, Boolean active) {
    StringBuilder sql = new StringBuilder(SELECCION).append(" WHERE true");
    if (countryId != null) {
      sql.append(" AND i.country_id = :pais");
    }
    if (kind != null) {
      sql.append(" AND i.kind = :tipo");
    }
    if (active != null) {
      sql.append(" AND i.is_active = :activa");
    }
    sql.append(" ORDER BY c.name, i.name, i.id");
    Query consulta = em.createNativeQuery(sql.toString(), Tuple.class);
    if (countryId != null) {
      consulta.setParameter("pais", countryId);
    }
    if (kind != null) {
      consulta.setParameter("tipo", kind);
    }
    if (active != null) {
      consulta.setParameter("activa", active);
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    return filas.stream().map(JpaPayoutInstitutionRepository::fila).toList();
  }

  @Override
  public void update(UUID id, String name, boolean active, OffsetDateTime at) {
    em.createNativeQuery(
            "UPDATE payout_institutions SET name = :nombre, is_active = :activa,"
                + " updated_at = :ahora WHERE id = :id")
        .setParameter("id", id)
        .setParameter("nombre", name)
        .setParameter("activa", active)
        .setParameter("ahora", at)
        .executeUpdate();
  }

  private Optional<InstitutionRow> una(String sql, UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(sql, Tuple.class).setParameter("id", id).getResultList();
    return filas.stream().findFirst().map(JpaPayoutInstitutionRepository::fila);
  }

  private static InstitutionRow fila(Tuple f) {
    return new InstitutionRow(
        (UUID) f.get("id"),
        (String) f.get("code"),
        (String) f.get("name"),
        (String) f.get("kind"),
        (UUID) f.get("pais"),
        ((String) f.get("pais_codigo")).trim(),
        (String) f.get("pais_nombre"),
        (Boolean) f.get("activa"),
        PayoutTimes.instante(f.get("creada")));
  }
}
