package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.CourseStatus;
import jakarta.validation.constraints.NotNull;

/** Cuerpo del cambio de estado de una leccion (RF-AC-030): ACTIVO o INACTIVO (VAL-002). */
public record ChangeLessonStatusRequest(
    @NotNull(message = "VAL-002: El estado es obligatorio y debe ser ACTIVO o INACTIVO.")
        CourseStatus status) {}
