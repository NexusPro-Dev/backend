package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.domain.repository.GatewayEventRepository;
import com.factech.nexus.modules.movements.domain.repository.GatewayEventRepository.StoredEvent;
import com.factech.nexus.modules.movements.domain.repository.LocalChargeRepository;
import com.factech.nexus.modules.movements.domain.repository.LocalChargeRepository.ChargeShop;
import com.factech.nexus.modules.movements.domain.repository.LocalChargeRepository.ReconcileTarget;
import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway.LocalTransaction;
import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway.Notice;
import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway.Outcome;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <b>Los avisos de la pasarela local, y lo que de verdad se hace con un cobro</b> (`RF-MV-049`,
 * `RN-MV-064`).
 *
 * <p>El aviso <b>no se cree</b>: PayRetailers no lo firma. Se guarda tal como llegó y solo dice por
 * qué pago preguntar. {@link #conciliar} pregunta a la pasarela —con nuestras credenciales— y
 * aplica <b>la respuesta</b>. Es el mismo método que usa el barrido ({@link LocalChargeSweep}): las
 * dos entradas no pueden discrepar porque ninguna decide.
 */
@Component
public class LocalChargeReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(LocalChargeReconciler.class);
  static final String CODIGO = "RN-MV-064";
  static final String NO_APROBADO = "La pasarela local cerró el cobro sin aprobarlo: ";

  private final LocalPaymentGateway pasarela;
  private final GatewayEventRepository eventos;
  private final LocalChargeRepository cobros;
  private final ShopSecrets secretos;
  private final ConfirmSaleService ventas;
  private final RejectPaymentService rechazos;
  private final PointsPurchaseService puntos;
  private final ApplicationEventPublisher avisos;
  private final TransactionTemplate tx;
  private final TaskExecutor hilos;
  private final boolean enLinea;
  private final Clock reloj;

  @Autowired
  public LocalChargeReconciler(
      LocalPaymentGateway pasarela,
      GatewayEventRepository eventos,
      LocalChargeRepository cobros,
      ShopSecrets secretos,
      ConfirmSaleService ventas,
      RejectPaymentService rechazos,
      PointsPurchaseService puntos,
      ApplicationEventPublisher avisos,
      PlatformTransactionManager transacciones,
      @Qualifier("applicationTaskExecutor") TaskExecutor hilos,
      @Value("${nexus.payretailers.process-inline:false}") boolean enLinea) {
    this.pasarela = pasarela;
    this.eventos = eventos;
    this.cobros = cobros;
    this.secretos = secretos;
    this.ventas = ventas;
    this.rechazos = rechazos;
    this.puntos = puntos;
    this.avisos = avisos;
    this.tx = new TransactionTemplate(transacciones);
    // REQUIRES_NEW: después del COMMIT de quien recibió, una REQUIRED se sumaría a la que ya
    // terminó, como en `GatewayEventProcessor`.
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.hilos = hilos;
    this.enLinea = enLinea;
    this.reloj = Clock.systemUTC();
  }

  public record Received(UUID id) {}

  /** Lo que se hizo con un cobro: el desenlace que se anota en el aviso. */
  public record Desenlace(String outcome, String error) {
    static final Desenlace SIN_CAMBIOS = new Desenlace("IGNORADO", null);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-049` — recibir
  // ---------------------------------------------------------------------------

  /** Guarda el aviso y responde. Lo que sigue va después del `COMMIT` ({@link #onReceived}). */
  @Transactional
  public void receive(byte[] cuerpo) {
    if (!pasarela.enabled()) {
      throw new ServiceUnavailableException(
          CODIGO, "La pasarela local no está configurada en este entorno.");
    }
    String texto = cuerpo == null ? "" : new String(cuerpo, StandardCharsets.UTF_8);
    Notice aviso =
        pasarela
            .parse(texto)
            .orElseThrow(
                () -> {
                  String mensaje = "El aviso no se puede leer: falta su identificador o su pago.";
                  return new ValidationException(
                      CODIGO, mensaje, List.of(new FieldError("body", CODIGO, mensaje)));
                });
    UUID id = UUID.randomUUID();
    String tipo = aviso.announcedStatus() == null ? "aviso" : aviso.announcedStatus();
    if (eventos.insertIfNew(id, LocalPaymentGateway.NOMBRE, aviso.externalId(), tipo, texto)) {
      avisos.publishEvent(new Received(id));
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReceived(Received recibido) {
    if (enLinea) {
      porAviso(recibido.id());
    } else {
      hilos.execute(() -> porAviso(recibido.id()));
    }
  }

  /** El aviso guardado: busca su pago y, si lo hay, pregunta por él. Nunca lanza. */
  public void porAviso(UUID eventoId) {
    try {
      Optional<StoredEvent> guardado = tx.execute(e -> eventos.lockPending(eventoId));
      if (guardado == null || guardado.isEmpty()) {
        return; // procesado ya
      }
      Notice aviso = pasarela.parse(guardado.get().payload()).orElse(null);
      UUID pago = aviso == null ? null : aviso.paymentId();
      boolean esDeUnPago =
          pago != null
              && Boolean.TRUE.equals(tx.execute(e -> cobros.lockForReconcile(pago).isPresent()));
      // `CA-MV-615`: un aviso que no es de ningún pago no provoca ninguna consulta.
      Desenlace d =
          esDeUnPago
              ? conciliar(pago)
              : new Desenlace("IGNORADO", "El aviso no corresponde a ningún pago.");
      UUID referido = esDeUnPago ? pago : null;
      tx.executeWithoutResult(
          e ->
              eventos.finish(
                  eventoId, d.outcome(), d.error(), referido, OffsetDateTime.now(reloj)));
    } catch (RuntimeException fallo) {
      LOG.warn(
          "El aviso {} de la pasarela local no se pudo procesar: {}", eventoId, fallo.toString());
      try {
        tx.executeWithoutResult(
            e ->
                eventos.finish(
                    eventoId,
                    "ERROR",
                    recortar(fallo.toString()),
                    null,
                    OffsetDateTime.now(reloj)));
      } catch (RuntimeException otro) {
        LOG.error("No se pudo anotar el fallo del aviso {}.", eventoId, otro);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Lo común al aviso y al barrido
  // ---------------------------------------------------------------------------

  /**
   * Pregunta a la pasarela por el cobro del pago —fuera de toda transacción— y aplica la respuesta
   * en una corta, con el pago bloqueado.
   *
   * @throws LocalPaymentGateway.Unavailable si la pasarela no respondió: el pago no cambia
   */
  public Desenlace conciliar(UUID pagoId) {
    // La tienda que lo abrió: la de su conversión (`RN-MV-063`).
    ChargeShop tienda =
        tx.execute(e -> cobros.findChargeShop(pagoId, OffsetDateTime.now(reloj)).orElse(null));
    if (tienda == null) {
      return new Desenlace("IGNORADO", "El pago no tiene cobro local con tienda.");
    }
    Optional<LocalTransaction> respuesta =
        pasarela.findByTracking(
            pagoId,
            new LocalPaymentGateway.Shop(
                tienda.shopId(), secretos.decrypt(tienda.shopSecretKey(), tienda.countryId())));
    return tx.execute(e -> aplicar(pagoId, respuesta.orElse(null)));
  }

  private Desenlace aplicar(UUID pagoId, LocalTransaction t) {
    ReconcileTarget pago = cobros.lockForReconcile(pagoId).orElse(null);
    if (pago == null) {
      return new Desenlace("IGNORADO", "El pago ya no existe.");
    }
    if (t == null || t.outcome() == Outcome.PENDIENTE) {
      return Desenlace.SIN_CAMBIOS; // el cliente aún no pagó: se volverá a preguntar
    }
    if (t.outcome() == Outcome.APROBADO) {
      return aprobado(pago, t);
    }
    // Fallido, rechazado, cancelado o caducado: el pago se rechaza y ese cobro no se reintenta.
    if ("PENDIENTE".equals(pago.status())) {
      String motivo = NO_APROBADO + t.rawStatus() + ".";
      if ("COMPRA_PUNTOS".equals(pago.movementType())) {
        puntos.rejectByGateway(pago.movementId(), motivo);
      } else {
        rechazos.rejectByGateway(pago.movementId(), motivo);
      }
      return new Desenlace("PROCESADO", null);
    }
    return Desenlace.SIN_CAMBIOS;
  }

  private Desenlace aprobado(ReconcileTarget pago, LocalTransaction t) {
    if ("CONFIRMADO".equals(pago.status())) {
      return new Desenlace("IGNORADO", "El pago ya estaba confirmado.");
    }
    if ("RECHAZADO".equals(pago.status())) {
      // `RN-MV-064`: el dinero entró y la venta ya no lo esperaba. Se devuelve a mano.
      cobros.markLateCharge(pago.paymentId(), OffsetDateTime.now(reloj));
      return new Desenlace("PROCESADO", "Cobro tardío: el pago ya estaba rechazado.");
    }
    if (pago.chargeAmount() == null) {
      return new Desenlace("ERROR", "El pago no tiene cobro local.");
    }
    long esperado = pago.chargeAmount().movePointRight(pago.chargeCurrencyDecimals()).longValue();
    boolean importe = t.amountMinor() != null && t.amountMinor() == esperado;
    boolean moneda =
        t.currency() != null && t.currency().equalsIgnoreCase(pago.chargeCurrencyCode());
    if (!importe || !moneda) {
      return new Desenlace(
          "ERROR",
          "La pasarela aprobó "
              + t.amountMinor()
              + " "
              + t.currency()
              + " y el cobro es de "
              + esperado
              + " "
              + pago.chargeCurrencyCode()
              + ": hay que revisarlo a mano.");
    }
    if ("COMPRA_PUNTOS".equals(pago.movementType())) {
      puntos.confirmByGateway(pago.movementId(), t.uid());
    } else {
      ventas.confirmByGateway(pago.movementId());
    }
    return new Desenlace("PROCESADO", null);
  }

  /** El importe de un pago en la unidad mínima de su moneda local, para las pruebas. */
  static long unidadMinima(BigDecimal importe, int decimales) {
    return importe.movePointRight(decimales).longValueExact();
  }

  private static String recortar(String texto) {
    return texto.length() > 480 ? texto.substring(0, 480) : texto;
  }
}
