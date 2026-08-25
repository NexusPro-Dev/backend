package com.factech.nexus.modules.system.users.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El motivo de un cambio administrativo (`RF-SP-041` · `T-03`). */
class ChangeReasonTest {

  @Test
  @DisplayName("recorta, y un motivo de SOLO ESPACIOS no construye")
  void soloEspacios() {
    // Es la razón de que este tipo exista. Con un String suelto, un motivo de
    // espacios llega hasta la fila de auditoría y queda ahí para siempre como un
    // registro que no explica nada.
    assertThatThrownBy(() -> new ChangeReason("   ")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new ChangeReason("")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new ChangeReason(null)).isInstanceOf(ValidationException.class);
  }

  @Test
  @DisplayName("un motivo de UNA palabra es válido: la regla es que tenga contenido, no longitud")
  void unaPalabra() {
    assertThat(new ChangeReason("  Reorganización  ").value()).isEqualTo("Reorganización");
  }

  @Test
  @DisplayName("el motivo desmesurado se rechaza: la auditoría no es un campo de texto libre")
  void demasiadoLargo() {
    assertThatThrownBy(() -> new ChangeReason("x".repeat(501)))
        .isInstanceOf(ValidationException.class);
    assertThat(new ChangeReason("x".repeat(500)).value()).hasSize(500);
  }

  @Test
  @DisplayName("el error es VAL-008 y señala el campo `reason`")
  void codigoYCampo() {
    assertThatThrownBy(() -> new ChangeReason(" "))
        .isInstanceOfSatisfying(
            ValidationException.class,
            fallo -> {
              assertThat(fallo.errorCode()).isEqualTo("VAL-008");
              assertThat(fallo.errors())
                  .singleElement()
                  .satisfies(error -> assertThat(error.field()).isEqualTo("reason"));
            });
  }
}
