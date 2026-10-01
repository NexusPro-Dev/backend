package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.WithdrawalDestinationResponse;
import com.factech.nexus.modules.movements.domain.models.PayoutAccountNumber;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementDetailView;
import com.factech.nexus.modules.movements.domain.repository.PayoutAccountRepository;
import com.factech.nexus.modules.movements.domain.repository.PayoutAccountRepository.AccountRow;
import com.factech.nexus.modules.movements.domain.repository.WithdrawalDestinationRepository;
import com.factech.nexus.modules.movements.domain.repository.WithdrawalDestinationRepository.DestinationRow;
import com.factech.nexus.modules.system.users.application.PayoutHolderLookup;
import com.factech.nexus.modules.system.users.application.PayoutHolderLookup.HolderView;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * <b>A dónde se paga un retiro</b> (`RN-MV-056`; `RF-MV-019` · `plan.md` §12): resolver la cuenta,
 * escribir la copia y leerla para el detalle (`RF-MV-007` · `plan.md` §12).
 *
 * <p><b>La copia se arma aquí una sola vez</b>: lo que se guarda al pedir y lo que publica el
 * detalle salen del mismo registro, y la respuesta del retiro y la del detalle no pueden discrepar.
 */
@Component
class WithdrawalDestinations {

  private final PayoutAccountRepository cuentas;
  private final WithdrawalDestinationRepository copias;
  private final PayoutHolderLookup titulares;

  WithdrawalDestinations(
      PayoutAccountRepository cuentas,
      WithdrawalDestinationRepository copias,
      PayoutHolderLookup titulares) {
    this.cuentas = cuentas;
    this.copias = copias;
    this.titulares = titulares;
  }

  /**
   * La cuenta indicada, o la principal, convertida en la copia del destino. <b>No toca ningún
   * saldo</b>: va antes de retener, y cualquier rechazo deja todo como estaba.
   */
  DestinationRow resolve(UUID quien, UUID cuentaId) {
    AccountRow cuenta =
        cuentas
            .findForWithdrawal(quien, cuentaId)
            .orElseThrow(
                () -> {
                  if (cuentaId != null) {
                    String mensaje = "No tiene una cuenta de cobro viva con ese identificador.";
                    return new UnprocessableEntityException(
                        "EX-007",
                        mensaje,
                        List.of(new FieldError("payoutAccountId", "EX-007", mensaje)));
                  }
                  String mensaje =
                      "No tiene ninguna cuenta de cobro: registre una para saber a dónde"
                          + " pagarle.";
                  return new BusinessRuleException(
                      "EX-006",
                      mensaje,
                      List.of(new FieldError("payoutAccountId", "EX-006", mensaje)));
                });
    if (!cuenta.institutionActive()) {
      String mensaje =
          "La entidad "
              + cuenta.institutionName()
              + " está desactivada: elija otra cuenta de cobro.";
      throw new BusinessRuleException(
          "EX-008", mensaje, List.of(new FieldError("payoutAccountId", "EX-008", mensaje)));
    }
    HolderView titular =
        titulares
            .holderOf(quien)
            .orElseThrow(() -> new IllegalStateException("La persona autenticada no existe."));
    if (!titular.hasDocument()) {
      String mensaje =
          "Su cuenta no tiene documento de identidad, y sin él no hay titular a quien pagar."
              + " Pida a administración que lo complete.";
      throw new BusinessRuleException(
          "EX-009", mensaje, List.of(new FieldError("user", "EX-009", mensaje)));
    }
    return new DestinationRow(
        cuenta.id(),
        cuenta.institutionCode(),
        cuenta.institutionName(),
        cuenta.institutionKind(),
        cuenta.accountType(),
        cuenta.number(),
        titular.fullName(),
        titular.documentType(),
        titular.documentNumber());
  }

  void save(UUID retiroId, DestinationRow destino) {
    copias.insert(retiroId, destino);
  }

  /**
   * El destino de un detalle, <b>solo si es un retiro</b>: una venta no gana ninguna sentencia
   * (`RF-MV-007` · `plan.md` §12).
   */
  WithdrawalDestinationResponse deRetiro(MovementDetailView detalle) {
    if (!"RETIRO".equals(detalle.header().type())) {
      return null;
    }
    return copias
        .findByMovement(detalle.header().id())
        .map(WithdrawalDestinations::respuesta)
        .orElse(null);
  }

  /** Para la auditoría: lo mismo, con el número enmascarado. */
  static Map<String, Object> enmascarado(DestinationRow d) {
    Map<String, Object> foto = new LinkedHashMap<>();
    foto.put("payout_account_id", d.payoutAccountId().toString());
    foto.put("institution_code", d.institutionCode());
    foto.put("account_type", d.accountType());
    foto.put("number", PayoutAccountNumber.masked(d.number()));
    return foto;
  }

  static WithdrawalDestinationResponse respuesta(DestinationRow d) {
    return new WithdrawalDestinationResponse(
        d.payoutAccountId(),
        new WithdrawalDestinationResponse.Institution(
            d.institutionCode(), d.institutionName(), d.institutionKind()),
        d.accountType(),
        d.number(),
        new WithdrawalDestinationResponse.Holder(
            d.holderName(), d.holderDocumentType(), d.holderDocumentNumber()));
  }
}
