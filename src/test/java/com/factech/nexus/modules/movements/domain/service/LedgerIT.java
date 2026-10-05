package com.factech.nexus.modules.movements.domain.service;

import static com.factech.nexus.modules.movements.LedgerFixtures.llenarBilletera;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.shared.persistence.MinorUnits;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * `RN-MV-042` en el esquema: el cuadre diferido y el saldo copiado (`RF-MV-019` · `plan.md` §11).
 */
class LedgerIT extends IntegrationTestBase {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreditService abonos;
  @Autowired private PlatformTransactionManager transacciones;

  private UUID persona;

  @BeforeEach
  void sembrar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM users WHERE username = 'lg-persona'");
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, 'lg-persona', 'lg-persona@factech.co', 'Nombre', 'Apellido', 'x', false,
                'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        persona);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM users WHERE username = 'lg-persona'");
  }

  @Test
  @DisplayName("un evento que no suma cero aborta el COMMIT, y no queda nada")
  void unAsientoCojoNoEntra() {
    llenarBilletera(abonos, persona, "10.00");
    UUID movimiento = jdbc.queryForObject("SELECT id FROM movements", UUID.class);
    UUID cuenta =
        jdbc.queryForObject(
            "SELECT id FROM accounts WHERE user_id = ? AND kind = 'BILLETERA'",
            UUID.class,
            persona);

    assertThatThrownBy(
            () ->
                new TransactionTemplate(transacciones)
                    .executeWithoutResult(
                        estado ->
                            jdbc.update(
                                "INSERT INTO movement_entries (id, movement_id, account_id, event,"
                                    + " amount, balance_after) VALUES (?, ?, ?, 'SOLICITUD', 1, 11)",
                                UUID.randomUUID(),
                                movimiento,
                                cuenta)))
        .hasStackTraceContaining("RN-MV-042");

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_entries WHERE event = 'SOLICITUD'", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("el saldo de cada cuenta es la suma de sus asientos, y balance_after lo sigue")
  void elSaldoEsLaCopiaDeSusAsientos() {
    llenarBilletera(abonos, persona, "10.00");
    llenarBilletera(abonos, persona, "5.50");

    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM accounts a
                 WHERE a.balance <> (SELECT COALESCE(sum(e.amount), 0)
                                       FROM movement_entries e WHERE e.account_id = a.id)
                """,
                Integer.class))
        .isZero();
    assertThat(
            MinorUnits.fromMinor(
                jdbc.queryForObject(
                    """
                    SELECT e.balance_after FROM movement_entries e
                      JOIN accounts a ON a.id = e.account_id
                     WHERE a.user_id = ? ORDER BY e.created_at DESC, e.id DESC LIMIT 1
                    """,
                    Long.class,
                    persona)))
        .isEqualByComparingTo("15.50");
  }
}
