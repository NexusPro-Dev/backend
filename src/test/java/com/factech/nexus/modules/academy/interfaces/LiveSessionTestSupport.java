package com.factech.nexus.modules.academy.interfaces;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lo que comparten las suites de las clases en vivo (`RF-AC-042` a `RF-AC-054`): horas futuras
 * fáciles de leer, el cuerpo de programar y una clase escrita directo en la tabla.
 */
final class LiveSessionTestSupport {

  private LiveSessionTestSupport() {}

  /** Mañana a esa hora, en Bogotá, como texto sin zona. */
  static String manana(int hora) {
    return OffsetDateTime.now(ZoneOffset.ofHours(-5))
        .plusDays(1)
        .withHour(hora)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .toLocalDateTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
  }

  static String cuerpo(
      String titulo, UUID curso, String inicio, String fin, String membresias, String servicios) {
    return """
        {"title":"%s","description":"Agenda de %s",%s"startsAt":"%s","endsAt":"%s",
         "membershipIds":[%s],"productIds":[%s]}
        """
        .formatted(
            titulo,
            titulo,
            curso == null ? "" : "\"courseId\":\"" + curso + "\",",
            inicio,
            fin,
            membresias,
            servicios);
  }

  static String ids(UUID... ids) {
    StringBuilder texto = new StringBuilder();
    for (UUID id : ids) {
      texto.append(texto.isEmpty() ? "" : ",").append('"').append(id).append('"');
    }
    return texto.toString();
  }

  /**
   * Una clase escrita directo, con el inicio y el fin en minutos desde ahora (negativos para el
   * pasado): lo que la API no deja crear —una clase en curso o terminada—.
   */
  static UUID clase(
      JdbcTemplate jdbc, UUID curso, UUID autor, int desdeMinutos, int hastaMinutos, long reunion) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO live_sessions (id, course_id, title, starts_at, ends_at, zoom_meeting_id,"
            + " created_by) VALUES (?, ?, 'Directa', now() + make_interval(mins => ?),"
            + " now() + make_interval(mins => ?), ?, ?)",
        id,
        curso,
        desdeMinutos,
        hastaMinutos,
        reunion,
        autor);
    return id;
  }

  static void abrirAMembresia(JdbcTemplate jdbc, UUID clase, UUID membresia) {
    jdbc.update(
        "INSERT INTO live_session_memberships (live_session_id, membership_id) VALUES (?, ?)",
        clase,
        membresia);
  }
}
