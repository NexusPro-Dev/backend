package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo del alta de un paquete (`RF-PM-017` §11).
 *
 * <p><b>Sin {@code price}, sin {@code products}, sin {@code status}</b>, y con {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} activo: enviar cualquiera de los tres devuelve {@code 400}
 * (`VAL-005`, `CA-PM-266`) y no se ignora en silencio. Los tres son lo que alguien que viene del
 * alta de producto intentaría mandar, y los tres tienen respuesta en otro sitio — la cuenta,
 * `RF-PM-023` y `RF-PM-021`.
 *
 * <p>Las cuatro validaciones de forma se devuelven <b>juntas</b> (`CA-PM-265`): quien se equivocó
 * en dos corrige una vez.
 */
public record RegisterPackageRequest(
    @NotBlank(
            message =
                "VAL-001: El código es obligatorio y debe empezar por una letra mayúscula y contener"
                    + " solo letras mayúsculas, dígitos y guion bajo.")
        @Size(max = 50, message = "VAL-001: El código no puede exceder 50 caracteres.")
        @Pattern(
            regexp = "^[A-Za-z][A-Za-z0-9_]*$",
            message =
                "VAL-001: El código es obligatorio y debe empezar por una letra mayúscula y contener"
                    + " solo letras mayúsculas, dígitos y guion bajo.")
        String code,
    @NotBlank(message = "VAL-002: El nombre es obligatorio y no puede superar los 150 caracteres.")
        @Size(
            max = 150,
            message = "VAL-002: El nombre es obligatorio y no puede superar los 150 caracteres.")
        String name,
    @Size(max = 1000, message = "VAL-002: La descripción no puede exceder 1000 caracteres.")
        String description,
    @NotNull(message = "VAL-003: La moneda es obligatoria.") UUID currencyId,
    // El valor fuera de dominio lo rechaza Jackson al deserializar el
    // enumerado, también con `400`.
    @NotNull(
            message =
                "VAL-004: El alcance es obligatorio y debe ser TIENDA, HOTLINK, AMBOS o NINGUNO.")
        ProductScope scope) {

  public RegisterPackageRequest {
    code = code == null ? null : code.trim();
    name = name == null ? null : name.trim();
    description = description == null ? null : description.trim();
  }
}
