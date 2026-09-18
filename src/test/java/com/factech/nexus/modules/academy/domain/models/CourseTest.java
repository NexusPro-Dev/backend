package com.factech.nexus.modules.academy.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** El agregado del curso (`RF-AC-008` · `T-05`, `RF-AC-011` · `T-02`, `RF-AC-012`), sin Spring. */
class CourseTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 18, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime DESPUES = AHORA.plusMinutes(5);
  private static final UUID INSTRUCTOR = UUID.randomUUID();

  @Test
  @DisplayName(
      "nace INACTIVO, sin portada, con el título recortado y las descripciones de espacios nulas")
  void nace() {
    Course c = crear("  Velas japonesas  ", "   ", "  ", null, 0);

    assertThat(c.getTitle()).isEqualTo("Velas japonesas");
    assertThat(c.getInstructorId()).isEqualTo(INSTRUCTOR);
    assertThat(c.getDifficulty()).isEqualTo(CourseDifficulty.PRINCIPIANTE);
    assertThat(c.getShortDescription()).isNull();
    assertThat(c.getLongDescription()).isNull();
    assertThat(c.getIntroVideoUrl()).isNull();
    assertThat(c.getStatus()).isEqualTo(CourseStatus.INACTIVO);
    assertThat(c.getCoverImageId()).isNull();
    assertThat(c.estaRetirado()).isFalse();
    assertThat(c.tieneDescripcionCorta()).isFalse();
    assertThat(c.getCreatedAt()).isEqualTo(AHORA);
    assertThat(c.getUpdatedAt()).isEqualTo(AHORA);
  }

  @Test
  @DisplayName("un título vacío o de más de 150 se rechaza con VAL-001")
  void tituloMalFormado() {
    assertThatThrownBy(() -> crear("   ", null, null, null, 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("título");
    assertThatThrownBy(() -> crear("x".repeat(151), null, null, null, 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("150");
  }

  @Test
  @DisplayName("instructor y dificultad nulos, y orden negativo, se rechazan")
  void obligatorios() {
    assertThatThrownBy(
            () ->
                Course.create(
                    UUID.randomUUID(),
                    "T",
                    null,
                    CourseDifficulty.AVANZADO,
                    null,
                    null,
                    null,
                    0,
                    AHORA))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("instructor");
    assertThatThrownBy(
            () ->
                Course.create(UUID.randomUUID(), "T", INSTRUCTOR, null, null, null, null, 0, AHORA))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("dificultad");
    assertThatThrownBy(() -> crear("T", null, null, null, -1))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("orden");
  }

  @Test
  @DisplayName("las descripciones se acotan a 300 y 10 000")
  void descripcionesLargas() {
    assertThatThrownBy(() -> crear("T", "x".repeat(301), null, null, 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("300");
    assertThatThrownBy(() -> crear("T", null, "x".repeat(10_001), null, 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("10 000");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://x", "www.x.com", "https://x y", "http://"})
  @DisplayName("un video mal formado se rechaza con VAL-006, y uno bien formado se guarda tal cual")
  void video(String enlace) {
    assertThatThrownBy(() -> crear("T", null, null, enlace, 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("http");
    assertThat(crear("T", null, null, " https://v.io/1 ", 0).getIntroVideoUrl())
        .isEqualTo("https://v.io/1");
  }

  @Test
  @DisplayName("la corrección devuelve solo lo que cambió, con antes y después, y avanza updatedAt")
  void corrige() {
    Course c = crear("Velas", "Corta", "Larga", "https://v.io/1", 0);
    UUID otro = UUID.randomUUID();

    Map<String, Object> cambios =
        c.update(
            Patchable.de("Velas japonesas"),
            Patchable.de(otro),
            Patchable.de(CourseDifficulty.INTERMEDIO),
            Patchable.de("Corta"),
            Patchable.de(null),
            Patchable.ausente(),
            Patchable.de(3),
            DESPUES);

    assertThat(cambios.keySet())
        .containsExactly(
            "title", "instructor_id", "difficulty", "long_description", "display_order");
    assertThat(cambios.get("instructor_id"))
        .isEqualTo(Map.of("before", INSTRUCTOR.toString(), "after", otro.toString()));
    assertThat(cambios.get("long_description")).isEqualTo(Map.of("before", "Larga", "after", ""));
    assertThat(c.getLongDescription()).isNull();
    assertThat(c.getShortDescription()).isEqualTo("Corta");
    assertThat(c.getIntroVideoUrl()).isEqualTo("https://v.io/1");
    assertThat(c.getUpdatedAt()).isEqualTo(DESPUES);
  }

  @Test
  @DisplayName("sin cambios de valor no hay diff ni avanza updatedAt")
  void sinCambios() {
    Course c = crear("Velas", "Corta", null, null, 0);
    Map<String, Object> cambios =
        c.update(
            Patchable.de("  Velas "),
            Patchable.de(INSTRUCTOR),
            Patchable.de(CourseDifficulty.PRINCIPIANTE),
            Patchable.de("Corta "),
            Patchable.de(null),
            Patchable.de("   "),
            Patchable.de(0),
            DESPUES);
    assertThat(cambios).isEmpty();
    assertThat(c.getUpdatedAt()).isEqualTo(AHORA);
  }

  @Test
  @DisplayName("activar y desactivar devuelven si hubo cambio; vaciar después no toca el estado")
  void estado() {
    Course c = crear("Velas", "Corta", "Larga", null, 0);
    assertThat(c.deactivate(DESPUES)).isFalse();
    assertThat(c.activate(DESPUES)).isTrue();
    assertThat(c.getStatus()).isEqualTo(CourseStatus.ACTIVO);
    assertThat(c.activate(DESPUES)).isFalse();

    c.update(
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.de(null),
        Patchable.ausente(),
        Patchable.ausente(),
        Patchable.ausente(),
        DESPUES);
    assertThat(c.getStatus()).isEqualTo(CourseStatus.ACTIVO);
    assertThat(c.tieneDescripcionCorta()).isFalse();

    assertThat(c.deactivate(DESPUES)).isTrue();
    assertThat(c.getStatus()).isEqualTo(CourseStatus.INACTIVO);
  }

  @Test
  @DisplayName("retirar marca la fila sin tocar el estado, y la instantánea lleva instructor_id")
  void retira() {
    Course c = crear("Velas", null, null, null, 0);
    c.activate(AHORA);
    assertThat(c.delete(DESPUES)).isTrue();
    assertThat(c.delete(DESPUES)).isFalse();
    assertThat(c.estaRetirado()).isTrue();
    assertThat(c.getStatus()).isEqualTo(CourseStatus.ACTIVO);
    assertThat(c.instantanea())
        .containsEntry("instructor_id", INSTRUCTOR.toString())
        .containsEntry("status", "ACTIVO")
        .containsEntry("cover_image_id", null)
        .doesNotContainKey("code");
  }

  private static Course crear(String titulo, String corta, String larga, String video, int orden) {
    return Course.create(
        UUID.randomUUID(),
        titulo,
        INSTRUCTOR,
        CourseDifficulty.PRINCIPIANTE,
        corta,
        larga,
        video,
        orden,
        AHORA);
  }
}
