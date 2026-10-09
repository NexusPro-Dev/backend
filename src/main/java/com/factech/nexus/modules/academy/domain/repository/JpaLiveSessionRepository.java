package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link LiveSessionRepository} y {@link LiveSessionQueryRepository} sobre SQL nativo.
 *
 * <p><b>El instructor de la clase se lee del curso</b> en cada lectura (`RN-AC-028`): no se guarda
 * en la clase, y si el curso cambia de instructor la clase pasa con él. <b>Los predicados del
 * listado se escriben por partes</b> y solo se enlaza lo que se usa: un parámetro nulo sin tipo
 * falla en PostgreSQL (`RF-MV-008`).
 */
@Repository
public class JpaLiveSessionRepository implements LiveSessionRepository, LiveSessionQueryRepository {

  private static final String COLUMNAS =
      """
      s.id AS id, s.course_id AS course_id, c.title AS course_title,
      c.instructor_id AS course_instructor_id, s.title AS title, s.description AS description,
      s.starts_at AS starts_at, s.ends_at AS ends_at, s.zoom_meeting_id AS zoom_meeting_id,
      s.status AS status, s.cancelled_at AS cancelled_at,
      s.cancellation_reason AS cancellation_reason, s.created_by AS created_by,
      s.created_at AS created_at, s.updated_at AS updated_at
      """;

  private static final String DESDE =
      " FROM live_sessions s LEFT JOIN courses c ON c.id = s.course_id";

  private final EntityManager em;

  public JpaLiveSessionRepository(EntityManager em) {
    this.em = em;
  }

  // ---------------------------------------------------------------------------
  // Escrituras
  // ---------------------------------------------------------------------------

  @Override
  @Transactional
  public void insert(NewLiveSession clase) {
    Query alta =
        em.createNativeQuery(
            """
            INSERT INTO live_sessions (id, course_id, title, description, starts_at, ends_at,
                                       zoom_meeting_id, status, created_by)
            VALUES (:id, :curso, :titulo, :descripcion, :inicio, :fin, :reunion,
                    'PROGRAMADA', :autor)
            """);
    uuid(alta, "curso", clase.courseId());
    alta.setParameter("id", clase.id())
        .setParameter("titulo", clase.title())
        .setParameter("descripcion", clase.description())
        .setParameter("inicio", clase.startsAt())
        .setParameter("fin", clase.endsAt())
        .setParameter("reunion", clase.zoomMeetingId())
        .setParameter("autor", clase.createdBy())
        .executeUpdate();
  }

  @Override
  @Transactional
  public void update(
      UUID id,
      String title,
      String description,
      UUID courseId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt) {
    Query cambio =
        em.createNativeQuery(
            """
            UPDATE live_sessions
               SET title = :titulo, description = :descripcion, course_id = :curso,
                   starts_at = :inicio, ends_at = :fin, updated_at = now()
             WHERE id = :id
            """);
    uuid(cambio, "curso", courseId);
    cambio
        .setParameter("id", id)
        .setParameter("titulo", title)
        .setParameter("descripcion", description)
        .setParameter("inicio", startsAt)
        .setParameter("fin", endsAt)
        .executeUpdate();
  }

  @Override
  @Transactional
  public void cancel(UUID id, String reason, OffsetDateTime at) {
    em.createNativeQuery(
            """
            UPDATE live_sessions
               SET status = 'CANCELADA', cancelled_at = :cuando, cancellation_reason = :motivo,
                   updated_at = now()
             WHERE id = :id
            """)
        .setParameter("id", id)
        .setParameter("cuando", at)
        .setParameter("motivo", reason)
        .executeUpdate();
  }

  @Override
  @Transactional
  public void replaceMemberships(UUID id, Collection<UUID> membershipIds) {
    em.createNativeQuery("DELETE FROM live_session_memberships WHERE live_session_id = :id")
        .setParameter("id", id)
        .executeUpdate();
    for (UUID membresia : membershipIds) {
      em.createNativeQuery(
              "INSERT INTO live_session_memberships (live_session_id, membership_id)"
                  + " VALUES (:id, :membresia)")
          .setParameter("id", id)
          .setParameter("membresia", membresia)
          .executeUpdate();
    }
  }

  @Override
  @Transactional
  public void replaceProducts(UUID id, Collection<UUID> productIds) {
    em.createNativeQuery("DELETE FROM live_session_products WHERE live_session_id = :id")
        .setParameter("id", id)
        .executeUpdate();
    for (UUID producto : productIds) {
      em.createNativeQuery(
              "INSERT INTO live_session_products (live_session_id, product_id)"
                  + " VALUES (:id, :producto)")
          .setParameter("id", id)
          .setParameter("producto", producto)
          .executeUpdate();
    }
  }

  @Override
  @Transactional
  public void saveRegistration(UUID id, UUID userId, String registrantId, String joinUrl) {
    em.createNativeQuery(
            """
            INSERT INTO live_session_registrations
                   (live_session_id, user_id, zoom_registrant_id, join_url)
            VALUES (:id, :persona, :registro, :enlace)
            """)
        .setParameter("id", id)
        .setParameter("persona", userId)
        .setParameter("registro", registrantId)
        .setParameter("enlace", joinUrl)
        .executeUpdate();
  }

  @Override
  @Transactional
  public Optional<LiveSessionRow> findForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT " + COLUMNAS + DESDE + " WHERE s.id = :id FOR UPDATE OF s", Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream().findFirst().map(JpaLiveSessionRepository::clase);
  }

  // ---------------------------------------------------------------------------
  // Lecturas
  // ---------------------------------------------------------------------------

  @Override
  @Transactional(readOnly = true)
  public Optional<LiveSessionRow> find(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery("SELECT " + COLUMNAS + DESDE + " WHERE s.id = :id", Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream().findFirst().map(JpaLiveSessionRepository::clase);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MembershipRef> findMembershipsOf(UUID id) {
    return membresiasDe(List.of(id)).getOrDefault(id, List.of());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProductRef> findProductsOf(UUID id) {
    return serviciosDe(List.of(id)).getOrDefault(id, List.of());
  }

  @Override
  @Transactional(readOnly = true)
  public List<RegistrationRow> findRegistrationsOf(UUID id) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT user_id, zoom_registrant_id, join_url, registered_at
                  FROM live_session_registrations
                 WHERE live_session_id = :id
                 ORDER BY registered_at, user_id
                """,
                Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream().map(JpaLiveSessionRepository::registro).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RegistrationRow> findRegistration(UUID id, UUID userId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT user_id, zoom_registrant_id, join_url, registered_at
                  FROM live_session_registrations
                 WHERE live_session_id = :id AND user_id = :persona
                """,
                Tuple.class)
            .setParameter("id", id)
            .setParameter("persona", userId)
            .getResultList();
    return filas.stream().findFirst().map(JpaLiveSessionRepository::registro);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LiveSessionItemRow> search(LiveSessionFilter filtro, int offset, int size) {
    Map<String, Object> parametros = new LinkedHashMap<>();
    String sql =
        """
        SELECT s.id AS id, s.course_id AS course_id, c.title AS course_title, s.title AS title,
               s.starts_at AS starts_at, s.ends_at AS ends_at, s.status AS status,
               (SELECT count(*) FROM live_session_memberships x
                 WHERE x.live_session_id = s.id) AS membership_count,
               (SELECT count(*) FROM live_session_products x
                 WHERE x.live_session_id = s.id) AS product_count,
               (SELECT count(*) FROM live_session_registrations x
                 WHERE x.live_session_id = s.id) AS registration_count
        """
            + DESDE
            + donde(filtro, parametros)
            + " ORDER BY s.starts_at DESC, s.id OFFSET :desde LIMIT :cuantos";
    parametros.put("desde", offset);
    parametros.put("cuantos", size);
    Query consulta = em.createNativeQuery(sql, Tuple.class);
    parametros.forEach(consulta::setParameter);
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    return filas.stream()
        .map(
            fila ->
                new LiveSessionItemRow(
                    (UUID) fila.get("id"),
                    (UUID) fila.get("course_id"),
                    (String) fila.get("course_title"),
                    (String) fila.get("title"),
                    JpaCourseQueryRepository.momento(fila.get("starts_at")),
                    JpaCourseQueryRepository.momento(fila.get("ends_at")),
                    (String) fila.get("status"),
                    ((Number) fila.get("membership_count")).longValue(),
                    ((Number) fila.get("product_count")).longValue(),
                    ((Number) fila.get("registration_count")).longValue()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long count(LiveSessionFilter filtro) {
    Map<String, Object> parametros = new LinkedHashMap<>();
    Query consulta = em.createNativeQuery("SELECT count(*)" + DESDE + donde(filtro, parametros));
    parametros.forEach(consulta::setParameter);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UpcomingRow> findUpcoming(Instant ahora, UUID courseId) {
    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + COLUMNAS
                + DESDE
                + " WHERE s.status = 'PROGRAMADA' AND s.ends_at > :ahora"
                + (courseId == null ? "" : " AND s.course_id = :curso")
                + " ORDER BY s.starts_at, s.id",
            Tuple.class);
    consulta.setParameter("ahora", ahora.atOffset(ZoneOffset.UTC));
    if (courseId != null) {
      consulta.setParameter("curso", courseId);
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    List<LiveSessionRow> clases = filas.stream().map(JpaLiveSessionRepository::clase).toList();
    if (clases.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = clases.stream().map(LiveSessionRow::id).toList();
    Map<UUID, List<MembershipRef>> membresias = membresiasDe(ids);
    Map<UUID, List<ProductRef>> servicios = serviciosDe(ids);
    return clases.stream()
        .map(
            c ->
                new UpcomingRow(
                    c.id(),
                    c.courseId(),
                    c.courseTitle(),
                    c.title(),
                    c.description(),
                    c.startsAt(),
                    c.endsAt(),
                    membresias.getOrDefault(c.id(), List.of()),
                    servicios.getOrDefault(c.id(), List.of())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> registeredAmong(UUID userId, List<UUID> ids) {
    if (userId == null || ids.isEmpty()) {
      return Set.of();
    }
    @SuppressWarnings("unchecked")
    List<UUID> filas =
        em.createNativeQuery(
                "SELECT live_session_id FROM live_session_registrations"
                    + " WHERE user_id = :persona AND live_session_id IN (:clases)")
            .setParameter("persona", userId)
            .setParameter("clases", ids)
            .getResultList();
    return new HashSet<>(filas);
  }

  // ---------------------------------------------------------------------------

  private Map<UUID, List<MembershipRef>> membresiasDe(Collection<UUID> ids) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT x.live_session_id AS clase, ms.id AS id, ms.code AS code, ms.name AS name,
                       ms.color AS color
                  FROM live_session_memberships x
                  JOIN memberships ms ON ms.id = x.membership_id
                 WHERE x.live_session_id IN (:clases)
                 ORDER BY ms.level, ms.id
                """,
                Tuple.class)
            .setParameter("clases", ids)
            .getResultList();
    return filas.stream()
        .collect(
            Collectors.groupingBy(
                f -> (UUID) f.get("clase"),
                LinkedHashMap::new,
                Collectors.mapping(
                    f ->
                        new MembershipRef(
                            (UUID) f.get("id"),
                            (String) f.get("code"),
                            (String) f.get("name"),
                            (String) f.get("color")),
                    Collectors.toList())));
  }

  private Map<UUID, List<ProductRef>> serviciosDe(Collection<UUID> ids) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT x.live_session_id AS clase, p.id AS id, p.code AS code, p.name AS name
                  FROM live_session_products x
                  JOIN products p ON p.id = x.product_id
                 WHERE x.live_session_id IN (:clases)
                 ORDER BY p.code, p.id
                """,
                Tuple.class)
            .setParameter("clases", ids)
            .getResultList();
    return filas.stream()
        .collect(
            Collectors.groupingBy(
                f -> (UUID) f.get("clase"),
                LinkedHashMap::new,
                Collectors.mapping(
                    f ->
                        new ProductRef(
                            (UUID) f.get("id"), (String) f.get("code"), (String) f.get("name")),
                    Collectors.toList())));
  }

  private static String donde(LiveSessionFilter filtro, Map<String, Object> parametros) {
    List<String> partes = new ArrayList<>();
    if (filtro.instructorId() != null) {
      partes.add("c.instructor_id = :instructor");
      parametros.put("instructor", filtro.instructorId());
    }
    if (filtro.courseId() != null) {
      partes.add("s.course_id = :curso");
      parametros.put("curso", filtro.courseId());
    }
    if (filtro.status() != null) {
      partes.add("s.status = :estado");
      parametros.put("estado", filtro.status());
    }
    if (filtro.ended() != null) {
      partes.add(filtro.ended() ? "s.ends_at <= :ahora" : "s.ends_at > :ahora");
      parametros.put("ahora", filtro.ahora().atOffset(ZoneOffset.UTC));
    }
    if (filtro.from() != null) {
      partes.add("s.starts_at >= :desdeFecha");
      parametros.put("desdeFecha", filtro.from());
    }
    if (filtro.to() != null) {
      partes.add("s.starts_at < :hastaFecha");
      parametros.put("hastaFecha", filtro.to());
    }
    return partes.isEmpty() ? "" : " WHERE " + String.join(" AND ", partes);
  }

  /**
   * Un {@code uuid} que puede ser nulo, enlazado con su tipo: sin él, PostgreSQL recibe un nulo sin
   * tipo y no sabe convertirlo (la trampa de `RF-MV-008`).
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void uuid(Query consulta, String nombre, UUID valor) {
    ((org.hibernate.query.NativeQuery) consulta.unwrap(org.hibernate.query.NativeQuery.class))
        .setParameter(nombre, valor, UUID.class);
  }

  private static LiveSessionRow clase(Tuple fila) {
    return new LiveSessionRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("course_id"),
        (String) fila.get("course_title"),
        (UUID) fila.get("course_instructor_id"),
        (String) fila.get("title"),
        (String) fila.get("description"),
        JpaCourseQueryRepository.momento(fila.get("starts_at")),
        JpaCourseQueryRepository.momento(fila.get("ends_at")),
        ((Number) fila.get("zoom_meeting_id")).longValue(),
        (String) fila.get("status"),
        JpaCourseQueryRepository.momento(fila.get("cancelled_at")),
        (String) fila.get("cancellation_reason"),
        (UUID) fila.get("created_by"),
        JpaCourseQueryRepository.momento(fila.get("created_at")),
        JpaCourseQueryRepository.momento(fila.get("updated_at")));
  }

  private static RegistrationRow registro(Tuple fila) {
    return new RegistrationRow(
        (UUID) fila.get("user_id"),
        (String) fila.get("zoom_registrant_id"),
        (String) fila.get("join_url"),
        JpaCourseQueryRepository.momento(fila.get("registered_at")));
  }
}
