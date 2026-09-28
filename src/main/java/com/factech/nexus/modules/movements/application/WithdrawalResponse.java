package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * `RF-MV-019`: el retiro pedido y los saldos de su moneda como quedan —la billetera ya descontada y
 * lo retenido con el importe sumado—, para que quien pide vea en la misma respuesta que el dinero
 * se apartó.
 */
@Schema(name = "WithdrawalResult")
public record WithdrawalResponse(LedgerMovementResponse movement, BalancesResponse balances) {}
