package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.BalancesResponse;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.AccountKind;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository.BalanceRow;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository.EntryFilter;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository.EntryRow;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-MV-022`: los saldos propios y su historial. Dos lecturas sobre lo que el {@code Ledger}
 * escribe, con el alcance en la sentencia: solo las cuentas de quien pregunta.
 */
@Service
public class BalanceService {

  private static final Set<String> CUENTAS_DE_PERSONA =
      Set.of(AccountKind.BILLETERA.name(), AccountKind.RETENIDO.name(), AccountKind.PUNTOS.name());

  private final LedgerRepository libro;
  private final AuthenticatedActor actor;
  private final Pagination paginacion;

  public BalanceService(LedgerRepository libro, AuthenticatedActor actor, Pagination paginacion) {
    this.libro = libro;
    this.actor = actor;
    this.paginacion = paginacion;
  }

  /** Una fila del historial (`RF-MV-022` · `spec.md` §6.4). */
  public record EntryResponse(
      UUID id,
      OffsetDateTime createdAt,
      String account,
      SaleResponse.Money currency,
      BigDecimal amount,
      BigDecimal balanceAfter,
      String event,
      Movement movement) {

    /** El movimiento que produjo el asiento. */
    public record Movement(
        UUID id, String code, String type, String status, String concept, String rejectionReason) {}
  }

  @Transactional(readOnly = true)
  public List<BalancesResponse> balances() {
    Map<UUID, BigDecimal[]> porMoneda = new LinkedHashMap<>();
    Map<UUID, String> codigos = new LinkedHashMap<>();
    for (BalanceRow fila : libro.balancesOf(actor.id())) {
      BigDecimal[] tres =
          porMoneda.computeIfAbsent(
              fila.currencyId(),
              k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
      codigos.put(fila.currencyId(), fila.currencyCode());
      switch (AccountKind.valueOf(fila.kind())) {
        case BILLETERA -> tres[0] = fila.balance();
        case RETENIDO -> tres[1] = fila.balance();
        case PUNTOS -> tres[2] = fila.balance();
        default -> {
          // Una cuenta de la empresa nunca lleva user_id (ck_accounts_kind).
        }
      }
    }
    List<BalancesResponse> resultado = new ArrayList<>(porMoneda.size());
    porMoneda.forEach(
        (moneda, tres) ->
            resultado.add(
                new BalancesResponse(
                    new SaleResponse.Money(moneda, codigos.get(moneda)),
                    tres[0],
                    tres[1],
                    tres[2])));
    return resultado;
  }

  @Transactional(readOnly = true)
  public PageResponse<EntryResponse> entries(
      Integer page,
      Integer size,
      UUID currencyId,
      String account,
      OffsetDateTime from,
      OffsetDateTime to) {
    // Los 400 SALEN JUNTOS: quien se equivocó en dos filtros lo sabe de una vez.
    List<FieldError> errores = new ArrayList<>();
    String cuenta = account == null || account.isBlank() ? null : account.trim().toUpperCase();
    if (cuenta != null && !CUENTAS_DE_PERSONA.contains(cuenta)) {
      errores.add(
          new FieldError("account", "VAL-002", "La cuenta es BILLETERA, RETENIDO o PUNTOS."));
    }
    if (from != null && to != null && from.isAfter(to)) {
      errores.add(
          new FieldError("from", "VAL-003", "La fecha inicial no puede ser posterior a la final."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }
    Pagination.Slice pagina = paginacion.resolver(page, size);
    EntryFilter filtro = new EntryFilter(currencyId, cuenta, from, to);
    UUID quien = actor.id();

    List<EntryResponse> filas = new ArrayList<>();
    for (EntryRow e : libro.entriesOf(quien, filtro, pagina.offset(), pagina.size())) {
      filas.add(
          new EntryResponse(
              e.id(),
              e.createdAt(),
              e.account(),
              new SaleResponse.Money(e.currencyId(), e.currencyCode()),
              e.amount(),
              e.balanceAfter(),
              e.event(),
              new EntryResponse.Movement(
                  e.movementId(),
                  e.movementCode(),
                  e.movementType(),
                  e.movementStatus(),
                  e.concept(),
                  e.rejectionReason())));
    }
    return PageResponse.de(filas, libro.countEntries(quien, filtro), pagina.page(), pagina.size());
  }
}
