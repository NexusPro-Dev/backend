package com.factech.nexus.shared.zoom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * El doble de Zoom de la suite: <b>ninguna prueba llama a Zoom</b> (`ac.md` §6.1, bloque 8).
 *
 * <p>Un bean {@code @Primary} en el paquete de la aplicación, y no un {@code @MockitoBean} por
 * suite: un bean sustituido abre un contexto nuevo por suite, y la suite se queda sin conexiones
 * (ver `ci-suites-fragiles-por-orden-y-uuid`). Anota lo que recibe, guarda las reuniones vivas y
 * <b>falla a voluntad</b> con {@link #fallarLaSiguiente()}. Quien lo use lo limpia con {@link
 * #reiniciar()}.
 */
@Component
@Primary
public class FakeZoomMeetings implements ZoomMeetings {

  /** El enlace general que Zoom daría: ninguna respuesta ni fila debe contenerlo. */
  public static final String ENLACE_GENERAL = "https://zoom.us/j/GENERAL-NO-DEBE-SALIR";

  private final AtomicLong secuencia = new AtomicLong(90_000_000_000L);
  private final Map<Long, MeetingSpec> reuniones =
      Collections.synchronizedMap(new LinkedHashMap<>());
  private final List<String> llamadas = Collections.synchronizedList(new ArrayList<>());
  private volatile boolean fallar;

  public synchronized void reiniciar() {
    reuniones.clear();
    llamadas.clear();
    fallar = false;
  }

  /** La próxima llamada lanza {@link ZoomUnavailableException}. */
  public void fallarLaSiguiente() {
    fallar = true;
  }

  public List<String> llamadas() {
    synchronized (llamadas) {
      return List.copyOf(llamadas);
    }
  }

  public Map<Long, MeetingSpec> reuniones() {
    synchronized (reuniones) {
      return Map.copyOf(reuniones);
    }
  }

  private void anotar(String llamada) {
    llamadas.add(llamada);
    if (fallar) {
      fallar = false;
      throw new ZoomUnavailableException("fallo simulado");
    }
  }

  @Override
  public long create(MeetingSpec reunion) {
    anotar("create");
    long id = secuencia.incrementAndGet();
    reuniones.put(id, reunion);
    return id;
  }

  @Override
  public void update(long meetingId, MeetingSpec reunion) {
    anotar("update:" + meetingId);
    reuniones.put(meetingId, reunion);
  }

  @Override
  public void delete(long meetingId) {
    anotar("delete:" + meetingId);
    reuniones.remove(meetingId);
  }

  @Override
  public Registrant register(long meetingId, String email, String firstName, String lastName) {
    anotar("register:" + meetingId + ":" + email);
    return new Registrant(
        "reg-" + Math.abs(email.hashCode()),
        "https://zoom.us/w/" + meetingId + "?tk=" + Math.abs(email.hashCode()));
  }

  @Override
  public String hostLink(long meetingId) {
    anotar("host:" + meetingId);
    return "https://zoom.us/s/" + meetingId + "?zak=anfitrion";
  }
}
