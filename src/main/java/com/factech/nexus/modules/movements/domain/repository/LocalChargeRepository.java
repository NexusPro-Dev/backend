package com.factech.nexus.modules.movements.domain.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El cobro por la pasarela local sobre {@code payments} (`RN-MV-063`, `RN-MV-064`; `RF-MV-048` a
 * `RF-MV-051`). Aparte de {@link PaymentRepository} porque sus lecturas son otras: quién paga y de
 * qué país, y qué cobros preguntar en el barrido.
 */
public interface LocalChargeRepository {

  /** Escribe el cobro en el pago: las cuatro columnas a la vez (`ck_payments_cobro_local`). */
  void setCharge(
      UUID paymentId,
      String reference,
      UUID currencyId,
      BigDecimal chargeAmount,
      UUID conversionRateId,
      String checkoutUrl);

  /** El cobro local abierto del pago pendiente del movimiento, si lo tiene. */
  Optional<OpenCharge> findOpenCharge(UUID movementId);

  /** Quien paga el movimiento —su titular—, con los datos que la pasarela pide. */
  Optional<PayerRow> findPayer(UUID movementId);

  /**
   * La tienda que abrió el cobro local del pago: la de la conversión con que se abrió —no la del
   * país que tenga hoy quien paga—, con la clave <b>vigente</b> de esa misma tienda en ese país,
   * por si se cambió después. Vacío si el pago no tiene cobro local o su conversión no tenía
   * tienda.
   */
  Optional<ChargeShop> findChargeShop(UUID paymentId, OffsetDateTime at);

  /** El pago, bloqueado, para aplicar lo que dijo la pasarela. */
  Optional<ReconcileTarget> lockForReconcile(UUID paymentId);

  /** Los pagos pendientes con cobro local abierto desde antes del instante, hasta el lote. */
  List<UUID> toReconcile(OffsetDateTime openedBefore, int limit);

  /** Marca `COBRO_TARDIO` en un pago rechazado sin incidencia. Falso si no estaba así. */
  boolean markLateCharge(UUID paymentId, OffsetDateTime at);

  record OpenCharge(
      UUID paymentId,
      String reference,
      String checkoutUrl,
      UUID currencyId,
      String currencyCode,
      BigDecimal amount) {}

  record PayerRow(
      UUID userId,
      String firstName,
      String lastName,
      String email,
      String documentNumber,
      String phone,
      UUID countryId,
      String countryCode) {}

  /** La tienda del cobro, con su clave cifrada y el país con que se cifró. */
  record ChargeShop(UUID countryId, String shopId, String shopSecretKey) {}

  record ReconcileTarget(
      UUID paymentId,
      UUID movementId,
      String movementType,
      String status,
      String gateway,
      BigDecimal chargeAmount,
      String chargeCurrencyCode,
      int chargeCurrencyDecimals) {}
}
