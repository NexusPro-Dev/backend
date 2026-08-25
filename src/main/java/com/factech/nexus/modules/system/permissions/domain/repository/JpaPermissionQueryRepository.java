package com.factech.nexus.modules.system.permissions.domain.repository;

import com.factech.nexus.modules.system.permissions.application.ListPermissionsQuery;
import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.modules.system.permissions.domain.models.Permission;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de consulta del catálogo de permisos (`RF-SP-010` · `T-06`).
 *
 * <p>Una sola sentencia, con y sin filtros: seis columnas de una tabla, materializadas con {@code
 * cb.construct} sobre {@code PermissionItem}. No hay conteo —el catálogo no se pagina—, no hay
 * {@code JOIN} y no hay colección perezosa. En particular <b>no toca {@code role_permissions}</b>:
 * `plan.md` §4 decide no devolver cuántos roles declaran cada permiso, y no tener la subconsulta es
 * lo único que lo hace verificable.
 */
@Repository
public class JpaPermissionQueryRepository implements PermissionQueryRepository {

  private static final String SEARCH_PARAMETER = "searchTerm";

  /**
   * Carácter de escape del {@code LIKE}. Se declara explícito y no se confía en el de por omisión:
   * en PostgreSQL coincide con este, pero dejarlo implícito hace que el escape de abajo dependa de
   * una configuración que nadie declara.
   */
  private static final char ESCAPE = '\\';

  private final EntityManager entityManager;

  public JpaPermissionQueryRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<PermissionItem> find(ListPermissionsQuery query) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<PermissionItem> criteria = cb.createQuery(PermissionItem.class);
    Root<Permission> permission = criteria.from(Permission.class);

    criteria.select(
        cb.construct(
            PermissionItem.class,
            permission.get("id"),
            permission.get("code"),
            permission.get("resource"),
            permission.get("action"),
            permission.get("name"),
            permission.get("description")));

    List<Predicate> filters = new ArrayList<>();

    // Igualdad, no contención: resource y action son valores de un dominio
    // conocido que el cliente obtiene del propio catálogo. Un filtro por
    // contención haría que «role» devolviera también los de «roles», y el
    // cliente no tendría forma de pedir solo uno de los dos. Buscar por
    // fragmento es lo que hace `search`.
    query.resource().ifPresent(value -> filters.add(cb.equal(permission.get("resource"), value)));
    query.action().ifPresent(value -> filters.add(cb.equal(permission.get("action"), value)));

    ParameterExpression<String> searchParameter = cb.parameter(String.class, SEARCH_PARAMETER);
    query.searchTerm().ifPresent(term -> filters.add(search(cb, permission, searchParameter)));

    if (!filters.isEmpty()) {
      criteria.where(cb.and(filters.toArray(Predicate[]::new)));
    }

    // Orden fijo, que el cliente no puede cambiar: sin ORDER BY explícito
    // PostgreSQL no garantiza orden alguno, y un catálogo que cambia de orden
    // entre dos llamadas hace inútil compararlo.
    criteria.orderBy(cb.asc(permission.get("resource")), cb.asc(permission.get("action")));

    TypedQuery<PermissionItem> typed = entityManager.createQuery(criteria);
    query.searchTerm().ifPresent(term -> typed.setParameter(SEARCH_PARAMETER, toPattern(term)));

    return typed.getResultList();
  }

  @Override
  public java.util.Optional<PermissionItem> findById(java.util.UUID id) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<PermissionItem> criteria = cb.createQuery(PermissionItem.class);
    Root<Permission> permission = criteria.from(Permission.class);

    // La MISMA proyección que el listado, y no la entidad: el detalle devuelve
    // los mismos seis campos, y leer el agregado para responder una consulta es
    // lo que abre la puerta a que alguien recorra una asociación desde el
    // mapeador. En particular, aquí NO se toca `role_permissions`: `spec.md`
    // §4.2 excluye los roles que declaran el permiso, y no tener la subconsulta
    // es lo único que lo hace verificable.
    criteria.select(
        cb.construct(
            PermissionItem.class,
            permission.get("id"),
            permission.get("code"),
            permission.get("resource"),
            permission.get("action"),
            permission.get("name"),
            permission.get("description")));
    criteria.where(cb.equal(permission.get("id"), id));

    return entityManager.createQuery(criteria).getResultList().stream().findFirst();
  }

  /**
   * Búsqueda insensible a mayúsculas y a acentos sobre el código y la descripción.
   *
   * <p>La normalización la hace <b>la base de datos, con la misma función</b> que usaría cualquier
   * índice futuro, y no Java: {@code java.text.Normalizer} produce un resultado parecido y no
   * idéntico al del diccionario {@code unaccent}, y esa diferencia aparecería como resultados que
   * el índice no encuentra.
   *
   * <p>{@code description} es nulable y por eso va envuelta en {@code coalesce}. Sin él, un permiso
   * sin descripción no aparecería jamás en una búsqueda —ni siquiera buscando su propio código—,
   * porque {@code NULL LIKE …} es {@code NULL} y esa rama del {@code OR} nunca sería verdadera.
   */
  private Predicate search(
      CriteriaBuilder cb, Root<Permission> permission, ParameterExpression<String> term) {

    Expression<String> normalizedTerm = unaccentLower(cb, term);

    return cb.or(
        cb.like(unaccentLower(cb, permission.get("code")), normalizedTerm, ESCAPE),
        cb.like(
            unaccentLower(cb, cb.coalesce(permission.get("description"), "")),
            normalizedTerm,
            ESCAPE));
  }

  private Expression<String> unaccentLower(CriteriaBuilder cb, Expression<String> value) {
    return cb.function("f_unaccent", String.class, cb.lower(value));
  }

  /**
   * Convierte el término en un patrón de contención, escapando antes los comodines.
   *
   * <p>El escape es lo que impide que un {@code %} escrito por el usuario convierta la búsqueda en
   * «devuélvemelo todo». La barra invertida se escapa <b>primero</b>: hacerlo después volvería a
   * escapar las barras que este mismo método acaba de introducir.
   *
   * <p>El valor viaja siempre como parámetro enlazado, nunca concatenado en la sentencia.
   */
  private static String toPattern(String term) {
    String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
