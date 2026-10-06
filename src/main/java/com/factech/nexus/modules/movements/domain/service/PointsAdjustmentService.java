package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.PointsAdjustmentResponse;
import com.factech.nexus.modules.movements.application.PointsRequests;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.AccountKind;
import com.factech.nexus.modules.movements.domain.models.EntryEvent;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.models.Movement;
import com.factech.nexus.modules.movements.domain.models.MovementCode;
import com.factech.nexus.modules.movements.domain.models.PointsAmount;
import com.factech.nexus.modules.movements.domain.models.PointsReceipt;
import com.factech.nexus.modules.movements.domain.models.RejectionReason;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementTypeView;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PointsAdjustmentRow;
import com.factech.nexus.modules.movements.domain.repository.PointsMovementQuery;
import com.factech.nexus.modules.movements.domain.repository.PointsMovementQuery.ReceiptInfoRow;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.ClientCatalog;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-MV-052`: administración suma o resta puntos a mano (`RN-MV-076`) —el caso que lo pidió: una
 * persona pagó por fuera de la plataforma, a la cuenta de la empresa—.
 *
 * <p>Es el bono de {@link CreditService} con puntos en vez de dinero y en los dos sentidos, entre
 * la cuenta {@code PUNTOS} de la persona y {@code PUNTOS_EMITIDOS} de la empresa. <b>La resta no se
 * comprueba antes</b>: la frena el {@link Ledger}, que mueve los saldos con la fila bloqueada, y el
 * error revierte el movimiento ya insertado (`plan.md` §1).
 */
@Service
public class PointsAdjustmentService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";
  private static final String TIPO = "AJUSTE_PUNTOS";
  private static final int REFERENCIA_MAXIMA = 120;

  private final MovementRepository movimientos;
  private final LedgerRepository libro;
  private final Ledger asientos;
  private final LedgerMovements comun;
  private final ClientCatalog personas;
  private final AuditWriter auditoria;
  private final AuthenticatedActor actor;
  private final PointsMovementQuery lecturas;
  private final AttachPointsReceiptService adjuntador;
  private final Clock reloj;

  @Autowired
  public PointsAdjustmentService(
      MovementRepository movimientos,
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      ClientCatalog personas,
      AuditWriter auditoria,
      AuthenticatedActor actor,
      PointsMovementQuery lecturas,
      AttachPointsReceiptService adjuntador) {
    this(
        movimientos,
        libro,
        asientos,
        comun,
        personas,
        auditoria,
        actor,
        lecturas,
        adjuntador,
        Clock.systemUTC());
  }

  PointsAdjustmentService(
      MovementRepository movimientos,
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      ClientCatalog personas,
      AuditWriter auditoria,
      AuthenticatedActor actor,
      PointsMovementQuery lecturas,
      AttachPointsReceiptService adjuntador,
      Clock reloj) {
    this.movimientos = movimientos;
    this.libro = libro;
    this.asientos = asientos;
    this.comun = comun;
    this.personas = personas;
    this.auditoria = auditoria;
    this.actor = actor;
    this.lecturas = lecturas;
    this.adjuntador = adjuntador;
    this.reloj = reloj;
  }

  /** Lo que salió de un ajuste: el ajuste, y si es nuevo o ya existía (`FA-001`). */
  public record AdjustmentResult(PointsAdjustmentResponse adjustment, boolean created) {}

  /** Sin comprobante: la petición JSON de siempre. */
  @Transactional
  public AdjustmentResult adjust(PointsRequests.Adjustment peticion, String claveRecibida) {
    return adjust(peticion, claveRecibida, null);
  }

  /**
   * Con el comprobante ya validado, o nulo (`RF-MV-057`): se guarda en la misma transacción que el
   * movimiento y sus asientos, de modo que una resta que no alcanza lo revierte también.
   */
  @Transactional
  public AdjustmentResult adjust(
      PointsRequests.Adjustment peticion, String claveRecibida, PointsReceipt comprobante) {
    // 1. Todo lo que no cuesta una consulta, antes de nada (`EX-001`).
    IdempotencyKey clave = new IdempotencyKey(claveRecibida);
    if (peticion == null || peticion.userId() == null) {
      throw LedgerMovements.invalido("userId", "VAL-001", "La persona es obligatoria.");
    }
    BigDecimal puntos = puntos(peticion.points());
    RejectionReason concepto = concepto(peticion.concept());
    String referencia = referencia(peticion.reference());
    CurrencyView moneda = comun.moneda(peticion.currencyId());

    // 2. ¿La misma petición otra vez? (`FA-001`, `EX-005`)
    Optional<UUID> previo = movimientos.findIdByIdempotencyKey(clave.value());
    if (previo.isPresent()) {
      PointsAdjustmentRow ya =
          movimientos.findPointsAdjustment(previo.get()).orElseThrow(Conflicto::claveAjena);
      boolean misma =
          ya.userId().equals(peticion.userId())
              && ya.currencyId().equals(moneda.id())
              && ya.points().compareTo(puntos) == 0
              // El archivo también decide si es la misma petición (`RF-MV-057` `EX-003`).
              && Objects.equals(
                  lecturas.findReceiptInfo(ya.id()).map(ReceiptInfoRow::sha256).orElse(null),
                  comprobante == null ? null : comprobante.sha256());
      if (!misma) {
        throw Conflicto.claveAjena();
      }
      return new AdjustmentResult(respuesta(ya), false);
    }

    // 3. La persona existe y no está eliminada. Bloqueada o en espera, recibe igual (`CA-MV-642`).
    personas
        .findClient(peticion.userId())
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "EX-002",
                    "La persona indicada no existe.",
                    List.of(new FieldError("userId", "EX-002", "La persona indicada no existe."))));

    // 4. El movimiento y sus dos asientos, o nada.
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    MovementTypeView tipo =
        movimientos
            .findTypeByCode(TIPO)
            .orElseThrow(() -> new IllegalStateException("Falta el tipo " + TIPO + "."));
    Movement ajuste =
        Movement.ajusteDePuntos(
            tipo.id(),
            peticion.userId(),
            moneda.id(),
            MovementCode.generar(tipo.prefix(), ahora),
            movimientos
                .findTypeStatus(tipo.id(), "REGISTRADO")
                .orElseThrow(() -> new IllegalStateException("Falta el estado REGISTRADO.")),
            moneda.decimalPlaces(),
            puntos,
            concepto.value(),
            referencia,
            clave.value(),
            // `RF-MV-053`: quién lo hizo queda en el movimiento, no solo en la auditoría.
            actor.id(),
            ahora);
    movimientos.saveWithoutLines(ajuste, () -> MovementCode.generar(tipo.prefix(), ahora));

    UUID emitidos = libro.accountOf(null, AccountKind.PUNTOS_EMITIDOS, moneda.id());
    UUID cuenta = libro.accountOf(peticion.userId(), AccountKind.PUNTOS, moneda.id());
    if (asientos
        .apply(
            ajuste.getId(),
            null,
            EntryEvent.AJUSTE,
            List.of(new Ledger.Leg(emitidos, puntos.negate()), new Ledger.Leg(cuenta, puntos)),
            ahora)
        .isEmpty()) {
      // Solo una resta puede quedarse corta. Lanzar revierte el movimiento ya insertado.
      throw sinPuntos(libro.balanceOf(peticion.userId(), AccountKind.PUNTOS, moneda.id()));
    }

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            ajuste.getId(),
            ChangeAction.CREATE,
            Map.of("after", ajuste.instantanea(), "event", EntryEvent.AJUSTE.name())));

    if (comprobante != null) {
      // La fila del ajuste es de esta transacción: nadie más puede verla todavía.
      adjuntador.guardar(ajuste.getId(), comprobante);
    }

    PointsAdjustmentRow hecho =
        movimientos
            .findPointsAdjustment(ajuste.getId())
            .orElseThrow(() -> new IllegalStateException("El ajuste desapareció."));
    return new AdjustmentResult(respuesta(hecho), true);
  }

  private PointsAdjustmentResponse respuesta(PointsAdjustmentRow fila) {
    return new PointsAdjustmentResponse(
        fila.id(),
        fila.code(),
        fila.userId(),
        fila.status(),
        new SaleResponse.Money(fila.currencyId(), fila.currencyCode()),
        fila.points(),
        fila.concept(),
        fila.externalReference(),
        fila.occurredAt(),
        fila.confirmedAt(),
        libro.balanceOf(fila.userId(), AccountKind.PUNTOS, fila.currencyId()),
        lecturas.findReceiptInfo(fila.id()).map(AttachPointsReceiptService::info).orElse(null));
  }

  /** Distintos de cero y con dos decimales como mucho, llevados a esa escala. */
  private static BigDecimal puntos(BigDecimal valor) {
    if (valor == null || valor.signum() == 0) {
      throw LedgerMovements.invalido(
          "points", "VAL-002", "Los puntos son obligatorios y distintos de cero.");
    }
    if (valor.stripTrailingZeros().scale() > PointsAmount.ESCALA) {
      throw LedgerMovements.invalido(
          "points",
          "VAL-003",
          "Los puntos no pueden tener más de " + PointsAmount.ESCALA + " decimales.");
    }
    return valor.setScale(PointsAmount.ESCALA);
  }

  private static RejectionReason concepto(String valor) {
    try {
      return new RejectionReason(valor);
    } catch (ValidationException e) {
      throw LedgerMovements.invalido(
          "concept", "VAL-004", "El motivo del ajuste es obligatorio, hasta 500 caracteres.");
    }
  }

  /** Opcional; si viene, con contenido y acotada (`VAL-005`). */
  private static String referencia(String valor) {
    if (valor == null) {
      return null;
    }
    String limpia = valor.strip();
    if (limpia.isEmpty() || limpia.length() > REFERENCIA_MAXIMA) {
      throw LedgerMovements.invalido(
          "reference",
          "VAL-005",
          "La referencia, si se indica, no puede ir en blanco y llega hasta "
              + REFERENCIA_MAXIMA
              + " caracteres.");
    }
    return limpia;
  }

  /** `EX-006`: la resta no alcanza. Con los puntos disponibles, para que se pueda corregir. */
  private static UnprocessableEntityException sinPuntos(BigDecimal disponibles) {
    String mensaje =
        "La persona no tiene puntos suficientes para esta resta: tiene "
            + disponibles.toPlainString()
            + ".";
    return new UnprocessableEntityException(
        "EX-006", mensaje, List.of(new FieldError("points", "EX-006", mensaje)));
  }

  /** La clave ya se usó para otra petición (`EX-005`). */
  private static final class Conflicto {
    static BusinessRuleException claveAjena() {
      String mensaje = "La clave de idempotencia ya se usó en otra petición.";
      return new BusinessRuleException(
          "EX-005", mensaje, List.of(new FieldError(IdempotencyKey.CABECERA, "EX-005", mensaje)));
    }
  }
}
