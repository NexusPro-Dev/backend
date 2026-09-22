package com.factech.nexus.modules.academy.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Lo que comparten las suites de categorías: limpieza, siembra directa por SQL y actores.
 *
 * <p><b>La limpieza no es higiene: es lo que impide romper a otras clases.</b> La base es una para
 * toda la suite, y una categoría que sobreviva a su clase cambia el total de un listado ajeno —o
 * choca por nombre con la que otra clase siembra— sin que el fallo diga de dónde vino.
 */
final class CourseCategoryTestSupport {

  /**
   * UN generador para toda la clase, no uno por fila: el v7 solo es monótono dentro del mismo
   * milisegundo si el contador vive en la misma instancia. Con uno nuevo por llamada, dos
   * categorías creadas en el mismo milisegundo salían en orden aleatorio y el desempate por
   * identificador (`CourseCategorySortField.POR_OMISION`) fallaba en CI, donde la máquina es más
   * rápida que la de desarrollo (21-09-2026).
   */
  static final UuidV7Generator IDS = new UuidV7Generator();

  private CourseCategoryTestSupport() {}

  static void limpiar(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM course_categories");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'AC'");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'AC'");
  }

  /** Una categoría directa en la tabla, viva. */
  static UUID categoria(JdbcTemplate jdbc, String nombre, int orden) {
    return categoria(jdbc, nombre, orden, "1E88E5", "chart-line", null);
  }

  static UUID categoria(
      JdbcTemplate jdbc, String nombre, int orden, String color, String icono, String descripcion) {
    UUID id = IDS.next();
    jdbc.update(
        "INSERT INTO course_categories (id, name, description, color, icon, display_order)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        id,
        nombre,
        descripcion,
        color,
        icono,
        orden);
    return id;
  }

  static void retirar(JdbcTemplate jdbc, UUID id) {
    jdbc.update("UPDATE course_categories SET deleted_at = now() WHERE id = ?", id);
  }

  static RequestPostProcessor con(String... permisos) {
    return con(UUID.randomUUID(), permisos);
  }

  static RequestPostProcessor con(UUID actor, String... permisos) {
    return user(actor.toString())
        .authorities(
            java.util.Arrays.stream(permisos)
                .map(p -> (org.springframework.security.core.GrantedAuthority) () -> p)
                .toList());
  }

  static String cuerpo(String nombre, String color, String icono, int orden) {
    return """
        {"name":"%s","color":"%s","icon":"%s","displayOrder":%d}
        """
        .formatted(nombre, color, icono, orden);
  }
}
