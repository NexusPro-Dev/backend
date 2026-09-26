package com.factech.nexus.modules.academy.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `StudentAccess`: los casos de `RF-AC-033` · plan §11. */
class StudentAccessTest {

  private static final UUID ORO = UUID.randomUUID();
  private static final UUID PLATINO = UUID.randomUUID();
  private static final UUID BOT = UUID.randomUUID();

  @Test
  @DisplayName("un curso sin llaves es de todos, con o sin membresía vigente")
  void sinLlaves() {
    assertThat(StudentAccess.courseAccessible(null, Set.of(), List.of(), List.of())).isTrue();
    assertThat(StudentAccess.courseAccessible(ORO, Set.of(BOT), List.of(), List.of())).isTrue();
  }

  @Test
  @DisplayName("la membresía abre por pertenencia y no por nivel")
  void membresia() {
    assertThat(StudentAccess.courseAccessible(ORO, Set.of(), List.of(ORO), List.of())).isTrue();
    assertThat(StudentAccess.courseAccessible(PLATINO, Set.of(), List.of(ORO), List.of()))
        .isFalse();
    assertThat(StudentAccess.courseAccessible(null, Set.of(), List.of(ORO), List.of())).isFalse();
  }

  @Test
  @DisplayName("un servicio vigente de la lista abre; uno ajeno no")
  void servicio() {
    assertThat(StudentAccess.courseAccessible(null, Set.of(BOT), List.of(ORO), List.of(BOT)))
        .isTrue();
    assertThat(
            StudentAccess.courseAccessible(
                null, Set.of(UUID.randomUUID()), List.of(), List.of(BOT)))
        .isFalse();
  }

  @Test
  @DisplayName("la lección se abre si el curso se abre o si está abierta")
  void leccion() {
    assertThat(StudentAccess.lessonAccessible(true, false)).isTrue();
    assertThat(StudentAccess.lessonAccessible(false, true)).isTrue();
    assertThat(StudentAccess.lessonAccessible(false, false)).isFalse();
  }
}
