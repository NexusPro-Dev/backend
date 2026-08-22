package com.factech.nexus.modules.system.permissions.domain.service;

import com.factech.nexus.modules.system.permissions.application.ListPermissionsQuery;
import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.modules.system.permissions.domain.repository.PermissionQueryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta del catálogo de permisos (`RF-SP-010` · `T-07`).
 *
 * <p>El caso de uso es delgado a propósito: traduce la consulta al puerto y devuelve la colección.
 * No hay regla de negocio que aplicar —`RN-SP-004` se cumple por ausencia de endpoint de escritura,
 * no por código— ni validación que ejecutar, porque `spec.md` §11 no declara ninguna. Existe de
 * todos modos, y no se llama al adaptador desde el controlador, porque es donde vive la frontera
 * transaccional y porque `RF-SP-015` añadirá aquí el detalle.
 */
@Service
public class ListPermissionsService {

  private final PermissionQueryRepository permissions;

  public ListPermissionsService(PermissionQueryRepository permissions) {
    this.permissions = permissions;
  }

  /**
   * Devuelve el catálogo completo que satisface los criterios.
   *
   * <p>{@code readOnly = true} no es decoración: marca la transacción como de solo lectura en el
   * driver, de modo que un intento de escritura desde este camino falla en lugar de pasar
   * inadvertido.
   */
  @Transactional(readOnly = true)
  public List<PermissionItem> list(ListPermissionsQuery query) {
    return permissions.find(query);
  }
}
