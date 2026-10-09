package com.factech.nexus.modules.academy.interfaces;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lo que comparten las dos suites de consulta del progreso (`RF-AC-040`, `RF-AC-041`): una red
 * comercial con su cartera, y filas de progreso escritas directo en la tabla.
 *
 * <p><b>La red se borra antes que las personas</b>: {@code user_supervisors} y {@code
 * client_sellers} no tienen {@code ON DELETE}, y {@code CourseTestSupport.limpiar} borra las
 * personas {@code ac-}.
 */
final class ProgressTestSupport {

  static final UUID ADMIN = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");
  static final UUID MANAGER = UUID.fromString("01a02a33-4c00-7005-9c4f-5e7ad1000003");
  static final UUID AGENTE = UUID.fromString("01a02a33-4c00-7007-9c4f-5e7ad1000005");
  static final UUID CLIENTE = UUID.fromString("01a02a33-4c00-7008-9c4f-5e7ad1000008");

  private ProgressTestSupport() {}

  static void limpiar(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM lesson_progress");
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ac-%') OR seller_id IN (SELECT id FROM users WHERE username LIKE 'ac-%')");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ac-%') OR supervisor_id IN (SELECT id FROM users WHERE username LIKE 'ac-%')");
    ClassroomTestSupport.limpiar(jdbc);
  }

  static void reportaA(JdbcTemplate jdbc, UUID subordinado, UUID superior) {
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now())",
        subordinado,
        superior);
  }

  static void esClienteDe(JdbcTemplate jdbc, UUID cliente, UUID vendedor) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, 'REGISTRO')",
        cliente,
        vendedor);
  }

  /**
   * Una fila de progreso: segundos vistos, si está completada, y hace cuántos minutos fue la última
   * actividad (la primera, una hora antes).
   */
  static void progreso(
      JdbcTemplate jdbc,
      UUID persona,
      UUID leccion,
      int segundos,
      boolean completada,
      int haceMinutos) {
    jdbc.update(
        "INSERT INTO lesson_progress (user_id, lesson_id, watched_seconds, completed_at,"
            + " first_opened_at, last_opened_at) VALUES (?, ?, ?,"
            + " CASE WHEN ? THEN now() - make_interval(mins => ?) END,"
            + " now() - make_interval(mins => ?) - interval '1 hour',"
            + " now() - make_interval(mins => ?))",
        persona,
        leccion,
        segundos,
        completada,
        haceMinutos,
        haceMinutos,
        haceMinutos);
  }
}
