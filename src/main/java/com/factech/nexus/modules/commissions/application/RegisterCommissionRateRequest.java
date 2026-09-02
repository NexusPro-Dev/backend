package com.factech.nexus.modules.commissions.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/commission-rates} (`RF-CM-001`).
 *
 * <p><b>Dos campos, y ninguno opcional.</b> Es lo que queda del alta anterior, que tenía cinco y
 * dejaba que la ausencia de tres de ellos decidiera el alcance. Aquí no hay nada que deducir: la
 * tasa dice qué gana un rol, y <b>sobre qué lo gana se declara asociándola</b> (`RF-CM-007`).
 *
 * <p><b>Lo que esto NO hace, y conviene saberlo:</b> registrar una tasa no la pone en vigor
 * (`RN-CM-012`). Hasta que se asocie a un producto <b>no paga nada a nadie</b>, y no falla — se
 * descubre liquidando.
 *
 * <p><b>El cero es un porcentaje válido</b> (`RN-CM-007`): significa «esto no comisiona», y es la
 * forma de asociar un producto a un rol declarando que no paga nada. De ahí el {@code DecimalMin}
 * inclusivo.
 *
 * <p><b>Lo que NO se valida aquí</b> es que el rol sea de tipo vendedor: depende de datos de `SP` y
 * lo comprueba el caso de uso.
 */
public record RegisterCommissionRateRequest(
    @NotNull(message = "VAL-001: El rol de la tasa es obligatorio.") UUID roleId,
    @NotNull(message = "VAL-002: El porcentaje es obligatorio.")
        @DecimalMin(
            value = "0.00",
            message = "VAL-003: El porcentaje debe estar entre cero y cien.")
        @DecimalMax(
            value = "100.00",
            message = "VAL-003: El porcentaje debe estar entre cero y cien.")
        @Digits(
            integer = 3,
            fraction = 2,
            message = "VAL-003: El porcentaje admite como mucho dos decimales.")
        BigDecimal percentage) {}
