package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.roles.application.ListRolesRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de lectura del listado de roles.
 *
 * <p><b>El predicado se genera en un solo sitio</b> ({@link #predicado}) y lo usan tanto la página
 * como el conteo. Escritos por separado divergen, y la divergencia se manifiesta como un total que
 * no corresponde a lo devuelto: un defecto que ninguna prueba de la página detecta.
 *
 * <p><b>Cada filtro presente añade su condición y los ausentes no añaden nada.</b> Se descarta el
 * patrón {@code (:estado IS NULL OR r.status = :estado)}, más corto de escribir: produce una única
 * sentencia para todas las combinaciones de filtros, y el planificador debe elegir un plan válido
 * para todas en lugar de aprovechar los índices que sirven a la combinación concreta.
 *
 * <p><b>Proyección, no entidades.</b> La sentencia selecciona exactamente las columnas que la
 * respuesta usa más las tres del padre, resuelto con {@code LEFT JOIN} en la misma pasada. Eso
 * elimina por construcción las dos consultas que sobran: el {@code N+1} sobre el rol padre —una por
 * fila si se navegara desde la entidad— y cualquier lectura de {@code role_permissions}, que con
 * entidades es una colección perezosa que basta rozar para disparar.
 */
@Repository
public class JpaRoleQueryRepository implements RoleQueryRepository {

  private final EntityManager em;

  public JpaRoleQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RoleRow> search(
      ListRolesRequest filtros, String ordenamiento, int offset, int limit) {

    String sql =
        """
        SELECT r.id AS id, r.code AS code, r.name AS name,
               r.description AS description, r.role_type AS role_type,
               r.status AS status, r.is_system AS is_system,
               r.deleted_at AS deleted_at,
               p.id AS p_id, p.code AS p_code, p.name AS p_name
          FROM roles r
          LEFT JOIN roles p ON p.id = r.parent_role_id
         WHERE """
            // El espacio va aquí y no al final del bloque de texto: Java recorta
            // el espacio final de cada línea, y sin él la sentencia dice `WHEREr`.
            + " "
            + predicado(filtros)
            + " ORDER BY "
            + ordenamiento
            + " OFFSET :salto LIMIT :tope";

    Query consulta = em.createNativeQuery(sql, Tuple.class);
    enlazar(consulta, filtros);
    consulta.setParameter("salto", offset).setParameter("tope", limit);

    List<Tuple> filas = consulta.getResultList();
    List<RoleRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new RoleRow(
              (java.util.UUID) fila.get("id"),
              (String) fila.get("code"),
              (String) fila.get("name"),
              (String) fila.get("description"),
              (String) fila.get("role_type"),
              (String) fila.get("status"),
              Boolean.TRUE.equals(fila.get("is_system")),
              (java.util.UUID) fila.get("p_id"),
              (String) fila.get("p_code"),
              (String) fila.get("p_name"),
              momento(fila.get("deleted_at"))));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long count(ListRolesRequest filtros) {
    // Sin el LEFT JOIN al padre: no participa en el predicado —la clave foránea
    // apunta a lo sumo a un rol, de modo que no cambia el número de filas— y
    // unirlo solo para contar es trabajo que no aporta nada.
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM roles r WHERE " + predicado(filtros));
    enlazar(consulta, filtros);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.Optional<RoleDetailRow> findDetail(java.util.UUID id) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT r.id AS id, r.code AS code, r.name AS name,
                       r.description AS description, r.role_type AS role_type,
                       r.status AS status, r.is_system AS is_system,
                       r.created_at AS created_at, r.updated_at AS updated_at,
                       p.id AS p_id, p.code AS p_code, p.name AS p_name,
                       (SELECT count(*) FROM roles h
                         WHERE h.parent_role_id = r.id AND h.deleted_at IS NULL) AS hijos,
                       (SELECT count(DISTINCT ur.user_id)
                          FROM user_roles ur
                          JOIN users u ON u.id = ur.user_id AND u.deleted_at IS NULL
                         WHERE ur.role_id = r.id) AS asignados
                  FROM roles r
                  LEFT JOIN roles p ON p.id = r.parent_role_id AND p.deleted_at IS NULL
                 WHERE r.id = :id AND r.deleted_at IS NULL
                """,
                Tuple.class)
            .setParameter("id", id)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new RoleDetailRow(
                    (java.util.UUID) fila.get("id"),
                    (String) fila.get("code"),
                    (String) fila.get("name"),
                    (String) fila.get("description"),
                    (String) fila.get("role_type"),
                    (String) fila.get("status"),
                    Boolean.TRUE.equals(fila.get("is_system")),
                    (java.util.UUID) fila.get("p_id"),
                    (String) fila.get("p_code"),
                    (String) fila.get("p_name"),
                    ((Number) fila.get("hijos")).longValue(),
                    ((Number) fila.get("asignados")).longValue(),
                    momento(fila.get("created_at")),
                    momento(fila.get("updated_at"))))
        .findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<com.factech.nexus.modules.system.permissions.application.PermissionItem>
      findDeclaredPermissions(java.util.UUID roleId) {

    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT pe.id AS id, pe.code AS code, pe.resource AS resource,
                       pe.action AS action, pe.name AS name, pe.description AS description
                  FROM role_permissions rp
                  JOIN permissions pe ON pe.id = rp.permission_id
                 WHERE rp.role_id = :id
                 ORDER BY pe.code
                """,
                Tuple.class)
            .setParameter("id", roleId)
            .getResultList();

    // El orden por código NO es decorativo: sin `ORDER BY` explícito PostgreSQL
    // no garantiza orden alguno, y una lista que cambia entre dos llamadas hace
    // inútil cualquier comparación entre roles.
    return filas.stream()
        .map(
            fila ->
                new com.factech.nexus.modules.system.permissions.application.PermissionItem(
                    (java.util.UUID) fila.get("id"),
                    (String) fila.get("code"),
                    (String) fila.get("resource"),
                    (String) fila.get("action"),
                    (String) fila.get("name"),
                    (String) fila.get("description")))
        .toList();
  }

  // ---------------------------------------------------------------------------
  // El predicado, en un solo sitio
  // ---------------------------------------------------------------------------

  private static String predicado(ListRolesRequest filtros) {
    StringBuilder donde = new StringBuilder();
    donde.append(filtros.incluirEliminados() ? "1 = 1" : "r.deleted_at IS NULL");

    if (filtros.status() != null) {
      donde.append(" AND r.status = :estado");
    }
    if (filtros.roleType() != null) {
      donde.append(" AND r.role_type = :clasificacion");
    }
    if (filtros.parentRoleId() != null) {
      // El padre DIRECTO y no el subárbol: `spec.md` §6.1 pide filtrar por rol
      // padre, y añadir un recorrido recursivo cambiaría el significado del
      // filtro sin que nadie lo haya pedido.
      donde.append(" AND r.parent_role_id = :padre");
    }
    if (filtros.search() != null) {
      // La normalización la hace LA BASE DE DATOS, con la misma función que
      // alimenta `ix_roles_busqueda`: normalizar en Java produce un resultado
      // parecido y no idéntico al del diccionario `unaccent`, y cualquier
      // divergencia se manifiesta como un rol que existe, está indexado y no
      // aparece en su propia búsqueda.
      donde.append(
          " AND (f_unaccent(lower(r.code)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'"
              + " OR f_unaccent(lower(r.name)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\')");
    }
    return donde.toString();
  }

  private static void enlazar(Query consulta, ListRolesRequest filtros) {
    if (filtros.status() != null) {
      consulta.setParameter("estado", filtros.status());
    }
    if (filtros.roleType() != null) {
      consulta.setParameter("clasificacion", filtros.roleType());
    }
    if (filtros.parentRoleId() != null) {
      consulta.setParameter("padre", filtros.parentRoleId());
    }
    if (filtros.search() != null) {
      consulta.setParameter("termino", "%" + escapar(filtros.search()) + "%");
    }
  }

  /**
   * Escapa lo que {@code LIKE} interpreta.
   *
   * <p>El valor va enlazado, de modo que esto no es defensa contra inyección: es que sin escapar,
   * buscar {@code 100%} devuelve el catálogo entero y buscar {@code _} coincide con cualquier
   * carácter. El término del cliente dejaría de ser un texto para pasar a ser un patrón (`spec.md`
   * §13).
   */
  private static String escapar(String termino) {
    return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
