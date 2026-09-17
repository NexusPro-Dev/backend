package com.factech.nexus.modules.academy.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo del alta de una categoría (`RF-AC-001` §11).
 *
 * <p><b>Sin {@code status}, sin {@code courses}, sin {@code coverImageUrl}</b>, y con {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} activo: enviar cualquiera de los tres devuelve {@code 400}
 * (`VAL-005`, `CA-AC-005`) y no se ignora en silencio. La categoría no tiene estado, los cursos se
 * clasifican desde el curso y la portada es un archivo con su propia petición.
 *
 * <p>Las cuatro validaciones de forma se devuelven <b>juntas</b> (`CA-AC-004`): quien se equivocó
 * en dos corrige una vez. El color se admite aquí en minúsculas —la expresión es insensible a la
 * caja— y lo normaliza el agregado antes de guardar.
 */
public record RegisterCourseCategoryRequest(
    @NotBlank(message = "VAL-001: El nombre es obligatorio y no puede superar los 150 caracteres.")
        @Size(
            max = 150,
            message = "VAL-001: El nombre es obligatorio y no puede superar los 150 caracteres.")
        String name,
    @Size(max = 1000, message = "VAL-001: La descripción no puede exceder 1000 caracteres.")
        String description,
    @NotBlank(
            message =
                "VAL-002: El color es obligatorio y debe ser seis dígitos hexadecimales sin el"
                    + " símbolo #.")
        @Pattern(
            regexp = "^[0-9A-Fa-f]{6}$",
            message =
                "VAL-002: El color es obligatorio y debe ser seis dígitos hexadecimales sin el"
                    + " símbolo #.")
        String color,
    @NotBlank(
            message =
                "VAL-003: El icono es obligatorio, solo admite minúsculas, dígitos y guion medio,"
                    + " debe empezar por letra y no puede exceder 50 caracteres.")
        @Size(
            max = 50,
            message =
                "VAL-003: El icono es obligatorio, solo admite minúsculas, dígitos y guion medio,"
                    + " debe empezar por letra y no puede exceder 50 caracteres.")
        @Pattern(
            regexp = "^[a-z][a-z0-9-]*$",
            message =
                "VAL-003: El icono es obligatorio, solo admite minúsculas, dígitos y guion medio,"
                    + " debe empezar por letra y no puede exceder 50 caracteres.")
        String icon,
    @NotNull(
            message =
                "VAL-004: El orden es obligatorio y debe ser un entero mayor o igual que cero.")
        @PositiveOrZero(
            message =
                "VAL-004: El orden es obligatorio y debe ser un entero mayor o igual que cero.")
        Integer displayOrder) {

  public RegisterCourseCategoryRequest {
    name = name == null ? null : name.trim();
    description = description == null ? null : description.trim();
    color = color == null ? null : color.trim();
    icon = icon == null ? null : icon.trim();
  }
}
