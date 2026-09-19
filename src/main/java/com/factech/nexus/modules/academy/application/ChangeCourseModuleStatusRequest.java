package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.CourseStatus;
import jakarta.validation.constraints.NotNull;

/** Cuerpo del cambio de estado de un módulo (RF-AC-024): ACTIVO o INACTIVO (VAL-002). */
public record ChangeCourseModuleStatusRequest(
    @NotNull(message = "VAL-002: El estado es obligatorio y debe ser ACTIVO o INACTIVO.")
        CourseStatus status) {}
