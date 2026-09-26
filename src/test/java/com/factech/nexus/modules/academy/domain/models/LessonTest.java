package com.factech.nexus.modules.academy.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La lección y su contenido (`RF-AC-028` · `T-02`, `RF-AC-029` · `T-02`, `RF-AC-030` · `T-01`), sin
 * Spring: el tipo manda, la pareja resultante se valida antes de aplicar nada, y la auditoría lleva
 * la longitud y no el texto.
 */
class LessonTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 18, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime DESPUES = AHORA.plusMinutes(5);
  private static final UUID MODULO = UUID.randomUUID();
  private static final String MARKDOWN = "# Título\n\nUn párrafo con <script>alert(1)</script>.\n";

  @Test
  @DisplayName("nace INACTIVA, open falsa si no vino, y el Markdown se guarda tal cual")
  void nace() {
    Lesson l = crear(LessonType.TEXTO, MARKDOWN, null);
    assertThat(l.getStatus()).isEqualTo(CourseStatus.INACTIVO);
    assertThat(l.isOpen()).isFalse();
    assertThat(l.getContent()).isEqualTo(MARKDOWN.strip());
    assertThat(l.tieneContenido()).isTrue();
    assertThat(crear(LessonType.TEXTO, "   ", true).getContent()).isNull();
    assertThat(crear(LessonType.TEXTO, null, true).isOpen()).isTrue();
  }

  @Test
  @DisplayName(
      "LessonContent: un VIDEO exige URL con el mensaje propio; un TEXTO admite cualquier cosa")
  void elTipoManda() {
    assertThatThrownBy(() -> crear(LessonType.VIDEO, "no es url", null))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("lección de video");
    assertThat(crear(LessonType.VIDEO, " https://vimeo.com/100000001 ", null).getContent())
        .isEqualTo("https://vimeo.com/100000001");
    assertThat(crear(LessonType.TEXTO, "https://vimeo.com/100000001", null).getContent())
        .isEqualTo("https://vimeo.com/100000001");
    assertThat(LessonContent.de(LessonType.TEXTO, null, "VAL-006")).isNull();
  }

  @Test
  @DisplayName("tipo nulo, título vacío, duración cero y orden negativo se rechazan")
  void obligatorios() {
    assertThatThrownBy(
            () ->
                Lesson.create(UUID.randomUUID(), MODULO, null, "T", null, null, 1, 0, null, AHORA))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("tipo");
    assertThatThrownBy(
            () ->
                Lesson.create(
                    UUID.randomUUID(),
                    MODULO,
                    LessonType.TEXTO,
                    " ",
                    null,
                    null,
                    1,
                    0,
                    null,
                    AHORA))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("título");
    assertThatThrownBy(
            () ->
                Lesson.create(
                    UUID.randomUUID(),
                    MODULO,
                    LessonType.TEXTO,
                    "T",
                    null,
                    null,
                    0,
                    0,
                    null,
                    AHORA))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("duración");
    assertThatThrownBy(
            () ->
                Lesson.create(
                    UUID.randomUUID(),
                    MODULO,
                    LessonType.TEXTO,
                    "T",
                    null,
                    null,
                    1,
                    -1,
                    null,
                    AHORA))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("orden");
  }

  @Test
  @DisplayName(
      "la pareja resultante: pasar a VIDEO con un texto guardado y sin URL se rechaza sin aplicar nada")
  void parejaResultante() {
    Lesson l = crear(LessonType.TEXTO, MARKDOWN, null);
    assertThatThrownBy(
            () ->
                l.update(
                    Patchable.de(LessonType.VIDEO),
                    Patchable.de("Otro título"),
                    Patchable.ausente(),
                    Patchable.ausente(),
                    Patchable.ausente(),
                    Patchable.ausente(),
                    Patchable.ausente(),
                    DESPUES))
        .isInstanceOf(ValidationException.class);
    assertThat(l.getType()).isEqualTo(LessonType.TEXTO);
    assertThat(l.getTitle()).isEqualTo("Título");
    assertThat(l.getUpdatedAt()).isEqualTo(AHORA);

    // Con la URL en la misma petición se admite.
    Map<String, Object> cambios =
        l.update(
            Patchable.de(LessonType.VIDEO),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.de("https://vimeo.com/100000001"),
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.ausente(),
            DESPUES);
    assertThat(cambios).containsKeys("type", "content_length");
    assertThat(cambios.get("content_length"))
        .isEqualTo(Map.of("before", MARKDOWN.strip().length(), "after", 27));
    assertThat(l.getContent()).isEqualTo("https://vimeo.com/100000001");

    // Pasar a TEXTO con una URL guardada se admite: una URL es un texto.
    assertThat(
            l.update(
                Patchable.de(LessonType.TEXTO),
                Patchable.ausente(),
                Patchable.ausente(),
                Patchable.ausente(),
                Patchable.ausente(),
                Patchable.ausente(),
                Patchable.ausente(),
                DESPUES))
        .containsOnlyKeys("type");
  }

  @Test
  @DisplayName(
      "el nulo vacía descripción y contenido; open y duración se auditan; sin cambios no hay diff")
  void corrige() {
    Lesson l = crear(LessonType.TEXTO, MARKDOWN, null);
    Map<String, Object> cambios =
        l.update(
            Patchable.ausente(),
            Patchable.ausente(),
            Patchable.de(null),
            Patchable.de(null),
            Patchable.de(30),
            Patchable.de(2),
            Patchable.de(true),
            DESPUES);
    assertThat(cambios.keySet())
        .containsExactly("content_length", "duration_seconds", "display_order", "open");
    assertThat(l.getContent()).isNull();
    assertThat(l.isOpen()).isTrue();
    assertThat(
            l.update(
                Patchable.de(LessonType.TEXTO),
                Patchable.de(" Título "),
                Patchable.ausente(),
                Patchable.ausente(),
                Patchable.de(30),
                Patchable.de(2),
                Patchable.de(true),
                DESPUES))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "activar exige contenido con el 409 de RF-AC-030; desactivar no; retirar no toca el estado")
  void estadoYRetiro() {
    Lesson vacia = crear(LessonType.TEXTO, null, null);
    assertThatThrownBy(() -> vacia.activate(DESPUES))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("contenido");
    assertThat(vacia.getStatus()).isEqualTo(CourseStatus.INACTIVO);

    Lesson llena = crear(LessonType.TEXTO, MARKDOWN, null);
    assertThat(llena.activate(DESPUES)).isTrue();
    assertThat(llena.activate(DESPUES)).isFalse();
    assertThat(llena.deactivate(DESPUES)).isTrue();
    assertThat(llena.activate(DESPUES)).isTrue();
    assertThat(llena.delete(DESPUES)).isTrue();
    assertThat(llena.delete(DESPUES)).isFalse();
    assertThat(llena.getStatus()).isEqualTo(CourseStatus.ACTIVO);
  }

  @Test
  @DisplayName("la instantánea lleva content_length y no el texto; la completa lleva el texto")
  void instantaneas() {
    Lesson l = crear(LessonType.TEXTO, MARKDOWN, null);
    assertThat(l.instantanea())
        .containsEntry("content_length", MARKDOWN.strip().length())
        .containsEntry("module_id", MODULO.toString())
        .doesNotContainKey("content");
    assertThat(l.instantaneaCompleta()).containsEntry("content", MARKDOWN.strip());
  }

  private static Lesson crear(LessonType tipo, String contenido, Boolean abierta) {
    return Lesson.create(
        UUID.randomUUID(), MODULO, tipo, " Título ", null, contenido, 12, 0, abierta, AHORA);
  }
}
