package com.factech.nexus.modules.system.users.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.system.users.application.CurrentProductsLookup;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `CurrentProductsLookup` (`RF-AC-033` · `T-03`): vigente es empezada, sin fin pasado y sin cerrar,
 * y la fila de nivel sin producto no aparece.
 *
 * <p>Siembra personas {@code cpl-} y productos {@code CPL_}, y los borra al terminar —las
 * posesiones antes que los productos: la clave foránea es {@code RESTRICT}—.
 */
class CurrentProductsLookupIT extends IntegrationTestBase {

  @Autowired private CurrentProductsLookup lookup;
  @Autowired private JdbcTemplate jdbc;

  private UUID persona;

  @BeforeEach
  void sembrar() {
    limpiar();
    persona = UUID.randomUUID();
    String usuario = "cpl-" + persona.toString().substring(0, 8);
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Ana', 'Prueba', 'x', false, 'ACTIVO',
                (SELECT id FROM countries ORDER BY code LIMIT 1))
        """,
        persona,
        usuario,
        usuario + "@nexus.test");
    // El suelo: una fila de nivel sin producto, que no debe aparecer.
    jdbc.update(
        "INSERT INTO user_products (id, user_id, membership_id)"
            + " SELECT gen_random_uuid(), ?, id FROM memberships WHERE code = 'BECA'",
        persona);
  }

  @AfterEach
  void limpiar() {
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'cpl-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'cpl-%'");
    jdbc.update("DELETE FROM products WHERE code LIKE 'CPL\\_%'");
  }

  @Test
  @DisplayName("devuelve solo los vigentes: ni vencido, ni cerrado, ni futuro, ni el nivel")
  void soloLosVigentes() {
    UUID vigente = producto("CPL_VIGENTE");
    UUID sinFin = producto("CPL_SIN_FIN");
    UUID vencido = producto("CPL_VENCIDO");
    UUID cerrado = producto("CPL_CERRADO");
    UUID futuro = producto("CPL_FUTURO");
    posesion(vigente, "now() - interval '1 day'", "now() + interval '1 day'", null);
    posesion(sinFin, "now() - interval '1 day'", null, null);
    posesion(vencido, "now() - interval '2 days'", "now() - interval '1 day'", null);
    posesion(cerrado, "now() - interval '2 days'", null, "now() - interval '1 day'");
    posesion(futuro, "now() + interval '1 day'", null, null);

    assertThat(lookup.currentProductIdsOf(persona)).containsExactlyInAnyOrder(vigente, sinFin);
  }

  @Test
  @DisplayName("una persona sin productos, una inexistente o un nulo: vacío")
  void vacio() {
    assertThat(lookup.currentProductIdsOf(persona)).isEmpty();
    assertThat(lookup.currentProductIdsOf(UUID.randomUUID())).isEmpty();
    assertThat(lookup.currentProductIdsOf(null)).isEmpty();
  }

  private UUID producto(String codigo) {
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

  private void posesion(UUID producto, String desde, String hasta, String cierre) {
    jdbc.update(
        "INSERT INTO user_products (id, user_id, product_id, started_at, ends_at, closed_at)"
            + " VALUES (gen_random_uuid(), ?, ?, "
            + desde
            + ", "
            + (hasta == null ? "NULL" : hasta)
            + ", "
            + (cierre == null ? "NULL" : cierre)
            + ")",
        persona,
        producto);
  }
}
