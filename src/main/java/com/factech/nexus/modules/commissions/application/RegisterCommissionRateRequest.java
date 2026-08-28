package com.factech.nexus.modules.commissions.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/commission-rates} (`RF-CM-001`).
 *
 * <p><b>El producto y la persona son opcionales, y su ausencia es la que da el alcance</b>: sin
 * persona la tarifa rige para todos los del rol, sin producto para todo el catalogo. No hay ningun
 * campo que diga «para todos» — podria contradecir a los otros dos, y esa contradiccion no la
 * detecta nada.
 *
 * <p><b>El cero es un porcentaje valido</b> (`RN-CM-007`): significa «esto no comisiona», y es la
 * unica forma de exceptuar un producto a un rol que si tiene tarifa por omision. De ahi el {@code
 * DecimalMin} inclusivo.
 *
 * <p><b>Lo que NO se valida aqui</b> es que el rol sea vendedor, que la persona porte el rol y que
 * el producto no este retirado: dependen de datos de otros modulos y las comprueba el caso de uso.
 */
public record RegisterCommissionRateRequest(
    @NotNull(message = "VAL-001: El rol de la tarifa es obligatorio.") UUID roleId,
    UUID productId,
    UUID userId,
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
        BigDecimal percentage,
    @NotNull(message = "VAL-004: El inicio de vigencia es obligatorio.") LocalDate validFrom,
    LocalDate validTo) {}
