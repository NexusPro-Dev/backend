package com.factech.nexus.modules.academy.application;

/**
 * Cuerpo del retiro de un curso (`RF-AC-013`): el motivo, que {@code DeletionReason} de {@code
 * shared/audit} valida antes de cualquier consulta.
 */
public record DeleteCourseRequest(String reason) {}
