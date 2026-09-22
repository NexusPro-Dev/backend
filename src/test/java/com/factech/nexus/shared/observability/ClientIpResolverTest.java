package com.factech.nexus.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * La dirección de origen de una petición (Art. V.15, `architecture.md` §6.6.1).
 *
 * <p><b>Lo que se está protegiendo es la evidencia.</b> {@code X-Forwarded-For} lo escribe el
 * cliente; sin comprobación, cualquiera elige qué IP queda registrada contra su nombre en la
 * auditoría. Por eso casi todas estas pruebas son de la forma «alguien miente y no se le cree».
 *
 * <p>Sin Spring: el resolvedor se construye con la cadena de configuración, que es exactamente lo
 * que hace el contenedor.
 */
class ClientIpResolverTest {

  @Nested
  @DisplayName("sin proxies declarados no se confía en nadie")
  class SinLista {

    private final ClientIpResolver resolvedor = new ClientIpResolver("");

    @Test
    @DisplayName("se usa la IP del socket")
    void usaElSocket() {
      assertThat(resolvedor.resolve(peticion("203.0.113.9", null))).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("y se IGNORA la cabecera, aunque venga")
    void ignoraLaCabecera() {
      // Es el caso que da sentido a todo lo demás: quien nos habla no es un
      // proxy nuestro, de modo que nada de lo que afirme es verificable.
      assertThat(resolvedor.resolve(peticion("203.0.113.9", "1.2.3.4"))).isEqualTo("203.0.113.9");
    }
  }

  @Nested
  @DisplayName("con una dirección suelta declarada")
  class DireccionExacta {

    private final ClientIpResolver resolvedor = new ClientIpResolver("10.0.0.1");

    @Test
    @DisplayName("se lee la cabecera cuando habla el proxy declarado")
    void confiaEnElProxy() {
      assertThat(resolvedor.resolve(peticion("10.0.0.1", "198.51.100.7")))
          .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("se salta la cadena de derecha a izquierda hasta el primer NO confiable")
    void saltaLosConfiables() {
      // Todo lo que hay a la izquierda del primer no confiable lo escribió él y
      // puede ser inventado.
      assertThat(resolvedor.resolve(peticion("10.0.0.1", "1.1.1.1, 198.51.100.7, 10.0.0.1")))
          .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("una dirección vecina NO cuenta como el proxy")
    void noConfiaEnLaVecina() {
      assertThat(resolvedor.resolve(peticion("10.0.0.2", "1.2.3.4"))).isEqualTo("10.0.0.2");
    }
  }

  @Nested
  @DisplayName("con un bloque CIDR declarado (D-21)")
  class Rangos {

    /**
     * Es la forma que D-21 necesita: en Railway no hay una dirección fija que declarar, y lo único
     * que se puede afirmar es «confío en la red privada del proveedor».
     */
    private final ClientIpResolver resolvedor = new ClientIpResolver("10.0.0.0/8, fd00::/8");

    @Test
    @DisplayName("cualquier dirección de dentro del bloque es el proxy")
    void dentroDelBloque() {
      assertThat(resolvedor.resolve(peticion("10.77.3.9", "198.51.100.7")))
          .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("una de fuera del bloque, no")
    void fueraDelBloque() {
      assertThat(resolvedor.resolve(peticion("11.0.0.1", "198.51.100.7"))).isEqualTo("11.0.0.1");
    }

    @Test
    @DisplayName("funciona en IPv6, que es lo único que hay en la red privada de Railway")
    void bloqueIPv6() {
      assertThat(resolvedor.resolve(peticion("fd12:3456::9", "198.51.100.7")))
          .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("una IPv4 NUNCA cae dentro de un bloque IPv6, ni al revés")
    void familiasSeparadas() {
      ClientIpResolver soloIPv6 = new ClientIpResolver("::/0");
      // `::/0` es «todo IPv6». Si las familias se mezclaran, esto confiaría en
      // el mundo entero — que es la forma más silenciosa de romper esto.
      assertThat(soloIPv6.resolve(peticion("203.0.113.9", "1.2.3.4"))).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("una máscara que no cae en frontera de byte se compara bit a bit")
    void mascaraNoAlineada() {
      // `100.64.0.0/10` es el rango de NAT de operador: /10 parte el segundo
      // byte por la mitad, que es donde una comparación byte a byte fallaría.
      ClientIpResolver conNat = new ClientIpResolver("100.64.0.0/10");

      assertThat(conNat.resolve(peticion("100.100.0.1", "198.51.100.7")))
          .as("100.100.0.1 sí está dentro de 100.64.0.0/10")
          .isEqualTo("198.51.100.7");
      assertThat(conNat.resolve(peticion("100.128.0.1", "198.51.100.7")))
          .as("100.128.0.1 ya está fuera")
          .isEqualTo("100.128.0.1");
    }
  }

  @Nested
  @DisplayName("lo que no es un literal de IP")
  class NoLiterales {

    private final ClientIpResolver resolvedor = new ClientIpResolver("10.0.0.0/8");

    @Test
    @DisplayName("un NOMBRE en la cabecera no se resuelve por DNS: se trata como cliente")
    void noConsultaElDns() {
      /*
       * Si se resolviera, quien manda la petición elegiría a qué servidor
       * consulta este proceso. Se devuelve tal cual, que es lo que llegó, y no
       * se le concede confianza.
       */
      assertThat(resolvedor.resolve(peticion("10.0.0.1", "interno.atacante.example")))
          .isEqualTo("interno.atacante.example");
    }

    @Test
    @DisplayName("una cabecera con basura tampoco gana confianza")
    void basura() {
      assertThat(resolvedor.resolve(peticion("10.0.0.1", "999.999.999.999")))
          .isEqualTo("999.999.999.999");
    }
  }

  @Nested
  @DisplayName("una configuración que no se entiende TUMBA EL ARRANQUE")
  class ConfiguracionInvalida {

    /**
     * Art. IX.5. Ignorarla en silencio dejaría un despliegue que <b>cree</b> tener configurada la
     * confianza y no la tiene, y el síntoma —una auditoría que apunta al proxy— no menciona nunca
     * la variable mal escrita.
     */
    @Test
    @DisplayName("una entrada que no es una IP")
    void noEsUnaIp() {
      assertThatThrownBy(() -> new ClientIpResolver("proxy-interno"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("trusted-proxies");
    }

    @Test
    @DisplayName("una máscara que no es un número")
    void mascaraNoNumerica() {
      assertThatThrownBy(() -> new ClientIpResolver("10.0.0.0/ocho"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("una máscara fuera del rango de su familia")
    void mascaraImposible() {
      assertThatThrownBy(() -> new ClientIpResolver("10.0.0.0/33"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("fuera de rango");
    }

    @Test
    @DisplayName("una lista vacía o con espacios NO tumba nada: es «no confío en nadie»")
    void vaciaEsValida() {
      assertThat(new ClientIpResolver("  ,  ").resolve(peticion("10.0.0.1", "1.2.3.4")))
          .isEqualTo("10.0.0.1");
    }
  }

  private static HttpServletRequest peticion(String socket, String reenviada) {
    MockHttpServletRequest peticion = new MockHttpServletRequest();
    peticion.setRemoteAddr(socket);
    if (reenviada != null) {
      peticion.addHeader("X-Forwarded-For", reenviada);
    }
    return peticion;
  }
}
