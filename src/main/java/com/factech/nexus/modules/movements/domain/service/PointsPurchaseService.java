package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.PointsPurchaseResponse;
import com.factech.nexus.modules.movements.application.PointsRequests;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.AccountKind;
import com.factech.nexus.modules.movements.domain.models.EntryEvent;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementCode;
import com.factech.nexus.modules.movements.domain.models.PointsAmount;
import com.factech.nexus.modules.movements.domain.models.RejectionReason;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementTypeView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PointsPurchaseFilter;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PointsPurchaseRow;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository.KeyedPayment;
import com.factech.nexus.modules.movements.domain.repository.PointsRateRepository.RateRow;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La compra de puntos (`requirements/mv.md` §4.4): comprar (`RF-MV-027`), confirmar el pago y
 * abonar (`RF-MV-028`), rechazarlo (`RF-MV-029`) y consultar las propias (`RF-MV-031`).
 *
 * <p><b>Un servicio y no cuatro</b>, como {@link WithdrawalService}: las cuatro operaciones son
 * sobre el mismo movimiento, y la forma de la respuesta es una sola.
 */
@Service
public class PointsPurchaseService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";
  private static final String TIPO = "COMPRA_PUNTOS";
  private static final String PUNTOS = "POINTS";
  private static final Set<String> ESTADOS = Set.of("PENDIENTE", "CONFIRMADA", "RECHAZADA");

  private final MovementRepository movimientos;
  private final PaymentRepository pagos;
  private final LedgerRepository libro;
  private final Ledger asientos;
  private final LedgerMovements comun;
  private final PointsRateService tasas;
  private final ClientCatalog personas;
  private final AuthenticatedActor actor;
  private final Pagination paginacion;
  private final AuditWriter auditoria;
  private final Clock reloj;
  private final CardPayment tarjeta;

  @Autowired
  public PointsPurchaseService(
      MovementRepository movimientos,
      PaymentRepository pagos,
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      PointsRateService tasas,
      ClientCatalog personas,
      AuthenticatedActor actor,
      Pagination paginacion,
      AuditWriter auditoria,
      CardPayment tarjeta) {
    this(
        movimientos,
        pagos,
        libro,
        asientos,
        comun,
        tasas,
        personas,
        actor,
        paginacion,
        auditoria,
        tarjeta,
        Clock.systemUTC());
  }

  PointsPurchaseService(
      MovementRepository movimientos,
      PaymentRepository pagos,
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      PointsRateService tasas,
      ClientCatalog personas,
      AuthenticatedActor actor,
      Pagination paginacion,
      AuditWriter auditoria,
      CardPayment tarjeta,
      Clock reloj) {
    this.tarjeta = tarjeta;
    this.movimientos = movimientos;
    this.pagos = pagos;
    this.libro = libro;
    this.asientos = asientos;
    this.comun = comun;
    this.tasas = tasas;
    this.personas = personas;
    this.actor = actor;
    this.paginacion = paginacion;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  /** Lo que salió de comprar: la compra, y si es nueva o la misma petición repetida. */
  public record BuyResult(PointsPurchaseResponse purchase, boolean created) {}

  // ---------------------------------------------------------------------------
  // `RF-MV-027` — comprar
  // ---------------------------------------------------------------------------

  @Transactional
  public BuyResult buy(PointsRequests.Purchase peticion, String claveRecibida) {
    // 1. Lo que no cuesta una consulta, antes de nada (`EX-001`).
    IdempotencyKey clave = new IdempotencyKey(claveRecibida);
    if (peticion == null) {
      throw LedgerMovements.invalido("currencyId", "VAL-001", "La moneda es obligatoria.");
    }
    if (peticion.paymentMethodId() == null) {
      throw LedgerMovements.invalido(
          "paymentMethodId", "VAL-001", "El método de pago es obligatorio.");
    }
    CurrencyView moneda = comun.moneda(peticion.currencyId());
    BigDecimal importe = LedgerMovements.importe(peticion.amount(), moneda);

    // 2. ¿La misma petición otra vez? (`FA-001`, `EX-008`)
    UUID quien = actor.id();
    Optional<KeyedPayment> previo = pagos.findByKey(clave.value());
    if (previo.isPresent()) {
      return new BuyResult(
          repetida(previo.get(), quien, moneda, importe, peticion)
              .conCobro(tarjeta.cobroExistente(previo.get().movementId())),
          false);
    }

    // 3. Una cuenta que todavía no opera no compra (`RN-SP-026`, `EX-006`).
    personas
        .findClient(quien)
        .filter(p -> "FTD_PENDIENTE".equals(p.status()))
        .ifPresent(
            p -> {
              String mensaje =
                  "Esa cuenta todavía no puede operar: le falta la confirmación de su depósito.";
              throw new BusinessRuleException(
                  "EX-006", mensaje, List.of(new FieldError("user", "EX-006", mensaje)));
            });

    // 4. La moneda vende puntos: activa y con tasa (`EX-003`, `RN-MV-050`).
    if (!moneda.active()) {
      String mensaje = "La moneda " + moneda.code() + " está inactiva.";
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("currencyId", "EX-003", mensaje)));
    }
    RateRow tasa = tasas.vigenteOConflicto(moneda, "EX-003");

    // 5. El método: existe (`EX-004`), está activo, se ofrece y no es POINTS (`EX-005`).
    PaymentMethodView metodo = metodo(peticion.paymentMethodId());

    // 6. Los puntos, hacia abajo. Un importe que da cero no compra nada.
    BigDecimal puntos = PointsAmount.comprados(importe, tasa.pointsPerUnit());
    if (puntos.signum() <= 0) {
      throw LedgerMovements.invalido(
          "amount", "VAL-002", "Ese importe no alcanza para ningún punto a la tasa vigente.");
    }

    // 7. La compra pendiente y su pago pendiente, en un acto. Sin asientos (`RN-MV-051`).
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    MovementTypeView tipo =
        movimientos
            .findTypeByCode(TIPO)
            .orElseThrow(() -> new IllegalStateException("Falta el tipo " + TIPO + " (V58)."));
    Movement compra =
        Movement.compraDePuntos(
            tipo.id(),
            quien,
            metodo.id(),
            moneda.id(),
            MovementCode.generar(tipo.prefix(), ahora),
            movimientos
                .findTypeStatus(tipo.id(), "REGISTRADO")
                .orElseThrow(() -> new IllegalStateException("Falta el estado REGISTRADO.")),
            importe,
            tasa.id(),
            puntos,
            ahora);
    movimientos.savePointsPurchase(compra, () -> MovementCode.generar(tipo.prefix(), ahora), clave);

    auditar(
        compra.getId(),
        ChangeAction.CREATE,
        Map.of("after", compra.instantanea(), "payment_status", "PENDIENTE"));
    // Con tarjeta, el cobro se abre ahora (`RF-MV-040`).
    return new BuyResult(
        respuesta(leer(compra.getId())).conCobro(tarjeta.abrirSiToca(metodo, compra.getId())),
        true);
  }

  /**
   * La clave ya existe. Si es <b>la misma petición</b> —una compra de puntos del actor con la misma
   * moneda, importe y método— se devuelve la que ya se registró; si no, `EX-008`.
   */
  private PointsPurchaseResponse repetida(
      KeyedPayment previo,
      UUID quien,
      CurrencyView moneda,
      BigDecimal importe,
      PointsRequests.Purchase peticion) {
    Optional<PointsPurchaseRow> ya = movimientos.findPointsPurchase(previo.movementId());
    boolean misma =
        ya.isPresent()
            && ya.get().userId().equals(quien)
            && ya.get().currencyId().equals(moneda.id())
            && ya.get().amount().compareTo(importe) == 0
            && previo.paymentMethodId().equals(peticion.paymentMethodId());
    if (!misma) {
      String mensaje = "La clave de idempotencia ya se usó en otra petición.";
      throw new BusinessRuleException(
          "EX-008", mensaje, List.of(new FieldError(IdempotencyKey.CABECERA, "EX-008", mensaje)));
    }
    return respuesta(ya.get());
  }

  private PaymentMethodView metodo(UUID id) {
    PaymentMethodView metodo =
        movimientos
            .findPaymentMethod(id)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-004",
                        "El método de pago indicado no existe.",
                        List.of(
                            new FieldError(
                                "paymentMethodId",
                                "EX-004",
                                "El método de pago indicado no existe."))));
    String problema = null;
    if (PUNTOS.equals(metodo.code())) {
      problema = "Los puntos no se compran con puntos.";
    } else if (!"PUBLICO".equals(metodo.visibility())) {
      // No solo GRATIS: también MANUAL, el del retiro (`RF-MV-027` · `plan.md` §1).
      problema = "Ese método de pago no se ofrece.";
    } else if (!metodo.active()) {
      problema = "Ese método de pago está desactivado.";
    }
    if (problema != null) {
      throw new BusinessRuleException(
          "EX-005", problema, List.of(new FieldError("paymentMethodId", "EX-005", problema)));
    }
    return metodo;
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-028` — confirmar y abonar
  // ---------------------------------------------------------------------------

  /**
   * La confirmación a mano, <b>desde el pago</b> (`RF-MV-044`, 01-10-2026): la invoca {@link
   * PaymentResolutionService} con la compra ya bloqueada y comprobada, también contra un cobro
   * abierto (`RN-MV-058`), y con la referencia ya validada.
   */
  void confirmPayment(UUID compraId, String referencia) {
    confirmar(compraId, referencia);
  }

  /** `RF-MV-041`: la pasarela notificó que el cobro entró. Con la referencia del cobro. */
  @Transactional
  public PointsPurchaseResponse confirmByGateway(UUID compraId, String referencia) {
    return confirmar(compraId, referencia);
  }

  private PointsPurchaseResponse confirmar(UUID compraId, String referencia) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    // 1. La transición, condicionada. Quien no la gana no toca nada (`CA-MV-322`, `CA-MV-323`).
    if (!movimientos.confirmPointsPurchaseIfPending(compraId, ahora)) {
      throw noPendiente(compraId);
    }
    UUID pago = movimientos.confirmPendingPayment(compraId, ahora, referencia);
    PointsPurchaseRow compra = leer(compraId);

    // 2. Los puntos CONGELADOS, no los de la tasa de hoy (`RN-MV-051`, `CA-MV-320`).
    UUID emitidos = libro.accountOf(null, AccountKind.PUNTOS_EMITIDOS, compra.currencyId());
    UUID cuenta = libro.accountOf(compra.userId(), AccountKind.PUNTOS, compra.currencyId());
    asientos
        .apply(
            compraId,
            pago,
            EntryEvent.ABONO,
            List.of(
                new Ledger.Leg(emitidos, compra.points().negate()),
                new Ledger.Leg(cuenta, compra.points())),
            ahora)
        .orElseThrow(() -> new IllegalStateException("Un abono no puede fallar por saldo."));

    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("status", "PENDIENTE"));
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("status", "CONFIRMADA");
    despues.put("payment_id", pago.toString());
    despues.put("provider_reference", referencia);
    despues.put("points", compra.points().toPlainString());
    despues.put("event", EntryEvent.ABONO.name());
    cambios.put("after", despues);
    auditar(compraId, ChangeAction.UPDATE, cambios);

    return respuesta(compra);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-029` — rechazar
  // ---------------------------------------------------------------------------

  /**
   * El rechazo a mano, <b>desde el pago</b> (`RF-MV-045`, 01-10-2026): lo invoca {@link
   * PaymentResolutionService} con la compra ya bloqueada y comprobada.
   */
  void rejectPayment(UUID compraId, RejectionReason motivo) {
    rechazar(compraId, motivo);
  }

  /** `RF-MV-041`: la pasarela canceló el cobro. */
  @Transactional
  public PointsPurchaseResponse rejectByGateway(UUID compraId, String motivo) {
    return rechazar(compraId, new RejectionReason(motivo));
  }

  private PointsPurchaseResponse rechazar(UUID compraId, RejectionReason motivo) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    if (!movimientos.rejectPointsPurchaseIfPending(compraId, ahora, motivo.value())) {
      throw noPendiente(compraId);
    }
    movimientos.rejectPendingPayment(compraId, ahora, motivo.value());

    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", Map.of("status", "PENDIENTE"));
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("status", "RECHAZADA");
    despues.put("rejection_reason", motivo.value());
    cambios.put("after", despues);
    auditar(compraId, ChangeAction.UPDATE, cambios);

    return respuesta(leer(compraId));
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-031` — las propias
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public PageResponse<PointsPurchaseResponse> listMine(
      Integer page,
      Integer size,
      String status,
      UUID currencyId,
      String code,
      OffsetDateTime from,
      OffsetDateTime to) {
    // Los 400 SALEN JUNTOS (`EX-001`).
    List<FieldError> errores = new ArrayList<>();
    String estado =
        status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    if (estado != null && !ESTADOS.contains(estado)) {
      errores.add(
          new FieldError("status", "VAL-001", "El estado es PENDIENTE, CONFIRMADA o RECHAZADA."));
    }
    if (from != null && to != null && from.isAfter(to)) {
      errores.add(
          new FieldError("from", "VAL-002", "La fecha inicial no puede ser posterior a la final."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }
    Pagination.Slice pagina = paginacion.resolver(page, size);
    PointsPurchaseFilter filtro = new PointsPurchaseFilter(estado, currencyId, code, from, to);
    UUID quien = actor.id();

    List<PointsPurchaseRow> filas =
        movimientos.findOwnPointsPurchases(quien, filtro, pagina.offset(), pagina.size());
    // Los pagos de toda la página, de una vez (`CA-MV-350`).
    Map<UUID, List<PaymentRow>> pagosDeCada =
        movimientos.findPaymentsOf(filas.stream().map(PointsPurchaseRow::id).toList());
    List<PointsPurchaseResponse> contenido = new ArrayList<>(filas.size());
    for (PointsPurchaseRow fila : filas) {
      contenido.add(respuesta(fila, pagosDeCada.getOrDefault(fila.id(), List.of())));
    }
    return PageResponse.de(
        contenido,
        movimientos.countOwnPointsPurchases(quien, filtro),
        pagina.page(),
        pagina.size());
  }

  // ---------------------------------------------------------------------------

  private PointsPurchaseRow leer(UUID compraId) {
    return movimientos
        .findPointsPurchase(compraId)
        .orElseThrow(() -> new IllegalStateException("La compra de puntos desapareció."));
  }

  private RuntimeException noPendiente(UUID compraId) {
    PointsPurchaseRow compra =
        movimientos
            .findPointsPurchase(compraId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una compra de puntos con ese identificador."));
    String mensaje = "La compra no está pendiente: está " + compra.status() + ".";
    return new BusinessRuleException(
        "EX-002", mensaje, List.of(new FieldError("status", "EX-002", mensaje)));
  }

  private PointsPurchaseResponse respuesta(PointsPurchaseRow fila) {
    return respuesta(
        fila, movimientos.findPaymentsOf(List.of(fila.id())).getOrDefault(fila.id(), List.of()));
  }

  private static PointsPurchaseResponse respuesta(PointsPurchaseRow fila, List<PaymentRow> pagos) {
    return new PointsPurchaseResponse(
        fila.id(),
        fila.code(),
        fila.status(),
        new SaleResponse.Money(fila.currencyId(), fila.currencyCode()),
        fila.amount(),
        new PointsPurchaseResponse.Rate(fila.pointsRateId(), fila.pointsPerUnit()),
        fila.points(),
        fila.occurredAt(),
        fila.confirmedAt(),
        fila.rejectedAt(),
        fila.rejectionReason(),
        SaleDetailMapper.pagos(pagos),
        null);
  }

  private void auditar(UUID movimiento, ChangeAction accion, Map<String, Object> cambios) {
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, movimiento, accion, cambios));
  }
}
