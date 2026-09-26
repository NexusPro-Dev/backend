package com.factech.nexus.modules.academy.application;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de la visibilidad por servicio (`RF-AC-037` §11): el producto, y solo él. <b>Una pareja
 * por petición</b>, como toda relación del sistema.
 */
public record GrantCourseProductRequest(
    @NotNull(message = "VAL-002: El producto es obligatorio.") UUID productId) {}
