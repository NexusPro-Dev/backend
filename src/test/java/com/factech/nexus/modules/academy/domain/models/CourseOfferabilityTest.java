package com.factech.nexus.modules.academy.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-AC-015` en un solo sitio (`RF-AC-008` · `T-06`): los cinco motivos por separado, el orden
 * cuando fallan varios, y el ofrecible.
 */
class CourseOfferabilityTest {

  @Test
  @DisplayName("un curso vivo, activo, con descripciones, membresías y módulo ofrecible se ofrece")
  void seOfrece() {
    CourseOfferability.Resultado r =
        CourseOfferability.decidir(false, CourseStatus.ACTIVO, true, true, 1, 1);
    assertThat(r.offerable()).isTrue();
    assertThat(r.reason()).isNull();
  }

  @Test
  @DisplayName("cada motivo por separado, con su mensaje")
  void cadaMotivo() {
    assertThat(CourseOfferability.decidir(true, CourseStatus.ACTIVO, true, true, 1, 1).reason())
        .isEqualTo(CourseOfferability.RETIRADO);
    assertThat(CourseOfferability.decidir(false, CourseStatus.INACTIVO, true, true, 1, 1).reason())
        .isEqualTo(CourseOfferability.INACTIVO);
    assertThat(CourseOfferability.decidir(false, CourseStatus.ACTIVO, false, true, 1, 1).reason())
        .isEqualTo(CourseOfferability.SIN_DESCRIPCION);
    assertThat(CourseOfferability.decidir(false, CourseStatus.ACTIVO, true, false, 1, 1).reason())
        .isEqualTo(CourseOfferability.SIN_DESCRIPCION);
    assertThat(CourseOfferability.decidir(false, CourseStatus.ACTIVO, true, true, 0, 1).reason())
        .isEqualTo(CourseOfferability.SIN_MEMBRESIAS);
    assertThat(CourseOfferability.decidir(false, CourseStatus.ACTIVO, true, true, 1, 0).reason())
        .isEqualTo(CourseOfferability.SIN_MODULO);
  }

  @Test
  @DisplayName(
      "cuando fallan varios gana el primero del orden: retirado, inactivo, descripción, membresías, módulo")
  void elOrden() {
    assertThat(CourseOfferability.decidir(true, CourseStatus.INACTIVO, false, false, 0, 0).reason())
        .isEqualTo(CourseOfferability.RETIRADO);
    assertThat(
            CourseOfferability.decidir(false, CourseStatus.INACTIVO, false, false, 0, 0).reason())
        .isEqualTo(CourseOfferability.INACTIVO);
    assertThat(CourseOfferability.decidir(false, CourseStatus.ACTIVO, false, false, 0, 0).reason())
        .isEqualTo(CourseOfferability.SIN_DESCRIPCION);
    assertThat(CourseOfferability.decidir(false, CourseStatus.ACTIVO, true, true, 0, 0).reason())
        .isEqualTo(CourseOfferability.SIN_MEMBRESIAS);
  }

  @Test
  @DisplayName("acepta el estado como cadena, tal como llega de una proyección")
  void comoCadena() {
    assertThat(CourseOfferability.decidir(false, "INACTIVO", true, true, 1, 1).reason())
        .isEqualTo(CourseOfferability.INACTIVO);
  }
}
