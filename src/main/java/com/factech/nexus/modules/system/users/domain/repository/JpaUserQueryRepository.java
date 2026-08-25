package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.ListUsersRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de lectura del listado y del detalle de personas.
 *
 * <p><b>El predicado se genera en un solo sitio</b> ({@link #predicado}) y lo usan tanto la página
 * como el conteo. Escritos por separado, divergen —y la divergencia se manifiesta como un total que
 * no coincide con lo que se ve, que es de los defectos más difíciles de creer cuando se reporta.
 */
@Repository
public class JpaUserQueryRepository implements UserQueryRepository {

  private final EntityManager em;

  public JpaUserQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserRow> search(
      ListUsersRequest filtros, String ordenamiento, int offset, int limit) {

    String sql =
        """
        SELECT u.id AS id, u.username AS username, u.email AS email,
               u.first_name AS first_name, u.last_name AS last_name,
               u.status AS status, u.deleted_at AS deleted_at,
               m.id AS m_id, m.code AS m_code, m.name AS m_name, m.level AS m_level,
               um.ends_at AS m_ends_at,
               (um.user_id IS NOT NULL AND (um.ends_at IS NULL OR um.ends_at > now())) AS m_current
          FROM users u
          LEFT JOIN user_memberships um ON um.user_id = u.id
          LEFT JOIN memberships m       ON m.id = um.membership_id
         WHERE """
            // El espacio va aquí y no al final del bloque de texto: Java recorta
            // el espacio final de cada línea, y sin él la sentencia dice `WHEREu`.
            + " "
            + predicado(filtros)
            + " ORDER BY "
            + ordenamiento
            + " OFFSET :salto LIMIT :tope";

    Query consulta = em.createNativeQuery(sql, Tuple.class);
    enlazar(consulta, filtros);
    consulta.setParameter("salto", offset).setParameter("tope", limit);

    List<Tuple> filas = consulta.getResultList();
    List<UserRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new UserRow(
              (UUID) fila.get("id"),
              (String) fila.get("username"),
              (String) fila.get("email"),
              (String) fila.get("first_name"),
              (String) fila.get("last_name"),
              (String) fila.get("status"),
              momento(fila.get("deleted_at")),
              null,
              null,
              null,
              null,
              (UUID) fila.get("m_id"),
              (String) fila.get("m_code"),
              (String) fila.get("m_name"),
              nivel(fila.get("m_level")),
              momento(fila.get("m_ends_at")),
              (Boolean) fila.get("m_current")));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long count(ListUsersRequest filtros) {
    // Sin los LEFT JOIN de la membresía: no participan en el predicado y unirlos
    // solo para contar es trabajo que no cambia el número.
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM users u WHERE " + predicado(filtros));
    enlazar(consulta, filtros);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<RoleRow>> rolesOf(List<UUID> userIds) {
    if (userIds.isEmpty()) {
      // Un `IN` con lista vacía es sintácticamente incómodo y semánticamente
      // inútil: la página vacía no tiene roles que traer.
      return Map.of();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT ur.user_id AS user_id, r.id AS id, r.code AS code,
                       r.name AS name, r.status AS status
                  FROM user_roles ur
                  JOIN roles r ON r.id = ur.role_id AND r.deleted_at IS NULL
                 WHERE ur.user_id IN (:ids)
                 ORDER BY ur.user_id, r.code
                """,
                Tuple.class)
            .setParameter("ids", userIds)
            .getResultList();

    Map<UUID, List<RoleRow>> agrupados = new LinkedHashMap<>();
    for (Tuple fila : filas) {
      agrupados
          .computeIfAbsent((UUID) fila.get("user_id"), clave -> new ArrayList<>())
          .add(
              new RoleRow(
                  (UUID) fila.get("id"),
                  (String) fila.get("code"),
                  (String) fila.get("name"),
                  (String) fila.get("status")));
    }
    return agrupados;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserRow> findDetail(UUID id) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT u.id AS id, u.username AS username, u.email AS email,
                       u.first_name AS first_name, u.last_name AS last_name,
                       u.status AS status,
                       u.last_login_at AS last_login_at, u.locked_until AS locked_until,
                       u.created_at AS created_at, u.updated_at AS updated_at,
                       m.id AS m_id, m.code AS m_code, m.name AS m_name, m.level AS m_level,
                       um.ends_at AS m_ends_at,
                       (um.user_id IS NOT NULL AND (um.ends_at IS NULL OR um.ends_at > now()))
                         AS m_current
                  FROM users u
                  LEFT JOIN user_memberships um ON um.user_id = u.id
                  LEFT JOIN memberships m       ON m.id = um.membership_id
                 WHERE u.id = :id AND u.deleted_at IS NULL
                """,
                Tuple.class)
            .setParameter("id", id)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new UserRow(
                    (UUID) fila.get("id"),
                    (String) fila.get("username"),
                    (String) fila.get("email"),
                    (String) fila.get("first_name"),
                    (String) fila.get("last_name"),
                    (String) fila.get("status"),
                    null,
                    momento(fila.get("last_login_at")),
                    momento(fila.get("locked_until")),
                    momento(fila.get("created_at")),
                    momento(fila.get("updated_at")),
                    (UUID) fila.get("m_id"),
                    (String) fila.get("m_code"),
                    (String) fila.get("m_name"),
                    nivel(fila.get("m_level")),
                    momento(fila.get("m_ends_at")),
                    (Boolean) fila.get("m_current")))
        .findFirst();
  }

  // ---------------------------------------------------------------------------
  // El predicado, en un solo sitio
  // ---------------------------------------------------------------------------

  /**
   * <b>Por qué {@code EXISTS} y no {@code JOIN}</b> en los dos filtros que no son del propio
   * usuario: un {@code JOIN} a {@code user_roles} multiplica la fila de la persona por cada
   * asignación que cumpla el predicado. Con un solo rol el resultado <b>parece</b> correcto, pero
   * el conteo se calcula sobre el mismo predicado y <b>contaría asignaciones en lugar de
   * personas</b> en cuanto alguien añadiera un segundo valor al filtro. {@code EXISTS} corta en la
   * primera coincidencia y no puede duplicar.
   *
   * <p>La membresía tiene clave primaria por persona y no podría multiplicar, pero se escribe igual
   * por simetría y para que el predicado de vigencia quede en un solo sitio.
   */
  private static String predicado(ListUsersRequest filtros) {
    StringBuilder donde = new StringBuilder();
    donde.append(filtros.incluirEliminados() ? "1 = 1" : "u.deleted_at IS NULL");

    if (filtros.status() != null) {
      donde.append(" AND u.status = :estado");
    }
    if (filtros.roleId() != null) {
      donde.append(
          " AND EXISTS (SELECT 1 FROM user_roles ur"
              + " WHERE ur.user_id = u.id AND ur.role_id = :rol)");
    }
    if (filtros.membershipId() != null) {
      donde.append(
          " AND EXISTS (SELECT 1 FROM user_memberships umf"
              + " WHERE umf.user_id = u.id AND umf.membership_id = :membresia"
              + " AND (umf.ends_at IS NULL OR umf.ends_at > now()))");
    }
    if (filtros.search() != null) {
      // La normalización la hace LA BASE DE DATOS, con la misma función que
      // alimenta el índice: normalizar en Java produce un resultado parecido y
      // no idéntico, y cualquier divergencia se manifiesta como una persona
      // indexada que no aparece en su propia búsqueda.
      donde.append(
          " AND (f_unaccent(lower(u.username)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'"
              + " OR f_unaccent(lower(u.email)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'"
              + " OR f_unaccent(lower(u.first_name || ' ' || u.last_name))"
              + " LIKE f_unaccent(lower(:termino)) ESCAPE '\\')");
    }
    return donde.toString();
  }

  private static void enlazar(Query consulta, ListUsersRequest filtros) {
    if (filtros.status() != null) {
      consulta.setParameter("estado", filtros.status());
    }
    if (filtros.roleId() != null) {
      consulta.setParameter("rol", filtros.roleId());
    }
    if (filtros.membershipId() != null) {
      consulta.setParameter("membresia", filtros.membershipId());
    }
    if (filtros.search() != null) {
      consulta.setParameter("termino", "%" + escapar(filtros.search()) + "%");
    }
  }

  /**
   * Escapa lo que {@code LIKE} interpreta.
   *
   * <p>Sin esto, buscar {@code %} devuelve la lista entera y buscar {@code _} devuelve todo lo que
   * tenga un carácter en esa posición: el término del cliente dejaría de ser un texto para pasar a
   * ser un patrón, y quien busque una dirección con guion bajo no encontraría la suya.
   */
  private static String escapar(String termino) {
    return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Short nivel(Object valor) {
    return valor == null ? null : ((Number) valor).shortValue();
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
