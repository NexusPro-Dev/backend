package com.factech.nexus.modules.system.users.application;

import java.util.UUID;

/**
 * Lo que el primer depósito activa, publicado <b>por `MV` para `SP`</b> (`RN-SP-057`, `RN-MV-075`,
 * 05-10-2026).
 *
 * <h2>Invierte la dirección, como {@link RegistrationSaleRegistrar}, y por lo mismo</h2>
 *
 * <p>`SP` es la raíz del grafo (`modules.md` §7) y no consume de nadie: importar algo de `MV`
 * <b>compila y abre un ciclo</b>. Se declara aquí y lo implementa `MV`, que es de quien son la
 * venta y su entrega.
 *
 * <h2>Por qué la llama el cambio de estado y no al revés</h2>
 *
 * <p>La venta del alta gratuita nace confirmada pero <b>sin entregar</b>: lo que espera es el
 * depósito, y el depósito lo declara `SP` al sacar la cuenta de {@code FTD_PENDIENTE} (hoy un actor
 * por `RF-SP-028`; mañana el webhook de `RF-SP-054`). Es `SP` quien sabe que ocurrió, y por eso es
 * quien avisa.
 */
public interface FirstDepositActivation {

  /**
   * Entrega las líneas todavía pendientes de la venta del alta de {@code userId}.
   *
   * <p>Se llama <b>dentro de la transacción del cambio de estado</b>: si la entrega falla, la
   * cuenta no sale de {@code FTD_PENDIENTE}. Un cliente activo sin lo que compró sería un FTD que
   * nadie contaría (`RN-CM-036`). <b>Es idempotente</b>: sin líneas pendientes no hace nada.
   *
   * @param movementId la venta del alta: la que cita {@code client_sellers.first_movement_id}
   */
  void activate(UUID userId, UUID movementId);
}
