package com.factech.nexus.modules.movements.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway;
import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway.LocalChargeOrder;
import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway.Payer;
import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway.Shop;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * El adaptador de PayRetailers (`architecture.md` §15.5) contra un servidor simulado, y el cifrado
 * de las claves de las tiendas (`CA-MV-632`): <b>cada llamada se autentica con la tienda que se le
 * pasa</b>, la clave cifrada no se lee con otra llave ni en otro país, y nada la muestra.
 */
class PayRetailersGatewayTest {

  private static final String BASE = "https://api.payretailers.test";
  private static final String LLAVE = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  private MockRestServiceServer servidor;
  private PayRetailersGateway pasarela;

  @BeforeEach
  void preparar() {
    RestClient.Builder constructor = RestClient.builder().baseUrl(BASE);
    servidor = MockRestServiceServer.bindTo(constructor).build();
    PayRetailersSettings ajustes = ajustes("clave-de-la-cuenta", LLAVE);
    pasarela =
        new PayRetailersGateway(
            ajustes, new AesGcmShopSecrets(ajustes), constructor.build(), new ObjectMapper());
  }

  @Test
  @DisplayName(
      "CA-MV-632 — abrir y consultar se autentican con la tienda que se les pasa, y la"
          + " Subscription Key de la cuenta")
  void conLaTiendaQueSeLePasa() {
    UUID pago = UUID.randomUUID();
    servidor
        .expect(requestTo(BASE + "/paywalls"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", basico("shop-col", "secreto-col")))
        .andExpect(header("Ocp-Apim-Subscription-Key", "clave-de-la-cuenta"))
        .andRespond(
            withSuccess(
                "{\"uid\":\"pw-1\",\"form\":{\"action\":\"https://pagar.test/pw-1\"}}",
                MediaType.APPLICATION_JSON));
    servidor
        .expect(requestTo(BASE + "/transactions/byTracking/" + pago))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", basico("shop-per", "secreto-per")))
        .andRespond(
            withSuccess(
                "{\"uid\":\"tx-1\",\"status\":\"APPROVED\",\"amount\":100,\"currency\":\"PEN\"}",
                MediaType.APPLICATION_JSON));

    assertThat(pasarela.open(new Shop("shop-col", "secreto-col"), orden()).checkoutUrl())
        .isEqualTo("https://pagar.test/pw-1");
    assertThat(pasarela.findByTracking(pago, new Shop("shop-per", "secreto-per")))
        .hasValueSatisfying(
            t -> assertThat(t.outcome()).isEqualTo(LocalPaymentGateway.Outcome.APROBADO));
    servidor.verify();
  }

  @Test
  @DisplayName("CA-MV-632 — encendida solo con Subscription Key y una llave válida de 32 bytes")
  void encendida() {
    assertThat(pasarela.enabled()).isTrue();
    for (PayRetailersSettings a :
        new PayRetailersSettings[] {
          ajustes("", LLAVE),
          ajustes("clave", null),
          ajustes("clave", "no-es-base64!"),
          ajustes("clave", "MDEyMzQ1Njc4OWFiY2RlZg==") // 16 bytes
        }) {
      assertThat(
              new PayRetailersGateway(
                      a, new AesGcmShopSecrets(a), RestClient.builder().build(), new ObjectMapper())
                  .enabled())
          .isFalse();
    }
  }

  @Test
  @DisplayName(
      "CA-MV-632 — la clave cifrada no es la clave, cada cifrado es distinto, y solo se descifra"
          + " con la misma llave y el mismo país")
  void cifrado() {
    AesGcmShopSecrets secretos = new AesGcmShopSecrets(ajustes("x", LLAVE));
    UUID colombia = UUID.randomUUID();
    String cifrada = secretos.encrypt("secreto-col", colombia);

    assertThat(cifrada).startsWith("v1:").doesNotContain("secreto-col");
    assertThat(secretos.encrypt("secreto-col", colombia)).isNotEqualTo(cifrada);
    assertThat(secretos.decrypt(cifrada, colombia)).isEqualTo("secreto-col");
    assertThatThrownBy(() -> secretos.decrypt(cifrada, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
    AesGcmShopSecrets otra =
        new AesGcmShopSecrets(ajustes("x", "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA="));
    assertThatThrownBy(() -> otra.decrypt(cifrada, colombia))
        .isInstanceOf(IllegalStateException.class);
    assertThat(new Shop("shop-col", "secreto-col").toString()).doesNotContain("secreto-col");
  }

  private static PayRetailersSettings ajustes(String subscription, String llave) {
    return new PayRetailersSettings(
        subscription,
        llave,
        BASE,
        null,
        null,
        false,
        Duration.ofSeconds(5),
        Duration.ofMinutes(10),
        50,
        false);
  }

  private static LocalChargeOrder orden() {
    return new LocalChargeOrder(
        UUID.randomUUID(),
        "VTA-1",
        4150500L,
        "COP",
        new Payer("Ana", "Pérez", "ana@factech.co", "123", null, "COL"));
  }

  private static String basico(String tienda, String secreto) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((tienda + ":" + secreto).getBytes(StandardCharsets.UTF_8));
  }
}
