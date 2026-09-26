package com.factech.nexus.modules.academy.application;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de la clasificación de un curso (`RF-AC-016` §11): la categoría, y solo ella. <b>Una
 * pareja por petición</b>, como toda relación del sistema (`plan.md` §9); un curso en varias
 * categorías se arma con una petición por cada una.
 */
public record ClassifyCourseRequest(
    @NotNull(message = "VAL-002: La categoría es obligatoria.") UUID categoryId) {}
