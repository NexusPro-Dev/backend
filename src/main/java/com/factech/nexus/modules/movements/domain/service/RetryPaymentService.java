package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.RetryPaymentRequest;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository.KeyedPayment;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository.RetryTarget;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-MV-018`: volver a pagar una venta propia pendiente.
 *
 * <p><b>El esquema decide, no una lectura previa</b> (`plan.md` §1). El pago se abre con un {@code
 * INSERT … ON CONFLICT DO NOTHING}: si entra, es un intento nuevo; si no, la transacción sigue viva
 * y se relee para explicar por qué —la misma petición repetida (`FA-001`), la clave de otra
 * petición (`EX-006`) o un pago ya pendiente (`EX-004`)—. Dos peticiones simultáneas con claves
 * distintas abren <b>un</b> pago, porque {@code uq_payments_uno_pendiente} lo impide (`CA-MV-210`).
 */
@Service
public class RetryPaymentService {

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "payments";

  private final MovementRepository movimientos;
  private final PaymentRepository pagos;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final SaleRules reglas;
  private final Clock reloj;
  private final PointsPayment puntos;

  @Autowired
  public RetryPaymentService(
      MovementRepository movimientos,
      PaymentRepository pagos,
      AuthenticatedActor actor,
      CurrentMembershipLookup membresias,
      AuditWriter auditoria,
      PointsPayment puntos) {
    this(movimientos, pagos, actor, membresias, auditoria, puntos, Clock.systemUTC());
  }

  RetryPaymentService(
      MovementRepository movimientos,
      PaymentRepository pagos,
      AuthenticatedActor actor,
      CurrentMembershipLookup membresias,
      AuditWriter auditoria,
      PointsPayment puntos,
      Clock reloj) {
    this.puntos = puntos;
    this.movimientos = movimientos;
    this.pagos = pagos;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reglas = new SaleRules(movimientos, membresias);
    this.reloj = reloj;
  }

  /** Lo que salió: la venta con sus pagos, y si el pago es nuevo o ya existía. */
  public record Result(SaleResponse sale, boolean created) {}

  @Transactional
  public Result retry(UUID movementId, RetryPaymentRequest peticion, String claveRecibida) {
    // 1. LA CLAVE, antes de mirar la venta (`EX-007`).
    IdempotencyKey clave = new IdempotencyKey(claveRecibida);
    UUID metodoPedido = peticion == null ? null : peticion.paymentMethodId();

    // 2. ¿La misma petición otra vez? (`FA-001`, `EX-006`)
    Optional<KeyedPayment> previo = pagos.findByKey(clave.value());
    if (previo.isPresent()) {
      return repetida(previo.get(), movementId, metodoPedido);
    }

    // 3. La venta: suya, una venta, y pendiente. Ajena e inexistente responden igual.
    UUID quien = actor.id();
    RetryTarget venta =
        pagos
            .findOwnSale(movementId, quien)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una compra propia con ese identificador."));
    if (!"PENDIENTE".equals(venta.status())) {
      String mensaje = "La venta no está pendiente: está " + venta.status() + ".";
      throw new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("status", "EX-003", mensaje)));
    }

    // 4. El método, con las reglas de registrar (`RN-MV-018`, `RN-MV-022`).
    PaymentMethodView metodo = reglas.resolverMetodoDePago(metodoPedido, venta.payableAmount());

    // 5. El pago. Cero filas: la clave o el pendiente único chocaron.
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    UUID pago = UUID.randomUUID();
    if (!pagos.open(pago, movementId, metodo.id(), venta.payableAmount(), clave.value(), ahora)) {
      Optional<KeyedPayment> carrera = pagos.findByKey(clave.value());
      if (carrera.isPresent()) {
        return repetida(carrera.get(), movementId, metodoPedido);
      }
      throw yaHayUnPagoPendiente();
    }

    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("movement_id", movementId.toString());
    despues.put("payment_method_id", metodo.id().toString());
    despues.put("amount", venta.payableAmount().toPlainString());
    despues.put("status", "PENDIENTE");
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, pago, ChangeAction.CREATE, despues));

    if (PointsPayment.esPuntos(metodo)) {
      // Con puntos, el pago nuevo se descuenta y la venta se confirma en el acto
      // (`RF-MV-030` `FA-001`); si no alcanzan, el pago no llega a existir.
      UUID moneda = detalle(movementId).currency().id();
      return new Result(puntos.pagar(movementId, pago, quien, moneda, venta.payableAmount()), true);
    }
    return new Result(detalle(movementId), true);
  }

  /**
   * La clave ya existe. Si es <b>la misma petición</b> —la misma venta y el mismo método— se
   * devuelve lo que ya se hizo; si no, la clave es de otra petición (`EX-006`). En una venta de
   * importe cero el método no viaja y se compara solo la venta.
   */
  private Result repetida(KeyedPayment previo, UUID movementId, UUID metodoPedido) {
    boolean mismaVenta = previo.movementId().equals(movementId);
    boolean mismoMetodo = metodoPedido == null || metodoPedido.equals(previo.paymentMethodId());
    if (!mismaVenta || !mismoMetodo) {
      String mensaje = "La clave de idempotencia ya se usó en otra petición.";
      throw new BusinessRuleException(
          "EX-006", mensaje, List.of(new FieldError(IdempotencyKey.CABECERA, "EX-006", mensaje)));
    }
    // Solo el dueño de la venta llega a verla: la clave no abre lo ajeno.
    pagos
        .findOwnSale(movementId, actor.id())
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-001", "No existe una compra propia con ese identificador."));
    return new Result(detalle(movementId), false);
  }

  private static BusinessRuleException yaHayUnPagoPendiente() {
    String mensaje =
        "La venta tiene un pago pendiente: hay que esperar a que se resuelva antes de volver a"
            + " pagarla.";
    return new BusinessRuleException(
        "EX-004", mensaje, List.of(new FieldError("payments", "EX-004", mensaje)));
  }

  private SaleResponse detalle(UUID movementId) {
    return SaleDetailMapper.de(
        movimientos
            .findById(movementId)
            .orElseThrow(() -> new IllegalStateException("La venta desapareció.")));
  }
}
