package com.factech.nexus.modules.academy.application;

import io.swagger.v3.oas.annotations.media.Schema;

/** El reporte del reproductor (`RF-AC-039`): dónde va, en segundos enteros. */
public record ReportLessonProgressRequest(
    @Schema(description = "La posición del reproductor en segundos, mayor o igual que cero.")
        Integer positionSeconds) {}
