package com.factech.nexus.modules.system.users.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Conceder el nivel comprado: <b>la primera escritura que `SP` publica hacia otro módulo</b> (D-26,
 * cerrada el 17-09-2026; `architecture.md` §15.2.1).
 *
 * <p>La invoca `MV` al confirmar el pago de una venta con un upgrade automático (`RF-MV-003`,
 * `RN-MV-020`), y hace lo que `RF-SP-032` hace a mano y con las mismas reglas: <b>cierra la
 * membresía vigente e inserta la comprada</b> (`RN-SP-014`), con la vigencia contada desde el
 * instante que la orden indica, deja el suelo intacto (`RN-SP-018`) y escribe su asiento en {@code
 * audit_change_log} como {@code user_memberships}.
 *
 * <p><b>Lo que NO hace es decidir si conceder.</b> Que el producto sea automático, que la venta se
 * haya confirmado y que el nivel no baje (`RN-MV-029`) son reglas de `MV` y se deciden <b>antes</b>
 * de llamar: una operación de `SP` que comparase niveles para decidir por `MV` sería la segunda
 * definición de una regla ajena. Recibe una orden y la cumple.
 *
 * <p><b>Siempre cierra e inserta, también cuando la membresía es la misma.</b> `RF-SP-032` en ese
 * caso solo mueve la fecha, porque corregir hasta cuándo vale un nivel es una corrección
 * administrativa; <b>una compra es un periodo nuevo pagado</b>, y el historial tiene que decir
 * cuántas veces se pagó (`requirements/mv.md` §5.4, decisión 2).
 *
 * <p><b>Se une a la transacción del que llama y no abre una propia</b>: si conceder falla, la venta
 * no se confirma, y al revés. Y <b>lanza</b> —no devuelve vacío— cuando la persona no existe o está
 * eliminada: eso no puede ocurrir desde una venta registrada, y si ocurre es un fallo del sistema
 * que tiene que deshacerlo todo, no un {@code 4xx}. Es lo contrario de una lectura, y es
 * deliberado: una escritura a medias es peor que ninguna.
 */
public interface MembershipGrant {

  /**
   * @throws IllegalArgumentException si la persona no existe o está eliminada, o la membresía no
   *     existe. Sube como fallo del sistema y deshace la transacción del que llama
   * @throws IllegalStateException si no hay transacción activa: la operación no abre una
   */
  GrantedMembership grant(GrantOrder order);

  /**
   * La orden, plana: quién, qué, por cuántos días y desde cuándo.
   *
   * @param validityDays nulo significa <b>no caduca</b> (`RN-PM-015`)
   * @param at el instante desde el que corre la vigencia — el de la confirmación, no el de la venta
   *     (`requirements/mv.md` §5.4, decisión 1)
   */
  record GrantOrder(UUID userId, UUID membershipId, Integer validityDays, OffsetDateTime at) {}

  /** Lo que quedó vigente, plano: sin entidad y sin con qué escribir. */
  record GrantedMembership(
      UUID membershipId,
      String code,
      String name,
      int level,
      OffsetDateTime startedAt,
      OffsetDateTime endsAt) {}
}
