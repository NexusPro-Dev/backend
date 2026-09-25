package com.factech.nexus.shared.video;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@link VideoDurationLookup} contra los dos proveedores (`RN-AC-017`, `ac.md` §5.2.11).
 *
 * <ul>
 *   <li><b>YouTube</b>: {@code GET {base}/videos?part=contentDetails&id={id}&key={clave}}, que
 *       responde la duración en ISO 8601 ({@code PT12M34S}). Exige clave aun para un video público;
 *       sin clave no se llama y el motivo lo dice. Una lista vacía es un video que no existe o es
 *       privado.
 *   <li><b>Vimeo, con token</b>: {@code GET {api}/videos/{id}[:{hash}]?fields=duration} con {@code
 *       Authorization: bearer {token}}, que responde {@code duration} en segundos, también de los
 *       videos privados de la cuenta del token. <b>Sin token</b>, el oEmbed público —{@code GET
 *       {base}/api/oembed.json?url=https://vimeo.com/{id}[/{hash}]}—, que no es fiable: el
 *       25-09-2026 respondió {@code 404} a un video público. Un {@code 404}, un {@code 403} o un
 *       {@code 401} es un video que no existe, es privado para esa cuenta o no se deja consultar.
 * </ul>
 *
 * <p><b>Siempre a la dirección fija del proveedor</b>, construida con el identificador reconocido
 * en {@link VideoLink}: el enlace que pegó administración no se usa como dirección. <b>Con plazo
 * corto</b> ({@link VideoSettings#timeout()}), aplicado al cliente —la de Resend lo declara y no lo
 * aplica; aquí sí importa—.
 */
@Component
public class ProviderVideoDurationLookup implements VideoDurationLookup {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderVideoDurationLookup.class);

  private final VideoSettings ajustes;
  private final RestClient http;

  @Autowired
  public ProviderVideoDurationLookup(VideoSettings ajustes, RestClient.Builder constructor) {
    this(ajustes, constructor.requestFactory(fabrica(ajustes.timeout())).build());
  }

  /** Para las pruebas: el cliente ya construido, con el servidor simulado detrás. */
  ProviderVideoDurationLookup(VideoSettings ajustes, RestClient http) {
    this.ajustes = ajustes;
    this.http = http;
  }

  @Override
  public int segundosDe(VideoLink video) {
    int segundos =
        switch (video.proveedor()) {
          case YOUTUBE -> deYoutube(video);
          case VIMEO -> deVimeo(video);
        };
    if (segundos <= 0) {
      throw new VideoDurationUnavailable(
          video.proveedor(), "el video no tiene duración; ¿es una emisión en vivo?");
    }
    return segundos;
  }

  private int deYoutube(VideoLink video) {
    if (!ajustes.hayClaveDeYoutube()) {
      throw new VideoDurationUnavailable(
          VideoProvider.YOUTUBE, "el sistema no tiene configurada la clave de YouTube");
    }
    String uri =
        UriComponentsBuilder.fromUriString(ajustes.youtubeBaseUrl())
            .path("/videos")
            .queryParam("part", "contentDetails")
            .queryParam("id", video.id())
            .queryParam("key", ajustes.youtubeApiKey())
            .build()
            .toUriString();
    JsonNode cuerpo = pedir(VideoProvider.YOUTUBE, uri);
    JsonNode items = cuerpo == null ? null : cuerpo.path("items");
    if (items == null || !items.isArray() || items.isEmpty()) {
      throw new VideoDurationUnavailable(VideoProvider.YOUTUBE, "el video no existe o es privado");
    }
    String iso = items.get(0).path("contentDetails").path("duration").asText("");
    try {
      return (int) Duration.parse(iso).getSeconds();
    } catch (DateTimeParseException fallo) {
      throw new VideoDurationUnavailable(
          VideoProvider.YOUTUBE, "respondió una duración que no se entiende: " + iso);
    }
  }

  private int deVimeo(VideoLink video) {
    if (ajustes.hayTokenDeVimeo()) {
      // `/videos/{id}:{hash}` es como la API nombra un video no listado.
      String uri =
          UriComponentsBuilder.fromUriString(ajustes.vimeoApiBaseUrl())
              .path("/videos/" + video.id() + (video.hash() == null ? "" : ":" + video.hash()))
              .queryParam("fields", "duration")
              .build()
              .toUriString();
      return duracionDeVimeo(
          pedir(VideoProvider.VIMEO, uri, "bearer " + ajustes.vimeoAccessToken()));
    }
    String objetivo =
        "https://vimeo.com/" + video.id() + (video.hash() == null ? "" : "/" + video.hash());
    String uri =
        UriComponentsBuilder.fromUriString(ajustes.vimeoBaseUrl())
            .path("/api/oembed.json")
            .queryParam("url", objetivo)
            .encode()
            .build()
            .toUriString();
    return duracionDeVimeo(pedir(VideoProvider.VIMEO, uri, null));
  }

  private static int duracionDeVimeo(JsonNode cuerpo) {
    JsonNode duracion = cuerpo == null ? null : cuerpo.get("duration");
    if (duracion == null || !duracion.canConvertToInt()) {
      throw new VideoDurationUnavailable(VideoProvider.VIMEO, "no informó la duración del video");
    }
    return duracion.asInt();
  }

  private JsonNode pedir(VideoProvider proveedor, String uri) {
    return pedir(proveedor, uri, null);
  }

  /**
   * @param autorizacion la cabecera {@code Authorization}, o nula si el proveedor no la pide. Nunca
   *     se registra: los mensajes de fallo llevan el estado, no la petición.
   */
  private JsonNode pedir(VideoProvider proveedor, String uri, String autorizacion) {
    try {
      RestClient.RequestHeadersSpec<?> peticion = http.get().uri(java.net.URI.create(uri));
      if (autorizacion != null) {
        peticion = peticion.header("Authorization", autorizacion);
      }
      return peticion.retrieve().body(JsonNode.class);
    } catch (RestClientResponseException fallo) {
      int estado = fallo.getStatusCode().value();
      if (estado == 404 || estado == 403 || estado == 401) {
        throw new VideoDurationUnavailable(
            proveedor, "el video no existe, es privado o no se deja consultar");
      }
      LOG.warn("{} respondió {} al pedir una duración", proveedor.nombre(), estado);
      throw new VideoDurationUnavailable(proveedor, "el proveedor respondió con un error");
    } catch (ResourceAccessException fallo) {
      throw new VideoDurationUnavailable(proveedor, "el proveedor no respondió a tiempo");
    }
  }

  private static SimpleClientHttpRequestFactory fabrica(Duration plazo) {
    SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
    fabrica.setConnectTimeout(plazo);
    fabrica.setReadTimeout(plazo);
    return fabrica;
  }
}
