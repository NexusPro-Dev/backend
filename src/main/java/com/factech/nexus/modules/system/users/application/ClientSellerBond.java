package com.factech.nexus.modules.system.users.application;

import java.util.UUID;

/**
 * Vincular a un cliente con el vendedor de cuyo enlace compró: <b>la segunda escritura que `SP`
 * publica hacia otro módulo</b> (`architecture.md` §15.2.1, 24-09-2026).
 *
 * <p>La invoca `MV` al registrar una compra por hotlink (`RF-MV-011`, `RN-MV-025`), y la reutiliza
 * `RF-MV-013` para el paquete sin escribir otra.
 *
 * <p><b>Lo que hace</b>: crea la fila {@code HOTLINK} de {@code client_sellers} si no existía, con
 * esa venta como {@code first_movement_id}. <b>Lo que NO hace, y es la mitad que importa</b>: no
 * toca el principal. La fila {@code REGISTRO} es de quien registró al cliente y es inmutable
 * (`RN-SP-049`) — comprar por el enlace de otro <b>suma</b> un vendedor, no sustituye a ninguno.
 *
 * <p><b>Lo que NO decide</b>: si hay que vincular. Eso lo decide `MV` al registrar la compra, por
 * el mismo reparto que `MembershipGrant`: el <i>qué significa</i> es de `SP` y el <i>si</i> es de
 * quien llama. Recibe una orden y la cumple.
 *
 * <p><b>Se une a la transacción del que llama y no abre una propia.</b> Si el vínculo falla, la
 * venta no queda; y si la venta falla, el vínculo tampoco. Un cliente vinculado a un vendedor por
 * una venta que no existe sería una atribución sin respaldo.
 *
 * <p><b>Es idempotente, y por el esquema.</b> Comprar dos veces por el mismo enlace deja un solo
 * vínculo, con la venta que lo creó — la primera, no la última. Ver {@link
 * com.factech.nexus.modules.system.users.domain.repository.ClientSellerRepository#attachByHotlink}.
 */
public interface ClientSellerBond {

  /**
   * @return {@code true} si el vínculo nació en esta llamada; {@code false} si ya existía, que es
   *     `FA-001` de `RF-MV-011` y no un error
   * @throws IllegalStateException si no hay transacción activa: la operación no abre una
   */
  boolean bind(BondOrder order);

  /**
   * La orden, plana: quién, con quién y por qué venta.
   *
   * <p><b>No cruza ninguna entidad</b>, como las demás operaciones publicadas: `SP` no sabe qué es
   * una venta — guarda el identificador que `MV` le entrega y deja que el motor se encargue de que
   * señale a algo que existe.
   *
   * @param movementId la venta que crea el vínculo. Es su {@code first_movement_id}, y solo se
   *     escribe cuando el vínculo nace
   */
  record BondOrder(UUID clientId, UUID sellerId, UUID movementId) {}
}
