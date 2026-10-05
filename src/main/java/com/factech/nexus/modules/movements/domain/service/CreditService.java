package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.CommissionPayout;
import com.factech.nexus.modules.movements.application.LedgerMovementResponse;
import com.factech.nexus.modules.movements.application.WithdrawalRequests;
import com.factech.nexus.modules.movements.domain.models.AccountKind;
import com.factech.nexus.modules.movements.domain.models.EntryEvent;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementCode;
import com.factech.nexus.modules.movements.domain.models.RejectionReason;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementTypeView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.WithdrawalRow;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que entra en una billetera sin ningún pago detrás: el bono (`RF-MV-023`) y el abono de un lote
 * de comisión (`RF-MV-024`). Los dos nacen <b>confirmados</b>, con su concepto y su clave de
 * idempotencia, y mueven una cuenta de la empresa hacia la billetera en un evento {@code ABONO}.
 *
 * <p><b>Un servicio y no dos</b>: la única diferencia es de dónde sale el dinero —{@code BONOS} o
 * {@code COMISIONES}— y quién lo pide.
 */
@Service
public class CreditService implements CommissionPayout {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  private final MovementRepository movimientos;
  private final LedgerRepository libro;
  private final Ledger asientos;
  private final LedgerMovements comun;
  private final ClientCatalog personas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public CreditService(
      MovementRepository movimientos,
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      ClientCatalog personas,
      AuditWriter auditoria) {
    this(movimientos, libro, asientos, comun, personas, auditoria, Clock.systemUTC());
  }

  CreditService(
      MovementRepository movimientos,
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      ClientCatalog personas,
      AuditWriter auditoria,
      Clock reloj) {
    this.movimientos = movimientos;
    this.libro = libro;
    this.asientos = asientos;
    this.comun = comun;
    this.personas = personas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  /** Lo que salió de un bono: el movimiento, y si es nuevo o ya existía (`FA-001`). */
  public record BonusResult(LedgerMovementResponse bonus, boolean created) {}

  // ---------------------------------------------------------------------------
  // `RF-MV-023` — otorgar un bono
  // ---------------------------------------------------------------------------

  @Transactional
  public BonusResult grantBonus(WithdrawalRequests.Bonus peticion, String claveRecibida) {
    // 1. Todo lo que no cuesta una consulta, antes de nada.
    IdempotencyKey clave = new IdempotencyKey(claveRecibida);
    if (peticion == null || peticion.userId() == null) {
      throw LedgerMovements.invalido("userId", "VAL-001", "La persona es obligatoria.");
    }
    RejectionReason concepto = conceptoDelBono(peticion.concept());
    CurrencyView moneda = comun.moneda(peticion.currencyId());
    BigDecimal importe = LedgerMovements.importe(peticion.amount(), moneda);

    // 2. ¿La misma petición otra vez?
    Optional<UUID> previo = movimientos.findIdByIdempotencyKey(clave.value());
    if (previo.isPresent()) {
      WithdrawalRow ya =
          movimientos.findWithoutLines(previo.get(), "BONO").orElseThrow(BonusConflict::claveAjena);
      boolean misma =
          ya.userId().equals(peticion.userId())
              && ya.currencyId().equals(moneda.id())
              && ya.amount().compareTo(importe) == 0;
      if (!misma) {
        throw BonusConflict.claveAjena();
      }
      return new BonusResult(comun.respuesta(ya.id(), "BONO"), false);
    }

    // 3. La persona existe y no está eliminada. Bloqueada o en espera, recibe
    //    igual: el saldo es suyo; lo que no puede es retirarlo (`CA-MV-264`).
    personas
        .findClient(peticion.userId())
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "EX-002",
                    "La persona indicada no existe.",
                    List.of(new FieldError("userId", "EX-002", "La persona indicada no existe."))));

    UUID bono =
        abonar(
            "BONO",
            AccountKind.BONOS,
            peticion.userId(),
            moneda.id(),
            importe,
            concepto.value(),
            clave.value());
    return new BonusResult(comun.respuesta(bono, "BONO"), true);
  }

  private static RejectionReason conceptoDelBono(String valor) {
    try {
      return new RejectionReason(valor);
    } catch (com.factech.nexus.shared.error.ValidationException e) {
      throw LedgerMovements.invalido(
          "concept", "VAL-004", "El motivo del bono es obligatorio, hasta 500 caracteres.");
    }
  }

  /** La clave ya se usó para otro bono (`EX-005`). */
  private static final class BonusConflict {
    static BusinessRuleException claveAjena() {
      String mensaje = "La clave de idempotencia ya se usó en otra petición.";
      return new BusinessRuleException(
          "EX-005", mensaje, List.of(new FieldError(IdempotencyKey.CABECERA, "EX-005", mensaje)));
    }
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-024` — abonar el pago de un lote (interfaz publicada)
  // ---------------------------------------------------------------------------

  /**
   * {@inheritDoc}
   *
   * <p><b>{@code MANDATORY}</b>: sin la transacción de `CM` no tiene sentido —abonar sin marcar el
   * lote es el estado que `RN-CM-030` prohíbe— y así se convierte en un error visible en lugar de
   * un abono huérfano (`plan.md` §3).
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public PayoutResult pay(PayoutOrder orden) {
    if (orden == null
        || orden.userId() == null
        || orden.currencyId() == null
        || orden.batchId() == null
        || orden.amount() == null
        || orden.amount().signum() < 0) {
      throw new IllegalArgumentException("Orden de abono incompleta o con importe negativo.");
    }
    CurrencyView moneda = comun.moneda(orden.currencyId());
    // `RN-MV-044`: el libro guarda dinero con los decimales de la moneda, a la
    // mitad hacia arriba. Desde V65 el lote ya llega con dos (ADR-006) y esto no
    // cambia nada; se queda por si una moneda volviera a admitir más.
    BigDecimal importe = orden.amount().setScale(moneda.decimalPlaces(), RoundingMode.HALF_UP);
    String clave = "lote-" + orden.batchId();

    Optional<UUID> previo = movimientos.findIdByIdempotencyKey(clave);
    if (previo.isPresent()) {
      WithdrawalRow ya =
          movimientos
              .findWithoutLines(previo.get(), "PAGO_COMISION")
              .orElseThrow(() -> new IllegalStateException("La clave del lote es de otro tipo."));
      return new PayoutResult(ya.id(), ya.code(), ya.amount());
    }

    String concepto =
        "Comisión del lote " + (orden.batchCode() == null ? orden.batchId() : orden.batchCode());
    UUID abono =
        abonar(
            "PAGO_COMISION",
            AccountKind.COMISIONES,
            orden.userId(),
            moneda.id(),
            importe,
            concepto,
            clave);
    WithdrawalRow hecho =
        movimientos
            .findWithoutLines(abono, "PAGO_COMISION")
            .orElseThrow(() -> new IllegalStateException("El abono desapareció."));
    return new PayoutResult(hecho.id(), hecho.code(), hecho.amount());
  }

  // ---------------------------------------------------------------------------

  /**
   * El movimiento confirmado y, si el importe no es cero, el evento {@code ABONO}: la cuenta de la
   * empresa baja y la billetera sube. Un importe cero no escribe asientos —un asiento de cero no
   * existe— y deja el movimiento igual (`RN-MV-044`).
   */
  private UUID abonar(
      String tipoCodigo,
      AccountKind origen,
      UUID persona,
      UUID moneda,
      BigDecimal importe,
      String concepto,
      String clave) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    MovementTypeView tipo =
        movimientos
            .findTypeByCode(tipoCodigo)
            .orElseThrow(() -> new IllegalStateException("Falta el tipo " + tipoCodigo + "."));
    Movement movimiento =
        Movement.abono(
            tipo.id(),
            persona,
            moneda,
            MovementCode.generar(tipo.prefix(), ahora),
            movimientos
                .findTypeStatus(tipo.id(), "REGISTRADO")
                .orElseThrow(() -> new IllegalStateException("Falta el estado REGISTRADO.")),
            importe,
            concepto,
            clave,
            ahora);
    movimientos.saveWithoutLines(movimiento, () -> MovementCode.generar(tipo.prefix(), ahora));

    if (importe.signum() > 0) {
      UUID empresa = libro.accountOf(null, origen, moneda);
      UUID billetera = libro.accountOf(persona, AccountKind.BILLETERA, moneda);
      asientos
          .apply(
              movimiento.getId(),
              null,
              EntryEvent.ABONO,
              List.of(
                  new Ledger.Leg(empresa, importe.negate()), new Ledger.Leg(billetera, importe)),
              ahora)
          .orElseThrow(() -> new IllegalStateException("Un abono no puede fallar por saldo."));
    }

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            movimiento.getId(),
            ChangeAction.CREATE,
            Map.of("after", movimiento.instantanea(), "event", EntryEvent.ABONO.name())));
    return movimiento.getId();
  }
}
