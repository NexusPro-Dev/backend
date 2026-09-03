package com.factech.nexus.modules.commissions.application;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/commission-rates/{id}/products} (`RF-CM-007`).
 *
 * <p><b>Un solo campo, y el rol no está.</b> No se recibe: se toma de la tasa que la ruta nombra.
 * Aceptarlo permitiría enviar uno distinto del que la tasa declara, y aunque la clave foránea
 * compuesta lo rechazaría en el motor, el error llegaría como una violación de integridad en lugar
 * de como lo que es — un dato que nadie tenía que dar.
 *
 * <p><b>Esta es la operación que pone la tasa en vigor</b> (`RN-CM-012`). Sin ella el catálogo no
 * paga nada a nadie.
 */
public record AssociateProductRequest(
    @NotNull(message = "VAL-001: El producto es obligatorio.") UUID productId) {}
