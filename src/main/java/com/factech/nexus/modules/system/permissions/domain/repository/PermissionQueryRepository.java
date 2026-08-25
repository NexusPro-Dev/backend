package com.factech.nexus.modules.system.permissions.domain.repository;

import com.factech.nexus.modules.system.permissions.application.ListPermissionsQuery;
import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

  /**
   * Un permiso del catálogo por su identificador (`RF-SP-015`).
   *
   * <p><b>Solo por identificador y nunca por código</b> (`spec.md` §14, pregunta 2): admitir dos
   * formas de direccionar el mismo recurso obliga a distinguir en cada petición cuál de las dos
   * llegó, y a decidir qué ocurre cuando un código parece un identificador. El código sigue siendo
   * la vía legible para <b>encontrarlo</b>, y de eso se encarga el filtro del catálogo.
   *
   * <p>Devuelve <b>la misma proyección</b> que el listado: `spec.md` §6.2 pide exactamente los seis
   * campos de {@link PermissionItem}, de modo que un tipo propio para el detalle sería una segunda
   * representación del mismo concepto sin un solo campo de diferencia.
   *
   * @return vacío si el identificador no corresponde a ningún permiso. El catálogo <b>no tiene
   *     borrado lógico</b>: cambia solo por migración (`RN-SP-004`), de modo que aquí no hay
   *     eliminados que excluir
   */
  Optional<PermissionItem> findById(UUID id);
}
