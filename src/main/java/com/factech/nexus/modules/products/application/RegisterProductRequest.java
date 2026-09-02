package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/products} (`RF-PM-001`).
 *
 * <p><b>No existe campo {@code status}</b>, y el cuerpo se deserializa con {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} activo: enviarlo devuelve {@code 400} y no se ignora en silencio
 * (`CA-PM-068`). Es lo mismo que `RF-SP-001` hizo con {@code isSystem}, y es lo que hace
 * verificable que el estado inicial no se pueda forzar desde fuera.
 *
 * <p><b>La condición cruzada de `RN-PM-002` NO se valida aquí.</b> Bean Validation puede expresar
 * «obligatorio si otro campo vale X» con un validador de clase, pero el {@code 400} que produce no
 * distingue si sobró el destino o si faltó — y `VAL-007` y `VAL-008` son dos mensajes distintos. La
 * comprueba el dominio.
 *
 * <p><b>Y `RN-PM-016` tampoco</b>, por lo mismo: que el icono solo valga en el upgrade depende del
 * tipo, no del icono. Aquí solo se acota su longitud, que es cierta para cualquier tipo; la forma
 * del identificador y la condición cruzada las comprueba el dominio.
 *
 * <p><b>La escala del precio tampoco.</b> No la fija este DTO sino la moneda (`RN-PM-007`), de modo
 * que aquí solo se acota lo que es cierto para cualquiera: hasta cuatro decimales, que es lo que la
 * columna admite. Los de verdad los decide el caso de uso.
 *
 * @param validityDays días que dura lo adquirido. Ausente o nulo significan lo mismo: no caduca
 */
public record RegisterProductRequest(
    @NotBlank(message = "VAL-009: El código del producto es obligatorio.")
        @Size(max = 50, message = "VAL-009: El código no puede exceder 50 caracteres.")
        @Pattern(
            regexp = "^[A-Za-z][A-Za-z0-9_]*$",
            message =
                "VAL-010: El código solo admite letras mayúsculas, dígitos y guion bajo, y debe"
                    + " empezar por letra.")
        String code,
    @NotNull(message = "VAL-001: El tipo de producto es obligatorio.") ProductType type,
    @NotBlank(message = "VAL-002: El nombre del producto es obligatorio.")
        @Size(max = 150, message = "VAL-003: El nombre no puede exceder 150 caracteres.")
        String name,
    @Size(max = 1000, message = "VAL-003: La descripción no puede exceder 1000 caracteres.")
        String description,
    @Size(max = 50, message = "VAL-012: El icono no puede exceder 50 caracteres.") String icon,
    // NINGUNA DE LAS DOS LLEVA `@NotNull`, y es deliberado: su obligatoriedad
    // depende del TIPO (`RN-PM-002`), que Bean Validation no puede mirar sin una
    // restricción de clase. La comprueba el dominio, que es donde vive la regla
    // — y donde además puede decir CUÁL de las dos falta.
    UUID sourceMembershipId,
    UUID targetMembershipId,
    @NotNull(message = "VAL-004: El precio es obligatorio.")
        @DecimalMin(
            value = "0.0",
            inclusive = false,
            message = "VAL-004: El precio debe ser mayor que cero.")
        @Digits(
            integer = 10,
            fraction = 4,
            message = "VAL-005: El precio admite como mucho cuatro decimales.")
        BigDecimal price,
    @NotNull(message = "VAL-006: La moneda es obligatoria.") UUID currencyId,
    @Min(value = 1, message = "VAL-011: La vigencia debe ser un número de días mayor que cero.")
        Integer validityDays) {

  /**
   * Recorta antes de que corran las validaciones.
   *
   * <p>Jackson construye el registro por este constructor y Bean Validation se ejecuta después, de
   * modo que aquí el recorte llega a tiempo para importar: sin él, {@code " "} pasaría por
   * {@code @NotBlank} sobre un nombre que en la base quedaría vacío.
   */
  public RegisterProductRequest {
    code = code == null ? null : code.trim();
    name = name == null ? null : name.trim();
    description = description == null ? null : description.trim();
    icon = icon == null ? null : icon.trim();
  }

  public RegisterProductCommand toCommand() {
    return new RegisterProductCommand(
        code,
        type,
        name,
        description,
        icon,
        sourceMembershipId,
        targetMembershipId,
        price,
        currencyId,
        validityDays);
  }
}
