package com.factech.nexus.modules.movements.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de las cuentas de cobro (`RN-MV-055`; `RF-MV-035` a `RF-MV-039`, y el retiro de
 * `RF-MV-019`).
 *
 * <p><b>Toda escritura sobre las cuentas de una persona va precedida de {@link #lockOwner}</b>:
 * serializa por persona «¿es la primera?», «desmarca la anterior» y «¿ya la tiene?», que con dos
 * peticiones a la vez darían dos principales o un duplicado. Los índices parciales son la segunda
 * defensa.
 */
public interface PayoutAccountRepository {

  /** Bloqueo consultivo de transacción sobre las cuentas de esa persona. */
  void lockOwner(UUID userId);

  /**
   * Las cuentas de esa persona: la principal primero y después de la más reciente a la más antigua.
   * Con {@code includeDeleted}, también las dadas de baja, al final y de la más reciente a la más
   * antigua.
   */
  List<AccountRow> of(UUID userId, boolean includeDeleted);

  /** Una cuenta viva de esa persona; vacío si no existe, es ajena o está dada de baja. */
  Optional<AccountRow> findLiveOwn(UUID id, UUID userId);

  /** Si esa persona ya tiene viva esa cuenta, sin contar {@code except}. */
  boolean existsLive(UUID userId, UUID institutionId, String number, UUID except);

  boolean hasLive(UUID userId);

  void insert(
      UUID id,
      UUID userId,
      UUID institutionId,
      String accountType,
      String number,
      boolean principal,
      OffsetDateTime at);

  /** Quita la marca de principal a la que la tenga. Va antes de ponérsela a otra. */
  void unmarkPrincipal(UUID userId, OffsetDateTime at);

  void update(UUID id, String accountType, String number, boolean principal, OffsetDateTime at);

  /** Baja lógica, y sin la marca de principal ({@code ck_payout_accounts_baja}). */
  void softDelete(UUID id, OffsetDateTime at);

  /** Hace principal la viva más antigua de esa persona, si le queda alguna. */
  Optional<UUID> promoteOldest(UUID userId, OffsetDateTime at);

  /**
   * La cuenta a la que se paga un retiro (`RN-MV-056`): la indicada si viene, o la principal; viva
   * y de esa persona. La cuenta y su entidad quedan bloqueadas {@code FOR SHARE} hasta el final de
   * la transacción, de modo que lo que se copia es lo que existía.
   */
  Optional<AccountRow> findForWithdrawal(UUID userId, UUID id);

  record AccountRow(
      UUID id,
      UUID userId,
      UUID institutionId,
      String institutionCode,
      String institutionName,
      String institutionKind,
      boolean institutionActive,
      String accountType,
      String number,
      boolean principal,
      OffsetDateTime createdAt,
      OffsetDateTime deletedAt) {}
}
