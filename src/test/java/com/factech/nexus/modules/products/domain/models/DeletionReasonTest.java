package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.shared.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El motivo del retiro (`RF-PM-006` · `T-01`).
 *
 * <p>Sin Spring: lo que se comprueba es que <b>un motivo vacío no pueda existir</b>, venga por
 * donde venga. Con un {@code String} suelto llegaría hasta el registro de eliminación y quedaría
 * ahí para siempre como una fila que no explica nada.
 */
class DeletionReasonTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t", "\n"})
  @DisplayName("`VAL-002` — el nulo, el vacío y el de solo espacios se rechazan igual")
  void rechazaElVacio(String valor) {
    ValidationException fallo =
        catchThrowableOfType(() -> new DeletionReason(valor), ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-002");
    assertThat(fallo.errors())
        .singleElement()
        .satisfies(error -> assertThat(error.field()).isEqualTo("reason"));
  }

  @Test
  @DisplayName("el motivo se recorta: los espacios de los bordes no son parte de lo escrito")
  void recorta() {
    assertThat(new DeletionReason("  Se descontinuó la línea.  ").value())
        .isEqualTo("Se descontinuó la línea.");
  }

  @Test
  @DisplayName("`VAL-003` — el motivo demasiado largo se RECHAZA, no se recorta")
  void rechazaElLargoSinRecortarlo() {
    String largo = "x".repeat(501);

    ValidationException fallo =
        catchThrowableOfType(() -> new DeletionReason(largo), ValidationException.class);

    // Un motivo truncado dice algo distinto de lo que se escribió, y lo dice
    // sin avisar a nadie: el registro quedaría con media frase que parece
    // completa.
    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-003");
  }

  @Test
  @DisplayName("quinientos caracteres exactos entran: el borde es admitido")
  void elBordeEntra() {
    assertThat(new DeletionReason("x".repeat(500)).value()).hasSize(500);
  }

  @Test
  @DisplayName("un motivo de un solo carácter basta, y es deliberado")
  void unSoloCaracterBasta() {
    // La restricción del esquema exige CONTENIDO, no longitud. Se decidió no
    // elevar el mínimo para no imponer fricción a quien sí redacta un motivo
    // útil (`architecture.md` §6.6.3).
    assertThat(new DeletionReason("x").value()).isEqualTo("x");
  }
}
