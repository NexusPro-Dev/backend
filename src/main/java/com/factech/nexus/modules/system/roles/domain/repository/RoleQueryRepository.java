package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.modules.system.roles.application.ListRolesRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecturas del listado y del detalle de roles (`RF-SP-002`, `RF-SP-003`).
 *
 * <p>Puerto <b>separado</b> de {@link RoleRepository} y no un método más de aquel: {@code
 * RoleRepository} devuelve el agregado {@code Role} y lo protege, mientras que esto devuelve una
 * proyección a la que le faltan los permisos y le sobra la forma del padre. Mezclarlos invitaría a
 * cargar la entidad para responder una consulta, que es el camino al {@code N+1} que este
 * requerimiento existe para evitar.
 */
public interface RoleQueryRepository {

  /**
   * Una página de roles, con su rol padre resuelto en la misma sentencia.
   *
   * @param ordenamiento cláusula ya resuelta contra la lista blanca — nunca texto del cliente
   */
  List<RoleRow> search(ListRolesRequest filtros, String ordenamiento, int offset, int limit);

  /** El conteo, con <b>el mismo predicado</b> que la página: se generan desde el mismo sitio. */
  long count(ListRolesRequest filtros);

  /**
   * El rol, su padre y los dos conteos, en <b>una sola</b> sentencia (`RF-SP-003`).
   *
   * <p>Vacío si no existe o está eliminado lógicamente: para este requerimiento son el mismo hecho,
   * y reconstruir qué era corresponde a la auditoría de eliminación (Art. V.13).
   */
  Optional<RoleDetailRow> findDetail(UUID id);

  /**
   * Los permisos que el rol <b>declara</b>, ordenados por código (`RF-SP-003`, `RN-SEG-004`).
   *
   * <p><b>Sin recorrer la cadena de ancestros</b>: el modelo no usa herencia y cada rol declara sus
   * permisos de forma explícita, que es la decisión que permite responder esta pregunta leyendo una
   * sola lista.
   *
   * <p>Va en sentencia aparte y no unida a la anterior: dos colecciones en la misma consulta
   * producen su producto cartesiano —diez permisos y tres hijos dan treinta filas— y obligan a
   * deduplicar en memoria un resultado que ya se leyó multiplicado.
   */
  List<PermissionItem> findDeclaredPermissions(UUID roleId);

  /**
   * Proyección de un rol para el listado.
   *
   * <p>Los tres campos del padre van planos y no anidados: es lo que devuelve la sentencia, y darle
   * forma de objeto es competencia de la capa que arma la respuesta.
   */
  record RoleRow(
      UUID id,
      String code,
      String name,
      String description,
      String roleType,
      String status,
      boolean isSystem,
      UUID parentId,
      String parentCode,
      String parentName,
      OffsetDateTime deletedAt) {

    public boolean tienePadre() {
      return parentId != null;
    }
  }

  /**
   * Proyección del detalle de un rol.
   *
   * <p><b>{@code childRoleCount} es un número y nunca una lista</b> (`CA-SP-150`): el listado de
   * hijos se obtiene con {@code GET /api/v1/roles?parentRoleId={id}}, que ya existe y ya está
   * paginado. Así el tamaño de la respuesta no depende de cuántos hijos tenga el rol.
   *
   * <p><b>{@code assignedUserCount} cuenta personas distintas y no asignaciones</b>, en cualquier
   * estado —activo, inactivo, bloqueado— y <b>excluyendo las eliminadas</b>. La distinción sostiene
   * a `RN-SEG-008`: alguien bloqueado sigue portando el rol y su existencia debe impedir el
   * borrado; alguien eliminado no existe. Cero es un dato, no una ausencia.
   */
  record RoleDetailRow(
      UUID id,
      String code,
      String name,
      String description,
      String roleType,
      String status,
      boolean isSystem,
      UUID parentId,
      String parentCode,
      String parentName,
      long childRoleCount,
      long assignedUserCount,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {

    public boolean tienePadre() {
      return parentId != null;
    }
  }
}
