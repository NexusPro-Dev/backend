package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El agregado del catálogo (`RF-PM-001` · `T-03`, `T-18`).
 *
 * <p>Sin Spring: lo que se comprueba aquí es que un producto <b>mal formado no pueda existir dentro
 * del modelo</b>, venga por donde venga. El DTO valida a quien llega por HTTP; esto vale también
 * para una siembra o para otro caso de uso.
 */
class ProductTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 8, 27, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final UUID DESTINO = UUID.randomUUID();
  private static final UUID MONEDA = UUID.randomUUID();

  @Test
  @DisplayName("`RN-PM-012` — todo producto nace INACTIVO, y el estado no se puede pasar")
  void naceInactivo() {
    assertThat(upgrade("UPGRADE_ORO", DESTINO).getStatus()).isEqualTo(ProductStatus.INACTIVO);
    assertThat(servicio("ASESORIA").getStatus()).isEqualTo(ProductStatus.INACTIVO);
  }

  @Test
  @DisplayName("el código se recorta y se pasa a mayúsculas al escribir")
  void normalizaElCodigo() {
    assertThat(upgrade("  upgrade_oro  ", DESTINO).getCode()).isEqualTo("UPGRADE_ORO");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "1ORO", "_ORO", "ORO-PLUS", "ORO PLUS", "ORO+"})
  @DisplayName("`VAL-010` — el código que no cumple el formato se rechaza")
  void rechazaCodigoMalFormado(String codigo) {
    ValidationException fallo =
        catchThrowableOfType(() -> upgrade(codigo, DESTINO), ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-010");
    assertThat(fallo.errors())
        .singleElement()
        .satisfies(e -> assertThat(e.field()).isEqualTo("code"));
  }

  @Test
  @DisplayName("`RN-PM-002` — un upgrade SIN destino se rechaza con VAL-007")
  void upgradeSinDestino() {
    ValidationException fallo =
        catchThrowableOfType(() -> upgrade("UPGRADE_ORO", null), ValidationException.class);

    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-007");
  }

  @Test
  @DisplayName(
      "`RN-PM-002` — un servicio CON destino se rechaza con VAL-008, que es la mitad que se olvida")
  void servicioConDestino() {
    ValidationException fallo =
        catchThrowableOfType(
            () ->
                Product.create(
                    UUID.randomUUID(),
                    "ASESORIA",
                    ProductType.SERVICIO,
                    "Asesoría",
                    null,
                    DESTINO,
                    new BigDecimal("49.99"),
                    MONEDA,
                    null,
                    AHORA),
            ValidationException.class);

    // No falla, promete: sin esta comprobación el servicio quedaría anunciando
    // un cambio de nivel que nadie va a aplicar.
    assertThat(fallo).isNotNull();
    assertThat(fallo.errorCode()).isEqualTo("VAL-008");
  }

  @Test
  @DisplayName("`RN-PM-015` — la vigencia es opcional: sin ella el producto no caduca")
  void vigenciaOpcional() {
    assertThat(upgrade("UPGRADE_ORO", DESTINO).getValidityDays()).isNull();

    Product conVigencia =
        Product.create(
            UUID.randomUUID(),
            "UPGRADE_MES",
            ProductType.UPGRADE_MEMBRESIA,
            "Ascenso mensual",
            null,
            DESTINO,
            new BigDecimal("19.99"),
            MONEDA,
            30,
            AHORA);

    assertThat(conVigencia.getValidityDays()).isEqualTo(30);
  }

  @Test
  @DisplayName("el nombre y la descripción se recortan; el vacío queda nulo")
  void recortaTextos() {
    Product producto =
        Product.create(
            UUID.randomUUID(),
            "ASESORIA",
            ProductType.SERVICIO,
            "  Asesoría personalizada  ",
            "   ",
            null,
            new BigDecimal("10.00"),
            MONEDA,
            null,
            AHORA);

    assertThat(producto.getName()).isEqualTo("Asesoría personalizada");
    assertThat(producto.getDescription()).isNull();
  }

  @Test
  @DisplayName("un servicio sin destino y un upgrade con destino se construyen sin queja")
  void losDosCasosValidos() {
    assertThatCode(() -> servicio("ASESORIA")).doesNotThrowAnyException();
    assertThatCode(() -> upgrade("UPGRADE_ORO", DESTINO)).doesNotThrowAnyException();
  }

  private static Product upgrade(String codigo, UUID destino) {
    return Product.create(
        UUID.randomUUID(),
        codigo,
        ProductType.UPGRADE_MEMBRESIA,
        "Ascenso a Oro",
        null,
        destino,
        new BigDecimal("49.99"),
        MONEDA,
        null,
        AHORA);
  }

  private static Product servicio(String codigo) {
    return Product.create(
        UUID.randomUUID(),
        codigo,
        ProductType.SERVICIO,
        "Asesoría",
        null,
        null,
        new BigDecimal("49.99"),
        MONEDA,
        null,
        AHORA);
  }
}
