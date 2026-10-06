package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.AccountKind;
import com.factech.nexus.modules.movements.domain.models.EntryEvent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de las cuentas y los asientos (`RN-MV-041`, `RN-MV-042`; `RF-MV-019` · `plan.md` §3).
 *
 * <p><b>Solo lo usa el {@code Ledger}</b> para escribir: el orden de bloqueo, el saldo que deja
 * cada asiento y la negativa a dejar una cuenta de persona en negativo tienen que ser idénticos en
 * los cinco requerimientos que mueven saldos.
 */
public interface LedgerRepository {

  /**
   * La cuenta de ese titular, tipo y moneda, <b>creándola si no existe</b>. Idempotente con dos
   * hilos: {@code INSERT … ON CONFLICT DO NOTHING} y relectura.
   *
   * @param titular la persona, o nulo para una cuenta de la empresa
   */
  UUID accountOf(UUID titular, AccountKind tipo, UUID moneda);

  /**
   * Suma {@code delta} al saldo, <b>bloqueando la fila</b>, y devuelve el saldo que queda. Vacío si
   * la cuenta es de una persona y quedaría en negativo: la sentencia no toca nada y la transacción
   * sigue viva. {@code ck_accounts_saldo} lo defiende además en el esquema.
   */
  Optional<BigDecimal> move(UUID cuenta, BigDecimal delta);

  /** Escribe un asiento. El cuadre lo comprueba el esquema al cerrar la transacción. */
  void post(
      UUID movimiento,
      UUID pago,
      UUID cuenta,
      EntryEvent evento,
      BigDecimal importe,
      BigDecimal saldoTras,
      OffsetDateTime cuando);

  /**
   * El saldo de esa cuenta, o cero si no existe. Sin bloquear: es para responder, no para decidir.
   */
  BigDecimal balanceOf(UUID titular, AccountKind tipo, UUID moneda);

  /** Todas las cuentas de una persona, con su moneda (`RF-MV-022`). */
  List<BalanceRow> balancesOf(UUID persona);

  /** El historial de las cuentas de una persona, del más reciente al más antiguo (`RF-MV-022`). */
  List<EntryRow> entriesOf(UUID persona, EntryFilter filtro, int offset, int limit);

  long countEntries(UUID persona, EntryFilter filtro);

  record BalanceRow(UUID currencyId, String currencyCode, String kind, BigDecimal balance) {}

  /**
   * Los filtros del historial (`RF-MV-022`).
   *
   * @param accounts las cuentas, combinadas con «o» (R-62, 06-10-2026); vacío es sin filtro
   */
  record EntryFilter(
      UUID currencyId, Set<String> accounts, OffsetDateTime from, OffsetDateTime to) {
    public EntryFilter {
      accounts = accounts == null ? Set.of() : Set.copyOf(accounts);
    }
  }

  record EntryRow(
      UUID id,
      OffsetDateTime createdAt,
      String account,
      UUID currencyId,
      String currencyCode,
      BigDecimal amount,
      BigDecimal balanceAfter,
      String event,
      UUID movementId,
      String movementCode,
      String movementType,
      String movementStatus,
      String concept,
      String rejectionReason) {}
}
