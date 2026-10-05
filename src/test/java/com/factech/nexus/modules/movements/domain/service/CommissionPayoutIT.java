package com.factech.nexus.modules.movements.domain.service;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.modules.movements.application.CommissionPayout;
import com.factech.nexus.modules.movements.application.CommissionPayout.PayoutOrder;
import com.factech.nexus.modules.movements.application.CommissionPayout.PayoutResult;
import com.factech.nexus.shared.persistence.MinorUnits;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * `RF-MV-024` — abonar el pago de un lote de comisión, por la interfaz que `MV` publica. Se invoca
 * como la invocará `CM`: dentro de una transacción.
 */
class CommissionPayoutIT extends IntegrationTestBase {

  @Autowired private CommissionPayout abono;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transacciones;

  private UUID persona;

  @BeforeEach
  void sembrar() {
    limpiar();
    persona = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, 'cp-persona', 'cp-persona@factech.co', 'Nombre', 'Apellido', 'x', false,
                'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        persona);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  private PayoutResult enTransaccion(PayoutOrder orden) {
    return new TransactionTemplate(transacciones).execute(estado -> abono.pay(orden));
  }

  private PayoutOrder orden(String importe, UUID lote) {
    return new PayoutOrder(
        persona, UUID.fromString(USD), new BigDecimal(importe), lote, "LOT-20260926-ABC123");
  }

  @Test
  @DisplayName(
      "CA-MV-269 — abona el importe redondeado a la mitad hacia arriba, desde COMISIONES, con un"
          + " PAGO_COMISION confirmado que cita el lote")
  void abona() {
    PayoutResult hecho = enTransaccion(orden("12.3450", UUID.randomUUID()));

    assertThat(hecho.amount()).isEqualByComparingTo("12.35");
    assertThat(hecho.code()).startsWith("PCM-");
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("12.35");
    assertThat(
            jdbc.queryForObject(
                "SELECT status || '|' || concept FROM movements WHERE id = ?",
                String.class,
                hecho.movementId()))
        .isEqualTo("CONFIRMADA|Comisión del lote LOT-20260926-ABC123");
    assertThat(
            MinorUnits.fromMinor(
                jdbc.queryForObject(
                    "SELECT balance FROM accounts WHERE user_id IS NULL AND kind = 'COMISIONES'",
                    Long.class)))
        .isEqualByComparingTo("-12.35");
  }

  @Test
  @DisplayName("CA-MV-270 — la misma orden dos veces devuelve el mismo abono y abona una vez")
  void unaVezPorLote() {
    UUID lote = UUID.randomUUID();
    PayoutResult primero = enTransaccion(orden("10.00", lote));
    PayoutResult segundo = enTransaccion(orden("10.00", lote));

    assertThat(segundo.movementId()).isEqualTo(primero.movementId());
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("10.00");
  }

  @Test
  @DisplayName("CA-MV-271 — un lote que redondea a cero deja un movimiento de cero sin asientos")
  void loteDeCero() {
    PayoutResult hecho = enTransaccion(orden("0.0040", UUID.randomUUID()));

    assertThat(hecho.amount()).isEqualByComparingTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_entries WHERE movement_id = ?",
                Integer.class,
                hecho.movementId()))
        .isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-272 — si la transacción de CM se revierte, no queda ni abono ni asiento; y sin"
          + " transacción, falla")
  void atomico() {
    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              abono.pay(orden("10.00", UUID.randomUUID()));
              estado.setRollbackOnly();
            });
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movements", Integer.class)).isZero();
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("0");

    assertThatThrownBy(() -> abono.pay(orden("10.00", UUID.randomUUID())))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("CA-MV-273 y CA-MV-274 — el abono está en el historial y queda auditado")
  void historialYAuditoria() {
    PayoutResult hecho = enTransaccion(orden("7.00", UUID.randomUUID()));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_entries WHERE movement_id = ? AND event = 'ABONO'",
                Integer.class,
                hecho.movementId()))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'movements'"
                    + " AND entity_id = ? AND action = 'CREATE'",
                Integer.class,
                hecho.movementId()))
        .isEqualTo(1);
  }

  private void limpiar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM users WHERE username = 'cp-persona'");
  }
}
