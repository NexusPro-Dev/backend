package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.ProductPackage;
import java.util.Optional;
import java.util.UUID;

/**
 * Escritura y lectura bloqueante del paquete (`RF-PM-017`).
 *
 * <p>La misma forma que {@link ProductRepository}: unicidad comprobada antes y <b>traducida</b>
 * después —la restricción total del código y el índice parcial del nombre muerden en el {@code
 * INSERT} y salen como el mismo {@code 409}—, y dos lecturas con bloqueo: la del vivo, para todo lo
 * que edita, y la de cualquiera, para el retiro, que necesita distinguir «no existe» de «ya está
 * retirado».
 */
public interface ProductPackageRepository {

  /** ¿Hay un paquete —vivo o retirado— con ese código? El código no se libera (`RN-PM-041`). */
  boolean existsCode(String code);

  /** ¿Hay un paquete vivo con ese nombre, sin distinguir acentos ni mayúsculas? */
  boolean existsAliveName(String name);

  /** Lo mismo, excluyendo al propio paquete: es lo que pregunta la corrección (`RF-PM-020`). */
  boolean existsAliveNameForOther(String name, UUID packageId);

  ProductPackage save(ProductPackage paquete);

  Optional<ProductPackage> findAliveByIdForUpdate(UUID id);

  Optional<ProductPackage> findByIdForUpdate(UUID id);

  /**
   * Vacía los cambios pendientes traduciendo las unicidades, para quien escribe sin {@link #save}.
   */
  void flush();
}
