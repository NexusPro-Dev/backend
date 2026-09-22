package com.factech.nexus.modules.system.teams.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Lo que comparten las suites de equipos: limpieza, siembra directa por SQL y actores.
 *
 * <p><b>La limpieza no es higiene: es lo que impide romper a otras clases.</b> La base es una para
 * toda la suite, y un equipo que sobreviva a su clase cambia el total de un listado ajeno —o choca
 * por nombre con el que otra clase siembra— sin que el fallo diga de dónde vino. Se limpia al
 * empezar <b>y</b> al terminar, que es la lección de `CA-SP-683`.
 */
final class TeamTestSupport {

  /**
   * UN generador para toda la clase, no uno por fila: el v7 solo es monótono dentro del mismo
   * milisegundo si el contador vive en la misma instancia. Es la misma lección que dejó escrita
   * `CourseCategoryTestSupport` el 21-09-2026, cuando CI —más rápido que la máquina de desarrollo—
   * sacó dos filas del mismo milisegundo en orden aleatorio.
   */
  static final UuidV7Generator IDS = new UuidV7Generator();

  private TeamTestSupport() {}

  static void limpiar(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM team_members");
    jdbc.update("DELETE FROM teams");
    jdbc.update("DELETE FROM audit_change_log WHERE entity IN ('teams', 'team_members')");
    jdbc.update("DELETE FROM audit_deletion_log WHERE entity = 'teams'");
  }

  /**
   * Las personas que siembra una clase, borradas por su nombre de usuario.
   *
   * <p><b>{@link #limpiar} no las toca a propósito</b>: borrar de {@code users} a ciegas se
   * llevaría por delante a las veinte de la semilla de desarrollo y a las que siembran otras
   * suites. Pero quien crea personas tiene que retirarlas, o la segunda prueba de su misma clase
   * choca contra {@code uq_users_email} —que es exactamente lo que pasó la primera vez que
   * `RF-SP-064` corrió su fixture—. Después de {@link #limpiar}, porque {@code
   * fk_team_members_user} es {@code ON DELETE RESTRICT} y una pertenencia viva impide borrar a su
   * dueño.
   */
  static void borrarPersonas(JdbcTemplate jdbc, String... usuarios) {
    for (String usuario : usuarios) {
      jdbc.update("DELETE FROM users WHERE username = ?", usuario);
    }
  }

  /** Un equipo directo en la tabla, vivo y activo. */
  static UUID equipo(JdbcTemplate jdbc, String nombre) {
    return equipo(jdbc, nombre, "ACTIVO", null);
  }

  static UUID equipo(JdbcTemplate jdbc, String nombre, String estado, String descripcion) {
    UUID id = IDS.next();
    jdbc.update(
        "INSERT INTO teams (id, name, description, status) VALUES (?, ?, ?, ?)",
        id,
        nombre,
        descripcion,
        estado);
    return id;
  }

  static void eliminar(JdbcTemplate jdbc, UUID id) {
    jdbc.update("UPDATE teams SET deleted_at = now() WHERE id = ?", id);
  }

  /**
   * Una pertenencia vigente, escrita a mano: `RF-SP-069` todavía no existe y el contrato del
   * detalle y del recuento ya es el definitivo. Es lo que hizo `RF-SP-061` con las filas `HOTLINK`
   * que nadie escribía.
   */
  static UUID pertenencia(JdbcTemplate jdbc, UUID equipo, UUID persona) {
    UUID id = IDS.next();
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id) VALUES (?, ?, ?)", id, equipo, persona);
    return id;
  }

  /**
   * Una pertenencia <b>cerrada</b>: historial, no equipo de hoy.
   *
   * <p>Es lo que distingue «cuántos hay» de «cuántos pasaron» (`RN-SP-052`), y sin ella el recuento
   * de `RF-SP-064` sería indistinguible de un {@code count(*)} sobre la tabla. La cierra una hora
   * después de abrirla porque {@code ck_team_members_periodo} exige que el fin sea posterior al
   * comienzo.
   */
  static UUID pertenenciaCerrada(JdbcTemplate jdbc, UUID equipo, UUID persona) {
    UUID id = IDS.next();
    jdbc.update(
        "INSERT INTO team_members (id, team_id, user_id, started_at, ended_at)"
            + " VALUES (?, ?, ?, now() - interval '2 hours', now() - interval '1 hour')",
        id,
        equipo,
        persona);
    return id;
  }

  /**
   * Una persona con el país que el esquema exige; sin rol: quién puede pertenecer lo decide 069.
   */
  static UUID persona(JdbcTemplate jdbc, String usuario) {
    UUID id = IDS.next();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id) VALUES (CAST(? AS uuid), ?, ?, 'Persona', 'De prueba', 'x', 'ACTIVO',"
            + " (SELECT id FROM countries WHERE code = 'COL'))",
        id.toString(),
        usuario,
        usuario + "@factech.co");
    return id;
  }

  static RequestPostProcessor con(String... permisos) {
    return con(UUID.randomUUID(), permisos);
  }

  static RequestPostProcessor con(UUID actor, String... permisos) {
    return user(actor.toString())
        .authorities(Arrays.stream(permisos).map(p -> (GrantedAuthority) () -> p).toList());
  }
}
