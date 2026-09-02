package com.factech.nexus.modules.commissions.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/user-commission-rates} (`RF-CM-006`).
 *
 * <p><b>No es el mismo alta que la de rol, y por eso es otro endpoint.</b> Hasta el 01-09-2026 las
 * dos eran una sola operación con campos opcionales; ahora una escribe en un catálogo sin fechas y
 * la otra registra una excepción con vigencia y con la exigencia de que no haya otra viva.
 * Fundirlas obligaría a un endpoint cuyas validaciones dependen de qué campo llegó.
 *
 * <p><b>No hay {@code roleId}, y no falta</b>: la tasa es de la persona y punto (`cm.md` §7.2).
 * Quien la tiene gana lo mismo venda lo que venda — y sigue ganándolo aunque deje de vender.
 *
 * <p><b>Tampoco hay {@code productId}</b>, y eso sí es una regla: una tasa personalizada <b>no se
 * acota a un producto</b> (`RN-CM-014`).
 */
public record RegisterUserCommissionRateRequest(
    @NotNull(message = "VAL-001: La persona de la tasa es obligatoria.") UUID userId,
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
