package com.factech.nexus.modules.products.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que `PM` publica de sus productos para que otro módulo pueda consultarlos (**D-25**).
 *
 * <p><b>Es la primera interfaz que `PM` publica.</b> Hasta el 28-08-2026 no publicaba ninguna, y no
 * por descuido: nadie lo consumía. Nace con `RF-CM-001` · `T-06`, y la escribe quien la necesita —
 * es el reparto que se decidió al cerrar D-25, y por el mismo motivo: ningún actor pide «publicar
 * una interfaz» como comportamiento.
 *
 * <p><b>{@code retired} es el motivo de que esta interfaz exista en esta forma.</b> `CM` necesita
 * distinguir un producto retirado de uno inexistente, porque son dos rechazos distintos: sobre el
 * retirado no se declaran tarifas nuevas (`RN-CM-010`) y quien escribió bien el identificador no
 * debe buscar el error donde no está. Devolver vacío para el retirado colapsaría los dos casos.
 *
 * <p><b>No viaja el precio ni la moneda.</b> Una interfaz por lectura y no una fachada: quien
 * necesite el importe de un producto pedirá su propia lectura, y así añadirlo no cambiará este
 * contrato ni sus dobles de prueba.
 */
public interface ProductCatalog {

  /**
   * El producto, si existe.
   *
   * @param id identificador del producto; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<ProductView> find(UUID id);

  /** Lo que cruza la frontera: datos planos, sin comportamiento y sin entidad. */
  record ProductView(UUID id, String code, String name, boolean retired) {}
}
