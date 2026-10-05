package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.domain.repository.GatewayEventRepository;
import com.factech.nexus.modules.movements.domain.repository.GatewayEventRepository.StoredEvent;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository.ReferencedPayment;
import com.factech.nexus.modules.movements.domain.service.CardGateway.GatewayEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <b>Procesar</b> una notificación de la pasarela (`RF-MV-041` · `plan.md` §1, segundo tiempo).
 *
 * <p>Después del {@code COMMIT} de quien la recibió, <b>en segundo plano</b>: la pasarela ya tiene
 * su respuesta. Cada notificación se procesa en <b>su propia transacción</b> —la que aplica y la
 * que anota el desenlace son la misma—, tomada con {@code FOR UPDATE SKIP LOCKED}, de modo que el
 * oyente y el barrido no la procesan a la vez. <b>Si falla</b>, la transacción se revierte, se
 * anota el intento en otra y la notificación sigue pendiente: el barrido la reintenta cada minuto,
 * hasta cinco veces.
 *
 * <p><b>Lo que no cuadra no se aplica</b> (`EX-003`): un cobro por otro importe o en otra moneda,
 * para un pago que no está pendiente o que no existe, queda con su error y no se reintenta —no va a
 * arreglarse solo—. <b>La incidencia de un pago que todavía no está confirmado sí se reintenta</b>:
 * las notificaciones pueden llegar desordenadas.
 */
@Component
public class GatewayEventProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(GatewayEventProcessor.class);
  static final int INTENTOS = 5;
  private static final String CANCELADO = "Cobro cancelado en la pasarela.";

  private final GatewayEventRepository eventos;
  private final PaymentRepository pagos;
  private final CardGateway pasarela;
  private final ConfirmSaleService ventas;
  private final RejectPaymentService rechazos;
  private final PointsPurchaseService puntos;
  private final TransactionTemplate tx;
  private final TaskExecutor hilos;
  private final Clock reloj;

  @Autowired
  public GatewayEventProcessor(
      GatewayEventRepository eventos,
      PaymentRepository pagos,
      CardGateway pasarela,
      ConfirmSaleService ventas,
      RejectPaymentService rechazos,
      PointsPurchaseService puntos,
      PlatformTransactionManager transacciones,
      @Qualifier("applicationTaskExecutor") TaskExecutor hilos) {
    this.eventos = eventos;
    this.pagos = pagos;
    this.pasarela = pasarela;
    this.ventas = ventas;
    this.rechazos = rechazos;
    this.puntos = puntos;
    this.tx = new TransactionTemplate(transacciones);
    // REQUIRES_NEW: después del COMMIT de quien recibió, una transacción REQUIRED se
    // sumaría a la que ya terminó y nada de lo que se escribe aquí se confirmaría.
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.hilos = hilos;
    this.reloj = Clock.systemUTC();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReceived(GatewayEventIntake.Received aviso) {
    if (pasarela.processInline()) {
      procesar(aviso.id());
    } else {
      hilos.execute(() -> procesar(aviso.id()));
    }
  }

  /** El barrido: lo que quedó pendiente hace más de un minuto, con intentos por gastar. */
  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
  public void barrer() {
    if (!pasarela.enabled()) {
      return;
    }
    for (UUID id :
        eventos.pendingForRetry(
            pasarela.name(), OffsetDateTime.now(reloj).minusMinutes(1), INTENTOS, 100)) {
      procesar(id);
    }
  }

  /** Procesa una notificación. <b>Nunca lanza</b>: lo que falla queda anotado y pendiente. */
  public void procesar(UUID id) {
    try {
      tx.executeWithoutResult(estado -> aplicar(id));
    } catch (RuntimeException fallo) {
      String motivo = fallo.getClass().getSimpleName() + ": " + fallo.getMessage();
      try {
        int llevados =
            tx.execute(e -> eventos.failAttempt(id, motivo, INTENTOS, OffsetDateTime.now(reloj)));
        LOG.warn("La notificación {} falló ({} de {}): {}", id, llevados, INTENTOS, motivo);
      } catch (RuntimeException otro) {
        LOG.error("No se pudo anotar el fallo de la notificación {}.", id, otro);
      }
    }
  }

  // ---------------------------------------------------------------------------

  private void aplicar(UUID id) {
    StoredEvent guardado = eventos.lockPending(id).orElse(null);
    if (guardado == null) {
      return; // procesada ya, o la está procesando otro hilo
    }
    GatewayEvent evento = pasarela.parse(guardado.payload());
    Desenlace d =
        switch (evento.type()) {
          case "payment_intent.succeeded" -> cobrado(evento);
          case "payment_intent.payment_failed" -> tarjetaRechazada(evento);
          case "payment_intent.canceled" -> cancelado(evento);
          case "charge.refunded" -> reembolsado(evento);
          case "charge.dispute.created" -> incidencia(evento, "EN_DISPUTA", null);
          case "charge.dispute.closed" -> disputaCerrada(evento);
          default -> new Desenlace("IGNORADO", null, null);
        };
    eventos.finish(id, d.outcome(), d.error(), d.pago(), OffsetDateTime.now(reloj));
  }

  private record Desenlace(String outcome, String error, UUID pago) {}

  /** Lo que la pasarela dice que no se debe reintentar: queda con su error. */
  private static Desenlace error(String mensaje, UUID pago) {
    return new Desenlace("ERROR", mensaje, pago);
  }

  /** Lo que todavía no se puede aplicar, pero podrá: se lanza y el barrido lo reintenta. */
  private static final class TodaviaNo extends RuntimeException {
    private static final long serialVersionUID = 1L;

    TodaviaNo(String mensaje) {
      super(mensaje);
    }
  }

  private Desenlace cobrado(GatewayEvent evento) {
    ReferencedPayment pago = pagos.findByReference(evento.chargeReference()).orElse(null);
    if (pago == null) {
      return error("El cobro " + evento.chargeReference() + " no corresponde a ningún pago.", null);
    }
    if (evento.paymentIdHint() != null
        && !evento.paymentIdHint().isBlank()
        && !evento.paymentIdHint().equals(pago.paymentId().toString())) {
      return error("El cobro anota otro pago que el que lo referencia.", pago.paymentId());
    }
    if ("CONFIRMADO".equals(pago.status())) {
      return new Desenlace("IGNORADO", "El pago ya estaba confirmado.", pago.paymentId());
    }
    if (!"PENDIENTE".equals(pago.status())) {
      return error(
          "El cobro entró para un pago " + pago.status() + ": hay que revisarlo a mano.",
          pago.paymentId());
    }
    long esperado = CardPayment.unidadMinima(pago.amount(), pago.currencyDecimals());
    boolean importe = evento.amountMinor() != null && evento.amountMinor() == esperado;
    boolean moneda = pago.currencyCode().equalsIgnoreCase(evento.currency());
    if (!importe || !moneda) {
      return error(
          "El cobro entró por "
              + evento.amountMinor()
              + " "
              + evento.currency()
              + " y el pago es de "
              + esperado
              + " "
              + pago.currencyCode()
              + ".",
          pago.paymentId());
    }
    if ("COMPRA_PUNTOS".equals(pago.movementType())) {
      puntos.confirmByGateway(pago.movementId(), evento.chargeReference());
    } else {
      ventas.confirmByGateway(pago.movementId());
    }
    return new Desenlace("PROCESADO", null, pago.paymentId());
  }

  private Desenlace tarjetaRechazada(GatewayEvent evento) {
    // La tarjeta rechazada NO cierra el pago (§4.6): se registra y sigue pendiente.
    UUID pago =
        pagos
            .findByReference(evento.chargeReference())
            .map(ReferencedPayment::paymentId)
            .orElse(null);
    return new Desenlace("PROCESADO", evento.failureMessage(), pago);
  }

  private Desenlace cancelado(GatewayEvent evento) {
    ReferencedPayment pago = pagos.findByReference(evento.chargeReference()).orElse(null);
    if (pago == null) {
      return error("El cobro cancelado no corresponde a ningún pago.", null);
    }
    if ("PENDIENTE".equals(pago.status())) {
      if ("COMPRA_PUNTOS".equals(pago.movementType())) {
        puntos.rejectByGateway(pago.movementId(), CANCELADO);
      } else {
        rechazos.rejectByGateway(pago.movementId(), CANCELADO);
      }
    }
    // Si ya estaba rechazado, la cancelación la pidió este sistema: nada que hacer.
    return new Desenlace("PROCESADO", null, pago.paymentId());
  }

  private Desenlace reembolsado(GatewayEvent evento) {
    if (evento.refundedMinor() == null || evento.refundedMinor() <= 0) {
      return error("El reembolso no trae importe.", null);
    }
    ReferencedPayment pago = pagos.findByReference(evento.chargeReference()).orElse(null);
    if (pago == null) {
      return error("El reembolso no corresponde a ningún pago.", null);
    }
    BigDecimal devuelto =
        BigDecimal.valueOf(evento.refundedMinor()).movePointLeft(pago.currencyDecimals());
    return incidencia(evento, "REEMBOLSADO", devuelto);
  }

  private Desenlace disputaCerrada(GatewayEvent evento) {
    if ("won".equals(evento.disputeStatus())) {
      return incidencia(evento, "DISPUTA_GANADA", null);
    }
    if ("lost".equals(evento.disputeStatus())) {
      return incidencia(evento, "DISPUTA_PERDIDA", null);
    }
    return error("La disputa se cerró con un estado inesperado: " + evento.disputeStatus(), null);
  }

  private Desenlace incidencia(GatewayEvent evento, String incidencia, BigDecimal devuelto) {
    ReferencedPayment pago = pagos.findByReference(evento.chargeReference()).orElse(null);
    if (pago == null) {
      return error("La incidencia no corresponde a ningún pago.", null);
    }
    if (!pagos.setIncident(pago.paymentId(), incidencia, devuelto, OffsetDateTime.now(reloj))) {
      // Llegó antes que la confirmación: se reintenta (`spec.md` §13).
      throw new TodaviaNo("El pago " + pago.paymentId() + " todavía no está confirmado.");
    }
    return new Desenlace("PROCESADO", null, pago.paymentId());
  }
}
