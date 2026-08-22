package com.factech.nexus.modules.system.roles.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** `RF-SP-001` · `T-10` — el objeto de valor del código de rol. */
class RoleCodeTest {

  @ParameterizedTest
  @ValueSource(strings = {"ADMIN", "CONTABILIDAD", "LIDER_ACADEMICO", "A", "A1", "ROL_2026"})
  @DisplayName("acepta mayúsculas, dígitos y guion bajo empezando por letra")
  void formatosValidos(String valor) {
    assertThat(new RoleCode(valor).value()).isEqualTo(valor);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "admin", // minúsculas
        "Admin", // capitalizado
        "ROL-NUEVO", // guion medio
        "ROL NUEVO", // espacio
        "1ROL", // empieza por dígito
        "_ROL", // empieza por guion bajo
        "ROL.NUEVO", // punto
        "ROLÉ" // acento
      })
  @DisplayName("rechaza todo lo demás con VAL-008")
  void formatosInvalidos(String valor) {
    assertThatThrownBy(() -> new RoleCode(valor))
        .isInstanceOf(ValidationException.class)
        .extracting(fallo -> ((ValidationException) fallo).errorCode())
        .isEqualTo("VAL-008");
  }

  @Test
  @DisplayName("NO normaliza: un código en minúsculas se rechaza, no se convierte")
  void noNormaliza() {
    // `spec.md` §13: el actor debe ver exactamente qué código quedó
    // registrado. Si esta prueba empezara a pasar devolviendo "CONTABILIDAD",
    // alguien habría añadido un toUpperCase() y el criterio se habría perdido.
    assertThatThrownBy(() -> new RoleCode("contabilidad")).isInstanceOf(ValidationException.class);
  }

  @Test
  @DisplayName("rechaza el código vacío con VAL-001 y el excesivamente largo con VAL-007")
  void obligatorioYAcotado() {
    assertThatThrownBy(() -> new RoleCode(""))
        .isInstanceOf(ValidationException.class)
        .extracting(f -> ((ValidationException) f).errorCode())
        .isEqualTo("VAL-001");

    assertThatThrownBy(() -> new RoleCode("A".repeat(51)))
        .isInstanceOf(ValidationException.class)
        .extracting(f -> ((ValidationException) f).errorCode())
        .isEqualTo("VAL-007");
  }
}
