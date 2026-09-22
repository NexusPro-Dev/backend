package com.factech.nexus.modules.academy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La dirección pública de una portada de Academia (`RF-AC-001` · `T-07`). */
class AcademyImageUrlsTest {

  @Test
  @DisplayName("un identificador se convierte en la ruta por imagen, y el nulo en nulo")
  void direccion() {
    UUID id = UUID.fromString("01a0b3c7-1000-7001-9c4f-5e7adc000001");
    assertThat(AcademyImageUrls.de(id)).isEqualTo("/api/v1/academy-images/" + id);
    assertThat(AcademyImageUrls.de(null)).isNull();
  }

  @Test
  @DisplayName("es otra ruta que la de PM: otra tabla, otro módulo")
  void noEsLaRutaDeProductos() {
    assertThat(AcademyImageUrls.RUTA).isNotEqualTo("/api/v1/product-images/");
  }
}
