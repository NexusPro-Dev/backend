package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.domain.models.EntryEvent;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>El único que escribe asientos</b> (`RN-MV-042`; `RF-MV-019` · `plan.md` §3).
 *
 * <p>Recibe un evento con sus patas —cuenta y delta— y, en este orden: <b>ordena las cuentas por
 * identificador</b>, mueve cada saldo con la fila bloqueada, y escribe cada asiento con el saldo
 * que dejó. Cinco requerimientos mueven saldos, y los tres pasos tienen que ser idénticos en los
 * cinco: el primero que bloqueara en otro orden produciría el {@code 40P01} que este proyecto ya
 * pagó una vez.
 *
 * <p><b>No decide si el evento cuadra</b>: lo comprueba el esquema al cerrar la transacción ({@code
 * tg_movement_entries_cuadre}). Y <b>no traduce el saldo insuficiente</b> a un error de negocio:
 * devuelve vacío, y quien lo llama —que sabe qué operación era— lanza el suyo, lo que revierte
 * también las patas que ya se habían movido.
 */
@Component
public class Ledger {

  private final LedgerRepository libro;

  public Ledger(LedgerRepository libro) {
    this.libro = libro;
  }

  /** Una pata del evento: cuánto entra (positivo) o sale (negativo) de una cuenta. */
  public record Leg(UUID account, BigDecimal delta) {}

  /**
   * Aplica el evento.
   *
   * @return el saldo que dejó cada cuenta, o vacío si una cuenta de persona habría quedado en
   *     negativo —y entonces quien llama tiene que abortar la transacción—
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<Map<UUID, BigDecimal>> apply(
      UUID movimiento, UUID pago, EntryEvent evento, List<Leg> patas, OffsetDateTime cuando) {
    List<Leg> ordenadas = new ArrayList<>(patas);
    ordenadas.sort(Comparator.comparing(Leg::account));

    Map<UUID, BigDecimal> saldos = new LinkedHashMap<>();
    for (Leg pata : ordenadas) {
      Optional<BigDecimal> saldo = libro.move(pata.account(), pata.delta());
      if (saldo.isEmpty()) {
        return Optional.empty();
      }
      saldos.put(pata.account(), saldo.get());
    }
    for (Leg pata : patas) {
      libro.post(
          movimiento,
          pago,
          pata.account(),
          evento,
          pata.delta(),
          saldos.get(pata.account()),
          cuando);
    }
    return Optional.of(saldos);
  }
}
