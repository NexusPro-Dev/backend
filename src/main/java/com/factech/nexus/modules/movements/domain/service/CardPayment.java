package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.CardChargeResponse;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository.PendingPayment;
import com.factech.nexus.modules.movements.domain.service.CardGateway.CancelResult;
import com.factech.nexus.modules.movements.domain.service.CardGateway.Charge;
import com.factech.nexus.modules.movements.domain.service.CardGateway.ChargeOrder;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * <b>El cobro con tarjeta</b>: abrirlo al registrar un pago (`RF-MV-040`), retomarlo o empezarlo
 * después (`RF-MV-042`), y cancelarlo antes de cerrar un pago (`RF-MV-005`, `RF-MV-018`).
 *
 * <p>Lo llaman las entradas <b>después de guardar el pago</b>, dentro de su transacción, como
 * llaman a {@link PointsPayment} cuando el método es {@code POINTS}. <b>Sin cobro no hay compra</b>
 * (`RN-MV-057`): si la pasarela falla, la excepción revierte el movimiento y el pago. Y si lo que
 * falla es el {@code COMMIT} después de abrir el cobro, la sincronización registrada al abrirlo lo
 * <b>cancela</b>, para que no quede un cobro sin pago que alguien pudiera pagar.
 *
 * <p>Sus rechazos llevan el código de la regla, {@code RN-MV-057}, como los de {@link
 * PointsPayment} llevan los suyos: entra por cinco rutas con numeraciones distintas.
 */
@Component
public class CardPayment {

  private static final Logger LOG = LoggerFactory.getLogger(CardPayment.class);
  static final String CODIGO = "RN-MV-057";

  /**
   * Lo mínimo que la pasarela cobra, por moneda. <b>Una moneda que no esté aquí no admite
   * tarjeta</b>: es preferible rechazar a cobrar en una moneda que nadie revisó.
   */
  private static final Map<String, BigDecimal> MINIMOS = Map.of("USD", new BigDecimal("0.50"));

  private final CardGateway pasarela;
  private final PaymentRepository pagos;

  public CardPayment(CardGateway pasarela, PaymentRepository pagos) {
    this.pasarela = pasarela;
    this.pagos = pagos;
  }

  /** ¿Cobra este método la pasarela, y está encendida? */
  public boolean cobraLaPasarela(PaymentMethodView metodo) {
    return metodo.gateway() != null && pasarela.enabled();
  }

  /**
   * `RF-MV-040`: abre el cobro de un pago recién registrado, si el método lo cobra la pasarela y
   * está encendida; si no, nada, y el pago queda pendiente como siempre.
   *
   * @return el cobro, o nulo si no tocaba abrirlo
   */
  public CardChargeResponse abrirSiToca(
      PaymentMethodView metodo,
      UUID pago,
      UUID movimiento,
      String codigoDelMovimiento,
      BigDecimal importe,
      String moneda,
      int decimales,
      String clave) {
    if (!cobraLaPasarela(metodo)) {
      return null;
    }
    return abrir(pago, movimiento, codigoDelMovimiento, importe, moneda, decimales, clave);
  }

  /**
   * `RF-MV-040`, para quien acaba de abrir el pago y no tiene a mano la moneda: lo lee del pago
   * pendiente del movimiento, que es el recién abierto.
   */
  public CardChargeResponse abrirSiToca(PaymentMethodView metodo, UUID movimiento) {
    if (!cobraLaPasarela(metodo)) {
      return null;
    }
    PendingPayment pendiente =
        pagos
            .lockPendingOf(movimiento)
            .orElseThrow(() -> new IllegalStateException("El pago recién abierto no está."));
    return abrir(
        pendiente.paymentId(),
        pendiente.movementId(),
        pendiente.movementCode(),
        pendiente.amount(),
        pendiente.currencyCode(),
        pendiente.currencyDecimals(),
        pendiente.idempotencyKey());
  }

  /**
   * `RF-MV-040` · `FA-002`: la misma petición repetida devuelve <b>el mismo cobro</b>, si el pago
   * sigue pendiente con él; si no, nada.
   */
  public CardChargeResponse cobroExistente(UUID movimiento) {
    PendingPayment pendiente = pagos.lockPendingOf(movimiento).orElse(null);
    if (pendiente == null || !pendiente.tieneCobroAbierto() || !pasarela.enabled()) {
      return null;
    }
    try {
      Charge cobro = pasarela.retrieve(pendiente.providerReference());
      return cobro.canceled()
          ? null
          : new CardChargeResponse(pendiente.paymentId(), pasarela.name(), cobro.clientSecret());
    } catch (CardGateway.Unavailable fallo) {
      throw noDisponible("La pasarela de pago no respondió; intente de nuevo.");
    }
  }

  /**
   * `RF-MV-042`: el cobro del pago pendiente con tarjeta de un movimiento propio —el mismo si ya
   * existe, uno nuevo si no—.
   */
  @Transactional
  public CardChargeResponse retomar(UUID movimiento, UUID actor) {
    PendingPayment pendiente =
        pagos
            .lockPendingOwn(movimiento, actor)
            .orElseThrow(() -> sinPagoPendiente(movimiento, actor));
    if (pendiente.gateway() == null) {
      throw conflicto(
          "El pago pendiente es con "
              + pendiente.methodCode()
              + ", no con tarjeta: para cambiar"
              + " de método, vuelva a pagar.");
    }
    if (!pasarela.enabled()) {
      throw noDisponible("La pasarela de pago no está configurada en este entorno.");
    }
    if (pendiente.providerReference() == null) {
      return abrir(
          pendiente.paymentId(),
          pendiente.movementId(),
          pendiente.movementCode(),
          pendiente.amount(),
          pendiente.currencyCode(),
          pendiente.currencyDecimals(),
          pendiente.idempotencyKey());
    }
    Charge cobro;
    try {
      cobro = pasarela.retrieve(pendiente.providerReference());
    } catch (CardGateway.Unavailable fallo) {
      throw noDisponible("La pasarela de pago no respondió; intente de nuevo.");
    }
    if (cobro.canceled()) {
      throw conflicto("El cobro con tarjeta fue cancelado: vuelva a pagar para abrir otro.");
    }
    return new CardChargeResponse(pendiente.paymentId(), pasarela.name(), cobro.clientSecret());
  }

  /** Si el pago pendiente del movimiento tiene cobro abierto, lo cancela (`RF-MV-005`). */
  public void cancelarSiHayCobroAbierto(
      UUID movimiento, String codigoYaCobrado, String codigoSinRespuesta) {
    pagos
        .lockPendingOf(movimiento)
        .filter(PendingPayment::tieneCobroAbierto)
        .ifPresent(p -> cancelarCobro(p, codigoYaCobrado, codigoSinRespuesta));
  }

  /**
   * Cancela el cobro abierto de un pago pendiente, antes de cerrarlo (`RN-MV-058`).
   *
   * @param codigoYaCobrado el código de error de quien llama si la pasarela ya lo cobró
   * @param codigoSinRespuesta el de la pasarela que no responde
   */
  public void cancelarCobro(
      PendingPayment pendiente, String codigoYaCobrado, String codigoSinRespuesta) {
    CancelResult resultado;
    try {
      resultado = pasarela.cancel(pendiente.providerReference());
    } catch (CardGateway.Unavailable fallo) {
      String mensaje = "La pasarela de pago no respondió al cancelar el cobro; intente de nuevo.";
      throw new ServiceUnavailableException(
          codigoSinRespuesta,
          mensaje,
          List.of(new FieldError("payments", codigoSinRespuesta, mensaje)));
    }
    if (resultado == CancelResult.YA_COBRADO) {
      String mensaje =
          "La pasarela ya cobró la tarjeta: el pago se confirmará con su notificación.";
      throw new BusinessRuleException(
          codigoYaCobrado, mensaje, List.of(new FieldError("payments", codigoYaCobrado, mensaje)));
    }
  }

  // ---------------------------------------------------------------------------

  private CardChargeResponse abrir(
      UUID pago,
      UUID movimiento,
      String codigoDelMovimiento,
      BigDecimal importe,
      String moneda,
      int decimales,
      String clave) {
    BigDecimal minimo = MINIMOS.get(moneda);
    if (minimo == null) {
      String mensaje = "La moneda " + moneda + " no admite pago con tarjeta.";
      throw new UnprocessableEntityException(
          CODIGO, mensaje, List.of(new FieldError("paymentMethodId", CODIGO, mensaje)));
    }
    if (importe.compareTo(minimo) < 0) {
      String mensaje =
          "La tarjeta no cobra menos de " + minimo.toPlainString() + " " + moneda + ".";
      throw new UnprocessableEntityException(
          CODIGO, mensaje, List.of(new FieldError("paymentMethodId", CODIGO, mensaje)));
    }
    Charge cobro;
    try {
      cobro =
          pasarela.open(
              new ChargeOrder(
                  unidadMinima(importe, decimales),
                  moneda,
                  clave,
                  pago,
                  movimiento,
                  codigoDelMovimiento));
    } catch (CardGateway.Unavailable fallo) {
      LOG.warn("La pasarela no abrió el cobro del pago {}: {}", pago, fallo.getMessage());
      throw noDisponible("La pasarela de pago no respondió; intente de nuevo.");
    }
    pagos.setProviderReference(pago, cobro.reference());
    deshacerSiSeRevierte(cobro.reference());
    return new CardChargeResponse(pago, pasarela.name(), cobro.clientSecret());
  }

  /** {@code 49.00} con dos decimales son {@code 4900} centavos. */
  static long unidadMinima(BigDecimal importe, int decimales) {
    return importe.movePointRight(decimales).longValueExact();
  }

  /**
   * Si la transacción que abrió el cobro termina revertida, el cobro se cancela: sin pago que lo
   * respalde, nadie debe poder pagarlo.
   */
  private void deshacerSiSeRevierte(String referencia) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int estado) {
            if (estado != STATUS_ROLLED_BACK) {
              return;
            }
            try {
              pasarela.cancel(referencia);
            } catch (RuntimeException fallo) {
              LOG.error(
                  "No se pudo cancelar el cobro {} tras revertirse su pago: queda sin pago que"
                      + " lo respalde.",
                  referencia,
                  fallo);
            }
          }
        });
  }

  /**
   * Lo ajeno y lo inexistente responden igual (`EX-001`); lo propio sin pago pendiente, conflicto.
   */
  private RuntimeException sinPagoPendiente(UUID movimiento, UUID actor) {
    return pagos.isOwnChargeable(movimiento, actor)
        ? conflicto("La compra no tiene un pago pendiente: está pagada, anulada o rechazada.")
        : new ResourceNotFoundException(
            "EX-001", "No existe una compra propia con ese identificador.");
  }

  private static BusinessRuleException conflicto(String mensaje) {
    return new BusinessRuleException(
        "EX-002", mensaje, List.of(new FieldError("payments", "EX-002", mensaje)));
  }

  private static ServiceUnavailableException noDisponible(String mensaje) {
    return new ServiceUnavailableException(
        CODIGO, mensaje, List.of(new FieldError("payments", CODIGO, mensaje)));
  }
}
