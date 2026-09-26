package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.BalancesResponse;
import com.factech.nexus.modules.movements.application.LedgerMovementResponse;
import com.factech.nexus.modules.movements.application.PaymentResponse;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.AccountKind;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.WithdrawalRow;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lo que comparten los requerimientos de la etapa 6 que no venden: validar un importe contra su
 * moneda, y dar forma de respuesta a un movimiento y a unos saldos. Escrito una vez para que un
 * retiro, un bono y un abono se lean igual.
 */
@Component
class LedgerMovements {

  private final MovementRepository movimientos;
  private final LedgerRepository libro;
  private final CurrencyCatalog monedas;

  LedgerMovements(MovementRepository movimientos, LedgerRepository libro, CurrencyCatalog monedas) {
    this.movimientos = movimientos;
    this.libro = libro;
    this.monedas = monedas;
  }

  /** La moneda, o {@code 422} si no existe. */
  CurrencyView moneda(UUID id) {
    if (id == null) {
      throw invalido("currencyId", "VAL-001", "La moneda es obligatoria.");
    }
    return monedas
        .find(id)
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "EX-002",
                    "La moneda indicada no existe.",
                    List.of(
                        new FieldError("currencyId", "EX-002", "La moneda indicada no existe."))));
  }

  /**
   * El importe, mayor que cero y con los decimales de su moneda (`RN-MV-014`), llevado a esa
   * escala. Un importe con decimales de más es un error y no se redondea: quien lo pidió no pidió
   * otra cifra.
   */
  static BigDecimal importe(BigDecimal importe, CurrencyView moneda) {
    if (importe == null || importe.signum() <= 0) {
      throw invalido("amount", "VAL-002", "El importe es obligatorio y mayor que cero.");
    }
    if (importe.stripTrailingZeros().scale() > moneda.decimalPlaces()) {
      throw invalido(
          "amount",
          "VAL-003",
          "El importe no puede tener más de " + moneda.decimalPlaces() + " decimales.");
    }
    return importe.setScale(moneda.decimalPlaces());
  }

  static ValidationException invalido(String campo, String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }

  /** Los tres saldos de una persona en una moneda, como están ahora. */
  BalancesResponse saldos(UUID persona, UUID monedaId, String codigo) {
    return new BalancesResponse(
        new SaleResponse.Money(monedaId, codigo),
        libro.balanceOf(persona, AccountKind.BILLETERA, monedaId),
        libro.balanceOf(persona, AccountKind.RETENIDO, monedaId),
        libro.balanceOf(persona, AccountKind.PUNTOS, monedaId));
  }

  /** El movimiento, releído, con sus pagos. */
  LedgerMovementResponse respuesta(UUID movimiento, String tipo) {
    WithdrawalRow fila =
        movimientos
            .findWithoutLines(movimiento, tipo)
            .orElseThrow(() -> new IllegalStateException("El movimiento desapareció."));
    List<PaymentResponse> pagos = new ArrayList<>();
    movimientos
        .findById(movimiento)
        .ifPresent(detalle -> pagos.addAll(SaleDetailMapper.pagos(detalle.payments())));
    return new LedgerMovementResponse(
        fila.id(),
        fila.code(),
        fila.type(),
        fila.status(),
        fila.amount(),
        new SaleResponse.Money(fila.currencyId(), fila.currencyCode()),
        fila.concept(),
        fila.occurredAt(),
        fila.confirmedAt(),
        fila.rejectedAt(),
        fila.rejectionReason(),
        pagos);
  }
}
