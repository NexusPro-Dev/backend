package com.factech.nexus.shared.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * La consulta a los dos proveedores (`RN-AC-017`), con los proveedores simulados: qué dirección se
 * pide —siempre la fija, con el identificador—, cómo se lee la duración y qué motivo sale cuando no
 * se puede.
 */
class ProviderVideoDurationLookupTest {

  private static final VideoLink YT = new VideoLink(VideoProvider.YOUTUBE, "dQw4w9WgXcQ", null);
  private static final VideoLink VIMEO = new VideoLink(VideoProvider.VIMEO, "76979871", null);

  @Test
  @DisplayName("YouTube: pide videos.list con la clave y convierte PT12M34S en 754")
  void youtube() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();
    proveedor
        .expect(
            requestTo(
                "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id=dQw4w9WgXcQ"
                    + "&key=clave-de-prueba"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"items\":[{\"contentDetails\":{\"duration\":\"PT12M34S\"}}]}",
                MediaType.APPLICATION_JSON));

    assertThat(consulta("clave-de-prueba", constructor).segundosDe(YT)).isEqualTo(754);
    proveedor.verify();
  }

  @Test
  @DisplayName("YouTube: sin clave no se llama, y el motivo lo dice")
  void youtubeSinClave() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();

    assertThatThrownBy(() -> consulta("", constructor).segundosDe(YT))
        .isInstanceOf(VideoDurationUnavailable.class)
        .hasMessageContaining("YouTube")
        .hasMessageContaining("clave");
    proveedor.verify();
  }

  @Test
  @DisplayName("YouTube: lista vacía es un video que no existe o es privado; P0D, una emisión")
  void youtubeSinVideo() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();
    proveedor
        .expect(requestTo(org.hamcrest.Matchers.startsWith("https://www.googleapis.com")))
        .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
    proveedor
        .expect(requestTo(org.hamcrest.Matchers.startsWith("https://www.googleapis.com")))
        .andRespond(
            withSuccess(
                "{\"items\":[{\"contentDetails\":{\"duration\":\"P0D\"}}]}",
                MediaType.APPLICATION_JSON));
    VideoDurationLookup consulta = consulta("k", constructor);

    assertThatThrownBy(() -> consulta.segundosDe(YT))
        .hasMessageContaining("no existe o es privado");
    assertThatThrownBy(() -> consulta.segundosDe(YT)).hasMessageContaining("en vivo");
  }

  @Test
  @DisplayName("Vimeo: pide el oEmbed público, sin credencial, y lee duration")
  void vimeo() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();
    proveedor
        .expect(
            requestTo(
                "https://vimeo.com/api/oembed.json?url=https://vimeo.com/76979871/ab12cd34ef"))
        .andRespond(withSuccess("{\"duration\":62}", MediaType.APPLICATION_JSON));

    assertThat(
            consulta(null, constructor)
                .segundosDe(new VideoLink(VideoProvider.VIMEO, "76979871", "ab12cd34ef")))
        .isEqualTo(62);
    proveedor.verify();
  }

  @Test
  @DisplayName(
      "Vimeo: 404 y 403 son un video que no existe o es privado; 500, un error del proveedor")
  void vimeoFalla() {
    RestClient.Builder constructor = RestClient.builder();
    MockRestServiceServer proveedor = MockRestServiceServer.bindTo(constructor).build();
    proveedor
        .expect(requestTo(org.hamcrest.Matchers.startsWith("https://vimeo.com")))
        .andRespond(withResourceNotFound());
    proveedor
        .expect(requestTo(org.hamcrest.Matchers.startsWith("https://vimeo.com")))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));
    proveedor
        .expect(requestTo(org.hamcrest.Matchers.startsWith("https://vimeo.com")))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    VideoDurationLookup consulta = consulta(null, constructor);

    assertThatThrownBy(() -> consulta.segundosDe(VIMEO)).hasMessageContaining("privado");
    assertThatThrownBy(() -> consulta.segundosDe(VIMEO)).hasMessageContaining("privado");
    assertThatThrownBy(() -> consulta.segundosDe(VIMEO))
        .isInstanceOf(VideoDurationUnavailable.class)
        .hasMessageContaining("Vimeo")
        .hasMessageContaining("error");
  }

  private static VideoDurationLookup consulta(String clave, RestClient.Builder constructor) {
    VideoSettings ajustes = new VideoSettings(clave, null, null, Duration.ofSeconds(1));
    return new ProviderVideoDurationLookup(ajustes, constructor.build());
  }
}
