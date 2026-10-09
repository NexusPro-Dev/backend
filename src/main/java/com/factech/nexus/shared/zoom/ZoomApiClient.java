package com.factech.nexus.shared.zoom;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@link ZoomMeetings} contra la API REST de Zoom, con una app <b>Server-to-Server OAuth</b>.
 *
 * <ul>
 *   <li><b>El token</b>: {@code POST {oauth}?grant_type=account_credentials&account_id=…} con las
 *       credenciales en {@code Authorization: Basic}. Dura una hora; <b>se reutiliza hasta un
 *       minuto antes de caducar</b>, y un {@code 401} de Zoom lo descarta y se reintenta una vez.
 *   <li><b>Crear</b>: {@code POST {api}/users/{anfitrión}/meetings}, reunión programada ({@code
 *       type 2}) en la zona de la plataforma, con {@code registration_type 1}, {@code approval_type
 *       0}, sin entrar antes que el anfitrión y <b>sin los correos de Zoom</b> a los registrados.
 *   <li><b>Corregir</b>: {@code PATCH {api}/meetings/{id}}. <b>Borrar</b>: {@code DELETE
 *       {api}/meetings/{id}}; un {@code 404} es una reunión que ya no está, y no es un fallo.
 *   <li><b>Registrar</b>: {@code POST {api}/meetings/{id}/registrants} con correo y nombre, que
 *       devuelve {@code registrant_id} y el {@code join_url} personal.
 *   <li><b>Anfitrión</b>: {@code GET {api}/meetings/{id}}, de la que se toma solo {@code
 *       start_url}.
 * </ul>
 *
 * <p><b>De la respuesta de crear no se toma el enlace general ni la contraseña</b> (`RN-AC-026`):
 * no salen de esta clase. <b>Con plazo corto</b>, aplicado al cliente, como en {@code
 * shared/video}.
 */
@Component
public class ZoomApiClient implements ZoomMeetings {

  private static final Logger LOG = LoggerFactory.getLogger(ZoomApiClient.class);
  private static final DateTimeFormatter LOCAL =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  private final ZoomSettings ajustes;
  private final RestClient http;
  private final Clock reloj;

  private String token;
  private Instant caduca = Instant.EPOCH;

  @Autowired
  public ZoomApiClient(ZoomSettings ajustes, RestClient.Builder constructor) {
    this(
        ajustes, constructor.requestFactory(fabrica(ajustes.timeout())).build(), Clock.systemUTC());
  }

  /** Para las pruebas: el cliente ya construido, con el servidor simulado detrás, y el reloj. */
  ZoomApiClient(ZoomSettings ajustes, RestClient http, Clock reloj) {
    this.ajustes = ajustes;
    this.http = http;
    this.reloj = reloj;
  }

  @Override
  public long create(MeetingSpec reunion) {
    Map<String, Object> cuerpo = cuerpoDe(reunion);
    Map<String, Object> ajustesDeReunion = new LinkedHashMap<>();
    ajustesDeReunion.put("approval_type", 0);
    ajustesDeReunion.put("registration_type", 1);
    ajustesDeReunion.put("join_before_host", false);
    ajustesDeReunion.put("waiting_room", false);
    ajustesDeReunion.put("registrants_email_notification", false);
    ajustesDeReunion.put("registrants_confirmation_email", false);
    cuerpo.put("type", 2);
    cuerpo.put("settings", ajustesDeReunion);
    JsonNode creada =
        llamar(
            "crear la reunión",
            t ->
                http.post()
                    .uri(uri("/users/" + ajustes.hostUser() + "/meetings"))
                    .header("Authorization", "Bearer " + t)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(JsonNode.class));
    if (creada == null || !creada.path("id").canConvertToLong()) {
      throw new ZoomUnavailableException("Zoom no devolvió el identificador de la reunión");
    }
    return creada.path("id").asLong();
  }

  @Override
  public void update(long meetingId, MeetingSpec reunion) {
    Map<String, Object> cuerpo = cuerpoDe(reunion);
    llamar(
        "corregir la reunión",
        t ->
            http.patch()
                .uri(uri("/meetings/" + meetingId))
                .header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity());
  }

  @Override
  public void delete(long meetingId) {
    try {
      llamar(
          "borrar la reunión",
          t ->
              http.delete()
                  .uri(uri("/meetings/" + meetingId))
                  .header("Authorization", "Bearer " + t)
                  .retrieve()
                  .toBodilessEntity());
    } catch (YaNoExiste fallo) {
      LOG.info("La reunión {} ya no existía en Zoom al borrarla", meetingId);
    }
  }

  @Override
  public Registrant register(long meetingId, String email, String firstName, String lastName) {
    Map<String, Object> cuerpo = new LinkedHashMap<>();
    cuerpo.put("email", email);
    cuerpo.put("first_name", firstName);
    cuerpo.put("last_name", lastName);
    JsonNode registro =
        llamar(
            "registrar a la persona",
            t ->
                http.post()
                    .uri(uri("/meetings/" + meetingId + "/registrants"))
                    .header("Authorization", "Bearer " + t)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(JsonNode.class));
    String id = registro == null ? "" : registro.path("registrant_id").asText("");
    String enlace = registro == null ? "" : registro.path("join_url").asText("");
    if (id.isBlank() || enlace.isBlank()) {
      throw new ZoomUnavailableException("Zoom no devolvió el registro de la persona");
    }
    return new Registrant(id, enlace);
  }

  @Override
  public String hostLink(long meetingId) {
    JsonNode reunion =
        llamar(
            "pedir el enlace de anfitrión",
            t ->
                http.get()
                    .uri(uri("/meetings/" + meetingId))
                    .header("Authorization", "Bearer " + t)
                    .retrieve()
                    .body(JsonNode.class));
    String enlace = reunion == null ? "" : reunion.path("start_url").asText("");
    if (enlace.isBlank()) {
      throw new ZoomUnavailableException("Zoom no devolvió el enlace de anfitrión");
    }
    return enlace;
  }

  // ---------------------------------------------------------------------------

  private Map<String, Object> cuerpoDe(MeetingSpec reunion) {
    Map<String, Object> cuerpo = new LinkedHashMap<>();
    cuerpo.put("topic", reunion.topic());
    if (reunion.agenda() != null) {
      cuerpo.put("agenda", reunion.agenda());
    }
    // Hora local de la zona de la plataforma, y la zona aparte: así la muestra Zoom.
    cuerpo.put("start_time", reunion.startsAt().atZoneSameInstant(ajustes.zona()).format(LOCAL));
    cuerpo.put("timezone", ajustes.timeZone());
    cuerpo.put("duration", reunion.durationMinutes());
    return cuerpo;
  }

  /** Llama con el token vigente; un {@code 401} lo descarta y reintenta una vez. */
  private <T> T llamar(String que, Function<String, T> llamada) {
    try {
      return llamada.apply(tokenVigente());
    } catch (RestClientResponseException fallo) {
      if (fallo.getStatusCode().value() == 401) {
        olvidarToken();
        try {
          return llamada.apply(tokenVigente());
        } catch (RestClientResponseException otraVez) {
          throw traducir(que, otraVez.getStatusCode());
        } catch (ResourceAccessException sinRespuesta) {
          throw new ZoomUnavailableException("Zoom no respondió a tiempo al " + que);
        }
      }
      throw traducir(que, fallo.getStatusCode());
    } catch (ResourceAccessException sinRespuesta) {
      throw new ZoomUnavailableException("Zoom no respondió a tiempo al " + que);
    }
  }

  private RuntimeException traducir(String que, HttpStatusCode estado) {
    if (estado.value() == 404) {
      return new YaNoExiste();
    }
    LOG.warn("Zoom respondió {} al {}", estado.value(), que);
    return new ZoomUnavailableException("Zoom respondió con un error al " + que);
  }

  private synchronized String tokenVigente() {
    if (!ajustes.configurado()) {
      throw new ZoomUnavailableException(
          "La plataforma no tiene configuradas las credenciales de Zoom");
    }
    if (token != null && reloj.instant().isBefore(caduca.minus(Duration.ofMinutes(1)))) {
      return token;
    }
    String basica =
        Base64.getEncoder()
            .encodeToString(
                (ajustes.clientId() + ":" + ajustes.clientSecret())
                    .getBytes(StandardCharsets.UTF_8));
    URI direccion =
        UriComponentsBuilder.fromUriString(ajustes.oauthUrl())
            .queryParam("grant_type", "account_credentials")
            .queryParam("account_id", ajustes.accountId())
            .build()
            .toUri();
    try {
      JsonNode respuesta =
          http.post()
              .uri(direccion)
              .header("Authorization", "Basic " + basica)
              .retrieve()
              .body(JsonNode.class);
      String nuevo = respuesta == null ? "" : respuesta.path("access_token").asText("");
      if (nuevo.isBlank()) {
        throw new ZoomUnavailableException("Zoom no entregó el token de acceso");
      }
      token = nuevo;
      caduca = reloj.instant().plusSeconds(respuesta.path("expires_in").asLong(3600));
      return token;
    } catch (RestClientResponseException fallo) {
      LOG.warn("Zoom respondió {} al pedir el token", fallo.getStatusCode().value());
      throw new ZoomUnavailableException("Zoom rechazó las credenciales de la plataforma");
    } catch (ResourceAccessException sinRespuesta) {
      throw new ZoomUnavailableException("Zoom no respondió a tiempo al pedir el token");
    }
  }

  private synchronized void olvidarToken() {
    token = null;
  }

  private URI uri(String camino) {
    return URI.create(ajustes.apiBaseUrl() + camino);
  }

  /** Con {@code java.net.http}: {@code HttpURLConnection} no sabe enviar {@code PATCH}. */
  private static JdkClientHttpRequestFactory fabrica(Duration plazo) {
    JdkClientHttpRequestFactory fabrica =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(plazo).build());
    fabrica.setReadTimeout(plazo);
    return fabrica;
  }

  /** Un {@code 404} de Zoom: la reunión no existe. Solo {@link #delete} lo tolera. */
  private static final class YaNoExiste extends ZoomUnavailableException {
    private static final long serialVersionUID = 1L;

    YaNoExiste() {
      super("La reunión ya no existe en Zoom");
    }
  }
}
