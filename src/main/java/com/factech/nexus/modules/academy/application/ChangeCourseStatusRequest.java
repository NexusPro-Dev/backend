package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.CourseStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo del cambio de estado de un curso (`RF-AC-012`): {@code ACTIVO} o {@code INACTIVO}. Un
 * valor fuera del dominio lo rechaza el editor canónico con el mismo {@code 400} (`VAL-002`).
 */
public record ChangeCourseStatusRequest(
    @NotNull(message = "VAL-002: El estado es obligatorio y debe ser ACTIVO o INACTIVO.")
        CourseStatus status) {}
