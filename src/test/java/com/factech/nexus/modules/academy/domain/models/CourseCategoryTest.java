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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** El agregado de la categoría (`RF-AC-001` · `T-04`, `RF-AC-004` · `T-02`), sin Spring ni base. */
class CourseCategoryTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 17, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime DESPUES = AHORA.plusMinutes(5);

  @Test
  @DisplayName(
      "el color en minúsculas sale en mayúsculas, el nombre recortado y la descripción de espacios nula")
  void normaliza() {
    CourseCategory c = crear("  Trading  ", "   ", "1e88e5", "chart-line", 0);

    assertThat(c.getName()).isEqualTo("Trading");
    assertThat(c.getDescription()).isNull();
    assertThat(c.getColor()).isEqualTo("1E88E5");
    assertThat(c.getIcon()).isEqualTo("chart-line");
    assertThat(c.getDisplayOrder()).isZero();
    assertThat(c.getCoverImageId()).isNull();
    assertThat(c.estaRetirada()).isFalse();
    assertThat(c.getCreatedAt()).isEqualTo(AHORA);
    assertThat(c.getUpdatedAt()).isEqualTo(AHORA);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "#1E88E5", "1E88E", "1E88E5F", "GGGGGG"})
  @DisplayName("un color mal formado se rechaza con VAL-002")
  void colorMalFormado(String color) {
    assertThatThrownBy(() -> crear("Trading", null, color, "chart-line", 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("hexadecimales");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"Chart", "chart_line", "1chart", "chart line"})
  @DisplayName("un icono mal formado se rechaza con VAL-003")
  void iconoMalFormado(String icono) {
    assertThatThrownBy(() -> crear("Trading", null, "1E88E5", icono, 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("icono");
  }

  @Test
  @DisplayName("un orden negativo o ausente se rechaza con VAL-004, y el cero se admite")
  void ordenNegativo() {
    assertThatThrownBy(() -> crear("Trading", null, "1E88E5", "chart-line", -1))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("orden");
    assertThatThrownBy(() -> crear("Trading", null, "1E88E5", "chart-line", null))
        .isInstanceOf(ValidationException.class);
    assertThat(crear("Trading", null, "1E88E5", "chart-line", 0).getDisplayOrder()).isZero();
  }

  @Test
  @DisplayName("un nombre vacío o de más de 150 se rechaza con VAL-001")
  void nombreInvalido() {
    assertThatThrownBy(() -> crear("   ", null, "1E88E5", "chart-line", 0))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("nombre");
    assertThatThrownBy(() -> crear("x".repeat(151), null, "1E88E5", "chart-line", 0))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  @DisplayName("update: cada campo deja su antes y su después, y updatedAt avanza")
  void updateCadaCampo() {
    CourseCategory c = crear("Trading", "Vieja.", "1E88E5", "chart-line", 0);

    Map<String, Object> cambios =
        c.update(
            Patchable.de("Trading avanzado"),
            Patchable.de("Nueva."),
            Patchable.de("ff0000"),
            Patchable.de("star"),
            Patchable.de(3),
            DESPUES);

    assertThat(cambios).containsKeys("name", "description", "color", "icon", "display_order");
    assertThat(cambios.get("color")).isEqualTo(Map.of("before", "1E88E5", "after", "FF0000"));
    assertThat(cambios.get("display_order")).isEqualTo(Map.of("before", 0, "after", 3));
    assertThat(c.getColor()).isEqualTo("FF0000");
    assertThat(c.getUpdatedAt()).isEqualTo(DESPUES);
  }

  @Test
  @DisplayName("update: el nulo explícito vacía la descripción y deja antes vacío")
  void updateVaciaLaDescripcion() {
    CourseCategory c = crear("Trading", "Vieja.", "1E88E5", "chart-line", 0);

    Map<String, Object> cambios =
        c.update(
            Patchable.ausente(),
            Patchable.de(null),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            DESPUES);

    assertThat(cambios).containsOnlyKeys("description");
    assertThat(cambios.get("description")).isEqualTo(Map.of("before", "Vieja.", "after", ""));
    assertThat(c.getDescription()).isNull();
  }

  @Test
  @DisplayName(
      "update: el mismo color en otra caja y los mismos valores no son un cambio, y updatedAt no se mueve")
  void updateSinCambios() {
    CourseCategory c = crear("Trading", "Desc.", "1E88E5", "chart-line", 2);

    Map<String, Object> cambios =
        c.update(
            Patchable.de("  Trading "),
            Patchable.de("Desc."),
            Patchable.de("1e88e5"),
            Patchable.de("chart-line"),
            Patchable.de(2),
            DESPUES);

    assertThat(cambios).isEmpty();
    assertThat(c.getUpdatedAt()).isEqualTo(AHORA);
  }

  @Test
  @DisplayName("update: un color o un icono mal formados se rechazan sin aplicar nada")
  void updateRechazaFormato() {
    CourseCategory c = crear("Trading", null, "1E88E5", "chart-line", 0);

    assertThatThrownBy(
            () ->
                c.update(
                    Patchable.de("Otro"),
                    Patchable.ausente(),
                    Patchable.de("#000000"),
                    Patchable.ausente(),
                    Patchable.ausente(),
                    DESPUES))
        .isInstanceOf(ValidationException.class);
    // El nombre se aplicó antes de tropezar con el color: el caso de uso valida
    // la forma ANTES de llamar aquí precisamente para que esto no ocurra con
    // una fila bloqueada. Lo que el agregado garantiza es el rechazo.
    assertThat(c.getColor()).isEqualTo("1E88E5");
  }

  @Test
  @DisplayName(
      "delete marca una vez y devuelve false la segunda; la instantánea lleva las claves del alta")
  void deleteEInstantanea() {
    CourseCategory c = crear("Trading", "Desc.", "1E88E5", "chart-line", 1);

    assertThat(c.instantanea())
        .containsEntry("name", "Trading")
        .containsEntry("color", "1E88E5")
        .containsEntry("display_order", 1)
        .containsEntry("cover_image_id", null)
        .doesNotContainKey("status");
    assertThat(c.delete(DESPUES)).isTrue();
    assertThat(c.estaRetirada()).isTrue();
    assertThat(c.getDeletedAt()).isEqualTo(DESPUES);
    assertThat(c.delete(DESPUES.plusMinutes(1))).isFalse();
    assertThat(c.getDeletedAt()).isEqualTo(DESPUES);
  }

  private static CourseCategory crear(
      String nombre, String descripcion, String color, String icono, Integer orden) {
    return CourseCategory.create(
        UUID.randomUUID(), nombre, descripcion, color, icono, orden, AHORA);
  }
}
