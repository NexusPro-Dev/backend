package com.factech.nexus.modules.academy.interfaces;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lo que comparten las tres suites del aula (`RF-AC-033` a `RF-AC-035`): cursos ofrecidos, llaves,
 * alumnos con lo que tienen vigente y la limpieza.
 *
 * <p><b>Los servicios se siembran con el prefijo {@code ACL_}</b> y se borran al terminar, después
 * de las filas que los señalan —{@code course_products} y {@code user_products}—: ninguna de las
 * dos claves foráneas tiene {@code ON DELETE}, y decenas de suites empiezan con {@code DELETE FROM
 * products}.
 */
final class ClassroomTestSupport {

  static final UUID ORO = UUID.fromString("01a04ad0-e800-7004-9c4f-5e7ad7000004");
  static final UUID PLATINO = UUID.fromString("01a04ad0-e800-7003-9c4f-5e7ad7000003");

  private ClassroomTestSupport() {}

  static void limpiar(JdbcTemplate jdbc) {
    // `CourseTestSupport.limpiar` ya borra course_products y los user_products
    // de las personas `ac-`; los productos van después.
    CourseTestSupport.limpiar(jdbc);
    CourseCategoryTestSupport.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE product_id IN"
            + " (SELECT id FROM products WHERE code LIKE 'ACL\\_%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'ACL\\_%'");
  }

  /**
   * Un curso que se ofrece: activo, con sus dos descripciones y un módulo activo con una lección de
   * texto activa y con contenido, cerrada. Sin llaves: es de todos hasta que se le pongan.
   */
  static Ofrecido ofrecido(JdbcTemplate jdbc, String titulo, UUID instructor, int orden) {
    UUID curso =
        CourseTestSupport.curso(
            jdbc, titulo, instructor, orden, "PRINCIPIANTE", "Corta", "Larga", "ACTIVO");
    UUID modulo = CourseTestSupport.modulo(jdbc, curso, "Módulo de " + titulo, 0, "ACTIVO");
    UUID leccion = CourseTestSupport.leccionActiva(jdbc, modulo, "Lección de " + titulo, 60);
    return new Ofrecido(curso, modulo, leccion);
  }

  record Ofrecido(UUID curso, UUID modulo, UUID leccion) {}

  static void abrirAMembresia(JdbcTemplate jdbc, UUID curso, UUID membresia) {
    jdbc.update(
        "INSERT INTO course_memberships (course_id, membership_id) VALUES (?, ?)",
        curso,
        membresia);
  }

  static void abrirAServicio(JdbcTemplate jdbc, UUID curso, UUID servicio) {
    jdbc.update(
        "INSERT INTO course_products (course_id, product_id) VALUES (?, ?)", curso, servicio);
  }

  static void abrirLeccion(JdbcTemplate jdbc, UUID leccion) {
    jdbc.update("UPDATE lessons SET open = true WHERE id = ?", leccion);
  }

  /** Un servicio {@code BOT} activo. */
  static UUID servicio(JdbcTemplate jdbc, String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, price, currency_id, scope, implementation,"
            + " status) VALUES (?, ?, 'BOT', ?, 100,"
            + " (SELECT id FROM currencies ORDER BY code LIMIT 1), 'NINGUNO', 'AUTOMATICA',"
            + " 'ACTIVO')",
        id,
        codigo,
        "Servicio " + codigo);
    return id;
  }

  /** Un alumno con el suelo {@code BECA} vigente y sin servicios. */
  static UUID alumno(JdbcTemplate jdbc) {
    return CourseTestSupport.persona(jdbc, "Ana", "Alumna", null);
  }

  /** Cambia la membresía vigente del alumno por otra, vigente. */
  static void conMembresia(JdbcTemplate jdbc, UUID alumno, UUID membresia) {
    jdbc.update(
        "UPDATE user_products SET membership_id = ? WHERE user_id = ? AND membership_id IS NOT NULL",
        membresia,
        alumno);
  }

  /** Deja vencida la membresía del alumno: empezó hace dos días y terminó ayer. */
  static void conMembresiaVencida(JdbcTemplate jdbc, UUID alumno) {
    jdbc.update(
        "UPDATE user_products SET started_at = now() - interval '2 days',"
            + " ends_at = now() - interval '1 day' WHERE user_id = ? AND membership_id IS NOT NULL",
        alumno);
  }

  /** Le da un servicio al alumno, con el periodo que se indique en días desde hoy. */
  static void conServicio(
      JdbcTemplate jdbc, UUID alumno, UUID servicio, int desdeDias, Integer hastaDias) {
    if (hastaDias == null) {
      jdbc.update(
          "INSERT INTO user_products (id, user_id, product_id, started_at) VALUES"
              + " (gen_random_uuid(), ?, ?, now() + make_interval(days => ?))",
          alumno,
          servicio,
          desdeDias);
      return;
    }
    jdbc.update(
        "INSERT INTO user_products (id, user_id, product_id, started_at, ends_at) VALUES"
            + " (gen_random_uuid(), ?, ?, now() + make_interval(days => ?),"
            + " now() + make_interval(days => ?))",
        alumno,
        servicio,
        desdeDias,
        hastaDias);
  }

  static void cerrarServicios(JdbcTemplate jdbc, UUID alumno) {
    jdbc.update(
        "UPDATE user_products SET closed_at = now() WHERE user_id = ? AND product_id IS NOT NULL"
            + " AND started_at <= now()",
        alumno);
  }
}
