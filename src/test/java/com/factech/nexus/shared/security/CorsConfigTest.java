package com.factech.nexus.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Lectura y validación de la lista de orígenes.
 *
 * <p>Lo que estas pruebas protegen es el <b>momento</b> del fallo. Un origen mal escrito no rompe
 * nada al arrancar: rompe en el navegador de quien consume la API, semanas después, con un error de
 * CORS que no menciona la variable mal puesta. Aquí se comprueba que ese error salga al arrancar y
 * con el motivo escrito.
 */
class CorsConfigTest {

  private static final Duration MEDIA_HORA = Duration.ofMinutes(30);

  @Test
  @DisplayName("sin lista configurada no se autoriza a ningún origen")
  void listaVaciaNoAutorizaANadie() {
    // Es el valor por defecto y el seguro: un despliegue que olvide declarar la
    // variable queda cerrado al navegador, no abierto.
    assertThat(politicaDe("").getAllowedOrigins()).isEmpty();
  }

  @Test
  @DisplayName("la lista se lee separada por coma, sin importar los espacios")
  void seLeeLaListaSeparadaPorComa() {
    CorsConfiguration politica =
        politicaDe(" https://app.nexus.co , http://localhost:5173 ,, https://app.nexus.co ");

    // El duplicado desaparece y el vacío entre comas se ignora: un `.env`
    // escrito a mano acaba con los dos, y ninguno debe tumbar el arranque.
    assertThat(politica.getAllowedOrigins())
        .containsExactly("https://app.nexus.co", "http://localhost:5173");
  }

  @Test
  @DisplayName("el comodín se rechaza al arrancar")
  void elComodinNoSeAdmite() {
    assertThatThrownBy(() -> new CorsConfig("*", MEDIA_HORA))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no admite '*'");
  }

  @ParameterizedTest(name = "«{0}» no es un origen")
  @DisplayName("un origen que no casaría nunca falla al arrancar, no en el navegador")
  @ValueSource(
      strings = {
        "localhost:5173", // sin esquema
        "https://app.nexus.co/", // barra final
        "https://app.nexus.co/api", // con ruta
        "ftp://app.nexus.co", // esquema que el navegador no envía
        "https://usuario@app.nexus.co" // con credencial
      })
  void unOrigenMalEscritoTumbaElArranque(String origen) {
    assertThatThrownBy(() -> new CorsConfig(origen, MEDIA_HORA))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(origen);
  }

  @Test
  @DisplayName("la política no autoriza credenciales de navegador")
  void sinCredenciales() {
    // D-08: no hay cookie de sesión. Activarlo dejaría viajar entre sitios una
    // cookie futura sin que nadie lo hubiera decidido.
    assertThat(politicaDe("https://app.nexus.co").getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
  }

  @Test
  @DisplayName("expone Location, que el navegador oculta por omisión")
  void exponeLasCabecerasQueLaInterfazNecesitaLeer() {
    // Sin esto, quien registra un rol recibe el 201 y no puede leer la
    // dirección del recurso que acaba de crear.
    assertThat(politicaDe("https://app.nexus.co").getExposedHeaders())
        .contains("Location", "X-Correlation-Id");
  }

  private static CorsConfiguration politicaDe(String configurados) {
    UrlBasedCorsConfigurationSource fuente =
        (UrlBasedCorsConfigurationSource)
            new CorsConfig(configurados, MEDIA_HORA).corsConfigurationSource();
    return fuente.getCorsConfigurations().get("/**");
  }
}
