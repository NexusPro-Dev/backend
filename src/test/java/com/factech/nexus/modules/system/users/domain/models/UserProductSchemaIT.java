package com.factech.nexus.modules.system.users.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `RF-SP-024` · `T-70` — lo que `user_products` admite y lo que no, probado por los DOS lados.
 *
 * <h2>Por qué esta prueba existe</h2>
 *
 * <p>`V38` convirtió `user_memberships` en `user_products` y con ello <b>las dos restricciones del
 * invariante pasaron a ser parciales</b> (`RN-SP-056`). El cambio no se ve en ninguna prueba de
 * comportamiento: el listado de usuarios y la confirmación de una venta siguen pasando con las
 * restricciones viejas, porque en sus escenarios nadie tiene <b>dos cosas a la vez</b>. Lo que
 * rompe es el día que alguien compra un bot teniendo membresía, y entonces no falla la operación —
 * falla el {@code INSERT} con un mensaje del motor.
 *
 * <h2>Y por qué las dos mitades</h2>
 *
 * <p>La condición parcial es la mitad del diseño, y una sola mitad de esta prueba la deja pasar:
 *
 * <ul>
 *   <li>Probar solo que dos membresías abiertas se rechazan — lo pasa también el único <b>total</b>
 *       que había antes de `V38`, que además prohíbe tener un bot y una membresía.
 *   <li>Probar solo que dos posesiones sin nivel conviven — lo pasa también un esquema <b>sin
 *       ningún único</b>, que dejaría a una persona con dos niveles a la vez.
 * </ul>
 *
 * <p>Juntas, solo las pasa la restricción parcial (`RN-SP-014` sobre `RN-SP-056`).
 */
class UserProductSchemaIT extends IntegrationTestBase {

  @Autowired private JdbcTemplate jdbc;

  private UUID ana;
  private UUID bot;
  private UUID otroBot;
  private UUID membresia;

  @BeforeEach
  void sembrar() {
    limpiar();
    // EL SUELO SE REPONE, no se da por hecho: otras suites vacian `memberships`
    // enteras y el orden alfabetico decide quien corre antes de esta clase.
    reponerElSuelo(jdbc);
    ana = persona("up-ana");
    bot = producto("UP_BOT_UNO");
    otroBot = producto("UP_BOT_DOS");
    membresia = suelo();
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  @Test
  @DisplayName("`RN-SP-056` — dos posesiones SIN nivel, abiertas y solapadas, se admiten")
  void dosPosesionesSinNivelConviven() {
    poseer(ana, bot, null);

    // Es el caso corriente —dos bots comprados el mismo mes— y lo que el único
    // TOTAL de antes de `V38` prohibía sin que nadie lo notara hasta el primer
    // cliente con dos cosas.
    assertThatCode(() -> poseer(ana, otroBot, null)).doesNotThrowAnyException();

    assertThat(abiertas(ana)).isEqualTo(2);
  }

  @Test
  @DisplayName("`RN-SP-056` — un bot y una membresía a la vez se admiten")
  void elBotYLaMembresiaConviven() {
    poseer(ana, bot, null);

    assertThatCode(() -> poseer(ana, null, membresia)).doesNotThrowAnyException();

    assertThat(abiertas(ana)).isEqualTo(2);
    assertThat(conNivel(ana)).isEqualTo(1);
  }

  @Test
  @DisplayName("`RN-SP-014` — dos membresías abiertas siguen siendo imposibles")
  void dosMembresiasAbiertasSeRechazan() {
    poseer(ana, null, membresia);

    // Y lo sostiene el índice, no una comprobación previa: dos concesiones
    // simultáneas leerían una tabla sin la segunda fila y las dos creerían que
    // pueden. Es la mitad del invariante que `V38` NO relajó.
    // LA MISMA membresia sirve, y es lo que hace la prueba mas fuerte: lo que
    // el unico prohibe es que la persona tenga DOS FILAS CON NIVEL abiertas,
    // sea cual sea el nivel. Pedir dos membresias distintas ataba la prueba a
    // que el catalogo tuviera dos, y el catalogo lo vacian otras suites.
    assertThatThrownBy(() -> poseer(ana, null, membresia))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(conNivel(ana)).isEqualTo(1);
  }

  @Test
  @DisplayName("`ck_user_products_origen` — una fila sin producto y sin membresía no describe nada")
  void laFilaVaciaSeRechaza() {
    // `product_id` nulo es legítimo —el suelo de `RN-SP-018` y la semilla— y
    // esta es la comprobación que impide que esa nulidad se extienda.
    assertThatThrownBy(() -> poseer(ana, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(abiertas(ana)).isZero();
  }

  @Test
  @DisplayName("`uq_user_products_linea` — varias posesiones SIN línea conviven")
  void lasPosesionesSinLineaConviven() {
    // El único sobre `movement_detail_id` admite varios nulos, y hace falta que
    // lo haga: todo lo que no viene de una venta lo lleva nulo, empezando por el
    // suelo que recibe cada persona al registrarse.
    poseer(ana, bot, null);

    assertThatCode(() -> poseer(ana, otroBot, null)).doesNotThrowAnyException();

    assertThat(sinLinea(ana)).isEqualTo(2);
  }

  @Test
  @DisplayName("`ck_user_products_validity_days` — una vigencia de cero días nace vencida")
  void laVigenciaDeCeroDiasSeRechaza() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_products (id, user_id, product_id, validity_days)"
                        + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), 0)",
                    UUID.randomUUID().toString(),
                    ana.toString(),
                    bot.toString()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------------------------------------------------------------------------
  // Siembra
  // ---------------------------------------------------------------------------

  private void poseer(UUID persona, UUID producto, UUID nivel) {
    jdbc.update(
        "INSERT INTO user_products (id, user_id, product_id, membership_id)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid))",
        UUID.randomUUID().toString(),
        persona.toString(),
        producto == null ? null : producto.toString(),
        nivel == null ? null : nivel.toString());
  }

  private int abiertas(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_products WHERE user_id = CAST(? AS uuid) AND closed_at IS NULL",
        Integer.class,
        persona.toString());
  }

  private int conNivel(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_products WHERE user_id = CAST(? AS uuid)"
            + " AND closed_at IS NULL AND membership_id IS NOT NULL",
        Integer.class,
        persona.toString());
  }

  private int sinLinea(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_products WHERE user_id = CAST(? AS uuid)"
            + " AND movement_detail_id IS NULL",
        Integer.class,
        persona.toString());
  }

  private UUID persona(String usuario) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, username, email, first_name, last_name, password_hash, status,"
            + " country_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, 'Ana', 'Ruiz', 'x', 'ACTIVO',"
            + " (SELECT id FROM countries ORDER BY code LIMIT 1))",
        id.toString(),
        usuario,
        usuario + "@nexus.test");
    return id;
  }

  /** Un `BOT`, que es lo que se posee sin conceder nivel (`RN-PM-002`). */
  private UUID producto(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (id, code, type, name, price, currency_id, scope, implementation)"
            + " VALUES (CAST(? AS uuid), ?, 'BOT', ?, 100, "
            + " (SELECT id FROM currencies ORDER BY code LIMIT 1), 'NINGUNO', 'AUTOMATICA')",
        id.toString(),
        codigo,
        codigo);
    return id;
  }

  /**
   * El suelo, que es la unica membresia que SIEMPRE esta (`RN-SP-008`: sembrada y no borrable).
   * Buscar por posicion ataria esta clase al orden de ejecucion.
   */
  private UUID suelo() {
    return jdbc.queryForObject("SELECT id FROM memberships WHERE code = 'BECA'", UUID.class);
  }

  private void limpiar() {
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'up-%')");
    jdbc.update(
        "DELETE FROM user_products WHERE product_id IN"
            + " (SELECT id FROM products WHERE code LIKE 'UP_BOT_%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'up-%'");
    jdbc.update("DELETE FROM products WHERE code LIKE 'UP_BOT_%'");
  }
}
