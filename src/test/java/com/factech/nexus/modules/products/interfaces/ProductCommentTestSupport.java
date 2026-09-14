package com.factech.nexus.modules.products.interfaces;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * La siembra que comparten las pruebas de las reseñas (`RF-PM-009` a `RF-PM-013`).
 *
 * <p>Productos de tipo BOT y no upgrades: {@code uq_products_upgrade_target} solo admite una pareja
 * origen-destino activa, y ese no es el punto de ninguna de estas pruebas. Las personas llevan el
 * prefijo {@code resena-} para que la limpieza no toque a nadie más.
 */
abstract class ProductCommentTestSupport extends IntegrationTestBase {

  static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired protected JdbcTemplate jdbc;

  protected UUID activo;
  protected UUID inactivo;
  protected UUID retirado;

  protected UUID ana;
  protected UUID luis;
  protected UUID admin;

  protected void sembrarBase() {
    limpiarResenas();
    activo = bot("RS_ACTIVO", "Bot reseñable", "ACTIVO", false);
    inactivo = bot("RS_INACTIVO", "Bot sin publicar", "INACTIVO", false);
    retirado = bot("RS_RETIRADO", "Bot retirado", "ACTIVO", true);
    ana = persona("resena-ana", "Ana", "Ruiz");
    luis = persona("resena-luis", "Luis", "Paz");
    admin = persona("resena-admin", "Adela", "Mora");
  }

  protected void limpiarResenas() {
    jdbc.update("DELETE FROM product_comments");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'PM'");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'PM'");
    jdbc.update("DELETE FROM audit_security_log");
    jdbc.update("DELETE FROM products WHERE code LIKE 'RS_%'");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'resena-%')");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'resena-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'resena-%'");
  }

  /** Con `products:comment`, y nada más: ni `products:read` ni ningún otro. */
  protected static RequestPostProcessor comoResenador(UUID quien) {
    return user(quien.toString()).authorities(() -> "products:comment");
  }

  /**
   * Un administrador de verdad: los cuatro de administración, la venta, el hotlink Y
   * `products:comment`.
   */
  protected static RequestPostProcessor comoAdministrador(UUID quien) {
    return user(quien.toString())
        .authorities(
            () -> "products:read",
            () -> "products:create",
            () -> "products:update",
            () -> "products:delete",
            () -> "products:sale",
            () -> "products:hotlink",
            () -> "products:comment");
  }

  /** Solo los de administración: `products:read` y compañía NO habilitan reseñar (`CA-PM-176`). */
  protected static RequestPostProcessor comoAdministradorSinComment(UUID quien) {
    return user(quien.toString())
        .authorities(
            () -> "products:read",
            () -> "products:create",
            () -> "products:update",
            () -> "products:delete");
  }

  protected UUID bot(String codigo, String nombre, String estado, boolean retirado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, description, price, currency_id, status,"
            + " scope, implementation, created_at, updated_at, deleted_at)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, 'Descripción de prueba', 10.00,"
            + " CAST(? AS uuid), ?, 'HOTLINKS', 'AUTOMATICA', now(), now(),"
            + " CASE WHEN ? THEN now() ELSE NULL END)",
        id.toString(),
        codigo,
        nombre,
        USD,
        estado,
        retirado);
    return id;
  }

  protected UUID persona(String username, String nombre, String apellido) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (CAST(? AS uuid), ?, ?, ?, ?, 'no-se-usa-en-esta-prueba', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id.toString(),
        username,
        username + "@nexus.test",
        nombre,
        apellido);
    return id;
  }

  /** Una reseña sembrada directamente, viva o retirada, con el instante que se le dé. */
  protected UUID resena(UUID producto, UUID autor, int puntuacion, String texto, boolean retirada) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO product_comments (id, product_id, user_id, rating, comment, created_at,"
            + " updated_at, deleted_at)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), ?, ?, now(), now(),"
            + " CASE WHEN ? THEN now() ELSE NULL END)",
        id.toString(),
        producto.toString(),
        autor.toString(),
        puntuacion,
        texto,
        retirada);
    return id;
  }

  protected long resenasVivas(UUID producto) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM product_comments WHERE product_id = CAST(? AS uuid)"
            + " AND deleted_at IS NULL",
        Long.class,
        producto.toString());
  }

  protected String cuerpo(int puntuacion, String texto) {
    return "{\"rating\": " + puntuacion + ", \"comment\": " + comillas(texto) + "}";
  }

  protected static String comillas(String texto) {
    return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
