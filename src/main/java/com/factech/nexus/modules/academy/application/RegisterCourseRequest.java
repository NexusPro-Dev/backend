package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.CourseDifficulty;
import com.factech.nexus.shared.video.VideoLink;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo del alta de un curso (`RF-AC-008` §11).
 *
 * <p><b>Sin {@code status}, sin recomendaciones, sin módulos, sin portada y sin código</b>, y con
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} activo: cualquiera de ellos devuelve {@code 400} (`VAL-007`,
 * `CA-AC-038`). <b>Categorías, servicios y membresías sí, desde el 25-09-2026</b>, como {@code
 * categoryIds}, {@code productIds} y {@code membershipIds} (`ac.md` §5.2.9): el curso nace
 * inactivo, en sus cajones y con quién lo puede ver. La portada va por {@code PUT
 * /courses/{id}/cover}.
 *
 * <p>Las seis validaciones de forma se devuelven <b>juntas</b> (`CA-AC-037`). La dificultad es un
 * enumerado: un valor fuera del dominio lo rechaza el editor canónico de {@code shared/error} antes
 * de llegar aquí, con el mismo {@code 400}.
 */
public record RegisterCourseRequest(
    @NotBlank(message = "VAL-001: El título es obligatorio y no puede superar los 150 caracteres.")
        @Size(
            max = 150,
            message = "VAL-001: El título es obligatorio y no puede superar los 150 caracteres.")
        String title,
    @NotNull(message = "VAL-002: El instructor es obligatorio.") UUID instructorId,
    @NotNull(
            message =
                "VAL-003: La dificultad es obligatoria y debe ser PRINCIPIANTE, INTERMEDIO o"
                    + " AVANZADO.")
        CourseDifficulty difficulty,
    @Size(max = 300, message = "VAL-005: La descripción corta no puede exceder 300 caracteres.")
        String shortDescription,
    @Size(
            max = 10_000,
            message = "VAL-005: La descripción larga no puede exceder 10 000 caracteres.")
        String longDescription,
    @Size(
            max = 500,
            message =
                "VAL-006: El enlace del video debe ser un video de YouTube o de Vimeo, sin espacios y de"
                    + " hasta 500 caracteres.")
        @Pattern(
            regexp = VideoLink.PATRON,
            message =
                "VAL-006: El enlace del video debe ser un video de YouTube o de Vimeo, sin espacios y de"
                    + " hasta 500 caracteres.")
        String introVideoUrl,
    @NotNull(
            message =
                "VAL-004: El orden es obligatorio y debe ser un entero mayor o igual que cero.")
        @PositiveOrZero(
            message =
                "VAL-004: El orden es obligatorio y debe ser un entero mayor o igual que cero.")
        Integer displayOrder,
    // Desde el 25-09-2026 (`RF-AC-008` 0.4.0, `ac.md` §5.2.9): las categorías en
    // que el curso nace. Los nulos se rechazan aquí, junto con los demás; las
    // repetidas, en el caso de uso, como `RF-MV-001` con las líneas.
    List<
            @NotNull(
                message =
                    "VAL-008: La lista de categorías no puede traer identificadores repetidos ni"
                        + " vacíos.")
            UUID>
        categoryIds,
    // Desde la segunda decisión del mismo día (`RF-AC-008` 0.6.0): quién lo puede
    // ver, por servicio (`RN-AC-020`) y por membresía (`RN-AC-012`).
    List<
            @NotNull(
                message =
                    "VAL-008: La lista de servicios no puede traer identificadores repetidos ni"
                        + " vacíos.")
            UUID>
        productIds,
    List<
            @NotNull(
                message =
                    "VAL-008: La lista de membresías no puede traer identificadores repetidos ni"
                        + " vacíos.")
            UUID>
        membershipIds) {

  public RegisterCourseRequest {
    categoryIds = categoryIds == null ? List.of() : categoryIds;
    productIds = productIds == null ? List.of() : productIds;
    membershipIds = membershipIds == null ? List.of() : membershipIds;
    title = title == null ? null : title.trim();
    shortDescription = recortar(shortDescription);
    longDescription = recortar(longDescription);
    // Un video en blanco es «no tiene», no un enlace mal formado.
    introVideoUrl = recortar(introVideoUrl);
  }

  private static String recortar(String valor) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.trim();
    return recortado.isEmpty() ? null : recortado;
  }
}
