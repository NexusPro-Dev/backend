package com.factech.nexus.modules.system.users.application;

import java.util.UUID;

/**
 * La beca del primer depósito, publicada <b>por `MV` para `SP`</b> (`RN-SP-079`, `RN-MV-079`,
 * 15-10-2026).
 *
 * <p>Quien se registra por enlace tiene su venta de la beca, que espera al depósito ({@link
 * FirstDepositActivation}). Quien se asocia a su cuenta de broker desde la plataforma no la tiene,
 * y sin ella el FTD no existiría. Cuando su cuenta llega a {@code FIRST_DEPOSIT}, `SP` pide a `MV`
 * esa venta: `SP` no vende, y por eso el puerto se declara aquí y lo implementa `MV`, como {@link
 * RegistrationSaleRegistrar}.
 */
public interface FirstDepositSale {

  /**
   * Registra, confirma y entrega la venta {@code BECA → BECA} de {@code userId}, <b>si procede</b>:
   * está {@code ACTIVO}, no tiene una membresía vigente superior a {@code BECA}, no tiene ya una
   * venta de la beca y hay producto activo. Si no procede, no hace nada y no es un error.
   *
   * <p>Se llama <b>dentro de la transacción</b> que mueve la cuenta del broker: si la venta falla
   * por un defecto, no se mueve nada.
   *
   * @param sellerUsername el dueño de la cuenta {@code VENDEDOR} de origen, o nulo: la línea va
   *     entonces sin vendedor. Uno que ya no es fuerza comercial cuenta como nulo
   * @return si registró la venta
   */
  boolean grantBeca(UUID userId, String sellerUsername);
}
