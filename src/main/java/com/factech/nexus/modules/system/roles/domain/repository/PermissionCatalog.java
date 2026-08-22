package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de resolución del catálogo de permisos (`RF-SP-001` · `T-14`).
 *
 * <p>Devuelve <b>los que existen</b>, sin fallar por los que no: quien llama compara el tamaño del
 * conjunto pedido con el devuelto y construye `EX-005` enumerando <b>todos</b> los ausentes. Si
 * este puerto lanzara al primer ausente, la respuesta solo podría nombrar uno.
 *
 * <p>Devuelve {@link PermissionItem}, que es el modelo de lectura de `RF-SP-010`. Es reutilización
 * deliberada y no un atajo: aquel plan lo amplió a seis campos declarando que tres de ellos existen
 * para el detalle de un rol, de modo que el concepto de permiso tiene un solo tipo en toda la API.
 */
public interface PermissionCatalog {

  /**
   * Resuelve identificadores contra el catálogo.
   *
   * @param ids identificadores pedidos, ya sin duplicados
   * @return los permisos encontrados, en orden de código; nunca nulo, posiblemente más corto que la
   *     entrada
   */
  List<PermissionItem> findAllById(Set<UUID> ids);
}
