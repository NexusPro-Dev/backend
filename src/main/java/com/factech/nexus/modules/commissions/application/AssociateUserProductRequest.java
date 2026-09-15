package com.factech.nexus.modules.commissions.application;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/user-commission-rates/{id}/products} (`RF-CM-006`, 11-09-2026).
 *
 * <p><b>Un producto por petición</b>, como lo hacía la asociación de la tasa de rol hasta que esta
 * pasó a nacer con su producto (`RN-CM-021`, 15-09-2026). Admitir una lista obligaría a decidir qué
 * hacer cuando la mitad pasa y la mitad choca — y las dos salidas son malas: parcial deja al
 * cliente sin saber qué quedó, y todo-o-nada rechaza cuatro asociaciones válidas por una repetida.
 */
public record AssociateUserProductRequest(
    @NotNull(message = "VAL-013: El producto es obligatorio.") UUID productId) {}
