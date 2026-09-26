package com.factech.nexus.modules.system.users.application;

import java.util.Set;
import java.util.UUID;

/**
 * «¿Qué productos tiene HOY esta persona?» — la cuarta lectura que `SP` publica para `AC` (D-25,
 * `requirements/sp.md` 1.87.0 §8).
 *
 * <p>La pide el aula (`RF-AC-033` a `RF-AC-035`): un curso se abre a quien tiene vigente uno de sus
 * servicios (`RN-AC-020`), y `AC` no puede leer {@code user_products}. <b>Vigente es lo mismo que
 * en {@link CurrentMembershipLookup}</b>: una fila con producto, empezada, sin fin pasado y sin
 * cerrar (`RN-SP-056`).
 *
 * <p><b>Devuelve un conjunto y no responde sobre un producto</b>, al revés que {@link
 * PermissionHolderLookup}: el catálogo del aula decide {@code accessible} para decenas de cursos, y
 * una pregunta por curso sería el {@code N+1} de siempre. <b>No viajan las fechas</b> por lo mismo
 * que {@code CurrentMembershipView} no lleva la de fin: son el dato con el que el consumidor podría
 * rehacer la comparación que este puerto existe para no repetir.
 */
public interface CurrentProductsLookup {

  /**
   * Los productos que la persona tiene vigentes ahora.
   *
   * @param userId identificador de la persona; un valor nulo devuelve vacío en lugar de fallar
   * @return vacío si no tiene ninguno vigente o si no existe; nunca nulo
   */
  Set<UUID> currentProductIdsOf(UUID userId);
}
