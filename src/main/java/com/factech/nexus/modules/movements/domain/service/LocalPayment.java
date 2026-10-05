package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.LocalChargeResponse;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.repository.CountryConversionRateRepository;
import com.factech.nexus.modules.movements.domain.repository.CountryConversionRateRepository.ConversionRow;
import com.factech.nexus.modules.movements.domain.repository.LocalChargeRepository;
import com.factech.nexus.modules.movements.domain.repository.LocalChargeRepository.PayerRow;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository;
import com.factech.nexus.modules.movements.domain.repository.PaymentRepository.PendingPayment;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ServiceUnavailableException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>El cobro por la pasarela local</b> (`RF-MV-048`, `RF-MV-051`, `RN-MV-063`): el gemelo de
 * {@link CardPayment} para `PSE`. Abre el cobro en la moneda del país de quien paga, con el precio
 * de cobro de su conversión vigente, redondeado hacia arriba a unidad entera, y guarda en el pago
 * cómo se convirtió. <b>No confirma nada</b>: eso es {@link LocalChargeReconciler}.
 */
@Component
public class LocalPayment {

  private static final Logger LOG = LoggerFactory.getLogger(LocalPayment.class);
  static final String CODIGO = "RN-MV-063";

  private final LocalPaymentGateway pasarela;
  private final PaymentRepository pagos;
  private final LocalChargeRepository cobros;
  private final CountryConversionRateRepository conversiones;
  private final Clock reloj;

  @Autowired
  public LocalPayment(
      LocalPaymentGateway pasarela,
      PaymentRepository pagos,
      LocalChargeRepository cobros,
      CountryConversionRateRepository conversiones) {
    this(pasarela, pagos, cobros, conversiones, Clock.systemUTC());
  }

  LocalPayment(
      LocalPaymentGateway pasarela,
      PaymentRepository pagos,
      LocalChargeRepository cobros,
      CountryConversionRateRepository conversiones,
      Clock reloj) {
    this.pasarela = pasarela;
    this.pagos = pagos;
    this.cobros = cobros;
    this.conversiones = conversiones;
    this.reloj = reloj;
  }

  /** ¿Lo cobra la pasarela local, y está encendida? */
  public boolean cobraLaPasarela(PaymentMethodView metodo) {
    return metodo != null && esLocal(metodo.gateway()) && pasarela.enabled();
  }

  static boolean esLocal(String gateway) {
    return LocalPaymentGateway.NOMBRE.equals(gateway);
  }

  /**
   * Tras registrar el pago pendiente del movimiento: abre el cobro si su método lo cobra la
   * pasarela local. Nulo si no toca, o si está apagada (`FA-001`).
   */
  public LocalChargeResponse abrirSiToca(PaymentMethodView metodo, UUID movimiento) {
    if (!cobraLaPasarela(metodo)) {
      return null;
    }
    PendingPayment pendiente =
        pagos
            .lockPendingOf(movimiento)
            .orElseThrow(() -> new IllegalStateException("El pago recién abierto no está."));
    return abrir(pendiente);
  }

  /** El cobro ya abierto del pago pendiente, para la misma petición repetida (`FA-002`). */
  public LocalChargeResponse cobroExistente(UUID movimiento) {
    return cobros
        .findOpenCharge(movimiento)
        .map(
            c ->
                new LocalChargeResponse(
                    c.paymentId(),
                    LocalPaymentGateway.NOMBRE,
                    c.checkoutUrl(),
                    new SaleResponse.Money(c.currencyId(), c.currencyCode()),
                    c.amount()))
        .orElse(null);
  }

  /** `RF-MV-051`: abrir o retomar el cobro local de una compra propia pendiente. */
  @Transactional
  public LocalChargeResponse retomar(UUID movimiento, UUID actor) {
    PendingPayment pendiente =
        pagos
            .lockPendingOwn(movimiento, actor)
            .orElseThrow(() -> sinPagoPendiente(movimiento, actor));
    if (!esLocal(pendiente.gateway())) {
      throw conflicto(
          "EX-002",
          "El pago pendiente es con "
              + pendiente.methodCode()
              + ", no con la pasarela local: para cambiar de método, vuelva a pagar.");
    }
    if (!pasarela.enabled()) {
      throw noDisponible("EX-004", "La pasarela local no está configurada en este entorno.");
    }
    if (pendiente.providerReference() != null) {
      return cobroExistente(movimiento);
    }
    return abrir(pendiente);
  }

  // ---------------------------------------------------------------------------

  private LocalChargeResponse abrir(PendingPayment pendiente) {
    PayerRow quien =
        cobros
            .findPayer(pendiente.movementId())
            .orElseThrow(() -> new IllegalStateException("El movimiento no tiene titular."));
    ConversionRow conversion =
        conversiones
            .current(quien.countryId(), OffsetDateTime.now(reloj))
            .orElseThrow(
                () ->
                    conflicto(
                        CODIGO,
                        "El país "
                            + quien.countryCode()
                            + " no tiene conversión vigente: no se puede cobrar por la"
                            + " pasarela local."));
    if (!conversion.baseCurrencyCode().equals(pendiente.currencyCode())) {
      throw conflicto(
          CODIGO,
          "La conversión del país "
              + quien.countryCode()
              + " es desde "
              + conversion.baseCurrencyCode()
              + ", y el pago es en "
              + pendiente.currencyCode()
              + ".");
    }
    BigDecimal local = convertir(pendiente.amount(), conversion.payInPrice());

    LocalPaymentGateway.LocalCharge cobro;
    try {
      cobro =
          pasarela.open(
              new LocalPaymentGateway.LocalChargeOrder(
                  pendiente.paymentId(),
                  pendiente.movementCode(),
                  local.movePointRight(conversion.currencyDecimalPlaces()).longValueExact(),
                  conversion.currencyCode(),
                  new LocalPaymentGateway.Payer(
                      quien.firstName(),
                      quien.lastName(),
                      quien.email(),
                      quien.documentNumber(),
                      quien.phone(),
                      quien.countryCode())));
    } catch (LocalPaymentGateway.Unavailable fallo) {
      LOG.warn(
          "La pasarela local no abrió el cobro del pago {}: {}",
          pendiente.paymentId(),
          fallo.getMessage());
      throw noDisponible(CODIGO, "La pasarela local no respondió; intente de nuevo.");
    } catch (LocalPaymentGateway.Rejected rechazo) {
      throw new UnprocessableEntityException(
          CODIGO,
          rechazo.getMessage(),
          List.of(new FieldError("paymentMethodId", CODIGO, rechazo.getMessage())));
    }
    cobros.setCharge(
        pendiente.paymentId(),
        cobro.reference(),
        conversion.currencyId(),
        local,
        conversion.id(),
        cobro.checkoutUrl());
    return new LocalChargeResponse(
        pendiente.paymentId(),
        LocalPaymentGateway.NOMBRE,
        cobro.checkoutUrl(),
        new SaleResponse.Money(conversion.currencyId(), conversion.currencyCode()),
        local);
  }

  /** `RN-MV-063`: el importe por el precio de cobro, hacia arriba a unidad entera. */
  static BigDecimal convertir(BigDecimal importe, BigDecimal precioDeCobro) {
    return importe.multiply(precioDeCobro).setScale(0, RoundingMode.CEILING);
  }

  private RuntimeException sinPagoPendiente(UUID movimiento, UUID actor) {
    return pagos.isOwnChargeable(movimiento, actor)
        ? conflicto(
            "EX-002", "La compra no tiene un pago pendiente: está pagada, anulada o rechazada.")
        : new ResourceNotFoundException(
            "EX-001", "No existe una compra propia con ese identificador.");
  }

  private static BusinessRuleException conflicto(String codigo, String mensaje) {
    return new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError("payments", codigo, mensaje)));
  }

  private static ServiceUnavailableException noDisponible(String codigo, String mensaje) {
    return new ServiceUnavailableException(
        codigo, mensaje, List.of(new FieldError("payments", codigo, mensaje)));
  }
}
