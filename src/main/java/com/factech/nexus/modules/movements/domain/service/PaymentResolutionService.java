package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.RejectionReason;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository.PaymentTarget;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Se concilia el pago, no el movimiento</b> (`RF-MV-044`, `RF-MV-045`, `RN-MV-061`; 01-10-2026).
 *
 * <p>No confirma ni rechaza nada por sí mismo: <b>localiza, bloquea, comprueba y delega</b>. Lo que
 * le pasa a cada tipo ya existe y lo comparte la notificación de la pasarela: {@link
 * ConfirmSaleService} y {@link RejectPaymentService} para la venta, {@link PointsPurchaseService}
 * para la compra de puntos.
 *
 * <p><b>El movimiento se bloquea antes de leer el pago</b>, en dos sentencias (`plan.md` §1): la
 * cabecera es la fila que serializa todos los caminos, y leer el pago después hace que lo que se
 * comprueba sea lo que dejó quien tenía el bloqueo. Con el movimiento bloqueado y a lo sumo un pago
 * pendiente (`RN-MV-039`), el pago que confirma la transición es el que se nombró.
 */
@Service
public class PaymentResolutionService {

  /** Lo que la columna admite (`V48`), como la referencia de un retiro. */
  private static final int LONGITUD_REFERENCIA = 120;

  private final PaymentRepository pagos;
  private final ConfirmSaleService ventas;
  private final RejectPaymentService rechazos;
  private final PointsPurchaseService puntos;
  private final GetMovementService comprobante;

  public PaymentResolutionService(
      PaymentRepository pagos,
      ConfirmSaleService ventas,
      RejectPaymentService rechazos,
      PointsPurchaseService puntos,
      GetMovementService comprobante) {
    this.pagos = pagos;
    this.ventas = ventas;
    this.rechazos = rechazos;
    this.puntos = puntos;
    this.comprobante = comprobante;
  }

  /** `RF-MV-044`: el pago entró, y el movimiento con él. */
  @Transactional
  public SaleResponse confirm(UUID paymentId, String providerReference) {
    String referencia = referencia(providerReference);
    PaymentTarget pago = resoluble(paymentId, "confirmar");
    if ("VENTA".equals(pago.movementType())) {
      ventas.confirmPayment(pago.movementId(), referencia);
    } else {
      puntos.confirmPayment(pago.movementId(), referencia);
    }
    return comprobante.get(pago.movementId());
  }

  /** `RF-MV-045`: el pago no entró; el movimiento sigue según su tipo. */
  @Transactional
  public SaleResponse reject(UUID paymentId, String reason) {
    // El motivo antes de bloquear nada (`spec.md` §8).
    RejectionReason motivo = new RejectionReason(reason);
    PaymentTarget pago = resoluble(paymentId, "rechazar");
    if ("VENTA".equals(pago.movementType())) {
      rechazos.rejectPayment(pago.movementId(), motivo);
    } else {
      puntos.rejectPayment(pago.movementId(), motivo);
    }
    return comprobante.get(pago.movementId());
  }

  // ---------------------------------------------------------------------------

  /** Bloquea el movimiento, lee el pago y comprueba en el orden de `spec.md` §10. */
  private PaymentTarget resoluble(UUID paymentId, String accion) {
    pagos.lockMovementOf(paymentId).orElseThrow(PaymentResolutionService::noExiste);
    PaymentTarget pago =
        pagos.findTarget(paymentId).orElseThrow(PaymentResolutionService::noExiste);

    if (!"VENTA".equals(pago.movementType()) && !"COMPRA_PUNTOS".equals(pago.movementType())) {
      String mensaje =
          "RETIRO".equals(pago.movementType())
              ? "El pago es de un retiro: se resuelve al aprobarlo o negarlo."
              : "El pago es de un movimiento " + pago.movementType() + ", que no se concilia.";
      throw conflicto("EX-002", "payment", mensaje);
    }
    if (!"PENDIENTE".equals(pago.status())) {
      // El estado en el mensaje: quien reintenta sabe que ya se hizo (`spec.md` §10).
      throw conflicto("EX-003", "status", "El pago no está pendiente: está " + pago.status() + ".");
    }
    if (pago.tieneCobroAbierto()) {
      throw conflicto(
          "EX-004",
          "payment",
          "El pago tiene un cobro con tarjeta abierto en la pasarela: lo "
              + ("confirmar".equals(accion) ? "confirma" : "rechaza")
              + " su notificación, no una persona.");
    }
    return pago;
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

  private static ResourceNotFoundException noExiste() {
    return new ResourceNotFoundException("EX-001", "No existe un pago con ese identificador.");
  }

  private static BusinessRuleException conflicto(String codigo, String campo, String mensaje) {
    return new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }
}
