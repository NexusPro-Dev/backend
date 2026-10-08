package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionBatchesPaymentResponse;
import com.factech.nexus.modules.commissions.application.CommissionBatchesPaymentResponse.BatchPaymentResult;
import com.factech.nexus.modules.commissions.application.PayCommissionBatchesRequest;
import com.factech.nexus.shared.error.DomainException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <b>Pagar varios lotes es pagar cada uno por su cuenta</b> (`RF-CM-025`, `RN-CM-049`).
 *
 * <p><b>Este servicio NO es {@code @Transactional}, y no debe serlo.</b> Cada lote se paga con
 * {@link PayCommissionBatchService#pay}, que cruza el proxy de Spring y abre y confirma <b>su
 * propia</b> transacción: bloqueo, abono, marca y auditoría, exactamente como un pago suelto. Con
 * una transacción aquí, la excepción del tercer lote la marcaría entera para revertir y se llevaría
 * los dos primeros pagos — justo lo que el responsable del proyecto descartó (`CA-CM-308`).
 *
 * <p><b>Lo que impide pagar un lote no es un error de la petición</b>: es una fila, con el código y
 * el mensaje que daría pagarlo solo. Un fallo inesperado también, con un motivo genérico, y se
 * registra: los demás lotes siguen.
 */
@Service
public class PayCommissionBatchesService {

  private static final Logger LOG = LoggerFactory.getLogger(PayCommissionBatchesService.class);

  private final PayCommissionBatchService pago;
  private final EmptyBatchesAfterPayment trasElPago;

  public PayCommissionBatchesService(
      PayCommissionBatchService pago, EmptyBatchesAfterPayment trasElPago) {
    this.pago = pago;
    this.trasElPago = trasElPago;
  }

  public CommissionBatchesPaymentResponse payAll(PayCommissionBatchesRequest peticion) {
    List<UUID> lotes = verificar(peticion);
    List<BatchPaymentResult> resultados = new ArrayList<>(lotes.size());
    for (UUID lote : lotes) {
      resultados.add(pagarUno(lote));
    }
    CommissionBatchesPaymentResponse respuesta = CommissionBatchesPaymentResponse.de(resultados);
    // `RN-CM-052` (08-10-2026): si se pagó alguno, los pendientes vacíos se borran una vez, al
    // final, con todos los pagos ya confirmados (`plan.md` §12).
    if (respuesta.paidCount() > 0) {
      trasElPago.run();
    }
    return respuesta;
  }

  private BatchPaymentResult pagarUno(UUID lote) {
    try {
      return BatchPaymentResult.pagado(pago.pay(lote));
    } catch (DomainException e) {
      return BatchPaymentResult.noPagado(lote, e.errorCode(), e.getMessage());
    } catch (RuntimeException e) {
      LOG.error("No se pudo pagar el lote {} dentro de un pago de varios.", lote, e);
      return BatchPaymentResult.noPagado(
          lote, "EX-001", "No se pudo pagar el lote por un fallo inesperado; los demás siguen.");
    }
  }

  /** `VAL-001` y `VAL-003`, juntos. `VAL-002` —el tope de cien— se retiró el 01-10-2026. */
  private static List<UUID> verificar(PayCommissionBatchesRequest peticion) {
    List<UUID> lotes =
        peticion == null || peticion.batchIds() == null ? List.of() : peticion.batchIds();
    List<FieldError> problemas = new ArrayList<>();
    if (lotes.isEmpty()) {
      problemas.add(new FieldError("batchIds", "VAL-001", "Indique al menos un lote."));
    }
    Set<UUID> vistos = new HashSet<>();
    for (int i = 0; i < lotes.size(); i++) {
      UUID lote = lotes.get(i);
      if (lote == null) {
        problemas.add(
            new FieldError("batchIds[" + i + "]", "VAL-001", "Cada lote es un identificador."));
      } else if (!vistos.add(lote)) {
        problemas.add(
            new FieldError(
                "batchIds[" + i + "]", "VAL-003", "El lote " + lote + " se pide dos veces."));
      }
    }
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La lista de lotes no es válida.", problemas);
    }
    return lotes;
  }
}
