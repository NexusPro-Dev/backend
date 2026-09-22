package com.factech.nexus.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El dominio cerrado de {@code ENVIRONMENT} (Art. IX.4).
 *
 * <p>Lo que esta clase vigila no es la traducción, que es trivial: es que <b>no exista un cuarto
 * estado</b>. De esa propiedad depende que «el entorno no es producción» sea una condición segura
 * para sembrar quince cuentas con la contraseña del superadministrador.
 */
class EnvironmentTest {

  @Test
  @DisplayName("los tres valores del Art. IX.4 se reconocen")
  void losTresAdmitidos() {
    assertThat(Environment.desdeConfiguracion("development")).isEqualTo(Environment.DEVELOPMENT);
    assertThat(Environment.desdeConfiguracion("testing")).isEqualTo(Environment.TESTING);
    assertThat(Environment.desdeConfiguracion("production")).isEqualTo(Environment.PRODUCTION);
  }

  @Test
  @DisplayName("la caja y los espacios no cambian el significado")
  void sinDistinguirCajaNiEspacios() {
    // No reabre nada: sigue siendo el mismo conjunto de tres. Lo que evita es
    // que `Production` —que quien lo escribe da por bueno— acabe clasificado
    // como «no es producción», que es el error que más caro sale.
    assertThat(Environment.desdeConfiguracion("PRODUCTION")).isEqualTo(Environment.PRODUCTION);
    assertThat(Environment.desdeConfiguracion("Production")).isEqualTo(Environment.PRODUCTION);
    assertThat(Environment.desdeConfiguracion("  production  ")).isEqualTo(Environment.PRODUCTION);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "prod", "produccion", "dev", "local", "staging", "PROD"})
  @DisplayName("cualquier otro valor TUMBA EL ARRANQUE en lugar de asumir uno")
  void loQueNoSeEntiendeNoSeAsume(String valor) {
    // Es el corazón del asunto. Si esto devolviera un valor por defecto, cada
    // uno de estos casos sería «no es producción» y sembraría. `prod` y `PROD`
    // están en la lista a propósito: son las dos abreviaturas que alguien
    // escribiría creyendo que dice producción.
    assertThatThrownBy(() -> Environment.desdeConfiguracion(valor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("development, testing, production");
  }

  @Test
  @DisplayName("el mensaje distingue «falta» de «no se entiende», y cita el valor recibido")
  void elMensajeDiceQuePasa() {
    // Sin el valor recibido en el mensaje, quien lo lea no puede ver el espacio
    // de más ni la mayúscula que lo rompió.
    assertThatThrownBy(() -> Environment.desdeConfiguracion(null))
        .hasMessageContaining("no está declarada");

    assertThatThrownBy(() -> Environment.desdeConfiguracion("staging"))
        .hasMessageContaining("no se reconoce")
        .hasMessageContaining("'staging'");
  }

  @Test
  @DisplayName("solo production es producción")
  void soloProduccionEsProduccion() {
    assertThat(Environment.PRODUCTION.esProduccion()).isTrue();
    assertThat(Environment.DEVELOPMENT.esProduccion()).isFalse();
    assertThat(Environment.TESTING.esProduccion()).isFalse();
  }
}
