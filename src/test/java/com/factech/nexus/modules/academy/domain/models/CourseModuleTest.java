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

/**
 * El módulo y la ofrecibilidad del módulo (`RF-AC-022` · `T-02`, `RF-AC-023` · `T-02`), sin Spring.
 */
class CourseModuleTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 18, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime DESPUES = AHORA.plusMinutes(5);
  private static final UUID CURSO = UUID.randomUUID();

  @Test
  @DisplayName("nace INACTIVO dentro de su curso, recortado y con descripciones vacías nulas")
  void nace() {
    CourseModule m =
        CourseModule.create(
            UUID.randomUUID(),
            CURSO,
            " Fundamentos ",
            "  ",
            null,
            " https://vimeo.com/100000003 ",
            0,
            AHORA);
    assertThat(m.getCourseId()).isEqualTo(CURSO);
    assertThat(m.getTitle()).isEqualTo("Fundamentos");
    assertThat(m.getShortDescription()).isNull();
    assertThat(m.getPresentationVideoUrl()).isEqualTo("https://vimeo.com/100000003");
    assertThat(m.getStatus()).isEqualTo(CourseStatus.INACTIVO);
    assertThat(m.estaRetirado()).isFalse();
  }

  @Test
  @DisplayName("título vacío, orden negativo y video mal formado se rechazan")
  void rechazos() {
    assertThatThrownBy(
            () -> CourseModule.create(UUID.randomUUID(), CURSO, " ", null, null, null, 0, AHORA))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(
            () -> CourseModule.create(UUID.randomUUID(), CURSO, "T", null, null, null, -1, AHORA))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(
            () ->
                CourseModule.create(UUID.randomUUID(), CURSO, "T", null, null, "ftp://x", 0, AHORA))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  @DisplayName(
      "la corrección devuelve solo lo que cambió, el nulo vacía descripciones y video, y activar/desactivar/retirar responden si hubo cambio")
  void corrigeYCambiaDeEstado() {
    CourseModule m =
        CourseModule.create(
            UUID.randomUUID(),
            CURSO,
            "Fundamentos",
            "Corta",
            "Larga",
            "https://vimeo.com/100000003",
            0,
            AHORA);
    Map<String, Object> cambios =
        m.update(
            Patchable.de(" Fundamentos "),
            Patchable.de(null),
            Patchable.ausente(),
            Patchable.de(null),
            Patchable.de(3),
            DESPUES);
    assertThat(cambios.keySet())
        .containsExactly("short_description", "presentation_video_url", "display_order");
    assertThat(m.getLongDescription()).isEqualTo("Larga");
    assertThat(m.getUpdatedAt()).isEqualTo(DESPUES);

    assertThat(m.deactivate(DESPUES)).isFalse();
    assertThat(m.activate(DESPUES)).isTrue();
    assertThat(m.activate(DESPUES)).isFalse();
    assertThat(m.delete(DESPUES)).isTrue();
    assertThat(m.delete(DESPUES)).isFalse();
    assertThat(m.getStatus()).isEqualTo(CourseStatus.ACTIVO);
    assertThat(m.instantanea())
        .containsEntry("course_id", CURSO.toString())
        .containsEntry("status", "ACTIVO");
  }

  @Test
  @DisplayName(
      "ModuleOfferability: retirado, inactivo, sin lección activa con contenido, y el ofrecible; LessonOfferability exige contenido")
  void ofrecibilidad() {
    assertThat(ModuleOfferability.decidir(true, "ACTIVO", 1).reason())
        .isEqualTo(ModuleOfferability.RETIRADO);
    assertThat(ModuleOfferability.decidir(false, "INACTIVO", 1).reason())
        .isEqualTo(ModuleOfferability.INACTIVO);
    assertThat(ModuleOfferability.decidir(false, "ACTIVO", 0).reason())
        .isEqualTo(ModuleOfferability.SIN_LECCION);
    assertThat(ModuleOfferability.decidir(false, "ACTIVO", 1).offerable()).isTrue();
    assertThat(ModuleOfferability.decidir(true, "INACTIVO", 0).reason())
        .isEqualTo(ModuleOfferability.RETIRADO);

    assertThat(LessonOfferability.offered("ACTIVO", false, true)).isTrue();
    assertThat(LessonOfferability.offered("ACTIVO", false, false)).isFalse();
    assertThat(LessonOfferability.offered("INACTIVO", false, true)).isFalse();
    assertThat(LessonOfferability.offered("ACTIVO", true, true)).isFalse();
  }
}
