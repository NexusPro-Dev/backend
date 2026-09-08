package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductImplementation;
import com.factech.nexus.modules.products.domain.models.ProductScope;
import com.factech.nexus.modules.products.domain.models.ProductType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entrada del caso de uso de alta de producto (`RF-PM-001`).
 *
 * <p><b>Sin {@code status}.</b> Todo producto nace {@code INACTIVO} (`RN-PM-012`), y no admitirlo
 * como argumento es lo que hace verificable que el estado inicial no se pueda forzar desde fuera.
 *
 * @param sourceMembershipId de qué membresía sale. Obligatorio si el tipo es upgrade, prohibido si
 *     es bot
 * @param targetMembershipId a cuál lleva. Mismas condiciones
 * @param icon identificador del icono; opcional en el upgrade y prohibido en el bot (`RN-PM-016`)
 * @param publicPrice el precio con el que el producto se anuncia; {@code null} significa que se
 *     anuncia con el del sistema, y <b>no</b> que valga cero (`RN-PM-023`). No se cobra
 * @param validityDays días que dura lo adquirido; {@code null} significa que no caduca
 * @param scope hasta dónde se muestra el producto. Obligatorio en los dos tipos (`RN-PM-019`)
 * @param implementation si lo comprado se aplica solo o espera autorización (`RN-PM-020`)
 */
public record RegisterProductCommand(
    String code,
    ProductType type,
    String name,
    String description,
    String icon,
    UUID sourceMembershipId,
    UUID targetMembershipId,
    BigDecimal price,
    BigDecimal publicPrice,
    UUID currencyId,
    Integer validityDays,
    ProductScope scope,
    ProductImplementation implementation) {}
