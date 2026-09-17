package com.factech.nexus.modules.academy.application;

/**
 * Cuerpo del retiro de una categoría (`RF-AC-005`): el motivo, que {@code DeletionReason} de {@code
 * shared/audit} valida.
 */
public record DeleteCourseCategoryRequest(String reason) {}
