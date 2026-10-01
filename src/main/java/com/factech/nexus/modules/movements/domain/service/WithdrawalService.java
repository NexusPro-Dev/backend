package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.LedgerMovementResponse;
import com.factech.nexus.modules.movements.application.WithdrawalRequests;
import com.factech.nexus.modules.movements.application.WithdrawalResponse;
import com.factech.nexus.modules.movements.domain.models.AccountKind;
import com.factech.nexus.modules.movements.domain.models.EntryEvent;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementCode;
import com.factech.nexus.modules.movements.domain.models.RejectionReason;
import com.factech.nexus.modules.movements.domain.models.TypeStatus;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementTypeView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.WithdrawalRow;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository;
import com.factech.nexus.modules.movements.domain.repository.WithdrawalDestinationRepository;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El retiro: pedirlo (`RF-MV-019`), aprobarlo (`RF-MV-020`) y negarlo (`RF-MV-021`).
 *
 * <p><b>Un servicio y no tres</b> (`RF-MV-019` · `tasks.md` §3): las tres operaciones mueven las
 * mismas dos cuentas —la billetera y lo retenido— y comparten cómo se lee y se responde un retiro.
 *
 * <p><b>Pedir retiene en el acto</b> (`RN-MV-043`): la billetera baja y lo retenido sube en el
 * mismo evento, y si la billetera no alcanza no queda ni retiro ni asiento. <b>Aprobar y negar
 * resuelven una sola vez</b>: la transición del retiro va primero y es la que decide; quien la
 * pierde no toca ningún saldo.
 */
@Service
public class WithdrawalService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";
  private static final String TIPO = "RETIRO";
  private static final String METODO_MANUAL = "MANUAL";
  private static final int LONGITUD_REFERENCIA = 120;

  private final MovementRepository movimientos;
  private final PaymentRepository pagos;
  private final LedgerRepository libro;
  private final Ledger asientos;
  private final LedgerMovements comun;
  private final ClientCatalog personas;
  private final WithdrawalDestinations destinos;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public WithdrawalService(
      MovementRepository movimientos,
      PaymentRepository pagos,
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      ClientCatalog personas,
      WithdrawalDestinations destinos,
      AuthenticatedActor actor,
      AuditWriter auditoria) {
    this(
        movimientos,
        pagos,
        libro,
        asientos,
        comun,
        personas,
        destinos,
        actor,
        auditoria,
        Clock.systemUTC());
  }

  WithdrawalService(
      MovementRepository movimientos,
      PaymentRepository pagos,
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      ClientCatalog personas,
      WithdrawalDestinations destinos,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.movimientos = movimientos;
    this.pagos = pagos;
    this.libro = libro;
    this.asientos = asientos;
    this.comun = comun;
    this.personas = personas;
    this.destinos = destinos;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-019` — pedir
  // ---------------------------------------------------------------------------

  @Transactional
  public WithdrawalResponse request(WithdrawalRequests.Request peticion) {
    // 1. Lo que no cuesta una consulta, primero (`EX-001`, `EX-002`).
    if (peticion == null) {
      throw LedgerMovements.invalido("amount", "VAL-002", "El importe es obligatorio.");
    }
    CurrencyView moneda = comun.moneda(peticion.currencyId());
    BigDecimal importe = LedgerMovements.importe(peticion.amount(), moneda);

    // 2. Una cuenta que todavía no opera no retira (`RN-SP-026`, `EX-004`).
    UUID quien = actor.id();
    personas
        .findClient(quien)
        .filter(p -> "FTD_PENDIENTE".equals(p.status()))
        .ifPresent(
            p -> {
              String mensaje =
                  "Esa cuenta todavía no puede operar: le falta la confirmación de su depósito.";
              throw new BusinessRuleException(
                  "EX-004", mensaje, List.of(new FieldError("user", "EX-004", mensaje)));
            });

    // 2b. A dónde se paga (`RN-MV-056`, 01-10-2026): la cuenta indicada o la
    //     principal, viva, de una entidad activa, y un titular con documento.
    //     Antes de tocar un saldo: un rechazo aquí no retiene nada (`EX-006` a
    //     `EX-009`).
    WithdrawalDestinationRepository.DestinationRow destino =
        destinos.resolve(quien, peticion.payoutAccountId());

    // 3. El retiro, pendiente.
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    MovementTypeView tipo = tipo();
    Movement retiro =
        Movement.retiro(
            tipo.id(),
            quien,
            moneda.id(),
            MovementCode.generar(tipo.prefix(), ahora),
            registrado(tipo),
            importe,
            ahora);
    movimientos.saveWithoutLines(retiro, () -> MovementCode.generar(tipo.prefix(), ahora));

    // 4. EL IMPORTE SE APARTA EN EL MISMO ACTO (`RN-MV-043`). Si la billetera no
    //    alcanza —o no existe—, se lanza y la transacción entera se revierte: ni
    //    retiro ni asiento (`CA-MV-226`).
    UUID billetera = libro.accountOf(quien, AccountKind.BILLETERA, moneda.id());
    UUID retenido = libro.accountOf(quien, AccountKind.RETENIDO, moneda.id());
    if (asientos
        .apply(
            retiro.getId(),
            null,
            EntryEvent.SOLICITUD,
            List.of(new Ledger.Leg(billetera, importe.negate()), new Ledger.Leg(retenido, importe)),
            ahora)
        .isEmpty()) {
      BigDecimal disponible = libro.balanceOf(quien, AccountKind.BILLETERA, moneda.id());
      String mensaje =
          "La billetera no alcanza: el disponible es %s %s."
              .formatted(disponible.toPlainString(), moneda.code());
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("amount", "EX-003", mensaje)));
    }

    // 5. La copia del destino, que no cambia nunca.
    destinos.save(retiro.getId(), destino);

    auditar(
        retiro.getId(),
        ChangeAction.CREATE,
        Map.of(
            "after", retiro.instantanea(),
            "destination", WithdrawalDestinations.enmascarado(destino)));

    return new WithdrawalResponse(
        comun.respuesta(retiro.getId(), TIPO),
        comun.saldos(quien, moneda.id(), moneda.code()),
        WithdrawalDestinations.respuesta(destino));
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-020` — aprobar
  // ---------------------------------------------------------------------------

  @Transactional
  public LedgerMovementResponse approve(UUID retiroId, WithdrawalRequests.Approval peticion) {
    String referencia = referencia(peticion == null ? null : peticion.providerReference());
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    if (!movimientos.confirmWithdrawalIfPending(retiroId, ahora)) {
      throw noPendiente(retiroId);
    }
    WithdrawalRow retiro = leer(retiroId);

    PaymentMethodView manual =
        movimientos
            .findPaymentMethodByCode(METODO_MANUAL)
            .orElseThrow(() -> new IllegalStateException("Falta el método MANUAL (V49)."));
    UUID pago = UUID.randomUUID();
    pagos.insertConfirmed(
        pago,
        retiroId,
        manual.id(),
        retiro.amount(),
        IdempotencyKey.generada().value(),
        referencia,
        ahora);

    UUID retenido = libro.accountOf(retiro.userId(), AccountKind.RETENIDO, retiro.currencyId());
    UUID salidas = libro.accountOf(null, AccountKind.RETIROS, retiro.currencyId());
    if (asientos
        .apply(
            retiroId,
            pago,
            EntryEvent.APROBACION,
            List.of(
                new Ledger.Leg(retenido, retiro.amount().negate()),
                new Ledger.Leg(salidas, retiro.amount())),
            ahora)
        .isEmpty()) {
      // Lo retenido estaba ahí desde la solicitud, y nadie más lo mueve: si falta,
      // el libro está roto, y no es un caso de negocio.
      throw new IllegalStateException("Lo retenido del retiro " + retiroId + " no alcanza.");
    }

    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("status", "PENDIENTE"));
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("status", "CONFIRMADA");
    despues.put("payment_id", pago.toString());
    despues.put("provider_reference", referencia);
    despues.put("event", EntryEvent.APROBACION.name());
    cambios.put("after", despues);
    auditar(retiroId, ChangeAction.UPDATE, cambios);

    return comun.respuesta(retiroId, TIPO);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-021` — negar
  // ---------------------------------------------------------------------------

  @Transactional
  public LedgerMovementResponse reject(UUID retiroId, WithdrawalRequests.Rejection peticion) {
    RejectionReason motivo = new RejectionReason(peticion == null ? null : peticion.reason());
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    if (!movimientos.rejectWithdrawalIfPending(retiroId, ahora, motivo.value())) {
      throw noPendiente(retiroId);
    }
    WithdrawalRow retiro = leer(retiroId);

    UUID retenido = libro.accountOf(retiro.userId(), AccountKind.RETENIDO, retiro.currencyId());
    UUID billetera = libro.accountOf(retiro.userId(), AccountKind.BILLETERA, retiro.currencyId());
    if (asientos
        .apply(
            retiroId,
            null,
            EntryEvent.RECHAZO,
            List.of(
                new Ledger.Leg(retenido, retiro.amount().negate()),
                new Ledger.Leg(billetera, retiro.amount())),
            ahora)
        .isEmpty()) {
      throw new IllegalStateException("Lo retenido del retiro " + retiroId + " no alcanza.");
    }

    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("status", "PENDIENTE"));
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("status", "RECHAZADA");
    despues.put("rejection_reason", motivo.value());
    despues.put("event", EntryEvent.RECHAZO.name());
    cambios.put("after", despues);
    auditar(retiroId, ChangeAction.UPDATE, cambios);

    return comun.respuesta(retiroId, TIPO);
  }

  // ---------------------------------------------------------------------------

  private MovementTypeView tipo() {
    return movimientos
        .findTypeByCode(TIPO)
        .orElseThrow(() -> new IllegalStateException("Falta el tipo RETIRO (V49)."));
  }

  private TypeStatus registrado(MovementTypeView tipo) {
    return movimientos
        .findTypeStatus(tipo.id(), "REGISTRADO")
        .orElseThrow(() -> new IllegalStateException("Falta el estado REGISTRADO (V49)."));
  }

  private WithdrawalRow leer(UUID retiroId) {
    return movimientos
        .findWithoutLines(retiroId, TIPO)
        .orElseThrow(() -> new IllegalStateException("El retiro desapareció."));
  }

  /** La transición no acertó: o no existe como retiro (`404`), o no está pendiente (`409`). */
  private RuntimeException noPendiente(UUID retiroId) {
    WithdrawalRow retiro =
        movimientos
            .findWithoutLines(retiroId, TIPO)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un retiro con ese identificador."));
    String mensaje = "El retiro no está pendiente: está " + retiro.status() + ".";
    return new BusinessRuleException(
        "EX-002", mensaje, List.of(new FieldError("status", "EX-002", mensaje)));
  }

  private static String referencia(String valor) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    String limpia = valor.trim();
    if (limpia.length() > LONGITUD_REFERENCIA) {
      throw LedgerMovements.invalido(
          "providerReference",
          "VAL-002",
          "La referencia no puede exceder " + LONGITUD_REFERENCIA + " caracteres.");
    }
    return limpia;
  }

  private void auditar(UUID movimiento, ChangeAction accion, Map<String, Object> cambios) {
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, movimiento, accion, cambios));
  }
}
