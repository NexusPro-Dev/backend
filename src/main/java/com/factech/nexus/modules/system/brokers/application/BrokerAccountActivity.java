package com.factech.nexus.modules.system.brokers.application;

import java.time.OffsetDateTime;

/**
 * Lo que el broker avisó de la cuenta (`RN-SP-073`, `RN-SP-074`): cuándo llegó el primer depósito y
 * cuántas operaciones lleva, con la primera y la última.
 *
 * <p>{@code firstDepositAt} es nulo si no hay depósito <b>o si la cuenta pasó a {@code
 * FIRST_DEPOSIT} sin aviso</b>, antes del 10-10-2026: el estado lo dice {@code status}, no esto.
 */
public record BrokerAccountActivity(
    OffsetDateTime firstDepositAt,
    int operationsCount,
    OffsetDateTime firstOperationAt,
    OffsetDateTime lastOperationAt) {}
