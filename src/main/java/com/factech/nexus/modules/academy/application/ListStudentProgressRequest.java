package com.factech.nexus.modules.academy.application;

import java.util.UUID;

/**
 * Filtros y paginación del listado del progreso (`RF-AC-040` §6.1). <b>Sin orden elegible</b>: la
 * última actividad descendente responde «quién estudió último».
 */
public record ListStudentProgressRequest(
    Integer page, Integer size, UUID userId, UUID courseId, Boolean completed) {}
