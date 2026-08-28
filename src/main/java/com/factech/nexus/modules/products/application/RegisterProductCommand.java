package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entrada del caso de uso de alta de producto (`RF-PM-001`).
 *
 * <p><b>Sin {@code status}.</b> Todo producto nace {@code INACTIVO} (`RN-PM-012`), y no admitirlo
 * como argumento es lo que hace verificable que el estado inicial no se pueda forzar desde fuera.
 *
 * @param targetMembershipId obligatorio si el tipo es upgrade, prohibido si es bot
 * @param icon identificador del icono; opcional en el upgrade y prohibido en el bot (`RN-PM-016`)
 * @param validityDays días que dura lo adquirido; {@code null} significa que no caduca
 */
public record RegisterProductCommand(
    String code,
    ProductType type,
    String name,
    String description,
    String icon,
    UUID targetMembershipId,
    BigDecimal price,
    UUID currencyId,
    Integer validityDays) {}
