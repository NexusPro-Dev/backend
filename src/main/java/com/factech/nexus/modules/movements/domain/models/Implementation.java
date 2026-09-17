package com.factech.nexus.modules.movements.domain.models;

/**
 * Cómo se entrega lo que una línea vendió, <b>copiado</b> del producto al registrar (`RN-MV-030`,
 * `V16`).
 *
 * <p>Es copia y no referencia porque `RF-PM-004` la corrige: lo vendido se entrega como se vendió,
 * no como el catálogo diga el día de la confirmación (`RN-MV-021`). Los dos literales son los de
 * {@code ck_products_implementation}, y este tipo existe para que el caso de uso no compare
 * cadenas.
 */
public enum Implementation {
  /** Confirmar el pago entrega en el acto. */
  AUTOMATICA,
  /** Confirmar el pago deja la línea esperando a que alguien la autorice (`RF-MV-010`). */
  MANUAL
}
