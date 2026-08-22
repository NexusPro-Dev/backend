package com.factech.nexus.modules.system.permissions.domain.repository;

import com.factech.nexus.modules.system.permissions.application.ListPermissionsQuery;
import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import java.util.List;

/**
 * Puerto de consulta del catálogo de permisos (`RF-SP-010` · `T-05`).
 *
 * <p><b>No se reutiliza {@code PermissionCatalog}</b>, el puerto de `RF-SP-001`. Aquel existe para
 * resolver un conjunto de identificadores a permisos y comprobar que existen: su firma es de
 * resolución, no de listado, y ampliarlo con un método de consulta filtrada mezclaría dos
 * responsabilidades en un puerto que el dominio usa para decidir. Es el mismo criterio con el que
 * `RF-SP-002` separó {@code RoleQueryRepository} de {@code RoleRepository}: lo que devuelve un
 * modelo de lectura no comparte puerto con lo que devuelve el agregado.
 */
public interface PermissionQueryRepository {

  /**
   * Devuelve el catálogo **completo** que satisface los criterios, sin paginar.
   *
   * <p>Ordenado por recurso y acción, siempre y sin que el cliente pueda cambiarlo: sin {@code
   * ORDER BY} explícito PostgreSQL no garantiza orden alguno, y un catálogo que cambia de orden
   * entre dos llamadas hace inútil compararlo.
   *
   * @return la lista, posiblemente vacía. Nunca {@code null}
   */
  List<PermissionItem> find(ListPermissionsQuery query);
}
