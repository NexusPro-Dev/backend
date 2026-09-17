package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * `RF-MV-003` · `CA-MV-097` — dos confirmaciones simultáneas conceden <b>una</b> vez.
 *
 * <p>Es lo que una pasarela hace: reentregar. Lo que lo sostiene es la transición condicionada al
 * estado anterior: la segunda espera el bloqueo de fila, despierta con la venta ya confirmada y
 * afecta cero filas sin haber leído las líneas.
 */
@AutoConfigureMockMvc
class ConfirmSaleConcurrencyIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String VIP = "01a04ad0-e800-7002-9c4f-5e7ad7000002";
  private static final String BECA = "01a04ad0-e800-7001-9c4f-5e7ad7000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID cliente;
  private UUID venta;

  @BeforeEach
  void sembrar() {
    limpiar();
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM memberships");
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color)
        VALUES (CAST(? AS uuid), 'VIP', 'VIP', 'Primer nivel de pago.', NULL, 3, '7E57C2'),
               (CAST(? AS uuid), 'BECA', 'Beca', 'Nivel de entrada.', CAST(? AS uuid), 4, '9E9E9E')
        """,
        VIP,
        BECA,
        VIP);

    cliente = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, 'cc-cliente', 'cc-cliente@factech.co', 'Nombre', 'Apellido', 'x', false,
                'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        cliente);
    darElSuelo(jdbc, cliente);

    UUID producto = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, 'CC_VIP', 'UPGRADE_MEMBRESIA', 'Ascenso', 'x',"
            + " CAST(? AS uuid), CAST(? AS uuid), 100.00, CAST(? AS uuid), 30, 'ACTIVO')",
        producto,
        BECA,
        VIP,
        USD);

    venta = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, user_id, payment_method_id, currency_id,
                               code, status, total_amount, discount_amount, payable_amount,
                               occurred_at)
        VALUES (?, CAST(? AS uuid), ?, CAST(? AS uuid), CAST(? AS uuid), ?, 'PENDIENTE',
                100.00, 0, 100.00, CAST(? AS timestamptz))
        """,
        venta,
        VENTA,
        cliente,
        TARJETA,
        USD,
        "VTA-" + venta.toString().substring(0, 8).toUpperCase(),
        OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC).toString());
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price, line_amount,
                                      validity_days, implementation)
        VALUES (?, ?, ?, ?, 'Ascenso', 'x', 1, 100.00, 100.00, 30, 'AUTOMATICA')
        """,
        UUID.randomUUID(),
        venta,
        producto,
        cliente);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("CA-MV-097 — dos confirmaciones a la vez: una 200, una 409, UNA membresía")
  void dosConfirmacionesALaVez() throws Exception {
    List<Outcome<Integer>> resultados = runTogether(2, indice -> confirmar());

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 200).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);

    // BECA cerrada y VIP abierta: dos filas, no tres.
    Integer periodos =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ?", Integer.class, cliente);
    assertThat(periodos).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT m.code FROM user_memberships um JOIN memberships m ON m.id = um.membership_id"
                    + " WHERE um.user_id = ? AND um.closed_at IS NULL",
                String.class,
                cliente))
        .isEqualTo("VIP");
  }

  private int confirmar() throws Exception {
    return mvc.perform(
            post("/api/v1/movements/{id}/confirmation", venta)
                .with(user(UUID.randomUUID().toString()).authorities(() -> "movements:confirm")))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code = 'CC_VIP'");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username = 'cc-cliente')");
    jdbc.update("DELETE FROM users WHERE username = 'cc-cliente'");
  }
}
