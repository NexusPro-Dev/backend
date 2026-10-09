package com.factech.nexus.modules.academy.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-AC-021` y `RN-AC-023`: acotar, el 90 %, el crédito y el porcentaje (`RF-AC-039` · `T-02`).
 */
class LessonProgressTest {

  @Test
  @DisplayName("la posición se acota a la duración y nunca baja de cero")
  void acotar() {
    assertThat(LessonProgress.acotar(120, 300)).isEqualTo(120);
    assertThat(LessonProgress.acotar(999, 300)).isEqualTo(300);
    assertThat(LessonProgress.acotar(-5, 300)).isZero();
  }

  @Test
  @DisplayName("el 90 % completa y el 89 % no; el umbral se redondea hacia arriba")
  void umbral() {
    assertThat(LessonProgress.umbral(300)).isEqualTo(270);
    assertThat(LessonProgress.completa(270, 300)).isTrue();
    assertThat(LessonProgress.completa(269, 300)).isFalse();
    // 90 % de 61 es 54,9: hacen falta 55.
    assertThat(LessonProgress.umbral(61)).isEqualTo(55);
    assertThat(LessonProgress.completa(54, 61)).isFalse();
  }

  @Test
  @DisplayName("una completada vale su duración entera aunque lo visto sea menos")
  void credito() {
    assertThat(LessonProgress.credito(100, 300, false)).isEqualTo(100);
    assertThat(LessonProgress.credito(400, 300, false)).isEqualTo(300);
    assertThat(LessonProgress.credito(270, 500, true)).isEqualTo(500);
  }

  @Test
  @DisplayName("el porcentaje es entero, hacia abajo, con tope 100 y cero sin nada que ver")
  void porcentaje() {
    assertThat(LessonProgress.porcentaje(2, 3)).isEqualTo(66);
    assertThat(LessonProgress.porcentaje(500, 300)).isEqualTo(100);
    assertThat(LessonProgress.porcentaje(10, 0)).isZero();
    assertThat(LessonProgress.porcentajeDeLeccion(270, 300, true)).isEqualTo(100);
    assertThat(LessonProgress.porcentajeDeLeccion(150, 300, false)).isEqualTo(50);
  }
}
