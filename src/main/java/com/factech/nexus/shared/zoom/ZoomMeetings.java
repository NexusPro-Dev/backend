package com.factech.nexus.shared.zoom;

import java.time.OffsetDateTime;

/**
 * Lo que la plataforma le pide a Zoom (`requirements/ac.md` §5.2.15, `RN-AC-026`, `RN-AC-027`,
 * `RN-AC-030`): reuniones con registro obligatorio, el registro de quien entra y el enlace de
 * anfitrión.
 *
 * <p><b>Un puerto y no el cliente</b>, como {@code VideoDurationLookup}: las pruebas lo sustituyen
 * por un doble en memoria y ninguna llama a Zoom. <b>Todo fallo es {@link
 * ZoomUnavailableException}</b> —sin credenciales, sin respuesta a tiempo, o con un error de Zoom—
 * salvo borrar una reunión que ya no existe, que no es un fallo.
 */
public interface ZoomMeetings {

  /**
   * Crea una reunión programada con registro obligatorio y aprobación automática, sin entrar antes
   * que el anfitrión y sin los correos de Zoom.
   *
   * @return el identificador de la reunión
   */
  long create(MeetingSpec reunion);

  /** Corrige título, inicio y duración de la reunión. */
  void update(long meetingId, MeetingSpec reunion);

  /** Borra la reunión; si ya no existe en Zoom, no hace nada. */
  void delete(long meetingId);

  /**
   * Registra a una persona en la reunión. Zoom devuelve el mismo registro si el correo ya estaba
   * registrado.
   */
  Registrant register(long meetingId, String email, String firstName, String lastName);

  /** El enlace de inicio como anfitrión, recién pedido: caduca a las pocas horas. */
  String hostLink(long meetingId);

  /**
   * Lo que Zoom sabe de una clase.
   *
   * @param startsAt el inicio, con su zona; a Zoom se le da en la zona de la plataforma
   * @param durationMinutes de inicio a fin
   */
  record MeetingSpec(String topic, String agenda, OffsetDateTime startsAt, int durationMinutes) {}

  /** El registro de una persona: su identificador en Zoom y su enlace personal. */
  record Registrant(String registrantId, String joinUrl) {}
}
