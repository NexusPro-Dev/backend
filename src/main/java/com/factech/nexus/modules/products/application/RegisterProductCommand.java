package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductImplementation;
import com.factech.nexus.modules.products.domain.models.ProductScope;
import com.factech.nexus.modules.products.domain.models.ProductType;
import java.math.BigDecimal;
import java.util.List;
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
 * @param links los enlaces que el producto declara, hasta <b>uno por tipo</b> (`RN-PM-048`). Nunca
 *     nula: <b>vacía</b> cuando no declara ninguno. Son enlaces, no archivos, y el sistema no los
 *     sigue (`pm.md` §5.2.8). El video vive aquí desde el 22-09-2026, con el tipo {@code
 *     VIDEO_PRESENTACION} (`RN-PM-032`)
 * @param purchasePrice lo que NEXUS paga por el producto cuando tiene que comprarlo; {@code null}
 *     significa que no se conoce, y <b>no</b> que costara cero (`RN-PM-023`). No se cobra y no sale
 *     de administración (`RN-PM-024`)
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
    List<ProductLinkRequest> links,
    UUID sourceMembershipId,
    UUID targetMembershipId,
    BigDecimal price,
    BigDecimal purchasePrice,
    UUID currencyId,
    Integer validityDays,
    ProductScope scope,
    ProductImplementation implementation) {}
