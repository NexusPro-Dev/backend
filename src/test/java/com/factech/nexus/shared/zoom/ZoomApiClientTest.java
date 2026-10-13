package com.factech.nexus.shared.zoom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.factech.nexus.shared.zoom.ZoomMeetings.Frequency;
import com.factech.nexus.shared.zoom.ZoomMeetings.MeetingSpec;
import com.factech.nexus.shared.zoom.ZoomMeetings.Registrant;
import com.factech.nexus.shared.zoom.ZoomMeetings.Repeat;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@link ZoomApiClient} contra un servidor simulado (`RF-AC-044` · `T-02`): lo que se le pide a
 * Zoom, el token reutilizado y los fallos. <b>Sin red.</b>
 */
class ZoomApiClientTest {

  private static final String TOKEN =
      "https://zoom.test/oauth/token?grant_type=account_credentials&account_id=cuenta";
  private static final ZoomSettings AJUSTES =
      new ZoomSettings(
          "cuenta",
          "cliente",
          "secreto",
          "anfitrion@negocio.co",
          "https://api.zoom.test/v2",
          "https://zoom.test/oauth/token",
          "America/Bogota",
          Duration.ofSeconds(2));
  private static final MeetingSpec REUNION =
      new MeetingSpec(
          "Velas en vivo", "Agenda", OffsetDateTime.parse("2026-10-15T19:00:00-05:00"), 90);

  private final RestClient.Builder constructor = RestClient.builder();
  private final MockRestServiceServer zoom = MockRestServiceServer.bindTo(constructor).build();
  private final ZoomApiClient cliente =
      new ZoomApiClient(
          AJUSTES,
          constructor.build(),
          Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC));

  private void token() {
    zoom.expect(requestTo(TOKEN))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Basic Y2xpZW50ZTpzZWNyZXRv"))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"tk\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
  }

  @Test
  @DisplayName(
      "crea la reunión con registro obligatorio, sin correos, en la zona de Bogotá; no toma el"
          + " enlace general")
  void crea() {
    token();
    zoom.expect(requestTo("https://api.zoom.test/v2/users/anfitrion@negocio.co/meetings"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer tk"))
        .andExpect(
            content()
                .json(
                    """
                    {"topic":"Velas en vivo","agenda":"Agenda","start_time":"2026-10-15T19:00:00",
                     "timezone":"America/Bogota","duration":90,"type":2,
                     "settings":{"approval_type":0,"registration_type":1,"join_before_host":false,
                                 "registrants_email_notification":false,
                                 "registrants_confirmation_email":false}}
                    """))
        .andRespond(
            withSuccess(
                "{\"id\":81234567890,\"join_url\":\"https://zoom.us/j/81234567890\","
                    + "\"password\":\"x\"}",
                MediaType.APPLICATION_JSON));

    assertThat(cliente.create(REUNION)).isEqualTo(81234567890L);
    zoom.verify();
  }

  @Test
  @DisplayName(
      "`RF-AC-055` — una semanal es tipo 8 con el día de la semana contado desde el domingo y el"
          + " número de fechas; quitarla al corregir vuelve al tipo 2")
  void repite() {
    token();
    // El 15-10-2026 es jueves: 5 contando desde el domingo.
    zoom.expect(requestTo("https://api.zoom.test/v2/users/anfitrion@negocio.co/meetings"))
        .andExpect(
            content()
                .json(
                    """
                    {"type":8,"recurrence":{"type":2,"repeat_interval":1,"weekly_days":"5",
                                            "end_times":10}}
                    """))
        .andRespond(withSuccess("{\"id\":3}", MediaType.APPLICATION_JSON));
    zoom.expect(requestTo("https://api.zoom.test/v2/meetings/3"))
        .andExpect(method(HttpMethod.PATCH))
        .andExpect(
            content()
                .json(
                    """
                    {"type":8,"recurrence":{"type":3,"repeat_interval":1,"monthly_day":15,
                                            "end_times":4}}
                    """))
        .andRespond(withSuccess());
    zoom.expect(requestTo("https://api.zoom.test/v2/meetings/3"))
        .andExpect(method(HttpMethod.PATCH))
        .andExpect(content().json("{\"type\":2}"))
        .andRespond(withSuccess());

    assertThat(
            cliente.create(
                new MeetingSpec(
                    REUNION.topic(),
                    REUNION.agenda(),
                    REUNION.startsAt(),
                    90,
                    new Repeat(Frequency.WEEKLY, 10, DayOfWeek.THURSDAY, 0))))
        .isEqualTo(3L);
    cliente.update(
        3,
        new MeetingSpec(
            REUNION.topic(),
            REUNION.agenda(),
            REUNION.startsAt(),
            90,
            new Repeat(Frequency.MONTHLY, 4, null, 15)));
    cliente.update(3, REUNION);
    zoom.verify();
  }

  @Test
  @DisplayName("el token se reutiliza mientras no caduca")
  void reutilizaElToken() {
    zoom.expect(ExpectedCount.once(), requestTo(TOKEN))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"tk\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
    zoom.expect(ExpectedCount.twice(), requestTo("https://api.zoom.test/v2/meetings/5"))
        .andRespond(
            withSuccess(
                "{\"start_url\":\"https://zoom.us/s/5?zak=a\"}", MediaType.APPLICATION_JSON));

    assertThat(cliente.hostLink(5)).isEqualTo("https://zoom.us/s/5?zak=a");
    assertThat(cliente.hostLink(5)).isEqualTo("https://zoom.us/s/5?zak=a");
    zoom.verify();
  }

  @Test
  @DisplayName("registra con correo y nombre y devuelve el enlace personal")
  void registra() {
    token();
    zoom.expect(requestTo("https://api.zoom.test/v2/meetings/7/registrants"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json("{\"email\":\"ana@x.co\",\"first_name\":\"Ana\",\"last_name\":\"Ruiz\"}"))
        .andRespond(
            withSuccess(
                "{\"registrant_id\":\"r1\",\"join_url\":\"https://zoom.us/w/7?tk=abc\"}",
                MediaType.APPLICATION_JSON));
    assertThat(cliente.register(7, "ana@x.co", "Ana", "Ruiz"))
        .isEqualTo(new Registrant("r1", "https://zoom.us/w/7?tk=abc"));
  }

  @Test
  @DisplayName("borrar una reunión que ya no existe no es un fallo; un 500 sí")
  void borrar() {
    token();
    zoom.expect(requestTo("https://api.zoom.test/v2/meetings/9"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    zoom.expect(requestTo("https://api.zoom.test/v2/meetings/10"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    cliente.delete(9);
    assertThatThrownBy(() -> cliente.delete(10)).isInstanceOf(ZoomUnavailableException.class);
    zoom.verify();
  }

  @Test
  @DisplayName("sin credenciales no llama y es ZoomUnavailableException")
  void sinCredenciales() {
    ZoomApiClient sinNada =
        new ZoomApiClient(
            new ZoomSettings(null, null, null, null, null, null, null, null),
            constructor.build(),
            Clock.systemUTC());
    assertThatThrownBy(() -> sinNada.create(REUNION))
        .isInstanceOf(ZoomUnavailableException.class)
        .hasMessageContaining("credenciales");
    zoom.verify();
  }
}
