package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.AccountKind;
import com.factech.nexus.modules.movements.domain.models.EntryEvent;
import com.factech.nexus.modules.movements.domain.models.PointsAmount;
import com.factech.nexus.modules.movements.domain.repository.LedgerRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.PaymentMethodView;
import com.factech.nexus.modules.movements.domain.repository.PointsRateRepository.RateRow;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagar una venta con puntos (`RF-MV-030`, `RN-MV-052`).
 *
 * <p><b>No es una operación: es lo que ocurre después de abrir un pago {@code POINTS}</b>, en la
 * misma transacción que la entrada que lo abrió. Descuenta a la tasa vigente —redondeando hacia
 * arriba— y confirma la venta <b>por {@link ConfirmSaleService}</b>, de modo que la entrega y el
 * aviso a `CM` son exactamente los de una confirmación a mano: hay un solo sitio que confirma.
 *
 * <p><b>Si no alcanza, lanza</b> y la transacción entera se revierte, con la venta y su pago
 * (`CA-MV-337`). El bloqueo de la cuenta que hace el {@link Ledger} es lo que impide gastar dos
 * veces el mismo saldo (`CA-MV-340`).
 */
@Component
public class PointsPayment {

  /** El código del método de los puntos. */
  static final String METODO = "POINTS";

  private static final String MODULO = "MV";
  private static final String ENTIDAD = "movements";

  private final LedgerRepository libro;
  private final Ledger asientos;
  private final LedgerMovements comun;
  private final PointsRateService tasas;
  private final ConfirmSaleService confirmar;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public PointsPayment(
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      PointsRateService tasas,
      ConfirmSaleService confirmar,
      AuditWriter auditoria) {
    this(libro, asientos, comun, tasas, confirmar, auditoria, Clock.systemUTC());
  }

  PointsPayment(
      LedgerRepository libro,
      Ledger asientos,
      LedgerMovements comun,
      PointsRateService tasas,
      ConfirmSaleService confirmar,
      AuditWriter auditoria,
      Clock reloj) {
    this.libro = libro;
    this.asientos = asientos;
    this.comun = comun;
    this.tasas = tasas;
    this.confirmar = confirmar;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  static boolean esPuntos(PaymentMethodView metodo) {
    return METODO.equals(metodo.code());
  }

  /**
   * `EX-003` de `RF-MV-030`: con puntos solo paga quien compra desde su propia cuenta. Lo llaman el
   * registro de un funcionario (`RF-MV-001`) y la venta del alta por enlace (`RF-SP-045`), <b>antes
   * de escribir nada</b>.
   */
  static void rechazarSiEsPuntos(PaymentMethodView metodo) {
    if (esPuntos(metodo)) {
      String mensaje =
          "Con puntos solo paga quien compra desde su propia cuenta: esta venta no los admite.";
      throw new BusinessRuleException(
          "RN-MV-052", mensaje, List.of(new FieldError("paymentMethodId", "RN-MV-052", mensaje)));
    }
  }

  /**
   * Descuenta los puntos del pago pendiente que se acaba de abrir y confirma la venta.
   *
   * @param importe lo que cuesta la venta en su moneda: el importe del pago
   * @return la venta confirmada
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public SaleResponse pagar(
      UUID venta, UUID pago, UUID comprador, UUID monedaId, BigDecimal importe) {
    CurrencyView moneda = comun.moneda(monedaId);
    RateRow tasa = tasas.vigenteOConflicto(moneda, "RN-MV-050");
    BigDecimal puntos = PointsAmount.costo(importe, tasa.pointsPerUnit());
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    UUID cuenta = libro.accountOf(comprador, AccountKind.PUNTOS, moneda.id());
    UUID emitidos = libro.accountOf(null, AccountKind.PUNTOS_EMITIDOS, moneda.id());
    boolean alcanzo =
        asientos
            .apply(
                venta,
                pago,
                EntryEvent.PAGO,
                List.of(new Ledger.Leg(cuenta, puntos.negate()), new Ledger.Leg(emitidos, puntos)),
                ahora)
            .isPresent();
    if (!alcanzo) {
      String mensaje =
          "Los puntos en "
              + moneda.code()
              + " no alcanzan: esta compra cuesta "
              + puntos.toPlainString()
              + " puntos.";
      throw new BusinessRuleException(
          "RN-MV-052", mensaje, List.of(new FieldError("paymentMethodId", "RN-MV-052", mensaje)));
    }

    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("payment_id", pago.toString());
    despues.put("points", puntos.toPlainString());
    despues.put("points_rate_id", tasa.id().toString());
    despues.put("event", EntryEvent.PAGO.name());
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, venta, ChangeAction.UPDATE, Map.of("after", despues)));

    return confirmar.confirmInternal(venta);
  }
}
