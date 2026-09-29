package com.factech.nexus.modules.system.users.application;

import java.util.Set;
import java.util.UUID;

/**
 * <b>Los roles del último eslabón</b> de la fuerza comercial (**D-25**; `RN-CM-045`).
 *
 * <p>La pide `CM` para devengar: quien vende una línea y <b>no</b> porta uno de estos roles cobra
 * la comisión por venta directa del producto en lugar de su tasa de rol (`RF-CM-013` `plan.md`
 * §13).
 *
 * <p><b>Se define por la forma de la jerarquía, no por el código</b>, con el criterio de
 * `RN-SP-019` y `RN-SP-051`: son los roles {@code VENDEDOR} vivos de los que <b>no cuelga ningún
 * otro rol {@code VENDEDOR} vivo</b>. Hoy, {@code AGENTE}. Un rango nuevo por debajo de él lo
 * convertiría en superior sin tocar nada.
 *
 * <p><b>Una interfaz y no un método más de {@link SellerRoleCatalog}</b>: una interfaz por lectura,
 * por la norma de `architecture.md` §15.2.
 */
public interface LastLinkRoles {

  /**
   * Los identificadores de los roles del último eslabón. Un conjunto pequeño: se pide por tanda.
   */
  Set<UUID> ids();
}
