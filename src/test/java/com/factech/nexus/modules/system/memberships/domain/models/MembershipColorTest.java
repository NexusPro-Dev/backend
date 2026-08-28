package com.factech.nexus.modules.system.memberships.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El color del nivel, normalizado y validado en el dominio (`RF-SP-016` · `T-20`, `RN-SP-024`).
 *
 * <p><b>Por qué existe esta prueba y no basta con la de API.</b> El {@code @Pattern} del DTO
 * atiende a quien llega por HTTP; esta comprobación atiende a cualquier otro camino —una siembra,
 * una migración de datos, otro caso de uso— y es la que hace que un {@code color} mal formado no
 * pueda existir dentro del modelo. Si alguien retirase la validación del dominio dejando la del
 * DTO, la suite de API seguiría en verde.
 */
class MembershipColorTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 8, 26, 12, 0, 0, 0, ZoneOffset.UTC);

  private static final MembershipInsertion CIMA =
      new MembershipInsertion(1, null, null, List.of(), null, null);

  @Test
  @DisplayName("el color se normaliza a mayúsculas al escribir, no al leer")
  void normalizaAMayusculas() {
    assertThat(conColor("1e88e5").getColor()).isEqualTo("1E88E5");
    assertThat(conColor("1E88E5").getColor()).isEqualTo("1E88E5");
    assertThat(conColor("1e88E5").getColor()).isEqualTo("1E88E5");
  }

  @Test
  @DisplayName("los espacios sobrantes se recortan antes de validar")
  void recortaEspacios() {
    // Sin el recorte, el patrón fallaría por un motivo que el mensaje no
    // explica: el actor ve «seis dígitos hexadecimales» sobre un valor que
    // tiene exactamente seis dígitos hexadecimales y un espacio.
    assertThat(conColor("  1e88e5  ").getColor()).isEqualTo("1E88E5");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "   ", // solo espacios
        "#1E88E5", // el `#` se RECHAZA, no se recorta
        "1E88E", // cinco
        "1E88E5F", // siete
        "1E88EZ", // Z no es hexadecimal
        "azulon", // seis caracteres, ninguno hexadecimal
        "1E 88E5" // un espacio en medio
      })
  @DisplayName("VAL-008 — todo lo que no sean seis dígitos hexadecimales se rechaza")
  void rechazaLoQueNoEsUnColor(String malo) {
    assertThatThrownBy(() -> conColor(malo))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("seis dígitos hexadecimales");
  }

  @Test
  @DisplayName("el rechazo señala el campo `color` y no la membresía entera")
  void elRechazoSenalaElCampo() {
    ValidationException fallo =
        (ValidationException)
            org.assertj.core.api.Assertions.catchThrowable(() -> conColor("#1E88E5"));

    assertThat(fallo.errorCode()).isEqualTo("VAL-008");
    assertThat(fallo.errors())
        .singleElement()
        .satisfies(e -> assertThat(e.field()).isEqualTo("color"));
  }

  private static Membership conColor(String color) {
    return Membership.create(UUID.randomUUID(), "ORO", "Oro", null, color, CIMA, AHORA);
  }
}
