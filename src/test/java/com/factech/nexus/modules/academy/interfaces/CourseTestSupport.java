package com.factech.nexus.modules.academy.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Lo que comparten las suites de cursos: limpieza, siembra directa por SQL y actores.
 *
 * <p><b>El instructor es una persona de `SP` con un rol que porta {@code courses:teach}</b>, y se
 * siembra por SQL porque es un fixture: construirlo por la API de usuarios haría que un fallo de
 * aquel alta se confundiera con el caso bajo prueba. Todo lo sembrado lleva el prefijo {@code ac-}
 * en el nombre de usuario y {@code INSTRUCTOR_AC_} en el código del rol, y la limpieza lo borra por
 * prefijo: la base es una para toda la suite.
 */
final class CourseTestSupport {

  static final UUID ADMIN_SEMBRADO = UUID.fromString("01a02a33-4c00-7002-9c4f-5e7ad1000002");

  /** Un generador para toda la clase: ver `CourseCategoryTestSupport.IDS`. */
  static final UuidV7Generator IDS = new UuidV7Generator();

  private CourseTestSupport() {}

  static void limpiar(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM course_memberships");
    jdbc.update("DELETE FROM course_products");
    jdbc.update("DELETE FROM course_category_items");
    jdbc.update("DELETE FROM lessons");
    jdbc.update("DELETE FROM course_modules");
    jdbc.update("DELETE FROM courses");
    // Las portadas que ya no señala nadie: sin `ON DELETE`, se borran DESPUÉS
    // de la fila que las señalaba (`V44`).
    jdbc.update(
        "DELETE FROM academy_images i WHERE NOT EXISTS (SELECT 1 FROM course_categories k"
            + " WHERE k.cover_image_id = i.id) AND NOT EXISTS (SELECT 1 FROM courses c"
            + " WHERE c.cover_image_id = i.id) AND NOT EXISTS (SELECT 1 FROM course_modules m"
            + " WHERE m.cover_image_id = i.id)");
    jdbc.update(
        "DELETE FROM audit_change_log WHERE module = 'AC' AND entity IN ('courses',"
            + " 'course_category_items', 'course_products', 'course_memberships',"
            + " 'course_modules', 'lessons')");
    jdbc.update(
        "DELETE FROM audit_deletion_log WHERE module = 'AC' AND entity IN ('courses',"
            + " 'course_category_items', 'course_products', 'course_memberships',"
            + " 'course_modules', 'lessons')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'ac-%')");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'ac-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ac-%'");
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code LIKE"
            + " 'INSTRUCTOR_AC_%')");
    jdbc.update("DELETE FROM roles WHERE code LIKE 'INSTRUCTOR_AC_%'");
  }

  /** Un rol de prueba, colgado de ADMIN, que porta exactamente {@code courses:teach}. */
  static UUID rolInstructor(JdbcTemplate jdbc) {
    UUID id = UUID.randomUUID();
    String sufijo = String.valueOf(Math.abs(id.getLeastSignificantBits() % 1_000_000));
    jdbc.update(
        """
        INSERT INTO roles (id, code, name, description, role_type, parent_role_id, status, is_system)
        VALUES (?, ?, ?, 'Porta courses:teach.', 'FUNCIONARIO', ?, 'ACTIVO', false)
        """,
        id,
        "INSTRUCTOR_AC_" + sufijo,
        "Instructor de prueba " + sufijo,
        ADMIN_SEMBRADO);
    jdbc.update(
        "INSERT INTO role_permissions (role_id, permission_id)"
            + " SELECT ?, id FROM permissions WHERE code = 'courses:teach'",
        id);
    return id;
  }

  /** Una persona viva con el rol dado (o sin rol si es nulo), con el suelo de `RN-SP-018`. */
  static UUID persona(JdbcTemplate jdbc, String nombre, String apellido, UUID rol) {
    UUID id = UUID.randomUUID();
    String usuario = "ac-" + id.toString().substring(0, 8);
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, ?, ?, 'no-se-usa-en-esta-prueba', false, 'ACTIVO',
                (SELECT id FROM countries ORDER BY code LIMIT 1))
        """,
        id,
        usuario,
        usuario + "@nexus.test",
        nombre,
        apellido);
    jdbc.update(
        "INSERT INTO user_products (id, user_id, membership_id)"
            + " SELECT gen_random_uuid(), ?, id FROM memberships WHERE code = 'BECA'",
        id);
    if (rol != null) {
      jdbc.update(
          "INSERT INTO user_roles (user_id, role_id, role_type)"
              + " SELECT ?, id, role_type FROM roles WHERE id = ?",
          id,
          rol);
    }
    return id;
  }

  /** Un instructor listo: persona viva con un rol que porta {@code courses:teach}. */
  static UUID instructor(JdbcTemplate jdbc) {
    return persona(jdbc, "Juan", "Pérez", rolInstructor(jdbc));
  }

  /** Un curso directo en la tabla, inactivo, sin descripciones. */
  static UUID curso(JdbcTemplate jdbc, String titulo, UUID instructor, int orden) {
    return curso(jdbc, titulo, instructor, orden, "PRINCIPIANTE", null, null, "INACTIVO");
  }

  static UUID curso(
      JdbcTemplate jdbc,
      String titulo,
      UUID instructor,
      int orden,
      String dificultad,
      String corta,
      String larga,
      String estado) {
    UUID id = IDS.next();
    jdbc.update(
        "INSERT INTO courses (id, title, instructor_id, difficulty, short_description,"
            + " long_description, display_order, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        titulo,
        instructor,
        dificultad,
        corta,
        larga,
        orden,
        estado);
    return id;
  }

  static void retirar(JdbcTemplate jdbc, UUID id) {
    jdbc.update("UPDATE courses SET deleted_at = now() WHERE id = ?", id);
  }

  /** Un módulo directo en la tabla, en el estado que se le dé. */
  static UUID modulo(JdbcTemplate jdbc, UUID curso, String titulo, int orden, String estado) {
    UUID id = IDS.next();
    jdbc.update(
        "INSERT INTO course_modules (id, course_id, title, display_order, status)"
            + " VALUES (?, ?, ?, ?, ?)",
        id,
        curso,
        titulo,
        orden,
        estado);
    return id;
  }

  static void retirarModulo(JdbcTemplate jdbc, UUID id) {
    jdbc.update("UPDATE course_modules SET deleted_at = now() WHERE id = ?", id);
  }

  /** Una lección directa en la tabla: tipo, contenido (nulo si no hay), duración y estado. */
  static UUID leccion(
      JdbcTemplate jdbc,
      UUID modulo,
      String titulo,
      String tipo,
      String contenido,
      int segundos,
      int orden,
      String estado) {
    UUID id = IDS.next();
    jdbc.update(
        "INSERT INTO lessons (id, module_id, type, title, content, duration_seconds,"
            + " display_order, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        modulo,
        tipo,
        titulo,
        contenido,
        segundos,
        orden,
        estado);
    return id;
  }

  /** Una lección de texto activa con contenido: la que hace ofrecible a un módulo. */
  static UUID leccionActiva(JdbcTemplate jdbc, UUID modulo, String titulo, int segundos) {
    return leccion(jdbc, modulo, titulo, "TEXTO", "# " + titulo, segundos, 0, "ACTIVO");
  }

  static void retirarLeccion(JdbcTemplate jdbc, UUID id) {
    jdbc.update("UPDATE lessons SET deleted_at = now() WHERE id = ?", id);
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

  static String cuerpo(String titulo, UUID instructor, String dificultad, int orden) {
    return """
        {"title":"%s","instructorId":"%s","difficulty":"%s","displayOrder":%d}
        """
        .formatted(titulo, instructor, dificultad, orden);
  }
}
