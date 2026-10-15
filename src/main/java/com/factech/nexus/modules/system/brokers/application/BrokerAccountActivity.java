package com.factech.nexus.modules.system.brokers.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lo que el broker avisó de la cuenta (`RN-SP-073`, `RN-SP-074`, `RN-SP-077`): cuándo llegó su
 * registro y de qué país es, sus depósitos —el primero y el total— y cuántas operaciones lleva, con
 * la primera y la última.
 *
 * <p>{@code firstDepositAt} es nulo si no hay depósito <b>o si la cuenta pasó a {@code
 * FIRST_DEPOSIT} sin aviso</b>, antes del 10-10-2026: el estado lo dice {@code status}, no esto. Y
 * {@code registeredAt}, nulo en la cuenta de la que el broker no ha avisado el registro —también
 * las anteriores al 15-10-2026—.
 *
 * <p><b>{@code depositsTotal} es cero y no nulo sin depósitos</b>, y está en {@code
 * depositCurrency}: un depósito avisado en otra moneda no suma.
 *
 * <p><b>Los nulos se emiten</b>, como en la cuenta que la contiene: el anotado de {@link
 * BrokerAccountItem} no alcanza a un registro anidado.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BrokerAccountActivity(
    OffsetDateTime registeredAt,
    String countryCode,
    OffsetDateTime firstDepositAt,
    BigDecimal firstDepositAmount,
    BigDecimal depositsTotal,
    String depositCurrency,
    int operationsCount,
    OffsetDateTime firstOperationAt,
    OffsetDateTime lastOperationAt) {}
